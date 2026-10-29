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
- fronteira da IA: adaptador `infrastructure/ai/tool/TransactionTools` (`@Component`, `@Validated`) com as tools
  `persist-transaction` e `list-transactions-by-category`
  - `PersistTransactionToolArguments` (record em `infrastructure/ai/tool/argument/`) com `@ToolParam` e as mesmas regras:
    `@NotBlank`/`@Size(max = 255)` em `description`, `@NotNull`/`@Positive`/`@Digits(integer = 16, fraction = 2)`
    em `amount`, `@NotNull` em `category`; `toInput()` converte para `PersistTransactionInput`
  - os use cases e o `PersistTransactionInput` perdem `@Tool` e `@ToolParam` (Spring AI fora de `application`);
    o `TransactionController` passa `defaultTools(transactionTools)`
  - bean `ToolExecutionExceptionProcessor` em `infrastructure/ai/tool/` (`alwaysThrow(true)`,
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
- Adaptador `TransactionTools` em `infrastructure/ai/tool` — a fronteira da IA fica na borda, como o controller no HTTP;
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
- Valor positivo na requisição por segundo `@Pattern`, e não `@Positive` — `@Positive` em `String` também falha
  quando o texto é malformado, duplicando o erro de formato; o `@Pattern` de formato passa a aceitar o sinal, e o
  de positividade recusa só zero e negativos já bem formados, então cada entrada inválida gera um único erro e
  `-10.00` diz "maior que zero" em vez de erro de formato (decisão do usuário)
- DTO da tool nomeado `PersistTransactionToolArguments` e em `infrastructure/ai/tool/argument/` — `Input` é
  reservado à camada `application`; `Request` sugere HTTP, e o modelo faz uma chamada de função com *argumentos*
  (vocabulário do protocolo); a infraestrutura separa os DTOs por papel, como `http/request`; `ai/tool/` é o lado
  de entrada (o modelo aciona a aplicação, como o HTTP) e deixa `ai/` livre para os adaptadores de saída da T09
  (decisão do usuário)
- Parâmetro da tool `persist-transaction` chamado `arguments` (era `input`); o JSON do modelo vira
  `{"arguments": {...}}` (decisão do usuário)
- A tool devolve ao modelo o `TransactionOutput` da aplicação, sem DTO próprio — comportamento anterior à T06;
  avaliar um `...ToolResponse` na T09
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
- [ ] Nenhuma anotação do Spring AI em `application`; tools em `infrastructure/ai/tool/TransactionTools`
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
  `propertyName` do `getPath()` (o advice já tem `jsonPath`). O primeiro nó do caminho é o parâmetro (`arguments`).
- `AiStub.givenToolCall("persist-transaction", "{\"arguments\": {...}}")`: o nome do parâmetro do método da tool
  é a chave do JSON; os payloads dos testes existentes passam de `input` para `arguments`. Com 422, o modelo não é chamado de novo e a voz não é gerada; stubs de
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

Status: Concluída  <!-- Não iniciada | Em andamento | Concluída -->

- [x] [TDD] `PersistTransactionUseCaseTest`: com `@Mock Clock` devolvendo um instante fixo, a transação salva tem
      `createdAt` igual a esse instante e o `TransactionOutput` o devolve — implementa o `Clock` no use case,
      `createdAt` no `Transaction` (construtor de criação e reidratação) e no `TransactionOutput`
- [x] [TDD] `TransactionTest` (novo, `domain`): `createdAt` com nanossegundos é truncado em milissegundos na
      criação
- [x] [TDD] `TransactionEndpointsIT`: `POST /transactions` devolve `createdAt` ISO 8601 em UTC, entre o instante
      antes e o depois da requisição, e o `GET /transactions/{category}` devolve o mesmo `createdAt` — implementa
      `ClockConfiguration` (`infrastructure/config/`), `created_at` `NOT NULL` no `TransactionEntity` e
      `createdAt` no `TransactionResponse`

### Plano de verificação

- `./gradlew build` → `BUILD SUCCESSFUL`, sem `OPENAI_API_KEY`
- `./gradlew test --tests "dio.budgeting.domain.TransactionTest"` → passa
- `./gradlew integrationTest --tests "dio.budgeting.infrastructure.http.TransactionEndpointsIT"` → passa
- `grep -rn "Instant.now()" src/main/java` → nenhuma linha (só `Instant.now(clock)`)

### Resumo da fase

- `Transaction` ganhou `createdAt` (`Instant`), truncado em milissegundos no construtor de criação; o
  `PersistTransactionUseCase` recebe o `Clock` e usa `Instant.now(clock)`; `ClockConfiguration`
  (`infrastructure/config/`) expõe `Clock.systemUTC()`.
- `TransactionEntity` tem `created_at` `NOT NULL`; `TransactionOutput` e `TransactionResponse` devolvem `createdAt`.
- O ciclo do `TransactionEndpointsIT` nasceu verde: a implementação já havia entrado no primeiro ciclo (o `Clock` e
  a coluna eram necessários para compilar). O teste de ida e volta passou sem ajuste de fuso.
- Verificação: `./gradlew build` sem `OPENAI_API_KEY` → `BUILD SUCCESSFUL`; `TransactionTest` e
  `TransactionEndpointsIT` passam; `grep "Instant.now()" src/main/java` → nenhuma linha.
- Para a próxima sessão: o construtor de criação do `Transaction` ainda não valida nada (Fase 2); `MySqlCleanerIT`
  foi ajustado ao novo construtor da entidade.

## Fase 2: Regras na fronteira HTTP e invariantes do domínio

Status: Concluída  <!-- Não iniciada | Em andamento | Concluída -->

- [x] [TDD] `TransactionTest`: a criação lança `IllegalArgumentException` com descrição nula, vazia, só espaços ou
      com mais de 255 caracteres depois do `strip`; valor nulo, zero ou negativo; categoria nula; `createdAt` nulo
- [x] [TDD] `TransactionTest`: a criação remove os espaços nas pontas da descrição (`"  Mercado "` → `"Mercado"`),
      e 255 caracteres depois do `strip` são aceitos
- [x] [TDD] `TransactionEndpointsIT` (parametrizado): descrição ausente, vazia, só espaços ou com 256 caracteres;
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

- O construtor de criação do `Transaction` valida descrição (presente, até 255 caracteres depois do `strip`), valor
  maior que zero, categoria e `createdAt`, com `IllegalArgumentException`, e grava a descrição sem espaços nas
  pontas; `Transaction.DESCRIPTION_MAX_LENGTH` é a fonte do 255 (usada também em `@Size` e `@Column`).
- `TransactionRequest`: `@NotBlank` e `@Size` na descrição, `@NotNull` na categoria e dois `@Pattern` no `amount`
  (formato com sinal opcional; positividade). Mensagens pt-BR em `messages.properties`.
- Decisão registrada: positividade por `@Pattern` em vez de `@Positive`, para um único erro por entrada e
  `-10.00` com "maior que zero" (ver Decisões).
- Verificação: `./gradlew build` sem `OPENAI_API_KEY` → `BUILD SUCCESSFUL`; `TransactionTest` e
  `TransactionEndpointsIT` passam; o `grep` de `messages.properties` lista as 4 mensagens novas, mais a
  `transaction.amount.invalid` já existente.
- Para a próxima sessão: o `ErrorResponseIT` não mudou; a Fase 3 deve reutilizar as mesmas regras na tool
  (`@Digits(integer = 16, fraction = 2)` no `PersistTransactionToolArguments`).

## Fase 3: Fronteira da IA

Status: Concluída  <!-- Não iniciada | Em andamento | Concluída -->

- [x] [TDD] `VoiceCommandIT`: tool `persist-transaction` com `amount` `-10` → 422 `application/problem+json`,
      `type` `urn:problem-type:budgeting:invalid-command`, `title` "Comando não processado", `detail` "Não foi
      possível processar o comando com os dados entendidos. Envie um novo comando de voz.", sem `errors`; nada
      gravado — implementa `TransactionTools` (`infrastructure/ai/tool/`) e `PersistTransactionToolArguments`
      (`infrastructure/ai/tool/argument/`), remove
      `@Tool`/`@ToolParam` de `application`, `defaultTools(transactionTools)` no controller, o bean
      `ToolExecutionExceptionProcessor`, `ProblemType.INVALID_COMMAND`, `problem.invalid-command.*` e o handler de
      `ConstraintViolationException`
- [x] [TDD] `VoiceCommandIT` (parametrizado): descrição vazia, descrição com 256 caracteres, `amount` `80.005`,
      `amount` `0` e categoria ausente → 422 `invalid-command`; nada gravado
- [x] [TDD] `VoiceCommandIT`: categoria inexistente (`"FOO"`) → 422 `invalid-command`; a saída do log não contém
      `FOO` — implementa o handler de `DatabindException` e o nível `ERROR` do `MethodToolCallback`
- [x] [TDD] `VoiceCommandIT` (logs): a recusa gera `INFO` `Request rejected` com `status` 422, `problemType`
      `urn:problem-type:budgeting:invalid-command` e `invalidFields` `["amount"]`, com o `traceId` da requisição
- [x] [TDD] `VoiceCommandIT`: falha inesperada dentro da tool (`@MockitoBean TransactionRepository` lançando
      `RuntimeException`) → 500 `about:blank` genérico, sem o texto da exceção no corpo — confirma o `alwaysThrow`

### Plano de verificação

- `./gradlew build` → `BUILD SUCCESSFUL`, sem `OPENAI_API_KEY`
- `./gradlew integrationTest --tests "dio.budgeting.infrastructure.http.VoiceCommandIT"` → passa, inclusive o
  caminho feliz existente
- `grep -rn "org.springframework.ai" src/main/java/dio/budgeting/application src/main/java/dio/budgeting/domain`
  → nenhuma linha
- `grep -rn "org.springframework.ai" src/main/java/dio/budgeting/infrastructure/http/error` → nenhuma linha

### Resumo da fase

- `infrastructure/ai/tool/`: `TransactionTools` (`@Component`, `@Validated`) com as tools `persist-transaction` e
  `list-transactions-by-category`, `PersistTransactionToolArguments` (`argument/`; Bean Validation + `@ToolParam`,
  `toInput()`) e `ToolExecutionConfiguration` (`ToolExecutionExceptionProcessor` com `alwaysThrow(true)` e
  `rethrowExceptions(ConstraintViolationException, DatabindException)`). Os use cases e o `PersistTransactionInput`
  não têm mais anotações do Spring AI; o controller usa `defaultTools(transactionTools)`.
- `ApiExceptionHandler`: `ConstraintViolationException` e `DatabindException` → 422 `invalid-command` sem `errors`;
  log `INFO` `Request rejected` com `invalidFields` (nomes dos campos, sem valores). `MethodToolCallback` em `ERROR`
  no `application.properties`.
- Decisões da fase (em Decisões): DTO da tool é `PersistTransactionToolArguments` em `ai/tool/argument/` (não
  `...Input`/`...Request`); parâmetro da tool é `arguments`, e o JSON do modelo é `{"arguments": {...}}`; a tool
  ainda devolve o `TransactionOutput` ao modelo (avaliar `...ToolResponse` na T09).
- Os itens 2, 3, 4 e 5 nasceram verdes (o item 1 já implementava o mecanismo). Conferido por mutação: sem o nível
  `ERROR` do `MethodToolCallback`, o teste da categoria `FOO` falha. Com `alwaysThrow(false)`, o teste do item 5 falha.
  O teste do item 5 está em uma classe `@Nested` por causa do `@MockitoBean`.
- Verificação: `./gradlew build` sem `OPENAI_API_KEY` → `BUILD SUCCESSFUL`; `VoiceCommandIT` passa, inclusive o
  caminho feliz; os dois `grep` de `org.springframework.ai` → nenhuma linha.
- Para a próxima sessão: a Fase 4 é só documentação (regras, `CLAUDE.md`, `docs/tarefas.md`); citar a estrutura
  `infrastructure/ai/tool/` e o 422 `invalid-command`.

## Fase 4: Documentação

Status: Em andamento  <!-- Não iniciada | Em andamento | Concluída -->

- [x] [verificação] `.claude/rules/api-conventions.md`: toda validação de dado externo acontece na fronteira em
      que ele entra (substitui "regras de negócio ficam na aplicação"); critério 400 (requisição inválida por si)
      x 422 (requisição válida, conteúdo interpretado ou estado impede a operação) na tabela de status
- [x] [verificação] `.claude/rules/error-handling.md`: linhas de `ConstraintViolationException` e
      `DatabindException` (argumentos de tool) → 422 `invalid-command` na tabela "Mapeamento"; `@Validated` só nos
      adaptadores de tool
- [x] [verificação] `.claude/rules/architecture.md`: validação na fronteira, invariantes do domínio como rede de
      proteção; adaptador de tools em `infrastructure/ai/tool` em "Portas e adaptadores"
- [x] [verificação] `.claude/rules/ai-integration.md`: argumentos das tools validados no adaptador (`@Validated`);
      recusa → 422 sem resposta do modelo; falhas de tool nunca voltam ao modelo como texto
- [x] [verificação] `CLAUDE.md` (Estrutura: `infrastructure/ai/tool/` e `infrastructure/config/`); `docs/tarefas.md`:
      nota na T09 (tools já em `infrastructure/ai/tool` desde a T06) e na T16 (áudio estático do 422 `invalid-command`)
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
