# T06 — Integridade da transação

Registrar a data e hora em que cada transação é criada (FN-02) e validar as regras de negócio de todo dado
externo na fronteira em que ele entra — requisição HTTP e argumentos das tools de IA —, com invariantes no domínio
como rede de proteção (FN-06).

## Escopo

**Dentro:**
- `createdAt` (`Instant`, UTC) definido pelo servidor no registro, com `Instant.now(clock)`; nem o cliente nem a
  IA o informam
  - bean `java.time.Clock` (`Clock.systemUTC()`) em `infrastructure/config/ClockConfiguration`, injetado no
    `PersistTransactionUseCase`
  - truncado em milissegundos no domínio
  - coluna `created_at` `NOT NULL` em `TransactionEntity`
  - `createdAt` em `TransactionOutput` e `TransactionResponse` (ISO 8601 com `Z`), na criação e na listagem
- regras de negócio da transação:
  - valor maior que zero
  - teto = limite de armazenamento do `Money` (16 dígitos inteiros, 2 casas — ADR 0001); sem teto de negócio
  - descrição presente (não vazia depois do `strip()`), sem espaços nas pontas e com no máximo 255 caracteres;
    `@Column(length = 255)` explícito
  - categoria presente
- fronteira HTTP (`POST /transactions`): Bean Validation no `TransactionRequest` — `@NotBlank` e
  `@Size(max = 255)` em `description`, `@NotNull` em `category`, `@Positive` em `amount` (além do formato atual de
  `amount` e `currency`) → 400 `validation-error` com `errors`/`pointer` e mensagens próprias em pt-BR
- fronteira da IA: adaptador `infrastructure/ai/TransactionTools` (`@Component`, `@Validated`) com as tools
  `persist-transaction` e `list-transactions-by-category`
  - `PersistTransactionToolInput` (record em `infrastructure/ai/`) com `@ToolParam` e as mesmas regras:
    `@NotBlank`/`@Size(max = 255)` em `description`, `@NotNull`/`@Positive`/`@Digits(integer = 16, fraction = 2)`
    em `amount`, `@NotNull` em `category`; `toInput()` converte para `PersistTransactionInput`
  - os use cases e o `PersistTransactionInput` perdem `@Tool` e `@ToolParam` (Spring AI fora de `application`);
    o `TransactionController` passa `defaultTools(transactionTools)`
  - bean `ToolExecutionExceptionProcessor` em `infrastructure/ai/` (`alwaysThrow(true)`,
    `rethrowExceptions(ConstraintViolationException, DatabindException)`): argumentos inválidos sobem sem
    invólucro; qualquer outra falha de tool sobe como `ToolExecutionException` (500) e nunca vai ao modelo como texto
  - `logging.level.org.springframework.ai.tool.method.MethodToolCallback=ERROR`: o `WARN` da falha de conversão
    traz o valor recusado no stack trace
- `ApiExceptionHandler`: `ConstraintViolationException` e `DatabindException` → 422
  `urn:problem-type:budgeting:invalid-command`, `title` e `detail` fixos em pt-BR, sem `errors`; log `INFO`
  `Request rejected` com `status`, `problemType` e `invalidFields` (nomes dos campos); sem segunda chamada ao
  modelo e sem síntese de voz
- domínio: o construtor de criação do `Transaction` recebe `createdAt`, normaliza (`strip` da descrição,
  `createdAt` em milissegundos) e garante as invariantes com `IllegalArgumentException` (rede de proteção; vira
  500); o construtor de reidratação não valida
- testes: `TransactionTest` (novo), `PersistTransactionUseCaseTest`, `TransactionEndpointsIT`, `VoiceCommandIT`
- documentação: `api-conventions.md`, `error-handling.md`, `architecture.md`, `ai-integration.md`, `CLAUDE.md`
  (Estrutura) e notas na T09 e na T16 de `docs/tarefas.md`

**Fora:**
- data informada pelo cliente ou pela IA (data passada) — melhoria futura, como campo novo `occurredAt`
- teto de negócio por transação
- 422 por estado do servidor (proposta expirada ou cancelada — T12/T13)
- áudio estático da recusa do comando (T16; nota registrada)
- orquestração do `ChatClient`, transcrição e voz fora do controller (T09)
- `@Transactional` nos use cases
- Flyway e migração de dados (banco local é recriado)
- README e ADR

