# T20 — Auditoria em banco NoSQL

Registrar em um MongoDB separado do MySQL os fatos essenciais do fluxo de voz e da decisão sobre a proposta (SEG-04):
proposta criada, comando não entendido, proposta confirmada e proposta cancelada, com autor, data e hora,
metadados do áudio, transcrição e proposta. A gravação é assíncrona e nunca segura a
requisição. Registrar em ADR a decisão de manter a auditoria em um banco separado do transacional (DOC-03).

## Escopo

**Dentro:**
- Eventos de domínio em `domain/event/`, `record`s que implementam a `sealed interface DomainEvent` (`userId()` e
  `occurredAt()`), sem validação própria (só os use cases os criam, a partir de dados já válidos):
  - `TransactionProposalCreatedEvent(UserId userId, Instant occurredAt,
    AudioMetadata audio, String transcription, TransactionProposal proposal)` — publicado pelo
    `ProcessVoiceCommandUseCase` logo depois de gravar a proposta; `occurredAt` = `createdAt` da proposta gravada
  - `VoiceCommandNotUnderstoodEvent(UserId userId, Instant occurredAt,
    AudioMetadata audio, String transcription)` — publicado pelo `ProcessVoiceCommandUseCase` depois da síntese de
    voz, no mesmo ponto em que a proposta seria gravada; `occurredAt` = `Instant.now(clock)`
  - `TransactionProposalConfirmedEvent(UserId userId, Instant occurredAt,
    TransactionProposalId transactionProposalId, TransactionId transactionId)` — publicado pelo
    `ConfirmTransactionProposalUseCase` depois de criar a transação e apagar a proposta; `occurredAt` = `now`
  - `TransactionProposalCanceledEvent(UserId userId, Instant occurredAt,
    TransactionProposalId transactionProposalId)` — publicado pelo `CancelTransactionProposalUseCase` depois de
    apagar a proposta; `occurredAt` = `now`
  - `AudioMetadata(String format, long sizeBytes)` — o `ProcessVoiceCommandUseCase` monta com
    `input.format().name()` e `input.audio().length` (o `AudioFormat` continua em `application/input`)
  - consulta de gastos, falha da síntese de voz, 422, 503, 500 e 404 não publicam nada
- Use cases: `ProcessVoiceCommandUseCase`, `ConfirmTransactionProposalUseCase` e `CancelTransactionProposalUseCase`
  recebem o `ApplicationEventPublisher` pelo construtor e publicam com `publishEvent`; a assinatura de `execute` não
  muda e os controllers não mudam
- Registros de auditoria:
  - porta `domain/AuditEntryRepository` — `void append(DomainEvent event)` (só acrescenta, nunca altera)
  - `application/AppendAuditEntryUseCase` — `execute(DomainEvent event)`, delega à porta; sem `@Transactional`
  - `infrastructure/event/DomainEventAuditListener` — adaptador de entrada: `@Async` +
    `@TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)`, com comentário do porquê (uma
    decisão que sofre rollback nunca vira auditoria; o comando de voz não tem transação e grava na hora); chama o
    `AppendAuditEntryUseCase`; captura `RuntimeException` e registra `WARN` `Audit entry not appended` com
    `eventType` (nome simples da classe do evento) e `errorType` (nome simples da classe da exceção), nunca conteúdo
    nem mensagem da exceção
  - `infrastructure/persistence/document/AuditEntryDocument` — `@Document("audit_entries")`;
    `static AuditEntryDocument from(DomainEvent event, String traceId)` com `switch` sobre a `sealed interface`
    (sem `default`); campos `id` (`ObjectId` gerado), `type` (`AuditEntryType`: `TRANSACTION_PROPOSAL_CREATED`,
    `TRANSACTION_PROPOSAL_CONFIRMED`, `TRANSACTION_PROPOSAL_CANCELED`, `VOICE_COMMAND_NOT_UNDERSTOOD`), `userId`
    (`UUID`), `occurredAt`, `traceId`, `transactionProposalId` (`UUID`), `transactionId` (`UUID`),
    `audio` (`format`, `sizeBytes`), `transcription`, `proposal` (`description`, `amount` com
    `@Field(targetType = FieldType.DECIMAL128)`, `currency`, `category`, `expiresAt`); campo nulo não é gravado
  - `infrastructure/persistence/document/AuditEntryType` — enum do `type`
  - `infrastructure/persistence/repository/MongoAuditEntryRepository` — adaptador da porta: `insert` de
    `AuditEntryDocument.from(event, traceId())`, com o `traceId` lido do `Tracer` (contexto propagado à thread
    assíncrona; `null` se não houver span)
  - `infrastructure/persistence/repository/AuditEntryDocumentRepository` — `MongoRepository<AuditEntryDocument,
    ObjectId>`
