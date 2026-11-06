# T21 — Garantia de entrega da auditoria

Garantir que nenhum registro de auditoria se perca e que uma falha do MongoDB não interrompa o fluxo do cliente
(SEG-05): a publicação de cada evento de domínio é gravada no MySQL, na mesma transação da escrita, pelo registro de
publicação de eventos do Spring Modulith (outbox transacional), e é entregue ao MongoDB pelo menos uma vez, de forma
idempotente, com reenvio periódico das que falharam.

## Escopo

**Dentro:**
- Spring Modulith como outbox:
  - `build.gradle`: `implementation platform("org.springframework.modulith:spring-modulith-bom:<2.1.x>")` (como o
    Spring AI) e `implementation 'org.springframework.modulith:spring-modulith-starter-jpa'`; a tabela
    `event_publication` é criada pelo `ddl-auto=update`, como as nossas
  - o registro grava uma publicação por `@TransactionalEventListener` na transação de quem publica e a conclui quando o
    listener termina sem erro; com `spring.modulith.events.completion-mode=DELETE`, a publicação concluída é apagada
  - *staleness monitor*: `spring.modulith.events.staleness.published=1m`, `.processing=1m` e `.resubmitted=1m` — uma
    publicação parada no estado além do prazo (queda da aplicação) passa a `FAILED` e entra no reenvio
- Eventos de domínio:
  - `domain/event/DomainEventId` — `record DomainEventId(UUID uuid)`, construtor sem argumentos gera
    `UUID.randomUUID()`, construtor compacto rejeita `null` (como os outros `<X>Id`)
  - `DomainEvent` ganha `DomainEventId id()`; `id` é o primeiro componente dos quatro eventos
  - `TransactionProposalCreatedEvent(DomainEventId id, UserId userId, Instant occurredAt, AudioMetadata audio,
    String transcription, TransactionProposalId transactionProposalId, String description, Money amount,
    Category category, Instant expiresAt)` — sem a entidade `TransactionProposal`, que tem dois construtores e não é
    desserializável no reenvio sem anotação Jackson no `domain`
  - os três use cases criam o ID (`new DomainEventId()`) ao montar o evento
- Atomicidade do comando de voz: o `ProcessVoiceCommandUseCase` recebe `TransactionOperations` pelo construtor (o
  `TransactionTemplate` do Boot) e, só no caso de proposta, grava a proposta e publica o evento dentro de
  `transactionOperations.execute(...)`, aberto depois da síntese de voz (nenhuma chamada à IA dentro da transação); o
  não entendido continua fora (a publicação é a única escrita)
- Listener: `DomainEventAuditListener.on` devolve `CompletableFuture<Void>` — sucesso → `completedFuture(null)`;
  `RuntimeException` do use case → `WARN` `Audit entry not appended` (como hoje, `eventType` e `errorType`) e
  `failedFuture(e)`, para o Modulith marcar a publicação `FAILED`; o `@Async` mantém a exceção dentro do *future*, sem o
  `ERROR` com stack trace do `SimpleAsyncUncaughtExceptionHandler`
- Idempotência:
  - `AuditEntryDocument` com `@Id UUID id` = `event.id().uuid()` (no lugar do `ObjectId` gerado); o `_id` já tem
    índice único no MongoDB, sem índice novo
  - `AuditEntryDocumentRepository extends MongoRepository<AuditEntryDocument, UUID>`
  - `MongoAuditEntryRepository.append` captura `DuplicateKeyException` (Spring) e a trata como sucesso, com comentário
    do porquê (entrega pelo menos uma vez; o registro já existe)
  - `AuditEntryDocument.from` lê os campos da proposta direto do `TransactionProposalCreatedEvent`; o documento gravado
    não muda, exceto pelo `_id`
- Reenvio:
  - `infrastructure/scheduling/EventPublicationResubmissionJob` — `@Component`,
    `@ConditionalOnBooleanProperty(name = "budgeting.audit.resubmission-enabled", matchIfMissing = true)`,
    `@Scheduled(fixedDelayString = "${budgeting.audit.resubmission-interval}")`; chama
    `FailedEventPublications.resubmit(ResubmissionOptions.defaults().withMinAge(...).withBatchSize(...))` direto, sem
    use case (operação técnica do framework, sem regra de negócio); sem log próprio
  - `AuditProperties` ganha `@NotNull Duration resubmissionInterval`, `@NotNull Duration resubmissionMinAge`,
    `@Positive int resubmissionBatchSize` e `boolean resubmissionEnabled` (como o `TransactionProposalProperties`)
  - `application.properties`: `budgeting.audit.resubmission-interval=1m`, `resubmission-min-age=1m`,
    `resubmission-batch-size=100`, `resubmission-enabled=true`, com comentário
  - `application-test.properties`: `budgeting.audit.resubmission-enabled=false`, com comentário (como a faxina)
