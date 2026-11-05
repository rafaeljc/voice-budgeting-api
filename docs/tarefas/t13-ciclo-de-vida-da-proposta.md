# T13 — Ciclo de vida da proposta

Fazer as propostas de transação expirarem após um prazo configurável (FN-04): uma proposta vencida não pode mais ser
confirmada nem cancelada, e uma rotina agendada apaga as vencidas em lotes. Provar com teste concorrente que
confirmar a mesma proposta mais de uma vez não cria transações duplicadas (RES-07).

## Escopo

**Dentro:**
- Idempotência: mantém o comportamento da T12 (segunda decisão → 404 `transaction-proposal-not-found`, nenhuma
  transação em dobro) e acrescenta `TransactionProposalConcurrencyIT`:
  - 10 confirmações simultâneas da mesma proposta → exatamente um `201`, nove `404`, uma transação, proposta apagada
  - confirmar e cancelar ao mesmo tempo → um vence (`201` ou `204`), o outro `404`; uma transação se a confirmação
    venceu, nenhuma se o cancelamento venceu; proposta apagada nos dois casos
- Vencimento no domínio:
  - `domain/TransactionProposalTimeToLive(Duration value)` — `record`; recusa nulo, zero e negativo
  - `TransactionProposal` ganha `expiresAt`: o construtor de criação recebe o prazo e calcula
    `expiresAt = createdAt + prazo` (invariante `expiresAt > createdAt`); a reidratação recebe `expiresAt` pronto
  - `TransactionProposal.isPendingAt(Instant instant)` = `instant.isBefore(expiresAt)`: pendente antes de `expiresAt`,
    vencida a partir dele (inclusive)
- Configuração `budgeting.transaction-proposals` (`infrastructure/config/TransactionProposalProperties`,
  `@Validated`, público): `time-to-live=5m`, `expiration-sweep-interval=1m`, `expiration-batch-size=1000`
  (`@Positive`), `expiration-sweep-enabled=true`; `TransactionProposalConfiguration` expõe o bean
  `TransactionProposalTimeToLive`
- Persistência: coluna `expires_at` `@Column(nullable = false)` e índice `@Table(indexes = @Index(columnList =
  "expires_at"))`, sem nome, como os da `TransactionEntity`
- Comando de voz: o `ProcessVoiceCommandUseCase` recebe o `TransactionProposalTimeToLive` pelo construtor e grava a
  proposta com o vencimento; `transactionProposal` na resposta ganha `expiresAt` (mesmo tipo e formato do
  `createdAt` do `TransactionResponse`, ISO-8601 UTC)
- Confirmar e cancelar: `findByIdAndUserIdForUpdate(id, userId).filter(found -> found.isPendingAt(now))
  .orElseThrow(TransactionProposalNotFoundException::new)`; proposta vencida → mesmo 404, nada gravado, linha deixada
  para a faxina. O `CancelTransactionProposalUseCase` passa a depender do `Clock`
- `detail` do 404 `transaction-proposal-not-found`: "A proposta de transação não existe, já foi decidida ou expirou."
  (`type` e `title` não mudam)
- Faxina em lotes:
  - porta `TransactionProposalRepository.deleteExpiredBatch(Instant expiredAt, int batchSize)` → `int` (quantidade
    apagada)
  - adaptador: `TransactionProposalEntityRepository` passa a estender `JpaRepository`;
    `findByExpiresAtLessThanEqualOrderByExpiresAt(Instant expiredAt, Limit limit)` + `deleteAllInBatch(expired)`,
    devolve `expired.size()`; sem `FOR UPDATE` (comentário do porquê no adaptador)
  - `application/DeleteExpiredTransactionProposalBatchUseCase` (`@Transactional`), `int execute(Instant expiredAt,
    int batchSize)`: apaga um lote
  - `infrastructure/scheduling/TransactionProposalExpirationJob`: `@Scheduled(fixedDelayString =
    "${budgeting.transaction-proposals.expiration-sweep-interval}")`; lê o `Clock` uma vez; chama o use case enquanto
    o lote vier cheio (cada chamada é uma transação, pelo proxy); soma o total; log `INFO`
    `Transaction proposals expired` (`count`) só quando `count > 0`;
    `@ConditionalOnBooleanProperty(name = "budgeting.transaction-proposals.expiration-sweep-enabled",
    matchIfMissing = true)`
  - `infrastructure/config/SchedulingConfiguration` com `@EnableScheduling`
  - perfil `test`: `budgeting.transaction-proposals.expiration-sweep-enabled=false`
