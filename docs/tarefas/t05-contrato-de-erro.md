# T05 — Contrato de erro padronizado

Padronizar todas as respostas de erro da API no formato *Problem Details* (RFC 9457), com `type` estável,
textos em pt-BR sem detalhes internos e falhas da IA distinguidas entre transitórias (503) e definitivas (500)
(API-04).

## Escopo

**Dentro:**
- `@RestControllerAdvice` único, `ApiExceptionHandler` (`infrastructure/http/error/`), estendendo
  `ResponseEntityExceptionHandler`; todo erro sai em `application/problem+json`
- `ProblemType` (URNs dos tipos) e `InvalidField` (item da extensão `errors`) em `infrastructure/http/error/`
- `type` em URN `urn:problem-type:budgeting:<slug>` para `validation-error`, `malformed-request` e
  `assistant-unavailable`; `about:blank` para erros puramente HTTP (404 de rota, 405, 413, 415, 500)
- `title` e `detail` em pt-BR, locale fixo `pt_BR` (`Accept-Language` ignorado), textos em
  `src/main/resources/messages.properties` com os códigos do Spring (`problemDetail.type.<FQN>`,
  `problemDetail.title.<FQN>`, `problemDetail.<FQN>`) e chaves próprias para os handlers do projeto
- mensagem própria no `@Pattern` de `amount` e `currency` do `TransactionRequest` (sem expor a regex)
- extensão `errors`: `{pointer, detail}` para campo do corpo (inclusive enum inválido, como `#/category`);
  `{parameter, detail}` para caminho, query ou parte do multipart; JSON malformado sem `errors`
- `instance` = caminho da requisição (padrão do Spring, sem query string); sem `requestId` no corpo
- erros mapeados: validação de campo, enum inválido no corpo, JSON malformado, parâmetro de caminho inválido,
  parte `file` ausente, rota inexistente (404), método não suportado (405), tipo de mídia não suportado (415),
  upload acima do limite (413) e exceção inesperada (500, `detail` genérico)
- falhas da IA tratadas no advice direto pelas exceções do SDK (`com.openai.errors.*`):
  - transitórias (IO, 429 e 5xx do provedor) → 503 `assistant-unavailable` com `Retry-After` fixo,
    configurável em `application.properties` (padrão 30 s)
  - definitivas (demais status do provedor: 400, 401, 403, 404, 422) → 500 `about:blank`
- logs no padrão de `error-handling.md`: 4xx → `INFO` `Request rejected` (`status`, `problemType`,
  `invalidFields` com os nomes dos campos); falha transitória da IA → `WARN` `AI provider unavailable`
  (`status`, `providerStatus` quando houver); 500 → `ERROR` `Unexpected error` com stack trace
- testes: `ErrorResponseIT` (novo), `UploadLimitIT` (novo, servidor real), casos de falha no `VoiceCommandIT`;
  `AiStub.givenTranscriptionFailure(AiFailure)` com o enum neutro `AiFailure`
- ADR `docs/adr/0003-contrato-de-erro.md`; tabela "Mapeamento" e regra para acrescentar exceções em
  `.claude/rules/error-handling.md`; `error/` na Estrutura do `CLAUDE.md`; nota na T09 de `docs/tarefas.md`

**Fora:**
- exceções de negócio: 404 de transação inexistente (T07) e 422 de regra de negócio (T06) — só acrescentam
  linhas à tabela de mapeamento
- timeout, retry e circuit breaker (T10/T15); degradação controlada (T16)
- `Retry-After` dinâmico, como o tempo restante do circuito aberto (T15)
- falhas da IA nas etapas de chat e voz nos testes (T10 estende o `AiStub`)
- erros dentro das tools, devolvidos pelo Spring AI ao modelo e não ao cliente (T09/T14)
- exceções lançadas em filtros e o despacho para `/error` (`BasicErrorController`), que continuam no formato
  antigo do Boot — T08, junto com o 401 do Spring Security