- Execução assíncrona e threads:
  - `infrastructure/config/AsyncConfiguration` com `@EnableAsync` (como a `SchedulingConfiguration`)
  - `spring.threads.virtual.enabled=true` (requisições, `@Async` e `@Scheduled` em virtual threads), com comentário
  - `spring.task.execution.propagate-context=true` (o Boot registra o `ContextPropagatingTaskDecorator`; o `traceId`
    chega à thread assíncrona)
- MongoDB:
  - dependências `spring-boot-starter-data-mongodb` e, nos testes, `org.testcontainers:testcontainers-mongodb`
  - `compose.yml`: serviço `audit-database`, `mongo:9.0`, porta `27018:27017`, volume `audit_data`, healthcheck com
    `mongosh` + `ping`, `MONGO_INITDB_ROOT_USERNAME: ${MONGO_USERNAME:?...}`,
    `MONGO_INITDB_ROOT_PASSWORD: ${MONGO_PASSWORD:?...}`, `MONGO_INITDB_DATABASE: audit`
  - `.env.example`: `MONGO_USERNAME` e `MONGO_PASSWORD` com valores de exemplo
  - `application.properties`: `spring.mongodb.representation.uuid=standard`; `budgeting.audit.connect-timeout=3s`,
    `budgeting.audit.server-selection-timeout=3s`, `budgeting.audit.read-timeout=5s`, com comentário (o driver espera
    30 s pela seleção de servidor e não limita a leitura); nenhuma URI (local pelo `spring-boot-docker-compose`;
    fora dele, `SPRING_MONGODB_URI`)
  - `infrastructure/config/AuditProperties` (`@ConfigurationProperties("budgeting.audit")`, `@Validated`, `Duration`
    `@NotNull`) e `infrastructure/config/MongoConfiguration` com um `MongoClientSettingsBuilderCustomizer` que
    aplica os três timeouts
- Testes:
  - publicação no `ProcessVoiceCommandUseCaseTest`, `ConfirmTransactionProposalUseCaseTest` e
    `CancelTransactionProposalUseCaseTest`; `DomainEventAuditListenerTest` (a falha do use case não escapa)
  - `AuditTrailIT` pela API, com Awaitility: os quatro tipos com todos os campos, `amount` gravado como `Decimal128`,
    `traceId` igual ao `X-Request-Id`; consulta e 404 sem entrada
  - `support/mongo/`: `MongoTestContainer` (`mongo:9.0`, `@ServiceConnection`, um por JVM), `MongoCleaner`,
    `ResetMongoExtension` e `MongoCleanerIT`, incluídos no `@IntegrationTest`
- ADR `docs/adr/0007-auditoria-em-banco-de-documentos-separado.md`
- Documentação: `CLAUDE.md`, regras (`architecture.md`, `error-handling.md`, `testing.md`),
  README (só pré-requisito e variáveis do `.env`), `docs/tarefas.md` (notas na T18, T21 e T22; checkbox da T20)

**Fora:**
- evento por proposta expirada — a faxina continua só com a quantidade (log `Transaction proposals expired`),
  sem apagar por ID (desvio da SEG-04, ver Decisões)
- evento para o 422 `invalid-command`, para falhas (503, 500), para decisões 404 e para os endpoints manuais
- o áudio em si, a duração e o hash do áudio
- canal/dispositivo (`User-Agent`, IP do cliente e cabeçalhos próprios), ver Decisões
- índices e retenção (TTL) na coleção
- endpoint de leitura da auditoria
- garantia de entrega, retentativas, outbox e testes com o MongoDB fora do ar (T21)
- teste de que as execuções usam virtual threads
- renomear o serviço `database` do MySQL no `compose.yml`
- `docs/escopo.md`, `docs/openapi.yaml` e `docs/exemplos/`
- seção "Auditoria" no README
- ADR sobre a gravação assíncrona ou sobre as virtual threads
- deploy
- chamada à OpenAI real em qualquer teste ou verificação

## Decisões