- Documentação: `docs/openapi.yaml`, README, `docs/arquitetura.md`, regras (`architecture.md`, `error-handling.md`,
  `testing.md`), `CLAUDE.md` (`infrastructure/scheduling/`, nomenclatura `<Substantivo>Job`) e `docs/tarefas.md`

**Fora:**
- confirmação repetida devolvendo a mesma resposta (o 404 da T12 continua)
- trava entre instâncias (ShedLock) — só uma faxina por vez na mesma instância, garantida pelo `fixedDelay`
- log ou evento por proposta expirada (T20, T22)
- ADR
- o prazo na fala do assistente (T14)
- migração de dados existentes; deploy
- `docs/exemplos/voz.http` (o exemplo do 404 continua válido)
- teste de presença do índice
- chamada à OpenAI real em qualquer teste ou verificação

## Decisões

- Idempotência da confirmação = segunda decisão responde 404, como na T12 e no ADR 0005; a T13 só prova com teste
  concorrente — o requisito ("não cria transações duplicadas") já é atendido, e devolver a mesma resposta reabriria
  o ADR recém-aceito (decisão do usuário)
- Prazo de 5 minutos, configurável — a confirmação acontece logo depois de ouvir a resposta; passado isso, a proposta
  está fora de contexto (FN-04) (decisão do usuário)
- Proposta vencida não pode ser confirmada nem cancelada, mesmo antes da faxina — a expiração vale no instante exato
  e não depende de a rotina rodar; a faxina é só limpeza (decisão do usuário)
- `expiresAt` gravado, `NOT NULL`, calculado na criação — mudar o prazo só afeta propostas novas, e a faxina compara
  direto com a coluna indexada; exposto ao cliente na resposta do comando de voz (decisão do usuário)
- Prazo como value object do domínio (`TransactionProposalTimeToLive`), entregue como bean a partir das propriedades —
  a regra do vencimento fica na entidade, a `application` não conhece a chave da configuração e prazo inválido falha
  ao subir (decisão do usuário)
- Checagem do vencimento no domínio (`isPendingAt`), depois da busca bloqueante, sem negações — regra testada sem
  banco, porta inalterada (decisão do usuário)
- Faxina com `fixedDelay` de 1 minuto — o intervalo conta a partir do fim da execução anterior; uma execução lenta
  nunca se sobrepõe à seguinte na mesma instância. Nunca `fixedRate` (decisão do usuário)
- Faxina em lotes com transação própria, tamanho configurável (padrão 1000, `@Positive`) — não segura bloqueios nem a
  transação por muito tempo quando há muitas vencidas; todos os lotes de uma execução usam o mesmo instante, para a
  execução terminar (decisão do usuário)
- O laço dos lotes fica no job, e o use case apaga um lote — uma transação por lote pelo proxy, sem
  `TransactionTemplate` na `application` e sem mudar as regras (decisão do usuário)
- Nome `DeleteExpiredTransactionProposalBatchUseCase` e porta `deleteExpiredBatch` — diz que apaga um lote; evita o
  `InBatch` do Spring Data, que significa "tudo numa instrução" (decisão do usuário)
- Lote por consulta derivada do Spring Data com `Limit` + `deleteAllInBatch`, sem SQL nativo nem `FOR UPDATE` —
  forma nativa do Spring; nenhuma proposta vencida é alterada por outro caminho entre o `SELECT` e o `DELETE`
  (confirmar e cancelar a recusam, ela nunca volta a ficar pendente e a faxina não se sobrepõe na instância)
  (decisão do usuário)