- Logs: `logging.level.org.springframework.modulith.events.support.CompletionRegisteringAdvisor=WARN`, com comentário
  (o `INFO` da falha traz a mensagem da exceção)
- Testes:
  - `DomainEventAuditListenerTest` — sucesso → *future* concluído e use case chamado; use case lança
    `RuntimeException` → *future* concluído com a exceção (substitui o atual "não propaga")
  - `MongoAuditEntryRepositoryIT` — o mesmo evento gravado duas vezes → um único documento, sem exceção, com `_id`
    igual ao ID do evento
  - `ProcessVoiceCommandUseCaseTest`, `ConfirmTransactionProposalUseCaseTest` e `CancelTransactionProposalUseCaseTest`
    conferem o evento publicado com `ArgumentCaptor` e `usingRecursiveComparison().ignoringFields("id")` (o ID é
    aleatório); o `ProcessVoiceCommandUseCaseTest` recebe `@Spy TransactionOperations transactionOperations =
    TransactionOperations.withoutTransaction()`
  - `AuditTrailIT` sem casos novos; continua passando com o `_id` novo e o Modulith no caminho
- Documentação: `CLAUDE.md` (stack), regras `architecture.md`, `error-handling.md` e `testing.md`, `docs/tarefas.md`
  (notas na T18 e na T22; checkbox da T21)

**Fora:**
- ADR da estratégia de garantia de entrega (desvio do DOC-03, ver Decisões)
- `*IT` de falha: MongoDB fora do ar, rollback sem publicação, reenvio de publicação pendente (desvio do QA-03, ver
  Decisões)
- teste da atomicidade do `TransactionOperations`, do job de reenvio e da serialização dos eventos
- esperar as tarefas `@Async` no desligamento (T18)
- métricas das publicações pendentes ou falhas (T22)
- retenção e arquivamento da `event_publication`; `republish-outstanding-events-on-restart`
- mudanças no ADR 0007, no README, em `docs/arquitetura.md`, `docs/openapi.yaml`, `docs/exemplos/` e `docs/escopo.md`
- migração dos registros locais do MongoDB com `_id` `ObjectId`
- deploy
- chamada à OpenAI real em qualquer teste ou verificação

## Decisões

- Outbox transacional no MySQL pelo *Event Publication Registry* do Spring Modulith (JPA), e não outbox próprio nem
  retentativa em memória — só uma gravação durável na transação da escrita cobre as três perdas da T20 (MongoDB fora
  do ar, queda entre o commit e a gravação, tarefas pendentes no desligamento); o Modulith já traz estado, tentativas e
  reenvio, e o `CLAUDE.md` pede o que o framework oferece em vez de reimplementar; a retentativa em memória não cobre a
  queda (decisão do usuário)
- Sem ADR — desvia do DOC-03 que a T21 lista; a estratégia fica registrada neste plano e nas regras (decisão do
  usuário). O ADR 0007 não é reescrito: a consequência "melhor esforço até que a T21 a reforce" fica como histórico
- Entrega pelo menos uma vez, com idempotência pelo `_id` = ID do evento — o `_id` já tem índice único, então o próprio
  `insert` faz a checagem, sem leitura antes e sem corrida; um campo `eventId` separado exigiria um índice único a mais
  e criado à mão (o Spring Data não cria índices por padrão). O UUID aleatório custa um pouco de localidade no índice
  em relação ao `ObjectId`, desprezível no volume da aplicação
- A `DuplicateKeyException` é tratada no `MongoAuditEntryRepository`, não no listener — o listener não conhece
  exceções do MongoDB; sem a captura, um reenvio de evento já gravado falharia para sempre (decisão do usuário)