- MongoDB 9.0 (`mongo:9.0`) — a *major release* mais recente (setembro de 2026, suporte até 31/10/2031); as X.0 são
  as de suporte longo, as X.1+ são *rapid releases*. Esquema flexível por tipo, *append-only*, Spring Data,
  `spring-boot-docker-compose`, Testcontainers e health indicator nativos (decisão do usuário)
- Só os eventos essenciais do ciclo de vida: proposta criada, confirmada, cancelada e comando não entendido — a
  criação guarda o que foi dito e o que a IA propôs; confirmação e cancelamento são as únicas marcas da decisão,
  porque a proposta é apagada (ADR 0005); o não entendido completa a base da métrica de qualidade da T22
  (`confirmadas / (confirmadas + canceladas + não entendidos)`) (decisão do usuário)
- Expiração sem evento por proposta — a quantidade expirada já no log da faxina basta para o escopo; desvia da
  SEG-04, que cita "expirada" entre as decisões; a taxa de expiradas da T22 vem de um contador na faxina (decisão do
  usuário)
- 422 `invalid-command` fora — separá-lo do "não entendido" invadiria o caminho de erro e o escopo de outra tarefa
  (decisão do usuário)
- Sem canal/dispositivo na auditoria — o `User-Agent` foi excluído da tarefa (decisão do usuário, depois de
  avaliadas as formas de levá-lo ao listener assíncrono: parâmetro dos use cases, publicação no controller e
  propagação de contexto, todas com custo maior que o ganho); desvia da SEG-04, que cita "canal/dispositivo". IP
  (dado pessoal, exige configurar proxy) e cabeçalhos próprios (mudam o contrato) seguem fora. Se voltar, será
  tarefa própria
- Metadados do áudio = formato detectado pelos bytes e tamanho — sem o áudio (dado biométrico, a transcrição já
  registra o conteúdo), sem duração (exige decodificar) e sem hash (reenvio é a T17) (decisão do usuário)
- Gravação obrigatoriamente assíncrona (`@Async`) — a auditoria não pode segurar o fluxo; a falha do MongoDB só
  gera `WARN` e a resposta não muda (decisão do usuário)
- `@TransactionalEventListener(AFTER_COMMIT, fallbackExecution = true)`, não `@EventListener` — com
  `@EventListener`, o evento vai para a fila no `publishEvent`, antes do commit; um commit que falha deixaria na
  auditoria uma confirmação que não aconteceu. `fallbackExecution` cobre o comando de voz, que não tem transação.
  Registrado em comentário no código, sem ADR (decisão do usuário)
- Eventos publicados no use case, não no adaptador de repositório (como no repositório de referência
  `thpoiani/dio-spring-boot-learning-track`) — só o use case tem a transcrição e o resultado da IA, e o não
  entendido não passa por repositório (decisão do usuário)
- Eventos em `domain/event/`, com sufixo `Event` e interface `DomainEvent` — tipo pede substantivo; "Audit" no nome
  mentiria (a T22 vai ouvir os mesmos eventos); `AuditEvent`/`AuditEventRepository` já existem no Spring Boot
  Actuator, no classpath. Vocabulário do código mantido: `TransactionProposal`, `Canceled`, `NotUnderstood` (decisão
  do usuário)
- Os eventos carregam tipos do domínio (`TransactionProposal`, IDs tipados, `AudioMetadata`) — a porta fica no
  `domain` e não pode depender de `application` (decisão do usuário)
- Cadeia `AuditEntryRepository` → `MongoAuditEntryRepository` → `AuditEntryDocumentRepository` →
  `AuditEntryDocument`; documento em `persistence/document/`, irmão de `entity/`; porta com `append` (decisão do
  usuário)
- Listener como adaptador de entrada em `infrastructure/event/`, chamando o `AppendAuditEntryUseCase` — todo
  adaptador de entrada do projeto chama um use case (controller, tools, job); os use cases do fluxo não chamam o de
  auditoria (seria síncrono, dentro da transação, e um use case não chama outro) (decisão do usuário)
- `AppendAuditEntryUseCase` sem teste unitário — só delega à porta (como o
  `DeleteExpiredTransactionProposalBatchUseCase`); coberto pelo `AuditTrailIT`
- `traceId` é lido pelo adaptador — é metadado técnico e chega à thread assíncrona pela propagação de contexto
  (decisão do usuário)
