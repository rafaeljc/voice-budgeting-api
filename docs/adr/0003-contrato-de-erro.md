---
status: aceito
data: 2026-10-28
---

# Padronizar os erros em Problem Details com `type` em URN, textos em pt-BR e falhas da IA separadas por causa

## Contexto e problema

Todo erro saía no JSON padrão do Spring Boot, sem `type` estável, com textos em inglês e mensagens internas de
exceção (API-04). Como dar ao cliente um contrato de erro previsível, sem expor detalhes internos, e distinguir a
falha passageira da IA (vale tentar de novo) da definitiva (bug nosso ou chave inválida)?

## Opções consideradas

- `type` como `about:blank` em todos os erros
- `type` como URL absoluta sob um domínio da API
- `type` como URI relativa
- `type` como URN (`urn:problem-type:budgeting:<slug>`) para erros com semântica própria e `about:blank` para os
  puramente HTTP
- `title` e `detail` em inglês, em pt-BR com locale fixo ou conforme `Accept-Language`
- Falhas da IA como um único 500, um único 503 ou separadas por causa (transitória x definitiva)

## Decisão

Opção escolhida: "`type` em URN para os erros com semântica própria e `about:blank` para os puramente HTTP", porque
é URI válida pelo RFC 9457, estável e sem inventar um domínio; uma URL absoluta exigiria um domínio que não temos, e
uma URI relativa muda de significado conforme o caminho.

- **Formato:** `ApiExceptionHandler` (`@RestControllerAdvice` único, estende `ResponseEntityExceptionHandler`) em
  `infrastructure/http/error/`; `application/problem+json` com `type`, `title`, `status`, `detail` e `instance`
  (caminho da requisição); sem `requestId` no corpo, pois o `X-Request-Id` do cabeçalho é a fonte única.
- **Validação:** extensão `errors` com `{pointer, detail}` (campo do corpo, inclusive enum inválido) ou
  `{parameter, detail}` (caminho, query, parte do multipart), como no exemplo do RFC 9457. Só JSON sintaticamente
  inválido é `malformed-request`, sem `errors`.
- **Idioma:** `title` e `detail` em pt-BR com locale fixo `pt_BR` (`Accept-Language` ignorado), em
  `messages.properties`; o projeto é pt-BR e i18n está fora do escopo.
- **IA:** falhas transitórias (rede, 429, 5xx do provedor) → 503 `assistant-unavailable` com `Retry-After` fixo e
  configurável (`WARN`); demais falhas do provedor → 500 genérico (`ERROR`). Um bug nosso ou uma chave inválida não
  aparece como indisponibilidade passageira, e a separação é a que a T10 vai retentar. O `Retry-After` do provedor
  não é repassado: mede o limite da nossa chave, compartilhado por todos os usuários, e só existe no 429.

### Consequências

- Bom, porque o cliente trata erros por `type` estável e por campo (`errors`), sem depender do texto.
- Bom, porque nenhuma resposta traz mensagem interna, regex ou stack trace; a causa vai só para o log.
- Bom, porque acrescentar uma exceção é mecânico: texto em `messages.properties`, linha na tabela de
  `error-handling.md` e, se tiver semântica própria, um `ProblemType`.
- Ruim, porque o advice conhece o SDK da OpenAI (`com.openai.errors.*`) até a T09 criar o adaptador de IA; a T09
  troca esse handler pela exceção neutra da porta.
- Ruim, porque erros lançados em filtros e o despacho para `/error` continuam no formato antigo do Boot; ficam para
  a T08, junto com o 401 do Spring Security.
- Ruim, porque o `Retry-After` é fixo (30 s) até a T15 poder informar o tempo restante do circuito aberto.
- Ruim, porque `title` e `detail` só existem em pt-BR; atender outro idioma exige i18n numa tarefa futura.