## Decisões

- `createdAt` = momento do registro, definido pelo servidor — entrega o FN-02 sem fuso nem interpretação de datas
  faladas; uma data informada viria como campo novo `occurredAt`, sem mudar o significado de um campo publicado
  (decisão do usuário)
- `Clock` injetado no use case, não `Instant.now()` direto — tempo determinístico nos testes; `Clock` é JDK, não
  fere as camadas
- Precisão em milissegundos — o MySQL `DATETIME(6)` guarda microssegundos e o relógio do Linux tem mais precisão;
  sem truncar, o valor lido difere do gravado
- Sem teto de negócio: o teto é o limite de armazenamento do `Money` — um padrão de vida pode tornar normais
  despesas altas (decisão do usuário)
- Descrição com até 255 caracteres — rótulo de gasto (Open Finance: 200 no nome do lançamento; Pix: 140); não há
  ganho em limite menor (decisão do usuário)
- Toda validação de dado externo acontece na fronteira em que ele entra (HTTP e argumentos de tool); o use case
  recebe dado válido (decisão do usuário)
- Invariantes no construtor de criação do `Transaction` como rede de proteção (`IllegalArgumentException` → 500)
  — uma entrada nova que esqueça a validação falha alto em vez de gravar dado inválido (decisão do usuário)
- 400 quando a requisição, sozinha, é inválida; 422 quando a requisição é válida, mas o conteúdo interpretado (ou o
  estado do servidor) impede a operação — reenviar igual não resolve em nenhum dos dois; o que muda é a causa
  (decisão do usuário)
- Argumentos inválidos na tool → 422 `invalid-command` em Problem Details, e não resposta gerada pelo modelo — é
  erro de validação; o retorno é um padrão pré-existente; poupa a segunda chamada ao modelo e a síntese (decisão do
  usuário)
- 422 sem `errors` — forma mais simples; a experiência ideal é o erro falado, que vem como áudio estático na T16
  (decisão do usuário)
- Adaptador `TransactionTools` em `infrastructure/ai` — a fronteira da IA fica na borda, como o controller no HTTP;
  `@Tool`/`@ToolParam` são contrato de canal (nome, prompt, schema), não da operação; a T12 vai separar tool e use
  case de qualquer forma; antecipa só as tools da T09 (decisão do usuário)
- Method validation do Spring (`@Validated` + `@Valid`) no adaptador, em vez de `Validator` manual e exceção
  própria — forma idiomática, mesmo mecanismo do `@Valid` no controller (decisão do usuário)
- Bean `ToolExecutionExceptionProcessor` em vez de `spring.ai.tools.throw-exception-on-error=true` — a propriedade
  relança o invólucro `ToolExecutionException`, que o handler genérico de `Exception` captura antes da causa (500);
  com `rethrowExceptions` a exceção de validação chega crua ao advice, que não importa Spring AI
- `Category` continua enum na tool — o schema enviado ao modelo lista os valores; valor inexistente falha na
  conversão do Spring AI (antes do método) com `DatabindException`, também mapeada para 422
- Mensagens de validação próprias só no `TransactionRequest` — o 422 não tem `errors`; na tool as anotações só
  recusam e dão os nomes dos campos ao log
- Sem ADR — decisões consideradas padrão, sem trade-offs significativos (decisão do usuário)
- Banco local recriado (`docker compose down -v`); sem dados a preservar nem deploy (decisão do usuário)
- Quatro fases: data e hora; regras na fronteira HTTP e invariantes; fronteira da IA; documentação

## Definição de pronto

- [ ] `./gradlew build` passa (Spotless, Checkstyle, `*Test`, `*IT` e JaCoCo), sem `OPENAI_API_KEY`
- [ ] Toda transação criada tem `createdAt` UTC em milissegundos, igual na criação e na listagem
- [ ] `POST /transactions` com descrição ausente, vazia ou com mais de 255 caracteres, categoria ausente ou valor
      `0.00` → 400 `validation-error` com o `pointer` do campo; nada gravado
