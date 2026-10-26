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

<!-- A definir na T04: nível por tipo de exceção, correlação, o que nunca logar. -->

## Não fazer

- `throw new RuntimeException(...)` ou `IllegalStateException` para resultado de negócio.
- `catch (Exception e)` silencioso.
- `@ResponseStatus` em exceção; handler por controller.