- documentação OpenAPI das respostas de erro (T07)
- README (seção de erros fica para quando o contrato for documentado na T07/T26)
- i18n (`Accept-Language`)

## Decisões

- Advice estende `ResponseEntityExceptionHandler` e lê os textos de um `MessageSource` com os códigos do
  Spring — forma idiomática; nenhum texto de erro no código Java
- `type` em URN (`urn:problem-type:budgeting:<slug>`) para os tipos com semântica própria e `about:blank` para os
  puramente HTTP — URI válida pelo RFC 9457, estável e sem domínio inventado; URL absoluta exigiria um domínio que
  não temos, e URI relativa muda de significado conforme o caminho (decisão do usuário)
- `title` e `detail` em pt-BR com locale fixo — conteúdo para o usuário; o projeto é pt-BR e i18n está fora do
  escopo (decisão do usuário)
- Extensão `errors` com `pointer` (JSON Pointer, corpo) ou `parameter` (caminho, query, parte do multipart), como
  no exemplo do RFC 9457; enum inválido no corpo é `validation-error` com pointer; só JSON sintaticamente
  inválido é `malformed-request` (decisão do usuário)
- `instance` = caminho, sem query; sem `requestId` no corpo — o `X-Request-Id` do cabeçalho é a fonte única
  (decisão do usuário)
- Falhas da IA separadas por causa: transitórias → 503 com `Retry-After`, `WARN`; definitivas → 500, `ERROR` —
  um bug nosso ou chave inválida não aparece como indisponibilidade passageira; separa o que a T10 vai retentar
  (decisão do usuário)
- `Retry-After` fixo e configurável por enquanto, não copiado da OpenAI — o `Retry-After` do provedor mede o
  limite da nossa chave (compartilhado por todos os usuários) e só existe no 429 (decisão do usuário)
- Advice conhece o SDK da OpenAI (`com.openai.errors.*`) — não existe adaptador de IA até a T09; uma exceção
  neutra agora exigiria `try/catch` no controller. A T09 troca o handler pela exceção neutra da porta, nota
  registrada em `docs/tarefas.md` (decisão do usuário)
- Erros de filtro e `/error` fora — o advice cobre tudo o que é lançado hoje; os primeiros erros de filtro reais
  surgem na T08 (Spring Security, com `AuthenticationEntryPoint` próprio) (decisão do usuário)
- Pacote `infrastructure/http/error/` — agrupa por assunto o que transforma falha em resposta HTTP e acomoda o
  `ErrorController` que a T08 pode criar; nome `ApiExceptionHandler` para não colidir com o
  `ProblemDetailsExceptionHandler` do Spring Boot (decisão do usuário)
- Log de 500: a mensagem do evento é fixa (`Unexpected error`) e a exceção vai só como stack trace (campos
  `error.*` do ECS), a exceção prevista na tabela de `error-handling.md`; o corpo da resposta nunca traz a mensagem
  da exceção
- README fora da tarefa (decisão do usuário)
- Três fases: erros HTTP, falhas da IA, documentação

## Definição de pronto

- [ ] `./gradlew build` passa (Spotless, Checkstyle, `*Test`, `*IT` e JaCoCo), sem `OPENAI_API_KEY`
- [ ] Todos os erros listados em "Dentro" respondem `application/problem+json` com `type`, `title` (pt-BR),
      `status`, `detail` (pt-BR) e `instance`; os de validação trazem `errors` com `pointer` ou `parameter`
- [ ] Nenhuma resposta de erro traz mensagem interna de exceção, regex ou stack trace
- [ ] Falha transitória da IA → 503 `urn:problem-type:budgeting:assistant-unavailable` com `Retry-After` igual
      ao configurado; falha definitiva → 500
- [ ] Logs de erro seguem a tabela de `error-handling.md` (nível, mensagem fixa, só nomes de campos)
- [ ] ADR 0003 aceito; `error-handling.md` sem o marcador "A definir na T05" e com a tabela de mapeamento;
      `CLAUDE.md` e a nota da T09 atualizados; checkbox da T05 marcado em `docs/tarefas.md` no último commit