- [ ] Comando de voz com argumentos inválidos na tool (valor não positivo, descrição vazia ou longa, valor com mais
      de 2 casas, categoria ausente ou inexistente) → 422 `invalid-command`; nada gravado; log `Request rejected`
      com `invalidFields`, sem o valor recusado
- [ ] Nenhuma anotação do Spring AI em `application`; tools em `infrastructure/ai/TransactionTools`
- [ ] Regras, `CLAUDE.md` e notas da T09 e T16 atualizadas; checkbox da T06 marcado em `docs/tarefas.md` no
      último commit

## Para futuros agentes

Execute com `/executar-tarefa`. Durante a execução:

- marque `- [x]` no mesmo commit que conclui o item;
- antes do último commit de uma fase, rode o plano de verificação, escreva o resumo da fase com o resultado e
  mude o status para `Concluída` — tudo nesse mesmo commit;
- na última fase, no mesmo commit, confira a definição de pronto e escreva o resumo final (e o plano de deploy,
  se houver);
- mudança no plano só com aprovação do usuário, registrada em Decisões com o motivo.

Contexto para uma sessão nova:

- Branch `feat/integridade-da-transacao`; título do PR `feat: registra data e hora e valida transações na
  fronteira`. Commits: `feat:`, `test:`, `refactor:`, `docs:`.
- Estado antes da tarefa: `Transaction` (Lombok `@Getter`/`@AllArgsConstructor` = reidratação; construtor de
  criação gera o ID) sem campo de tempo nem validação; `@Tool` em `PersistTransactionUseCase.execute` e
  `ListTransactionsByCategoryUseCase.execute`, `@ToolParam` no `PersistTransactionInput`; o `TransactionController`
  monta o `ChatClient` com `defaultTools(<use cases>)`. Schema por `ddl-auto=update`.
- Spring AI 2.0.2 (conferido no bytecode):
  - `MethodToolCallback.call` converte os argumentos (`buildTypedArgument`) antes de chamar o método; falha de
    conversão vira `ToolExecutionException` com a causa do Jackson e um `log.warn` com o stack trace.
  - `callMethod` invoca o `Method` da classe-alvo sobre o bean (proxy CGLIB): a method validation do `@Validated`
    roda; o método `@Tool` precisa ser público. A `ConstraintViolationException` volta embrulhada em
    `ToolExecutionException`.
  - `DefaultToolExecutionExceptionProcessor.process`: causa `RuntimeException` presente em `rethrowExceptions` →
    relança a causa; senão, com `alwaysThrow`, relança o `ToolExecutionException`; senão devolve a mensagem ao
    modelo. O auto-configure (`ToolCallingAutoConfiguration`) cria o processor padrão só se não houver outro bean.
  - Confirmar no teste que a exceção chega ao advice sem outro embrulho do `ChatClient`/`OpenAiChatModel`.
- Spring: `@ExceptionHandler` casa primeiro pelo tipo de topo; um handler de `Exception` captura o invólucro antes
  de olhar a causa — por isso as exceções de validação precisam chegar cruas. `ResponseEntityExceptionHandler` não
  trata `ConstraintViolationException` (só `HandlerMethodValidationException`, do controller).
- `invalidFields` no log: último nó do `propertyPath` da `ConstraintViolation`; na `DatabindException`, o último
  `propertyName` do `getPath()` (o advice já tem `jsonPath`). O primeiro nó do caminho é o parâmetro (`input`).
- `AiStub.givenToolCall("persist-transaction", "{\"input\": {...}}")`: manter o parâmetro do método da tool com o
  nome `input` para os payloads não mudarem. Com 422, o modelo não é chamado de novo e a voz não é gerada; stubs de
  resposta e voz ociosos não quebram o WireMock.
- `@Positive` em `String`: o Hibernate Validator aceita `CharSequence`; se não aceitar, usar
  `@DecimalMin(value = "0.00", inclusive = false)`. `"abc"` em `amount` pode gerar dois itens em `errors`
  (formato e positivo) — aceito.
- Hibernate + MySQL: `Instant` → `DATETIME(6)`. O teste de ida e volta (`createdAt` do `POST` igual ao do `GET`)
  pega erro de fuso e de precisão; se falhar por fuso, `spring.jpa.properties.hibernate.jdbc.time_zone=UTC`.
  O `POST` devolve o valor em memória (`save` faz `merge` e não relê) e o `GET` lê do banco.