- `TransactionOperations` só em volta da gravação da proposta e da publicação no comando de voz — `@Transactional` no
  `execute` prenderia uma conexão do Hikari durante as chamadas à IA (até ~96 s); um método privado anotado é ignorado
  pelo proxy; um use case transacional separado violaria "um use case não chama outro use case transacional"; aceitar
  a janela entre os dois commits deixaria um caso de perda conhecido (decisão do usuário). A interface
  `TransactionOperations` (não a classe `TransactionTemplate`) permite o `TransactionOperations.withoutTransaction()` no
  teste, sem mock de classe do framework
- Sem `*IT` de falha — testariam o Modulith e o driver, não o nosso código, como testar se o `@Transactional`
  funciona; desvia do QA-03 que a T21 lista (decisão do usuário)
- Só dois testes novos: o listener devolver a falha ao Modulith e a gravação idempotente — são as duas decisões do
  nosso código das quais a garantia depende e que nenhum outro teste acusaria (decisão do usuário)
- Sem teste de serialização dos eventos — testaria o Jackson; o risco (evento não desserializável, que só falharia no
  reenvio) é coberto por regra de modelagem no `architecture.md`: eventos só carregam `record`s, IDs tipados, enums e
  tipos do JDK (decisão do usuário)
- `FailedEventPublications.resubmit` + *staleness monitor* (1 min por estado), e não
  `resubmitIncompletePublicationsOlderThan` — no Modulith 2.1.x o reenvio com `ResubmissionOptions` só pega `FAILED`;
  uma publicação presa em `PUBLISHED` ou `PROCESSING` depois de uma queda só sai de lá pelo monitor. O
  `OlderThan` pega todos os estados, mas sem lote: com o MongoDB fora do ar por horas, cada rodada carregaria todas as
  pendentes, com as transcrições (decisão do usuário)
- Sem trava (`SELECT ... FOR UPDATE`) durante a entrega — a trava só vale com a transação aberta, o que prenderia uma
  conexão do Hikari por gravação no MongoDB (até ~8 s); com o MongoDB lento, o pool se esgota e o fluxo do cliente
  para, o que a SEG-05 proíbe; o Modulith também não funciona assim. O prazo do monitor troca o sinal de vida da trava
  por até ~1 min de atraso na recuperação (decisão do usuário)
- Job `EventPublicationResubmissionJob` sem use case — reenviar é operação técnica do framework, e um use case em
  `application` teria de importar o Modulith; exceção registrada no `architecture.md` (decisão do usuário)
- Job a cada 1 min (`fixedDelay`), idade mínima de 1 min (bem acima do pior caso de uma entrega, 3 s + 5 s), lote de
  100, desligável por propriedade e desligado no perfil `test` (decisão do usuário)
- Sem `republish-outstanding-events-on-restart` — o job e o monitor cobrem a reinicialização; com várias instâncias, a
  republicação no boot reenviaria publicações que outra instância está entregando (decisão do usuário)
- `completion-mode=DELETE` — a publicação carrega a transcrição serializada; concluída, não fica no MySQL
- Listener devolve `CompletableFuture<Void>` (`failedFuture` na falha), e não `void` com `throw` — a exceção que
  escapa de um `@Async` `void` gera um `ERROR` com stack trace (mensagem crua, proibida pelo `error-handling.md`), e
  silenciar o `SimpleAsyncUncaughtExceptionHandler` esconderia a falha de qualquer `@Async` futuro (decisão do usuário)
- `INFO` do Modulith silenciado por nível de log (`CompletionRegisteringAdvisor=WARN`) — ele traz a mensagem da
  exceção; o nosso `WARN` com `eventType` e `errorType` continua sendo o registro da falha (decisão do usuário)
- `DomainEventId` tipado, primeiro componente de cada evento; `TransactionProposalCreatedEvent` com os dados da
  proposta no mesmo nível (decisão do usuário)
- Registros locais antigos com `_id` `ObjectId` ficam como estão — ambiente de desenvolvimento
- Três fases; tipo `feat`

## Definição de pronto

- [ ] `env -u OPENAI_API_KEY -u JWT_SECRET ./gradlew build` passa (Spotless, Checkstyle, `*Test`, `*IT`, JaCoCo)
- [ ] Spring Modulith 2.1.x no `build.gradle` com `spring-modulith-starter-jpa`; `event_publication` criada no MySQL
      local; `completion-mode=DELETE` e *staleness* de 1 min nos três estados no `application.properties`