- Virtual threads (`spring.threads.virtual.enabled=true`) para requisições, `@Async` e `@Scheduled` — Java 25 já tem a
  JEP 491 (sem *pinning* em `synchronized`); o limite real do banco continua sendo o Hikari (10 conexões, 3 s);
  sem teste próprio e sem ADR (decisão do usuário)
- Propagação de contexto por `spring.task.execution.propagate-context=true`, sem bean próprio — o Boot 4.1.2 registra
  o `ContextPropagatingTaskDecorator`
- Timeouts do MongoDB (`connect` 3 s, `server-selection` 3 s, `read` 5 s) por `MongoClientSettingsBuilderCustomizer`
  com `budgeting.audit.*` — o Boot não tem propriedade para eles, e sem limite de leitura uma gravação travada prende
  a thread para sempre; valores espelham os do MySQL (decisão do usuário)
- Documento único com campos opcionais por tipo; IDs no primeiro nível; `amount` em `Decimal128` (o Spring Data grava
  `BigDecimal` como texto por padrão, o que impede agregação e contraria o ADR 0001); `type` por `switch` sobre a
  `sealed interface` (decisão do usuário)
- Sem índices e sem retenção — a aplicação não lê a coleção; quanto guardar é decisão legal e de negócio, aplicável
  depois no banco de auditoria sem tocar no MySQL (decisão do usuário)
- `AuditTrailIT` usa um usuário novo por teste e filtra as entradas por ele — uma gravação assíncrona de um teste
  anterior pode chegar depois da limpeza; a ausência de entrada é conferida depois de uma entrada seguinte do mesmo
  usuário ter sido gravada, sem `sleep`
- Falha do MongoDB coberta só pelo `DomainEventAuditListenerTest` — o MongoDB fora do ar em `*IT` e o `WARN` real são
  da T21 (decisão do usuário)
- Um ADR (0007) com as opções MySQL, MongoDB e Cassandra — mesma instância ou outra é detalhe de infraestrutura;
  Elasticsearch não é opção de armazenamento (decisão do usuário)
- README sem seção nova, só o pré-requisito e as variáveis do `.env` (decisão do usuário)
- Driver `mongodb-driver-sync` fixado em 5.14.0 por `ext['mongodb.version']` — o 5.8.1 gerenciado pelo Boot 4.1.2 não é
  testado com o servidor 9.0 (tabela de compatibilidade da MongoDB); 5.12+ é totalmente compatível. Custo: a linha
  fica no `build.gradle` e deve ser removida quando o Boot gerenciar o 5.14 ou mais novo (decisão do usuário)
- Quatro fases; tipo `feat`

## Definição de pronto

- [ ] `env -u OPENAI_API_KEY -u JWT_SECRET ./gradlew build` passa (Spotless, Checkstyle, `*Test`, `*IT`, JaCoCo)
- [ ] comando de registro → uma entrada `TRANSACTION_PROPOSAL_CREATED` com `userId`, `occurredAt`, `traceId` igual ao
      `X-Request-Id`, `transactionProposalId`, `audio`, `transcription` e `proposal` (`amount` em
      `Decimal128`)
- [ ] comando não entendido → `VOICE_COMMAND_NOT_UNDERSTOOD` com `audio` e `transcription`
- [ ] confirmar → `TRANSACTION_PROPOSAL_CONFIRMED` com o `transactionId` da resposta; cancelar →
      `TRANSACTION_PROPOSAL_CANCELED`
- [ ] consulta de gastos e decisão 404 → nenhuma entrada
- [ ] a gravação roda em `@Async` + `@TransactionalEventListener(AFTER_COMMIT, fallbackExecution = true)`, com
      comentário do porquê; uma falha do use case de auditoria não escapa do listener
- [ ] `spring.threads.virtual.enabled=true` e `spring.task.execution.propagate-context=true` no
      `application.properties`
- [ ] `docker compose config` válido com o serviço `audit-database` (`mongo:9.0`); a aplicação sobe localmente e
      `/actuator/health` responde `UP`
- [ ] ADR 0007, `CLAUDE.md`, regras, README e `docs/tarefas.md` atualizados; checkbox da T20
      marcado no último commit

## Para futuros agentes

Execute com `/executar-tarefa`. Durante a execução:

- marque `- [x]` no mesmo commit que conclui o item;
- antes do último commit de uma fase, rode o plano de verificação, escreva o resumo da fase com o resultado e
  mude o status para `Concluída` — tudo nesse mesmo commit;