- Log da faxina só com a quantidade, uma linha por execução com algo apagado — evento por proposta fica para T20/T22
  (decisão do usuário)
- Job desligável por `expiration-sweep-enabled` (`@ConditionalOnBooleanProperty`), desligado no perfil `test` — a
  faxina não apaga propostas no meio de outro teste; com intervalo longo ela ainda rodaria na inicialização
  (decisão do usuário)
- `@EnableScheduling` em `infrastructure/config/SchedulingConfiguration` — uma `@Configuration` por assunto, como a
  `ClockConfiguration` (decisão do usuário)
- `ProcessVoiceCommandUseCaseTest` recebe o prazo por `@Spy` com instância real — o `@InjectMocks` só injeta
  `@Mock`/`@Spy` (decisão do usuário)
- Novo `detail` do 404 cita a expiração, mantendo um corpo único (decisão do usuário)
- Só o índice de `expires_at`, declarado na entidade sem nome e com o nome da coluna, como os da `TransactionEntity`
  (conferido no MySQL com um teste temporário, removido), sem teste permanente — confirmar e cancelar usam a chave
  primária (decisão do usuário)
- Sem ADR — consequência direta do ADR 0005, registrada no plano, nas regras e em comentários (decisão do usuário)
- `DeleteExpiredTransactionProposalBatchUseCase` sem teste unitário — só delega à porta (sem lógica); coberto pelo
  `*IT` do adaptador e pelo teste do job
- Três fases; tipo `feat` (o contrato muda: `expiresAt` e o `detail` do 404)

## Definição de pronto

- [ ] `env -u OPENAI_API_KEY -u JWT_SECRET ./gradlew build` passa (Spotless, Checkstyle, `*Test`, `*IT`, JaCoCo)
- [ ] comando de registro → `transactionProposal.expiresAt` = criação + 5 min, igual ao gravado
- [ ] confirmar ou cancelar proposta vencida → 404 `transaction-proposal-not-found` com o novo `detail`, nenhuma
      transação, proposta mantida para a faxina
- [ ] `deleteExpiredBatch` apaga no máximo `batchSize` vencidas (`expires_at <= instante`), preserva as pendentes e
      devolve a quantidade
- [ ] o job repete os lotes com o mesmo instante até um lote incompleto e registra `Transaction proposals expired`
      com a soma só quando apagou algo
- [ ] 10 confirmações simultâneas → um `201`, nove `404`, uma transação; confirmar × cancelar → um vence, o outro 404,
      banco coerente; o teste falha sem o `@Lock` (conferido e revertido)
- [ ] job ausente no perfil `test`; `fixedDelay` (não `fixedRate`) com comentário do porquê
- [ ] `docs/openapi.yaml`, README, `docs/arquitetura.md`, regras, `CLAUDE.md` e `docs/tarefas.md` atualizados;
      checkbox da T13 marcado no último commit

## Para futuros agentes

Execute com `/executar-tarefa`. Durante a execução:

- marque `- [x]` no mesmo commit que conclui o item;
- antes do último commit de uma fase, rode o plano de verificação, escreva o resumo da fase com o resultado e
  mude o status para `Concluída` — tudo nesse mesmo commit;
- na última fase, no mesmo commit, confira a definição de pronto e escreva o resumo final (e o plano de deploy,
  se houver);
- mudança no plano só com aprovação do usuário, registrada em Decisões com o motivo.

Contexto para uma sessão nova:

- Branch `feat/ciclo-de-vida-da-proposta`; título do PR
  `feat: adiciona expiração das propostas e testa a confirmação concorrente`. Commits `feat:`/`test:`/`refactor:`
  (código e testes) e `docs:` (documentação e regras).
