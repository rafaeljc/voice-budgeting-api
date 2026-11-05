package dio.budgeting.infrastructure.http;

import static dio.budgeting.support.ApiPaths.TRANSACTION_PROPOSALS_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import dio.budgeting.domain.Category;
import dio.budgeting.infrastructure.persistence.entity.TransactionProposalEntity;
import dio.budgeting.infrastructure.persistence.repository.TransactionEntityRepository;
import dio.budgeting.infrastructure.persistence.repository.TransactionProposalEntityRepository;
import dio.budgeting.support.IntegrationTest;
import dio.budgeting.support.security.AccessTokens;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;

// The decisions must overlap in the database, so the calls go through a real server instead of MockMvc
@IntegrationTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class TransactionProposalConcurrencyIT {
    private static final int CONFIRMATIONS = 10;

    @LocalServerPort
    private int port;

    private final TransactionProposalEntityRepository proposalRepository;
    private final TransactionEntityRepository transactionRepository;

    TransactionProposalConcurrencyIT(
            TransactionProposalEntityRepository proposalRepository, TransactionEntityRepository transactionRepository) {
        this.proposalRepository = proposalRepository;
        this.transactionRepository = transactionRepository;
    }

    @Test
    void should_createOneTransaction_when_sameProposalIsConfirmedConcurrently() throws Exception {
        // given
        var proposalId = saveProposal();
        var confirmations = Collections.nCopies(CONFIRMATIONS, (Callable<Integer>) () -> decide("confirm", proposalId));

        // when
        var statuses = callTogether(confirmations);

        // then
        assertThat(Collections.frequency(statuses, 201)).isEqualTo(1);
        assertThat(Collections.frequency(statuses, 404)).isEqualTo(CONFIRMATIONS - 1);
        assertThat(transactionRepository.count()).isEqualTo(1);
        assertThat(proposalRepository.findById(proposalId)).isEmpty();
    }

    @Test
    void should_letOnlyOneDecisionWin_when_confirmingAndCancelingConcurrently() throws Exception {
        // given
        var proposalId = saveProposal();

        // when
        var statuses = callTogether(List.of(() -> decide("confirm", proposalId), () -> decide("cancel", proposalId)));

        // then
        var confirmWon = statuses.get(0) == 201;
        assertThat(statuses).isIn(List.of(201, 404), List.of(404, 204));
        assertThat(transactionRepository.count()).isEqualTo(confirmWon ? 1 : 0);
        assertThat(proposalRepository.findById(proposalId)).isEmpty();
    }

    private List<Integer> callTogether(List<Callable<Integer>> calls) throws Exception {
        var ready = new CountDownLatch(calls.size());
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(calls.size())) {
            var futures = calls.stream()
                    .map(call -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return call.call();
                    }))
                    .toList();
            ready.await();
            start.countDown();
            var statuses = new ArrayList<Integer>();
            for (var future : futures) {
                statuses.add(future.get(30, TimeUnit.SECONDS));
            }
            return statuses;
        }
    }

    private int decide(String action, UUID proposalId) {
        return RestClient.create("http://localhost:" + port)
                .post()
                .uri(TRANSACTION_PROPOSALS_PATH + "/{id}/" + action, proposalId)
                .header(AUTHORIZATION, AccessTokens.authorization())
                .exchange((request, response) -> response.getStatusCode().value());
    }

    private UUID saveProposal() {
        var id = UUID.randomUUID();
        var now = Instant.now();
        proposalRepository.save(new TransactionProposalEntity(
                id,
                AccessTokens.DEFAULT_USER.uuid(),
                "Mercado",
                new BigDecimal("80.00"),
                Category.GROCERIES,
                now,
                now.plus(Duration.ofMinutes(5))));
        return id;
    }
}