- na última fase, no mesmo commit, confira a definição de pronto e escreva o resumo final (e o plano de deploy,
  se houver);
- mudança no plano só com aprovação do usuário, registrada em Decisões com o motivo.

Contexto para uma sessão nova:

- Branch `feat/auditoria-em-banco-nosql`; título do PR `feat: adiciona auditoria do fluxo de voz em banco nosql`.
  Commits `feat:`/`test:`/`refactor:`/`build:` (código, testes, dependências e compose) e `docs:` (documentação e
  regras).
- Estado antes da tarefa: não há MongoDB, `@EnableAsync`, eventos nem virtual threads (`spring.threads.virtual.enabled`
  ausente; requisições, `@Async` e `@Scheduled` em platform threads). O `compose.yml` tem `database` (MySQL 9.6,
  `3307`) e `swagger-ui`. A conexão com o MySQL não está no `application.properties`: vem do
  `spring-boot-docker-compose` (local) e do `@ServiceConnection` (testes); o MongoDB segue o mesmo caminho.
- `ProcessVoiceCommandUseCase.execute(UserId, ProcessVoiceCommandInput)`: transcreve, interpreta
  (`AssistantOutput.Proposal | Summary | NotUnderstood`), sintetiza e só então grava a proposta (`save` devolve a
  proposta gravada, com `createdAt` truncado a milissegundos); não tem `@Transactional`. Confirmar e cancelar são
  `@Transactional`, buscam com `findByIdAndUserIdForUpdate`, filtram `isPendingAt(now)` e apagam.
- Testes unitários que chamam `execute` e precisam do novo parâmetro: `ProcessVoiceCommandUseCaseTest`,
  `ConfirmTransactionProposalUseCaseTest`, `CancelTransactionProposalUseCaseTest` (`@InjectMocks`; o
  `ApplicationEventPublisher` entra como `@Mock`, e o `ArgumentCaptor` confere o evento).
- Testcontainers 2.x: o MySQL usa `org.testcontainers.mysql.MySQLContainer`; o MongoDB, `org.testcontainers.mongodb
  .MongoDBContainer` (módulo `testcontainers-mongodb`). `@IntegrationTest` passa a ter
  `@ImportTestcontainers({MySqlTestContainer.class, MongoTestContainer.class})`, `@Import({MySqlCleaner.class,
  MongoCleaner.class})` e `@ExtendWith({ResetMySqlExtension.class, ResetMongoExtension.class})`. O `MongoCleaner`
  apaga os documentos de todas as coleções do banco (`MongoTemplate`).
- Awaitility já vem no `spring-boot-starter-test`. `AuditTrailIT` lê os documentos crus
  (`MongoTemplate.find(..., Document.class, "audit_entries")`) para conferir o tipo BSON `Decimal128`; usa
  `AccessTokens.authorization(userId)` com um
  `UserId` novo por teste; a IA é simulada pelo `@AutoConfigureAiStub` (`givenTranscription`, `givenToolCall`,
  `givenAnswer`, `givenSpeech`).
- O `ContextPropagatingTaskDecorator` exige `io.micrometer:context-propagation` no classpath (vem com o Micrometer
  Tracing); se o `traceId` não chegar à thread assíncrona, parar e perguntar.
- Compatibilidade: conferir na matriz do driver Java do MongoDB que a versão gerenciada pelo Boot 4.1.2 suporta o
  servidor 9.0; se não suportar, parar e perguntar.
- O `spring-boot-docker-compose` lê `MONGO_INITDB_ROOT_USERNAME`/`PASSWORD`/`DATABASE` do container; se a
  autenticação do usuário root (banco `admin`) falhar no `bootRun`, parar e perguntar.
- Logs: nunca transcrição, descrição, valor, categoria nem mensagem da exceção (`error-handling.md`); o conteúdo vai
  só para o MongoDB.
- Comentários em inglês, só o porquê; documentação em pt-BR; Spotless confere `.properties`, `.yml` e `.md`.

## Fase 1: Infraestrutura do MongoDB e das execuções assíncronas

Status: Concluída  <!-- Não iniciada | Em andamento | Concluída -->

- [x] [verificação] Dependências no `build.gradle`: `spring-boot-starter-data-mongodb` e `testImplementation
      'org.testcontainers:testcontainers-mongodb'`; conferir a compatibilidade do driver gerenciado com o servidor 9.0
