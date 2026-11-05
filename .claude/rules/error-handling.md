---
paths:
  - "src/main/java/**"
---

# Tratamento de erros

## Respostas de erro

- Formato único: Problem Details (RFC 9457).
- Um único `@RestControllerAdvice` converte exceções em resposta; controllers não montam corpo de erro.
- `detail` é conteúdo para o usuário: em pt-BR, sem stack trace, sem mensagem interna de exceção.

## Exceções

- Resultado de negócio esperado → exceção tipada `<Condição>Exception` (ex.: `TransactionNotFoundException`).
- Violação de invariante na construção → `IllegalArgumentException` (erro de programação; vira 500).
- Falha de infraestrutura (banco, provedor de IA) → tratada no adaptador, onde se decide retentar, degradar ou
  propagar. A do provedor de IA sai da porta como exceção neutra (`AiProviderUnavailableException`, transitória, ou
  `AiProviderFailedException`, definitiva), traduzida pelo aspecto `OpenAiExceptionTranslationAspect`; o use case e
  o advice nunca veem tipo do SDK.

## Mapeamento

Todo erro sai em `application/problem+json` pelo `ApiExceptionHandler` (`infrastructure/http/error/`), com `type`,
`title`, `status`, `detail` e `instance` (caminho da requisição, sem query string); sem `requestId` no corpo (o
`X-Request-Id` do cabeçalho é a fonte). `title` e `detail` vêm de `messages.properties` (locale fixo `pt_BR`).

| Exceção                                              | Status | `type`                                           | Chave dos textos                                            | Log                          |
|------------------------------------------------------|--------|--------------------------------------------------|-------------------------------------------------------------|------------------------------|
| `AuthenticationException` (token ausente ou inválido)  | 401    | `urn:problem-type:budgeting:unauthenticated`     | `problem.unauthenticated.*`, com `WWW-Authenticate: Bearer` | `WARN` `Authentication failed` |
| `TransactionNotFoundException` (ID inexistente, malformado ou de outro usuário) | 404 | `urn:problem-type:budgeting:transaction-not-found` | `problem.transaction-not-found.*` | `INFO` `Request rejected`    |
| `TransactionProposalNotFoundException` (ID inexistente, malformado, de outro usuário, já confirmada, já cancelada ou expirada) | 404 | `urn:problem-type:budgeting:transaction-proposal-not-found` | `problem.transaction-proposal-not-found.*` | `INFO` `Request rejected` |
| `MethodArgumentNotValidException`                    | 400    | `urn:problem-type:budgeting:validation-error`    | `problem.validation-error.*`                                | `INFO` `Request rejected`    |
| `HttpMessageNotReadableException` (enum inválido)    | 400    | `urn:problem-type:budgeting:validation-error`    | `problem.validation-error.*`                                | `INFO` `Request rejected`    |
| `HttpMessageNotReadableException` (JSON malformado)  | 400    | `urn:problem-type:budgeting:malformed-request`   | `problem.malformed-request.*`                               | `INFO` `Request rejected`    |
| `MethodArgumentTypeMismatchException`                | 400    | `urn:problem-type:budgeting:validation-error`    | `problem.validation-error.*`                                | `INFO` `Request rejected`    |
| `MissingServletRequestPartException`                 | 400    | `urn:problem-type:budgeting:validation-error`    | `problem.validation-error.*`                                | `INFO` `Request rejected`    |
| `ConstraintViolationException` (argumentos de tool)  | 422    | `urn:problem-type:budgeting:invalid-command`     | `problem.invalid-command.*`                                 | `INFO` `Request rejected`, com `invalidFields` |
| `DatabindException` (argumentos de tool)             | 422    | `urn:problem-type:budgeting:invalid-command`     | `problem.invalid-command.*`                                 | `INFO` `Request rejected`, com `invalidFields` |
| `MultipleToolCallsException` (segunda chamada de tool no mesmo comando) | 422 | `urn:problem-type:budgeting:invalid-command` | `problem.invalid-command.*` | `INFO` `Request rejected`, sem `invalidFields` |
| `NoResourceFoundException`                           | 404    | `about:blank`                                    | `problemDetail.title.<FQN>`, `problemDetail.<FQN>`          | `INFO` `Request rejected`    |
| `HttpRequestMethodNotSupportedException`             | 405    | `about:blank`                                    | `problemDetail.title.<FQN>`, `problemDetail.<FQN>`          | `INFO` `Request rejected`    |
| `HttpMediaTypeNotSupportedException`                 | 415    | `about:blank`                                    | `problemDetail.title.<FQN>`, `problemDetail.<FQN>`          | `INFO` `Request rejected`    |
| `MaxUploadSizeExceededException`                     | 413    | `about:blank`                                    | `problemDetail.title.<FQN>`, `problemDetail.<FQN>`          | `INFO` `Request rejected`    |
| `AiProviderUnavailableException` (IO, 429, 5xx)      | 503    | `urn:problem-type:budgeting:assistant-unavailable` | `problem.assistant-unavailable.*`, com `Retry-After`      | `WARN` `AI provider unavailable` |
| `AiProviderFailedException` (demais status)          | 500    | `about:blank`                                    | `problem.unexpected-error.*`                                | `ERROR` `Unexpected error`   |
| Qualquer outra `Exception`                           | 500    | `about:blank`                                    | `problem.unexpected-error.*`                                | `ERROR` `Unexpected error`   |