- Banco local antigo: o `ALTER TABLE` da coluna `NOT NULL` pode falhar com linhas existentes — recriar com
  `docker compose down -v` (citar no PR).
- 500 de falha não validada dentro da tool: `@MockitoBean TransactionRepository` lançando `RuntimeException` (como
  no `ErrorResponseIT`).
- Checkstyle limita métodos a 50 linhas, inclusive em testes.
- Regras: `.claude/rules/architecture.md`, `error-handling.md`, `api-conventions.md`, `ai-integration.md`,
  `testing.md`.

## Fase 1: Data e hora da transação

Status: Não iniciada  <!-- Não iniciada | Em andamento | Concluída -->

- [ ] [TDD] `PersistTransactionUseCaseTest`: com `@Mock Clock` devolvendo um instante fixo, a transação salva tem
      `createdAt` igual a esse instante e o `TransactionOutput` o devolve — implementa o `Clock` no use case,
      `createdAt` no `Transaction` (construtor de criação e reidratação) e no `TransactionOutput`
- [ ] [TDD] `TransactionTest` (novo, `domain`): `createdAt` com nanossegundos é truncado em milissegundos na
      criação
- [ ] [TDD] `TransactionEndpointsIT`: `POST /transactions` devolve `createdAt` ISO 8601 em UTC, entre o instante
      antes e o depois da requisição, e o `GET /transactions/{category}` devolve o mesmo `createdAt` — implementa
      `ClockConfiguration` (`infrastructure/config/`), `created_at` `NOT NULL` no `TransactionEntity` e
      `createdAt` no `TransactionResponse`

### Plano de verificação

- `./gradlew build` → `BUILD SUCCESSFUL`, sem `OPENAI_API_KEY`
- `./gradlew test --tests "dio.budgeting.domain.TransactionTest"` → passa
- `./gradlew integrationTest --tests "dio.budgeting.infrastructure.http.TransactionEndpointsIT"` → passa
- `grep -rn "Instant.now()" src/main/java` → nenhuma linha (só `Instant.now(clock)`)

### Resumo da fase

_(preencher ao concluir a fase)_

## Fase 2: Regras na fronteira HTTP e invariantes do domínio

Status: Não iniciada  <!-- Não iniciada | Em andamento | Concluída -->

- [ ] [TDD] `TransactionTest`: a criação lança `IllegalArgumentException` com descrição nula, vazia, só espaços ou
      com mais de 255 caracteres depois do `strip`; valor nulo, zero ou negativo; categoria nula; `createdAt` nulo
- [ ] [TDD] `TransactionTest`: a criação remove os espaços nas pontas da descrição (`"  Mercado "` → `"Mercado"`),
      e 255 caracteres depois do `strip` são aceitos
- [ ] [TDD] `TransactionEndpointsIT` (parametrizado): descrição ausente, vazia, só espaços ou com 256 caracteres;
      categoria ausente; `amount` `"0.00"` → 400 `validation-error` com `errors` `[{pointer, detail}]` do campo
      (`#/description`, `#/category`, `#/amount`) e `detail` próprio em pt-BR; nada gravado — implementa as
      anotações no `TransactionRequest`, as mensagens em `messages.properties` e `@Column(length = 255)` no
      `TransactionEntity`

### Plano de verificação

- `./gradlew build` → `BUILD SUCCESSFUL`, sem `OPENAI_API_KEY`
- `./gradlew test --tests "dio.budgeting.domain.TransactionTest"` → passa
- `./gradlew integrationTest --tests "dio.budgeting.infrastructure.http.TransactionEndpointsIT"` → passa
- `grep -n "transaction\.\(description\|category\|amount\)\." src/main/resources/messages.properties` → uma linha
  por mensagem nova

### Resumo da fase

_(preencher ao concluir a fase)_

## Fase 3: Fronteira da IA

Status: Não iniciada  <!-- Não iniciada | Em andamento | Concluída -->