- [x] [verificação] `compose.yml`: serviço `audit-database` (`mongo:9.0`, `27018:27017`, volume `audit_data`,
      healthcheck `mongosh` + `ping`, credenciais `MONGO_USERNAME`/`MONGO_PASSWORD` obrigatórias com `:?`,
      `MONGO_INITDB_DATABASE: audit`); `.env.example` com as duas variáveis
- [x] [verificação] `application.properties`: `spring.threads.virtual.enabled=true`,
      `spring.task.execution.propagate-context=true`, `spring.mongodb.representation.uuid=standard` e
      `budgeting.audit.*` (3 s, 3 s, 5 s), cada grupo com comentário do porquê
- [x] [verificação] `AuditProperties` + `MongoConfiguration` (`MongoClientSettingsBuilderCustomizer` com
      `connectTimeout`, `readTimeout` e `serverSelectionTimeout`) e `AsyncConfiguration` (`@EnableAsync`) em
      `infrastructure/config/`
- [x] [TDD] `support/mongo/`: `MongoTestContainer`, `MongoCleaner`, `ResetMongoExtension`; `MongoCleanerIT` →
      documento inserido em uma coleção é apagado pelo `clean()`; `@IntegrationTest` com os dois containers, as duas
      limpezas e as duas extensões

### Plano de verificação

- `env -u OPENAI_API_KEY -u JWT_SECRET ./gradlew build` → `BUILD SUCCESSFUL` (todos os `*IT` existentes sobem com
  MySQL e MongoDB)
- `./gradlew integrationTest --tests dio.budgeting.support.mongo.MongoCleanerIT` → teste passa
- `docker compose --env-file .env.example config --quiet` → sem erro
- `./gradlew dependencyInsight --dependency mongodb-driver-sync --configuration runtimeClasspath` → versão registrada
  no resumo, compatível com o servidor 9.0
- `bootRun` com o `.env` carregado (`OPENAI_API_KEY` de exemplo basta para subir) e `curl -s
  localhost:8080/actuator/health` → `{"status":"UP"}`; encerrar o processo depois
- `grep -n "spring.threads.virtual.enabled=true\|propagate-context=true" src/main/resources/application.properties`
  → as duas linhas

### Resumo da fase

Feito: dependências do MongoDB (`spring-boot-starter-data-mongodb`, `testcontainers-mongodb`), serviço
`audit-database` (`mongo:9.0`) no `compose.yml` e variáveis no `.env.example`, propriedades de virtual threads,
propagação de contexto, UUID padrão e timeouts da auditoria, `AuditProperties`, `MongoConfiguration`,
`AsyncConfiguration` e o suporte de testes `support/mongo/` (`MongoTestContainer`, `MongoCleaner`,
`ResetMongoExtension`, `MongoCleanerIT`), já incluído no `@IntegrationTest`.

Decisões: o driver gerenciado pelo Boot 4.1.2 (5.8.1) não é testado com o servidor 9.0; o `mongodb-driver-sync` foi
fixado em 5.14.0 por `ext['mongodb.version']` (ver Decisões), com comentário para remover a linha quando o Boot
gerenciar o 5.14 ou mais novo.

Verificação: `./gradlew build` → `BUILD SUCCESSFUL`; `MongoCleanerIT` passa; `docker compose --env-file
.env.example config --quiet` sem erro; driver resolvido em 5.14.0; `bootRun` → `{"status":"UP"}`; as duas
propriedades presentes no `application.properties`.

Para uma sessão nova: o `.env` local precisa de `MONGO_USERNAME` e `MONGO_PASSWORD` (copie do `.env.example`), senão
o `bootRun` falha na interpolação do `compose.yml`. Nenhum `AuditEntry*` existe ainda; a Fase 2 começa pelos
eventos de domínio.

## Fase 2: Eventos de domínio e publicação

Status: Concluída  <!-- Não iniciada | Em andamento | Concluída -->

- [x] [verificação] `domain/event/`: `DomainEvent` (`sealed`), `AudioMetadata` e os quatro `*Event`
- [x] [TDD] `ProcessVoiceCommandUseCase` publica: `ProcessVoiceCommandUseCaseTest` → proposta →
      `TransactionProposalCreatedEvent` com `AudioMetadata` (formato e tamanho do input), transcrição, a
      proposta gravada e `occurredAt` = `createdAt` dela; não entendido → `VoiceCommandNotUnderstoodEvent` com
      `occurredAt` = `Instant.now(clock)`; consulta → nada publicado; falha na síntese → nada publicado