- [ ] os quatro eventos têm `DomainEventId id()`; o `TransactionProposalCreatedEvent` não carrega a entidade
- [ ] no comando de voz, a gravação da proposta e a publicação rodam no mesmo `TransactionOperations.execute`
- [ ] o listener devolve `CompletableFuture<Void>` e entrega a falha ao Modulith (`DomainEventAuditListenerTest`)
- [ ] o mesmo evento gravado duas vezes vira um único registro, com `_id` igual ao ID do evento
      (`MongoAuditEntryRepositoryIT`)
- [ ] `EventPublicationResubmissionJob` com `FailedEventPublications.resubmit`, propriedades `budgeting.audit.resubmission-*`
      e desligado no perfil `test`
- [ ] `INFO` do `CompletionRegisteringAdvisor` silenciado, com comentário
- [ ] `AuditTrailIT` passa sem casos novos; a aplicação sobe localmente e `/actuator/health` responde `UP`
- [ ] `CLAUDE.md`, regras e `docs/tarefas.md` atualizados; checkbox da T21 marcado no último commit

## Para futuros agentes

Execute com `/executar-tarefa`. Durante a execução:

- marque `- [x]` no mesmo commit que conclui o item;
- antes do último commit de uma fase, rode o plano de verificação, escreva o resumo da fase com o resultado e
  mude o status para `Concluída` — tudo nesse mesmo commit;
- na última fase, no mesmo commit, confira a definição de pronto e escreva o resumo final (e o plano de deploy,
  se houver);
- mudança no plano só com aprovação do usuário, registrada em Decisões com o motivo.

Contexto para uma sessão nova:

- Branch `feat/garantia-de-entrega-da-auditoria`; título do PR `feat: garante a entrega dos registros de auditoria`.
  Commits `feat:`/`test:`/`refactor:`/`build:` (código, testes, dependências) e `docs:` (documentação e regras).
- Estado antes da tarefa (T20): os use cases publicam os quatro `DomainEvent` com `ApplicationEventPublisher`; o
  `DomainEventAuditListener` (`@Async` + `@TransactionalEventListener(AFTER_COMMIT, fallbackExecution = true)`) chama o
  `AppendAuditEntryUseCase` e captura a `RuntimeException` só com `WARN`; o `MongoAuditEntryRepository` faz `insert`
  de `AuditEntryDocument.from(event, traceId)` com `_id` `ObjectId`. Confirmar e cancelar são `@Transactional`; o
  `ProcessVoiceCommandUseCase` não é (chama a IA) e grava a proposta só depois da síntese de voz. Virtual threads e
  `spring.task.execution.propagate-context=true` já estão ligados; o `traceId` chega à thread assíncrona.
- O Boot não gerencia a versão do Modulith: use o BOM, na versão 2.1.x mais recente compatível com o Boot 4.1.2
  (o 2.1 GA, de junho de 2026, é a linha do Boot 4.1). Se não houver versão compatível ou se o Modulith exigir o
  Jackson 2, parar e perguntar.
- O `PersistentApplicationEventMulticaster` do Modulith grava a publicação mesmo sem transação ativa (em transação
  própria do repositório), por isso o não entendido funciona sem `TransactionOperations`.
- No Modulith 2.1.x, `IncompleteEventPublications.resubmitIncompletePublications(ResubmissionOptions)` e
  `FailedEventPublications.resubmit(ResubmissionOptions)` chamam o mesmo `processFailedPublications` (só `FAILED`);
  o *staleness monitor* marca como `FAILED` as paradas em `PUBLISHED`, `PROCESSING` e `RESUBMITTED`. Conferir o nome da
  propriedade do intervalo de checagem do monitor (se houver) e se o bean `FailedEventPublications` é injetável; se não
  for, parar e perguntar.
- O logger da falha é `CompletionRegisteringAdvisor$CompletionRegisteringMethodInterceptor` (classe aninhada); o
  Logback trata `$` como separador, então o nível em `...CompletionRegisteringAdvisor` deve valer — conferir.
- O interceptor do Modulith trata `CompletableFuture`: falha do *future* → `FAILED`; sucesso → concluída.
- Os testes unitários dos use cases hoje conferem o evento com `verify(eventPublisher).publishEvent(new XEvent(...))`;
  com o ID aleatório, passam a usar `ArgumentCaptor` e `usingRecursiveComparison().ignoringFields("id")`.
- O `MySqlCleaner` faz `TRUNCATE` de todas as tabelas lidas de `information_schema`, incluindo a `event_publication`.
- Se a tabela `event_publication` não for criada pelo `ddl-auto=update` (entidade do Modulith fora do *entity scan*),
  parar e perguntar.
