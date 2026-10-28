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
- Falha de infraestrutura (banco, OpenAI) → tratada no adaptador, onde se decide retentar, degradar ou propagar.

## Mapeamento

<!-- A definir na T05: tabela exceção → status → type/title. -->

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