- [ ] [TDD] `VoiceCommandIT`: tool `persist-transaction` com `amount` `-10` → 422 `application/problem+json`,
      `type` `urn:problem-type:budgeting:invalid-command`, `title` "Comando não processado", `detail` "Não foi
      possível processar o comando com os dados entendidos. Envie um novo comando de voz.", sem `errors`; nada
      gravado — implementa `TransactionTools` e `PersistTransactionToolInput` (`infrastructure/ai/`), remove
      `@Tool`/`@ToolParam` de `application`, `defaultTools(transactionTools)` no controller, o bean
      `ToolExecutionExceptionProcessor`, `ProblemType.INVALID_COMMAND`, `problem.invalid-command.*` e o handler de
      `ConstraintViolationException`
- [ ] [TDD] `VoiceCommandIT` (parametrizado): descrição vazia, descrição com 256 caracteres, `amount` `80.005`,
      `amount` `0` e categoria ausente → 422 `invalid-command`; nada gravado
- [ ] [TDD] `VoiceCommandIT`: categoria inexistente (`"FOO"`) → 422 `invalid-command`; a saída do log não contém
      `FOO` — implementa o handler de `DatabindException` e o nível `ERROR` do `MethodToolCallback`
- [ ] [TDD] `VoiceCommandIT` (logs): a recusa gera `INFO` `Request rejected` com `status` 422, `problemType`
      `urn:problem-type:budgeting:invalid-command` e `invalidFields` `["amount"]`, com o `traceId` da requisição
- [ ] [TDD] `VoiceCommandIT`: falha inesperada dentro da tool (`@MockitoBean TransactionRepository` lançando
      `RuntimeException`) → 500 `about:blank` genérico, sem o texto da exceção no corpo — confirma o `alwaysThrow`

### Plano de verificação

- `./gradlew build` → `BUILD SUCCESSFUL`, sem `OPENAI_API_KEY`
- `./gradlew integrationTest --tests "dio.budgeting.infrastructure.http.VoiceCommandIT"` → passa, inclusive o
  caminho feliz existente
- `grep -rn "org.springframework.ai" src/main/java/dio/budgeting/application src/main/java/dio/budgeting/domain`
  → nenhuma linha
- `grep -rn "org.springframework.ai" src/main/java/dio/budgeting/infrastructure/http/error` → nenhuma linha

### Resumo da fase

_(preencher ao concluir a fase)_

## Fase 4: Documentação

Status: Não iniciada  <!-- Não iniciada | Em andamento | Concluída -->

- [ ] [verificação] `.claude/rules/api-conventions.md`: toda validação de dado externo acontece na fronteira em
      que ele entra (substitui "regras de negócio ficam na aplicação"); critério 400 (requisição inválida por si)
      x 422 (requisição válida, conteúdo interpretado ou estado impede a operação) na tabela de status
- [ ] [verificação] `.claude/rules/error-handling.md`: linhas de `ConstraintViolationException` e
      `DatabindException` (argumentos de tool) → 422 `invalid-command` na tabela "Mapeamento"; `@Validated` só nos
      adaptadores de tool
- [ ] [verificação] `.claude/rules/architecture.md`: validação na fronteira, invariantes do domínio como rede de
      proteção; adaptador de tools em `infrastructure/ai` em "Portas e adaptadores"
- [ ] [verificação] `.claude/rules/ai-integration.md`: argumentos das tools validados no adaptador (`@Validated`);
      recusa → 422 sem resposta do modelo; falhas de tool nunca voltam ao modelo como texto
- [ ] [verificação] `CLAUDE.md` (Estrutura: `infrastructure/ai/` e `infrastructure/config/`); `docs/tarefas.md`:
      nota na T09 (tools já em `infrastructure/ai` desde a T06) e na T16 (áudio estático do 422 `invalid-command`)
- [ ] [verificação] Marcar a T06 em `docs/tarefas.md` (último commit)

### Plano de verificação

- `./gradlew build` → `BUILD SUCCESSFUL`
- `grep -n "invalid-command" .claude/rules/error-handling.md .claude/rules/ai-integration.md` → ao menos uma
  linha em cada arquivo
- `grep -n "fronteira" .claude/rules/api-conventions.md .claude/rules/architecture.md` → ao menos uma linha em
  cada arquivo
- `grep -n "ai/" CLAUDE.md` → uma linha na Estrutura

### Resumo da fase

_(preencher ao concluir a fase)_

## Resumo final

_(preencher ao concluir todas as fases)_