- Logs: nunca transcrição, descrição, valor, categoria nem mensagem da exceção (`error-handling.md`).
- Comentários em inglês, só o porquê; documentação em pt-BR; Spotless confere `.properties`, `.yml` e `.md`.

## Fase 1: Outbox, ID do evento e transação do comando de voz

Status: Em andamento  <!-- Não iniciada | Em andamento | Concluída -->

- [x] [verificação] `build.gradle`: BOM `spring-modulith-bom` 2.1.x por `platform(...)` e
      `spring-modulith-starter-jpa`; conferir a compatibilidade com o Boot 4.1.2 e o Jackson 3
- [x] [verificação] `application.properties`: `spring.modulith.events.completion-mode=DELETE`, com comentário (a
      publicação carrega a transcrição)
- [x] [TDD] `DomainEventId` e `DomainEvent.id()` nos quatro eventos; `TransactionProposalCreatedEvent` com os dados da
      proposta no lugar da entidade; os três use cases criam o ID: `ProcessVoiceCommandUseCaseTest`,
      `ConfirmTransactionProposalUseCaseTest` e `CancelTransactionProposalUseCaseTest` conferem o evento com
      `ArgumentCaptor`, ignorando o `id`, e que ele não é nulo
- [x] [verificação] `ProcessVoiceCommandUseCase` recebe `TransactionOperations` e grava a proposta e publica o
      `TransactionProposalCreatedEvent` dentro de `execute(...)`, com comentário do porquê; o teste recebe
      `@Spy TransactionOperations.withoutTransaction()`
- [ ] [verificação] `AuditEntryDocument` com `@Id UUID id` = `event.id().uuid()` e campos da proposta lidos do evento;
      `AuditEntryDocumentRepository` com chave `UUID`; `AuditTrailIT` passa sem mudança

### Plano de verificação

- `env -u OPENAI_API_KEY -u JWT_SECRET ./gradlew build` → `BUILD SUCCESSFUL`
- `./gradlew dependencyInsight --dependency spring-modulith-events-core --configuration runtimeClasspath` → versão
  2.1.x, registrada no resumo
- `./gradlew integrationTest --tests dio.budgeting.infrastructure.event.AuditTrailIT` → 5 testes passam
- `grep -rn "import org.springframework\|import jakarta\|import tools.jackson" src/main/java/dio/budgeting/domain` →
  nenhuma linha
- `grep -n "TransactionProposal proposal" src/main/java/dio/budgeting/domain/event/*.java` → nenhuma linha
- `bootRun` com o `.env` carregado e `curl -s localhost:8080/actuator/health` → `{"status":"UP"}`; com o MySQL do
  compose, `docker compose exec database sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" transaction -e "SHOW TABLES
  LIKE '\''event_publication'\''"'` → uma linha; encerrar o processo depois

### Resumo da fase

_(preencher ao concluir a fase)_

## Fase 2: Falha, idempotência e reenvio

Status: Não iniciada  <!-- Não iniciada | Em andamento | Concluída -->

- [ ] [TDD] `DomainEventAuditListener.on` devolve `CompletableFuture<Void>`: `DomainEventAuditListenerTest` → sucesso
      → *future* concluído e use case chamado com o evento; use case lança `RuntimeException` → *future* concluído
      com a exceção (o `WARN` continua, sem teste)
- [ ] [TDD] `MongoAuditEntryRepository.append` idempotente: `MongoAuditEntryRepositoryIT` → o mesmo evento gravado
      duas vezes → um único documento em `audit_entries`, sem exceção, com `_id` igual a `event.id().uuid()`
- [ ] [verificação] `application.properties`: `spring.modulith.events.staleness.published=1m`, `.processing=1m`,
      `.resubmitted=1m` (e o intervalo de checagem, se a propriedade existir) e
      `logging.level.org.springframework.modulith.events.support.CompletionRegisteringAdvisor=WARN`, cada grupo com
      comentário do porquê
- [ ] [verificação] `AuditProperties` com `resubmissionInterval`, `resubmissionMinAge`, `resubmissionBatchSize` e
      `resubmissionEnabled`; `budgeting.audit.resubmission-*` no `application.properties` (1m, 1m, 100, `true`) e
      `budgeting.audit.resubmission-enabled=false` no `application-test.properties`, com comentário