- Estado antes da tarefa: `TransactionProposal` (`id`, `userId`, `description`, `amount`, `category`, `createdAt`;
  `@AllArgsConstructor` de reidratação + construtor de criação que valida) sem vencimento;
  `ConfirmTransactionProposalUseCase` e `CancelTransactionProposalUseCase` (`@Transactional`) buscam com
  `findByIdAndUserIdForUpdate` (`@Lock(PESSIMISTIC_WRITE)` no `TransactionProposalEntityRepository`, hoje
  `CrudRepository`) e apagam; o `ProcessVoiceCommandUseCase` cria a proposta com `Instant.now(clock)` e a grava por
  último, depois da voz; não há `@EnableScheduling` nem job.
- Pontos de criação a ajustar ao novo construtor: `ProcessVoiceCommandUseCase`, `TransactionProposalEntity`
  (`from`/`toDomain` e o construtor usado pelos testes), `TransactionProposalTest`,
  `ConfirmTransactionProposalUseCaseTest`, `CancelTransactionProposalUseCaseTest`, `TransactionProposalEndpointsIT`
  (`saveProposal` grava `PROPOSED_AT` fixo de 2026-03-01: o `expiresAt` precisa ser futuro em relação ao relógio real,
  ex.: `Instant.now().plus(5 min)`; manter a asserção de `createdAt` da transação depois da criação da proposta).
- `TransactionProposalOutput` e `TransactionProposalResponse` ganham `expiresAt`; `VoiceCommandIT` confere o campo.
- `ProcessVoiceCommandUseCaseTest`: `@Spy private TransactionProposalTimeToLive timeToLive = new
  TransactionProposalTimeToLive(Duration.ofMinutes(5));`. Se o spy de `record` falhar no Mockito, parar e perguntar.
- `TransactionProposalProperties` precisa ser público (o job, em `scheduling/`, lê o tamanho do lote e o intervalo
  vem do `fixedDelayString`); no `TransactionProposalExpirationJobTest` ele também entra por `@Spy` com instância
  real, e o log é conferido com `OutputCaptureExtension` + `LogEvents`.
- `@Modifying`/`deleteAllInBatch` e o `@Lock` exigem a transação do use case.
- `ddl-auto=update` não acrescenta coluna `NOT NULL` a uma tabela com linhas: banco local com propostas antigas →
  `docker compose down -v` antes do `bootRun`. Nos testes o container é novo.
- `TransactionProposalConcurrencyIT`: `@IntegrationTest(webEnvironment = RANDOM_PORT)`, `RestClient` para a porta
  local, `AccessTokens.authorization()`, `CountDownLatch` para soltar as chamadas juntas, proposta gravada pelo
  `TransactionProposalEntityRepository` com `expiresAt` futuro. Conferir que falha sem o `@Lock` e reverter (não
  commitar a remoção); registrar no resumo da fase.
- Comentários em inglês, só o porquê; documentação em pt-BR; Spotless confere `.properties` e `.md`.

## Fase 1: Vencimento da proposta

Status: Concluída  <!-- Não iniciada | Em andamento | Concluída -->

- [x] [TDD] `TransactionProposalTimeToLive` e vencimento na `TransactionProposal`: `TransactionProposalTimeToLiveTest`
      → nulo, zero e negativo recusados; `TransactionProposalTest` → `expiresAt = createdAt + prazo`, prazo nulo
      recusado, `isPendingAt` verdadeiro um instante antes de `expiresAt` e falso em `expiresAt` e depois
- [x] [TDD] Confirmar e cancelar recusam a proposta vencida: `ConfirmTransactionProposalUseCaseTest` e
      `CancelTransactionProposalUseCaseTest` → `Clock` em `expiresAt` → `TransactionProposalNotFoundException`, nada
      gravado nem apagado; pendente → comportamento atual
- [x] [TDD] `ProcessVoiceCommandUseCase` grava o vencimento: `ProcessVoiceCommandUseCaseTest` → proposta gravada com
      `expiresAt = Instant.now(clock) + 5 min` e devolvida no output (`TransactionProposalOutput.expiresAt`)