- [x] [TDD] Confirmar e cancelar publicam: `ConfirmTransactionProposalUseCaseTest` →
      `TransactionProposalConfirmedEvent` com os dois IDs e `occurredAt` = instante da confirmação;
      `CancelTransactionProposalUseCaseTest` → `TransactionProposalCanceledEvent`; proposta inexistente ou vencida →
      nada publicado nos dois

### Plano de verificação

- `env -u OPENAI_API_KEY -u JWT_SECRET ./gradlew build` → `BUILD SUCCESSFUL`
- `./gradlew test --tests dio.budgeting.application.ProcessVoiceCommandUseCaseTest --tests 'dio.budgeting.application.*TransactionProposalUseCaseTest'`
  → testes passam
- `grep -rn "import org.springframework\|import jakarta\|import tools.jackson" src/main/java/dio/budgeting/domain` →
  nenhuma linha

### Resumo da fase

Feito: `DomainEvent` (`sealed`), `AudioMetadata` e os quatro `*Event` em `domain/event/`; o
`ProcessVoiceCommandUseCase` publica `TransactionProposalCreatedEvent` (depois de gravar a proposta) e
`VoiceCommandNotUnderstoodEvent`; o `ConfirmTransactionProposalUseCase` e o `CancelTransactionProposalUseCase`
publicam `TransactionProposalConfirmedEvent` e `TransactionProposalCanceledEvent`. Consulta, falha da síntese e
proposta inexistente ou vencida não publicam nada. Todos recebem o `ApplicationEventPublisher` pelo construtor.

Decisões: o `User-Agent` foi excluído da tarefa (ver Decisões) — o `UserAgent`, o parâmetro dos use cases e a leitura
nos controllers saíram do escopo e do código; a assinatura de `execute` e os controllers não mudaram. O commit
`refactor: remove o user agent da auditoria` desfaz o value object criado antes dessa decisão.

Verificação: `./gradlew build` → `BUILD SUCCESSFUL`; os três `*UseCaseTest` passam; `grep` de imports de Spring,
Jakarta e Jackson no `domain` → nenhuma linha.

Para uma sessão nova: os eventos não têm `userAgent`; `DomainEvent` expõe só `userId()` e `occurredAt()`. A Fase 3
cria o listener, a porta `AuditEntryRepository`, o documento e o `AuditTrailIT` (sem `userAgent` nos campos).

## Fase 3: Gravação da auditoria

Status: Concluída  <!-- Não iniciada | Em andamento | Concluída -->

- [x] [TDD] `DomainEventAuditListener` + `AppendAuditEntryUseCase` + porta `AuditEntryRepository`:
      `DomainEventAuditListenerTest` → o use case recebe o evento; o use case lança `RuntimeException` → o listener
      não a propaga
- [x] [TDD] `AuditEntryDocument`, `AuditEntryType`, `MongoAuditEntryRepository` e `AuditEntryDocumentRepository`:
      `AuditTrailIT` → comando de registro → `TRANSACTION_PROPOSAL_CREATED` com todos os campos (`amount`
      `Decimal128`, `traceId` = `X-Request-Id`); não entendido →
      `VOICE_COMMAND_NOT_UNDERSTOOD` com `audio` e `transcription`; confirmar → `TRANSACTION_PROPOSAL_CONFIRMED` com o
      `transactionId` da resposta; cancelar → `TRANSACTION_PROPOSAL_CANCELED`; consulta e decisão 404 → nenhuma
      entrada (conferido depois de uma entrada seguinte do mesmo usuário)
- [x] [verificação] Comentário do porquê no `@TransactionalEventListener(AFTER_COMMIT, fallbackExecution = true)` e
      no `@Async`

### Plano de verificação

- `env -u OPENAI_API_KEY -u JWT_SECRET ./gradlew build` → `BUILD SUCCESSFUL`
- `./gradlew test --tests dio.budgeting.infrastructure.event.DomainEventAuditListenerTest` → testes passam
- `./gradlew integrationTest --tests dio.budgeting.infrastructure.event.AuditTrailIT` → testes passam
- `grep -n "AFTER_COMMIT" -B3 src/main/java/dio/budgeting/infrastructure/event/DomainEventAuditListener.java` →
  comentário presente
- `grep -rn "transcription\|getDescription" src/main/java --include=*.java | grep -i "addKeyValue"` → nenhuma linha