- [ ] [verificação] `EventPublicationResubmissionJob` em `infrastructure/scheduling/` (`@ConditionalOnBooleanProperty`,
      `@Scheduled(fixedDelayString = ...)`, `FailedEventPublications.resubmit` com idade mínima e lote), com comentário
      do porquê da idade mínima

### Plano de verificação

- `env -u OPENAI_API_KEY -u JWT_SECRET ./gradlew build` → `BUILD SUCCESSFUL`
- `./gradlew test --tests dio.budgeting.infrastructure.event.DomainEventAuditListenerTest` → testes passam
- `./gradlew integrationTest --tests dio.budgeting.infrastructure.persistence.MongoAuditEntryRepositoryIT` → teste
  passa
- `unzip -l "$(find ~/.gradle -name 'spring-modulith-events-core-2.1*.jar' | head -1)" | grep CompletionRegisteringAdvisor`
  → a classe e a aninhada `$CompletionRegisteringMethodInterceptor` presentes
- `grep -n "staleness\|completion-mode\|CompletionRegisteringAdvisor\|resubmission" src/main/resources/application.properties`
  → as linhas presentes; `grep -n "resubmission-enabled=false" src/test/resources/application-test.properties` →
  presente
- `bootRun` com o `.env` carregado e `curl -s localhost:8080/actuator/health` → `{"status":"UP"}`; encerrar o processo
  depois

### Resumo da fase

_(preencher ao concluir a fase)_

## Fase 3: Documentação

Status: Não iniciada  <!-- Não iniciada | Em andamento | Concluída -->

- [ ] [verificação] `CLAUDE.md`: Spring Modulith (registro de publicação de eventos, JPA) na stack, como outbox da
      auditoria
- [ ] [verificação] `architecture.md`: a publicação do evento é gravada pelo registro do Modulith na transação da
      escrita (outbox) e concluída quando o listener termina; em use case sem transação, a escrita e a publicação
      ficam juntas em `TransactionOperations`; eventos só carregam `record`s, IDs tipados, enums e tipos do JDK (são
      desserializados no reenvio); o `MongoAuditEntryRepository` trata a chave duplicada como sucesso (entrega pelo
      menos uma vez, `_id` = ID do evento), e o listener devolve `CompletableFuture<Void>` e repassa ao Modulith
      qualquer outra falha (`failedFuture`), para a publicação ficar `FAILED` e ser reenviada; o
      `EventPublicationResubmissionJob` chama o Modulith direto, sem use case (exceção registrada); *staleness* para
      as publicações presas depois de uma queda
- [ ] [verificação] `error-handling.md`: o `WARN` `Audit entry not appended` continua, e a publicação fica `FAILED`
      para reenvio; o `INFO` do Modulith com a mensagem da exceção é silenciado; um `@Async` devolve
      `CompletableFuture` para a falha não virar `ERROR` com stack trace
- [ ] [verificação] `testing.md`: o job de reenvio fica desligado no perfil `test`; a garantia de entrega (outbox,
      reenvio, *staleness*) é do framework e não tem `*IT` próprio; a idempotência é coberta pelo
      `MongoAuditEntryRepositoryIT`; os testes de use case conferem o evento ignorando o `id` aleatório
- [ ] [verificação] `docs/tarefas.md`: nota na T18 (o desligamento não perde registro de auditoria: a publicação
      pendente é reenviada depois; esperar as tarefas `@Async` vira só otimização); nota na T22 (listeners de métrica
      usam `@EventListener`, não `@TransactionalEventListener`, senão cada evento ganha uma publicação no registro; a
      quantidade de publicações `FAILED` é métrica candidata); checkbox da T21 marcado

### Plano de verificação

- `env -u OPENAI_API_KEY -u JWT_SECRET ./gradlew build` → `BUILD SUCCESSFUL`
- `grep -n "Modulith" CLAUDE.md .claude/rules/architecture.md .claude/rules/error-handling.md .claude/rules/testing.md`
  → presente nos quatro
- `grep -n "TransactionOperations\|DuplicateKey\|EventPublicationResubmissionJob" .claude/rules/architecture.md` →
  presentes
- `grep -n "Nota (T21)" docs/tarefas.md` → notas na T18 e na T22
- `grep -n "\[x\] \[T21" docs/tarefas.md` → checkbox marcado

### Resumo da fase

_(preencher ao concluir a fase)_

## Resumo final

_(preencher ao concluir todas as fases)_
