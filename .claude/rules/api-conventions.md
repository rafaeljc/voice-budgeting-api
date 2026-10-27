---
paths:
  - "src/main/java/**/infrastructure/http/**"
---

# Convenções de API

Regras que os endpoints HTTP seguem. O contrato publicado é documentado em OpenAPI.

## Recursos e URLs

- Versão do contrato no caminho (`/api/v1`); mudança incompatível gera nova versão.
- Recursos no plural, minúsculos, kebab-case.
- Sem verbos no caminho; transição de estado é um sub-recurso com substantivo (`/{id}/confirmation`, não `/{id}/confirm`).
- Filtros, paginação e ordenação como query params, nunca como segmento do caminho.
- Recurso de outro usuário responde 404, como se não existisse.

## Métodos

| Método | Uso                               | Sucesso                           |
|--------|-----------------------------------|-----------------------------------|
| GET    | leitura e listagem                | 200                               |
| POST   | criação                           | 201 + `Location` + recurso criado |
| POST   | transição de estado (sub-recurso) | 200 + recurso atualizado          |
| PUT    | substituição completa             | 200 + recurso atualizado          |
| PATCH  | atualização parcial               | 200 + recurso atualizado          |
| DELETE | remoção                           | 204                               |

## Status de erro

Corpo sempre em Problem Details (ver `error-handling.md`).

| Status | Quando                                                     |
|--------|------------------------------------------------------------|
| 400    | requisição malformada ou que falha na validação de formato |
| 401    | não autenticado                                            |
| 404    | recurso inexistente ou de outro usuário                    |
| 409    | conflito com o estado atual do recurso                     |
| 413    | corpo acima do tamanho máximo                              |
| 415    | tipo de mídia não aceito                                   |
| 422    | entrada bem formada que viola regra de negócio             |
| 429    | limite de requisições atingido                             |
| 500    | erro inesperado                                            |
| 503    | dependência externa indisponível                           |

- Erros de validação identificam cada campo inválido.
- 429 e 503 informam quando tentar de novo (`Retry-After`).

## Representação

- JSON com campos em camelCase; o recurso é o corpo, sem envelope.
- IDs como string; enums como string em `UPPER_SNAKE_CASE`.
- Data e hora em ISO 8601 com fuso (UTC); data sem hora em `yyyy-MM-dd`.
- Valor monetário como string decimal `^\d{1,16}\.\d{2}$` mais `currency` (`BRL`), lado a lado; nunca em ponto
  flutuante.
- Campos ausentes omitidos; listas nunca `null`.
- Áudio com o tipo de mídia correto (`audio/mpeg` para MP3); upload em `multipart/form-data`.

## Listagens

- Sempre paginadas, com tamanho máximo de página.
- Ordenação padrão explícita e estável.
- Períodos com início inclusivo e fim exclusivo.

## Cabeçalhos

- `Location` em toda resposta 201.
- O ID de correlação da requisição é devolvido na resposta.

## Controllers e DTOs

- Controller sem lógica: converte a requisição, chama um único use case, converte a resposta.
- `record` com sufixo `Request` / `Response`, só em `infrastructure/http/request|response`.
- `request.toInput()` e `Response.from(output)`; o controller não monta entidades de domínio.
- Validação de formato com Bean Validation; regras de negócio ficam na aplicação.
- Todo endpoint documenta em OpenAPI as respostas de sucesso e de erro, com descrições em pt-BR.

## Não fazer

- Expor entidade de domínio ou JPA na resposta; serializar `Page` do Spring Data direto.
- Montar corpo de erro no controller; responder 200 com corpo de erro.
- Verbo no caminho.
- Data e hora sem fuso no contrato.
- 403 para recurso de outro usuário (revela que ele existe).