## Para futuros agentes

Execute com `/executar-tarefa`. Durante a execução:

- marque `- [x]` no mesmo commit que conclui o item;
- antes do último commit de uma fase, rode o plano de verificação, escreva o resumo da fase com o resultado e
  mude o status para `Concluída` — tudo nesse mesmo commit;
- na última fase, no mesmo commit, confira a definição de pronto e escreva o resumo final (e o plano de deploy,
  se houver);
- mudança no plano só com aprovação do usuário, registrada em Decisões com o motivo.

Contexto para uma sessão nova:

- Branch `feat/contrato-de-erro`; título do PR `feat: padroniza respostas de erro com problem details`. Commits:
  `feat:`, `test:`, `docs:`.
- Estado antes da tarefa: nenhum `@RestControllerAdvice` nem exceção própria; todo erro sai no JSON padrão do
  Boot (`BasicErrorController`). `spring.mvc.problemdetails.enabled` continua desligado: o advice do projeto é o
  `ResponseEntityExceptionHandler`.
- Exceções de hoje e o que o Spring já lança: `MethodArgumentNotValidException` (`@Valid` no corpo),
  `HttpMessageNotReadableException` (JSON malformado ou enum inválido; no Jackson 3 a causa é
  `tools.jackson.databind.DatabindException`/`MismatchedInputException`, com `getPath()` para montar o pointer),
  `MethodArgumentTypeMismatchException` (`GET /transactions/{category}`), `MissingServletRequestPartException`
  (`/transactions/ai` sem `file`), `NoResourceFoundException` (404), `HttpRequestMethodNotSupportedException`
  (405), `HttpMediaTypeNotSupportedException` (415), `MaxUploadSizeExceededException` (413).
- Locale: conferir no Spring Boot 4.1.2 o nome das propriedades de locale fixo (no Boot 3:
  `spring.web.locale=pt_BR` e `spring.web.locale-resolver=fixed`). O Boot interpola as mensagens do Bean
  Validation pelo `MessageSource`, então `message = "{chave}"` no `@Pattern` resolve em `messages.properties`
  (UTF-8 por padrão).
- `ResponseEntityExceptionHandler` resolve `type`, `title` e `detail` pelos códigos `problemDetail.type.<FQN>`,
  `problemDetail.title.<FQN>` e `problemDetail.<FQN>` quando recebe o `MessageSource` (é `MessageSourceAware`).
  Sobrescrever `handleExceptionInternal` (ou `createResponseEntity`) concentra o log de 4xx num ponto só.
- SDK da OpenAI (`openai-java` 4.80.0): base `com.openai.errors.OpenAIException`; `OpenAIIoException` (rede);
  `OpenAIServiceException` com `statusCode()` e subclasses (`RateLimitException`, `InternalServerException`,
  `UnauthorizedException`, `BadRequestException`…). Classificar por tipo/status: IO, 429 ou ≥ 500 → transitória.
  O `AiProviderIsolationIT` só afirma a causa raiz (`ConnectException`): conferir o tipo de topo que chega ao
  controller. O Spring casa `@ExceptionHandler` com causas aninhadas, mas um handler de `Exception` casa antes
  com o wrapper — se o Spring AI embrulhar a exceção do SDK, procurar `OpenAIException` na cadeia de causas.
- `/transactions/ai` declara `produces = "audio/mp3"`: confirmar no teste que o erro sai em
  `application/problem+json`; se a negociação de conteúdo falhar, remover o atributo
  `HandlerMapping.PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE` do request no handler.
