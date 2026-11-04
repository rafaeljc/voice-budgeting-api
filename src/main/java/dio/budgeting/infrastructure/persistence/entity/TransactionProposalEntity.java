package dio.budgeting.infrastructure.persistence.entity;

import dio.budgeting.domain.Category;
import dio.budgeting.domain.Money;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionProposal;
import dio.budgeting.domain.TransactionProposalId;
import dio.budgeting.domain.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransactionProposalEntity {
    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(length = Transaction.DESCRIPTION_MAX_LENGTH)
    private String description;

    @Column(precision = 18, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private Category category;

    @Column(nullable = false)
    private Instant createdAt;

    public static TransactionProposalEntity from(TransactionProposal proposal) {
        return new TransactionProposalEntity(
                proposal.getId().uuid(),
                proposal.getUserId().uuid(),
                proposal.getDescription(),
                proposal.getAmount().value(),
                proposal.getCategory(),
                proposal.getCreatedAt());
    }

    public TransactionProposal toDomain() {
        return new TransactionProposal(
                new TransactionProposalId(this.id),
                new UserId(this.userId),
                this.description,
                new Money(this.amount),
                this.category,
                this.createdAt);
    }
}