- O `validation-error` também cobre a parte `file` do áudio vazia ou fora de M4A/MP3/WAV (`@AudioFile`), com
  `{parameter: "file", detail}`, sem chamada à IA.
- Extensão `errors` nos `validation-error`: lista de `{pointer, detail}` para campo do corpo (JSON Pointer, ex.:
  `#/amount`) ou `{parameter, detail}` para caminho, query ou parte do multipart. JSON malformado não traz `errors`.
- Um record de query params com `@Valid` (sem `@RequestBody`, como `ListTransactionsRequest`) chega ao handler como
  `MethodArgumentNotValidException` igual ao corpo; o handler olha o `@RequestBody` do parâmetro para escolher
  `pointer` (corpo) ou `parameter` (query). Falha de conversão (`category=FOO`, `size=abc`) vira o texto
  `problem.invalid-value`, sem repetir o valor rejeitado.
- A mensagem de `TransactionNotFoundException` não vai ao corpo nem ao log; o 404 é o mesmo para o ID malformado e
  para a transação de outro usuário.
- `transaction-proposal-not-found` (404): um só corpo para a proposta inexistente, malformada, de outro usuário, já
  confirmada, já cancelada (a proposta é apagada na decisão) ou expirada (vencida, ainda não apagada pela faxina). O
  `detail` cita as três situações. A mensagem da exceção não vai ao corpo nem ao log.
  O controller faz o `parse` do ID e lança a exceção quando ele é malformado.
- `unauthenticated` (401): um só corpo para token ausente, malformado, com assinatura errada, expirado, sem `exp` ou
  com `sub` que não é UUID. O `AuthenticationEntryPoint` repassa a exceção ao `HandlerExceptionResolver`, e o
  `ApiExceptionHandler` monta a resposta; o log leva `status` e `problemType`, nunca o token.
- `invalid-command` (422): o modelo enviou argumentos inválidos à tool (valor não positivo, descrição vazia ou
  longa, valor com mais de 2 casas, categoria ausente ou inexistente) ou mais de uma proposta diferente no mesmo
  comando. Sem `errors`; `title` e `detail` fixos. Não há
  segunda chamada ao modelo nem síntese de voz. As exceções chegam sem invólucro porque o
  `ToolExecutionExceptionProcessor` (`infrastructure/ai/tool/`) as relança; qualquer outra falha de tool vira 500 e
  nunca volta ao modelo como texto.
- `@Validated` (method validation) só nos adaptadores de tool; no HTTP a validação é o `@Valid` do controller.
- `type` em URN (`urn:problem-type:budgeting:<slug>`) só para erro com semântica própria; erro puramente HTTP usa
  `about:blank`, com o `title` em pt-BR pelos códigos do Spring.
- O `detail` do 500 é sempre genérico; a mensagem da exceção só vai para o log, como stack trace.
- `Retry-After` do 503 vem de `budgeting.errors.assistant-unavailable-retry-after` (padrão `30s`).

### Acrescentar uma exceção

1. Mapeie a exceção no `ApiExceptionHandler` (sobrescreva o método do `ResponseEntityExceptionHandler` ou crie um
   `@ExceptionHandler`); nunca monte corpo de erro no controller.
2. Acrescente os textos em `messages.properties` (`title` e `detail` em pt-BR): chave `problem.<slug>.*` para tipo
   próprio, ou os códigos `problemDetail.title.<FQN>` e `problemDetail.<FQN>` para a exceção do Spring.
3. Se o erro tem semântica própria, crie o `type` em URN no `ProblemType`; senão, deixe `about:blank`.
4. Acrescente uma linha a esta tabela e um teste no `ErrorResponseIT`.

## Logs

- Logs em JSON (ECS) com `traceId` e `spanId` do Micrometer Tracing em toda linha da requisição; o `traceId` é o ID
  de correlação e volta ao cliente em `X-Request-Id`.
- Evento = mensagem fixa em inglês + pares chave-valor (`log.atInfo().addKeyValue("resultCount", n).log("Transactions listed")`);
  nunca concatenar valores na mensagem.
- Campos permitidos: IDs (`transactionId`) e metadados técnicos (tamanhos, contagens, tipo de mídia, status).
- Nunca logar: transcrição, prompt, resposta do modelo, texto sintetizado, argumentos de tools, áudio, dados de
  negócio (descrição, valor, categoria), cabeçalhos, query string, valores rejeitados em validação nem a mensagem
  crua de exceção. O conteúdo vai para a auditoria (T20), não para o log.
- As opções de observação do Spring AI que escrevem conteúdo (`log-prompt`, `log-completion`, `log-transcript`,
  `log-text`, `include-content`) ficam sempre em `false`.

| Situação                                        | Nível   | Conteúdo                                                        |
|-------------------------------------------------|---------|-----------------------------------------------------------------|
| Exceção inesperada (500)                        | `ERROR` | stack trace                                                     |
| Falha de dependência externa (banco, OpenAI)    | `WARN`  | no adaptador que a trata                                        |
| 401 e 429                                       | `WARN`  | sinais de segurança                                             |
| Demais 4xx                                      | `INFO`  | uma linha, sem stack trace, com status e tipo do problema (nomes dos campos em validação) |

## Não fazer

- `throw new RuntimeException(...)` ou `IllegalStateException` para resultado de negócio.
- `catch (Exception e)` silencioso.
- `@ResponseStatus` em exceção; handler por controller.