### Resumo da fase

Feito: porta `domain/AuditEntryRepository` (`append`), `AppendAuditEntryUseCase`, `DomainEventAuditListener`
(`@Async` + `@TransactionalEventListener(AFTER_COMMIT, fallbackExecution = true)`, com comentário do porquê; captura
`RuntimeException` e registra `WARN` `Audit entry not appended` só com `eventType` e `errorType`),
`AuditEntryDocument` (`switch` sobre a `sealed interface`, `amount` em `Decimal128`, campo nulo não gravado),
`AuditEntryType`, `AuditEntryDocumentRepository` e `MongoAuditEntryRepository` (lê o `traceId` do `Tracer`).

Verificação: `./gradlew build` → `BUILD SUCCESSFUL`; `DomainEventAuditListenerTest` passa; `AuditTrailIT` (5 testes)
passa: os quatro tipos com todos os campos, `traceId` igual ao `X-Request-Id`, `amount` como `Decimal128`, e consulta
e 404 sem entrada. O `grep` de `addKeyValue` com `transcription` acha uma linha, o `transcriptionLength` que já existia
no `ProcessVoiceCommandUseCase` (só o tamanho, nunca o conteúdo); nenhum log novo leva conteúdo.

Para uma sessão nova: os itens do listener e do documento foram commitados juntos (`de820d7`), porque o contexto
Spring só sobe com uma implementação da porta. Não houve RED isolado do `AuditTrailIT`: ele passou de primeira, e
confirmei no relatório que os 5 testes rodaram. A Fase 4 é só documentação.

## Fase 4: Documentação

Status: Em andamento  <!-- Não iniciada | Em andamento | Concluída -->

- [x] [verificação] ADR `docs/adr/0007-auditoria-em-banco-de-documentos-separado.md` a partir do `template.md`:
      opções MySQL, MongoDB e Cassandra; decisão MongoDB; consequências (tecnologia a mais para operar, nenhuma
      transação comum entre os bancos — motivo da T21 —, retenção aplicável depois sem tocar no MySQL, esquema por
      tipo de evento)
- [x] [verificação] `CLAUDE.md`: stack (MongoDB 9.0 no `compose.yml`, virtual threads), estrutura (`domain/event/`,
      `infrastructure/event/`, `persistence/document/`) e nomenclatura (`<Fato>Event`, `Mongo<X>Repository`,
      `<X>Document`, `<X>DocumentRepository`); README: MongoDB nos pré-requisitos e `MONGO_USERNAME`/`MONGO_PASSWORD`
      na configuração, sem seção nova
- [ ] [verificação] Regras: `architecture.md` (eventos de domínio em `domain/event/`, publicação no use case,
      listener como adaptador de entrada, `AFTER_COMMIT` + `@Async`, porta `append`); `error-handling.md` (`WARN`
      `Audit entry not appended`; o conteúdo vai para a auditoria); `testing.md` (container do MongoDB, limpeza entre
      testes, Awaitility e usuário novo por teste para a gravação assíncrona)
- [ ] [verificação] `docs/tarefas.md`: nota na T18 (o executor assíncrono não espera as tarefas pendentes ao desligar;
      o health passa a incluir o MongoDB); nota na T21 (o `DomainEventAuditListener` e o `MongoAuditEntryRepository`
      são os pontos a reforçar; perdas possíveis: MongoDB fora do ar, queda entre o commit e a gravação, tarefas
      pendentes no desligamento); nota na T22 (os `DomainEvent` existem para contar as taxas; a expiração não tem
      evento, então a taxa de expiradas vem de um contador na faxina); checkbox da T20 marcado

### Plano de verificação

- `env -u OPENAI_API_KEY -u JWT_SECRET ./gradlew build` → `BUILD SUCCESSFUL`
- `ls docs/adr/0007-*.md` → o arquivo existe; `grep -n "^status: aceito" docs/adr/0007-*.md` → presente
- `grep -n "domain/event\|persistence/document\|Mongo<X>Repository" CLAUDE.md` → presentes
- `grep -n "MONGO_USERNAME" README.md .env.example` → presentes nos dois
- `grep -n "Nota (T20)" docs/tarefas.md` → notas na T18, T21 e T22
- `grep -n "\[x\] \[T20" docs/tarefas.md` → checkbox marcado

### Resumo da fase

_(preencher ao concluir a fase)_

## Resumo final

_(preencher ao concluir todas as fases)_