- 413: o MockMvc não aplica o limite de multipart (`MockMultipartFile` não passa pelo resolver). O
  `UploadLimitIT` usa servidor real (`webEnvironment = RANDOM_PORT`), expondo o atributo em `@IntegrationTest`
  com `@AliasFor(annotation = SpringBootTest.class)`; enviar arquivo pouco acima de 1 MB (limite padrão) e abaixo
  do `max-swallow-size` do Tomcat (2 MB), para o cliente receber a resposta em vez de conexão reiniciada.
- 500: provocar com `@MockitoBean` na porta `TransactionRepository` lançando `RuntimeException` com um texto
  marcador; afirmar que o corpo não contém o marcador.
- `Retry-After`: propriedade `budgeting.errors.assistant-unavailable-retry-after=30s` (`Duration`), lida por um
  `@ConfigurationProperties` record em `infrastructure/http/error/`; cabeçalho em segundos (`30`).
- `AiStub`/`OpenAiWireMockStub`: `AiFailure { CONNECTION_FAILURE, RATE_LIMITED, PROVIDER_ERROR,
  REQUEST_REJECTED }` → `Fault.CONNECTION_RESET_BY_PEER`, 429, 500 e 401 em `/v1/audio/transcriptions`. O perfil
  `test` já tem `spring.ai.openai.max-retries=0`.
- Logs: `LogEvents.withMessage(output, ...)`; conferir na saída real como o ECS serializa uma lista em
  `addKeyValue` (`invalidFields`) antes de fixar a asserção.
- Checkstyle limita métodos a 50 linhas, inclusive em testes.
- Regras: `.claude/rules/error-handling.md`, `api-conventions.md`, `testing.md`, `architecture.md`.

## Fase 1: Erros HTTP

Status: Em andamento  <!-- Não iniciada | Em andamento | Concluída -->

- [x] [TDD] `ErrorResponseIT` (novo, `infrastructure/http`): `POST /transactions` com `amount` inválido → 400
      `application/problem+json`, `type` `urn:problem-type:budgeting:validation-error`, `title` e `detail` em
      pt-BR, `instance` `/transactions`, `errors` `[{pointer: "#/amount", detail: <mensagem própria>}]` sem a
      regex — implementa `ApiExceptionHandler`, `ProblemType`, `InvalidField`, `messages.properties`, locale fixo
      `pt_BR` e as mensagens do `@Pattern` (`amount` e `currency`)
- [x] [TDD] `ErrorResponseIT`: `category` inexistente no corpo → 400 `validation-error` com pointer
      `#/category`; JSON malformado → 400 `urn:problem-type:budgeting:malformed-request`, sem `errors`
- [x] [TDD] `ErrorResponseIT`: `GET /transactions/FOO` → 400 `validation-error` com `errors`
      `[{parameter: "category", ...}]`; `POST /transactions/ai` sem a parte `file` → 400 `validation-error` com
      `parameter` `file`
- [ ] [TDD] `ErrorResponseIT`: rota inexistente (404), método não suportado (405) e `Content-Type` não suportado
      (415) → `about:blank` com `title` e `detail` em pt-BR
- [ ] [TDD] `UploadLimitIT` (novo, servidor real): upload acima do limite em `/transactions/ai` → 413
      `about:blank` em `application/problem+json` — expõe `webEnvironment` em `@IntegrationTest`
- [ ] [TDD] `ErrorResponseIT`: falha inesperada na porta `TransactionRepository` (`@MockitoBean`) → 500
      `about:blank` com `detail` genérico, sem a mensagem da exceção no corpo
- [ ] [TDD] `ErrorResponseIT` (logs): 4xx de validação gera `INFO` `Request rejected` com `status`,
      `problemType` e `invalidFields` (nomes), sem o valor rejeitado; 500 gera `ERROR` `Unexpected error` com stack
      trace e o `traceId` da requisição

### Plano de verificação