- [x] [TDD] Persistência e configuração: `expires_at` `NOT NULL` + `@Index` na `TransactionProposalEntity`;
      `TransactionProposalProperties` e `TransactionProposalConfiguration` (bean do prazo); chaves em
      `application.properties` com comentário; `VoiceCommandIT` → `transactionProposal.expiresAt` na resposta igual
      ao gravado e 5 min depois do `createdAt`; `TransactionProposalEndpointsIT.saveProposal` com `expiresAt` futuro
- [x] [TDD] `TransactionProposalEndpointsIT` → proposta vencida (gravada com `expiresAt` no passado): `confirm` e
      `cancel` → 404 com o novo `detail`, nenhuma transação, proposta mantida
- [x] [TDD] Novo `detail` em `messages.properties` ("A proposta de transação não existe, já foi decidida ou
      expirou."); asserções do `TransactionProposalEndpointsIT` atualizadas
- [x] [verificação] `docs/openapi.yaml`: `expiresAt` (`date-time`, obrigatório) no schema `TransactionProposal`;
      404 de `confirm` e `cancel` citando a proposta expirada, com o novo `detail` nos exemplos; descrição do
      `/transactions/ai` dizendo que a proposta vale até `expiresAt`

### Plano de verificação

- `env -u OPENAI_API_KEY -u JWT_SECRET ./gradlew build` → `BUILD SUCCESSFUL`
- `./gradlew test --tests 'dio.budgeting.domain.TransactionProposal*' --tests 'dio.budgeting.application.*TransactionProposal*' --tests dio.budgeting.application.ProcessVoiceCommandUseCaseTest`
  → testes passam
- `./gradlew integrationTest --tests dio.budgeting.infrastructure.http.TransactionProposalEndpointsIT --tests dio.budgeting.infrastructure.http.VoiceCommandIT`
  → testes passam
- `grep -n "expiresAt" docs/openapi.yaml` → presente no schema `TransactionProposal`
- `grep -rn "não existe ou já foi decidida" src docs --exclude-dir=tarefas` → nenhuma linha

### Resumo da fase

Feito: `TransactionProposalTimeToLive` e `expiresAt` na `TransactionProposal` (`isPendingAt`); confirmar e cancelar
recusam a proposta vencida (404, linha mantida); `ProcessVoiceCommandUseCase` grava o vencimento e a resposta traz
`expiresAt`; `expires_at` `NOT NULL` com índice; `TransactionProposalProperties` (com as quatro chaves) e bean do prazo;
novo `detail` do 404; `docs/openapi.yaml` atualizado.

Decisões: os itens de código e teste dependiam uns dos outros para compilar (o construtor mudou), então entraram juntos
em um commit; o contrato e o fechamento da fase, em outro. `expiresAt` = `createdAt` original + prazo, truncado a
milissegundos como o `createdAt`; sem checagem extra, pois o prazo positivo já garante `expiresAt > createdAt`. Índice
sem nome e com o nome da coluna, como os da `TransactionEntity` (decisão do usuário); conferido no MySQL: índice em
`expires_at` e coluna `datetime(6) NOT NULL`.

Verificação: `env -u OPENAI_API_KEY -u JWT_SECRET ./gradlew build` → `BUILD SUCCESSFUL` (inclui `*Test` e `*IT`);
`grep expiresAt docs/openapi.yaml` presente; `grep` do `detail` antigo fora de `docs/tarefas` → nenhuma linha.

Para uma sessão nova: as chaves da faxina (`expiration-sweep-*`, `expiration-batch-size`) já estão em
`application.properties` e em `TransactionProposalProperties`, mas sem uso; `application-test.properties` ainda não
desliga a faxina (Fase 2).

## Fase 2: Faxina em lotes

Status: Não iniciada  <!-- Não iniciada | Em andamento | Concluída -->

- [ ] [TDD] Porta `deleteExpiredBatch` e adaptador (`JpaRepository`, `findByExpiresAtLessThanEqualOrderByExpiresAt`
      com `Limit`, `deleteAllInBatch`): `JpaTransactionProposalRepositoryIT` (chamado dentro de uma transação, como
      no use case) → lote 2 com 5 vencidas e 1 pendente: 2, 2, 1 e 0; a de `expiresAt` igual ao instante é apagada;
      a pendente fica
- [ ] [TDD] `DeleteExpiredTransactionProposalBatchUseCase` (`@Transactional`, delega à porta) e
      `TransactionProposalExpirationJob`: `TransactionProposalExpirationJobTest` → repete enquanto o lote vem cheio e
      para no incompleto; o mesmo instante (`Clock` lido uma vez) em todas as chamadas; log
      `Transaction proposals expired` com `count` = soma; nenhum log quando nada foi apagado
- [ ] [verificação] Agendamento: `@Scheduled(fixedDelayString = ...)` com comentário do porquê de `fixedDelay`;
      `@ConditionalOnBooleanProperty` no job; `SchedulingConfiguration` com `@EnableScheduling`; chaves em
      `application.properties`; `expiration-sweep-enabled=false` em `application-test.properties`

### Plano de verificação

- `env -u OPENAI_API_KEY -u JWT_SECRET ./gradlew build` → `BUILD SUCCESSFUL`
- `./gradlew test --tests dio.budgeting.infrastructure.scheduling.TransactionProposalExpirationJobTest` → testes passam
- `./gradlew integrationTest --tests dio.budgeting.infrastructure.persistence.JpaTransactionProposalRepositoryIT`
  → testes passam
- `grep -rn "fixedRate" src/main` → nenhuma linha
- `grep -n "expiration-sweep-enabled=false" src/test/resources/application-test.properties` → presente

### Resumo da fase

_(preencher ao concluir a fase)_

## Fase 3: Concorrência e documentação

Status: Não iniciada  <!-- Não iniciada | Em andamento | Concluída -->

- [ ] [TDD] `TransactionProposalConcurrencyIT`: 10 confirmações simultâneas → um `201`, nove `404`, uma transação,
      proposta apagada; confirmar × cancelar simultâneos → um vence, o outro `404`, banco coerente com o vencedor.
      Conferir que falha sem o `@Lock(PESSIMISTIC_WRITE)` e reverter
- [ ] [verificação] README: a proposta expira (5 minutos por padrão) e, depois disso, confirmar ou cancelar responde
      404; `docs/arquitetura.md`: proposta vencida → 404 na "Decisão sobre a proposta" e parágrafo da faxina
      (`fixedDelay`, lotes com transação própria, mesmo instante), sem diagrama novo
- [ ] [verificação] Regras: `architecture.md` (vencimento no domínio com `isPendingAt` e
      `TransactionProposalTimeToLive`; `deleteExpiredBatch`; job como adaptador de entrada em
      `infrastructure/scheduling/` que faz o laço dos lotes, uma transação por lote); `error-handling.md` (404 com
      proposta expirada); `testing.md` (job desligado no perfil `test`; faxina testada pelo job e pelo adaptador);
      `CLAUDE.md` (`infrastructure/scheduling/` na estrutura e `<Substantivo>Job` na nomenclatura)
- [ ] [verificação] `docs/tarefas.md`: nota da T13 (expiração e teste concorrente feitos); notas na T20 e na T22 (a
      faxina registra só a quantidade; evento por proposta expirada exige apagar por ID e registrar cada uma)

### Plano de verificação

- `env -u OPENAI_API_KEY -u JWT_SECRET ./gradlew build` → `BUILD SUCCESSFUL`
- `./gradlew integrationTest --tests dio.budgeting.infrastructure.http.TransactionProposalConcurrencyIT` → testes passam
- `grep -n "isPendingAt" .claude/rules/architecture.md` → presente
- `grep -n "expirada\|expirou" .claude/rules/error-handling.md` → presente
- `grep -n "scheduling/" CLAUDE.md` → presente
- `grep -n "faxina\|expirad" docs/tarefas.md` → notas na T13, T20 e T22

### Resumo da fase

_(preencher ao concluir a fase)_

## Resumo final

_(preencher ao concluir todas as fases)_