- `./gradlew build` → `BUILD SUCCESSFUL`, sem `OPENAI_API_KEY`
- `./gradlew integrationTest --tests "dio.budgeting.infrastructure.http.ErrorResponseIT"` → passa
- `./gradlew integrationTest --tests "dio.budgeting.infrastructure.http.UploadLimitIT"` → passa
- `grep -rn "@ResponseStatus\|ProblemDetail" src/main/java --include=*.java | grep -v "infrastructure/http/error/"`
  → só o `@ResponseStatus(HttpStatus.CREATED)` do `TransactionController` (nenhum corpo de erro fora do advice)

### Resumo da fase

_(preencher ao concluir a fase)_

## Fase 2: Falhas da IA

Status: Não iniciada  <!-- Não iniciada | Em andamento | Concluída -->

- [ ] [TDD] `VoiceCommandIT` (parametrizado): falha transitória na transcrição (`CONNECTION_FAILURE`,
      `RATE_LIMITED`, `PROVIDER_ERROR`) → 503 `application/problem+json`,
      `urn:problem-type:budgeting:assistant-unavailable`, `Retry-After: 30` — implementa
      `AiStub.givenTranscriptionFailure(AiFailure)` no `OpenAiWireMockStub`, o handler de
      `com.openai.errors.OpenAIException` e a propriedade `budgeting.errors.assistant-unavailable-retry-after`
- [ ] [TDD] `VoiceCommandIT`: falha definitiva (`REQUEST_REJECTED`) → 500 `about:blank` com `detail` genérico
- [ ] [TDD] `VoiceCommandIT` (logs): falha transitória gera `WARN` `AI provider unavailable` com `status` e
      `providerStatus` (quando houver); definitiva gera `ERROR` `Unexpected error` com stack trace

### Plano de verificação

- `./gradlew build` → `BUILD SUCCESSFUL`, sem `OPENAI_API_KEY`
- `./gradlew integrationTest --tests "dio.budgeting.infrastructure.http.VoiceCommandIT"` → passa
- `grep -rn "com.openai" src/main/java` → só em `infrastructure/http/error/ApiExceptionHandler.java`
- `grep -n "assistant-unavailable-retry-after" src/main/resources/application.properties` → uma linha

### Resumo da fase

_(preencher ao concluir a fase)_

## Fase 3: Documentação

Status: Não iniciada  <!-- Não iniciada | Em andamento | Concluída -->

- [ ] [verificação] `.claude/rules/error-handling.md`, seção "Mapeamento": tabela exceção → status → `type` →
      chave do `title` → nível de log, cobrindo todos os erros da tarefa; regra para acrescentar uma exceção nova
      (entrada em `messages.properties`, linha na tabela, `type` em URN se tiver semântica própria); formato da
      extensão `errors`
- [ ] [verificação] ADR `docs/adr/0003-contrato-de-erro.md` (a partir de `docs/adr/template.md`,
      `status: aceito`): contexto (API-04); opções de `type` (`about:blank`, URL absoluta, URN, URI relativa), de
      idioma e de classificação das falhas da IA; decisão e consequências (troca do handler do SDK na T09, erros
      de filtro na T08, `Retry-After` fixo até a T15)
- [ ] [verificação] `CLAUDE.md` (Estrutura: `request/  response/  filter/  error/`); `docs/tarefas.md`, T09: nota
      de que o handler de `com.openai.errors.*` sai do `ApiExceptionHandler` quando o adaptador de IA lançar uma
      exceção neutra
- [ ] [verificação] Marcar a T05 em `docs/tarefas.md` (último commit)

### Plano de verificação

- `./gradlew build` → `BUILD SUCCESSFUL` (inclui Spotless nos `.md`)
- `grep -n "A definir na T05" .claude/rules/error-handling.md` → nenhuma linha
- `grep -n "urn:problem-type:budgeting" .claude/rules/error-handling.md docs/adr/0003-contrato-de-erro.md` → ao
  menos uma linha em cada arquivo
- `grep -n "error/" CLAUDE.md` → uma linha na Estrutura

### Resumo da fase

_(preencher ao concluir a fase)_

## Resumo final

_(preencher ao concluir todas as fases)_
