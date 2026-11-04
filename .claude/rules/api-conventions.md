---
paths:
  - "src/main/java/**/infrastructure/http/**"
---

# Convenções de API

Regras que os endpoints HTTP seguem. O contrato publicado é documentado em OpenAPI.

## Recursos e URLs

- Versão do contrato no caminho (`/api/v1`); mudança incompatível gera nova versão.
- Recursos no plural, minúsculos, kebab-case.
- Sem verbos no caminho, exceto a ação de negócio que não é CRUD sobre o recurso: `POST /{id}/<verbo>` no recurso
  que a recebe (`/transaction-proposals/{id}/confirm`, `/transaction-proposals/{id}/cancel`). O recurso é um
  substantivo no plural, e o verbo vem só depois do `{id}`.
- Filtros, paginação e ordenação como query params, nunca como segmento do caminho.
- Recurso de outro usuário responde 404, como se não existisse.
- Identificador malformado é recurso inexistente: o mesmo 404 do ID que não existe, nunca 400.

## Métodos

| Método | Uso                               | Sucesso                           |
|--------|-----------------------------------|-----------------------------------|
| GET    | leitura e listagem                | 200                               |
| POST   | criação                           | 201 + `Location` + recurso criado |
| POST   | ação de negócio (`/{id}/<verbo>`) | 201 + `Location` + recurso criado, se cria um recurso (ex.: confirmar uma proposta); 204, se não devolve nada |
| PUT    | substituição completa             | 200 + recurso atualizado          |
| PATCH  | atualização parcial               | 200 + recurso atualizado          |
| DELETE | remoção                           | 204                               |

## Status de erro

Corpo sempre em Problem Details (ver `error-handling.md`).

| Status | Quando                                                     |
|--------|------------------------------------------------------------|
| 400    | requisição inválida por si só: malformada ou que falha na validação |
| 401    | não autenticado                                            |
| 404    | recurso inexistente ou de outro usuário                    |
| 409    | conflito com o estado atual do recurso                     |
| 413    | corpo acima do tamanho máximo                              |
| 415    | tipo de mídia não aceito                                   |
| 422    | requisição válida, mas o conteúdo interpretado (ex.: argumentos da tool de IA) ou o estado do servidor impede a operação |
| 429    | limite de requisições atingido                             |
| 500    | erro inesperado                                            |
| 503    | dependência externa indisponível                           |

- Erros de validação identificam cada campo inválido.
- 400 e 422 não se resolvem reenviando a mesma requisição; o que muda é a causa: no 400 o erro está na requisição,
  no 422 está no conteúdo interpretado ou no estado do servidor.
- 429 e 503 informam quando tentar de novo (`Retry-After`).

## Representação

- JSON com campos em camelCase; o recurso é o corpo, sem envelope. A exceção são as listagens paginadas
  (`content` + `page`, ver abaixo).
- IDs como string; enums como string em `UPPER_SNAKE_CASE`.
- Data e hora em ISO 8601 com fuso (UTC); data sem hora em `yyyy-MM-dd`.
- Valor monetário como string decimal `^\d{1,16}\.\d{2}$` mais `currency` (`BRL`), lado a lado; nunca em ponto
  flutuante.
- Campos ausentes omitidos; listas nunca `null`.
- Áudio recebido em `multipart/form-data`. O comando de voz responde `200` JSON com a fala dentro do corpo
  (`speech.contentType` = `audio/mpeg` para MP3, `speech.content` em base64) e, só quando nasce uma proposta,
  `transactionProposal`; nunca o áudio cru como corpo.
- Áudio recebido: M4A, MP3 ou WAV, até 1 MB. O formato vem da assinatura dos bytes (`AudioFormat.detect`), nunca do
  `Content-Type` nem da extensão do cliente; vazio ou fora do formato é 400 `validation-error` na parte `file`.

## Listagens

- Sempre paginadas, com tamanho máximo de página.
- Corpo `{content: [...], page: {size, number, totalElements, totalPages}}`; `page.size` e `page.number` mostram o
  valor aplicado.
- `page` a partir de 0 (padrão 0); `size` padrão 20, máximo 100. Valor numérico fora da faixa é ajustado em
  silêncio (`size` > 100 vira 100, `size` < 1 vira 20, `page` < 0 vira 0); valor não numérico é 400.
- Ordenação padrão explícita e estável: `sort=campo,direção`, restrito a uma lista de campos do contrato (nunca
  nomes da entidade), com desempate por `id` na mesma direção. Campo ou direção fora da lista é 400.
- Períodos com início inclusivo e fim exclusivo.

## Cabeçalhos

- `Authorization: Bearer <token>` em toda requisição, exceto `/actuator/health`; sem ele, ou com token inválido, a
  resposta é 401 com `WWW-Authenticate: Bearer` (ver `security.md`).
- `Location` em toda resposta 201.
- `X-Request-Id` em toda resposta, com o `traceId` da requisição (32 hex); um `X-Request-Id` enviado pelo cliente é
  ignorado. A correlação de entrada é pelo `traceparent` (W3C).

## Controllers e DTOs

- Controller sem lógica: converte a requisição, chama um único use case, converte a resposta.
- `record` com sufixo `Request` / `Response`, só em `infrastructure/http/request|response`.
- `request.toInput()` e `Response.from(output)`; o controller não monta entidades de domínio.
- Toda validação de dado externo acontece na fronteira em que ele entra: no HTTP, Bean Validation no `Request`
  (formato e regras de negócio); o use case recebe dado válido. O domínio só mantém as invariantes como rede de
  proteção.
- O contrato OpenAPI fica em `docs/openapi.yaml`, e o PR que muda um
  endpoint, um status ou um erro atualiza o arquivo. Todo endpoint documenta as respostas de sucesso e de erro, com
  descrições em pt-BR. O Swagger UI do `compose.yml` (`docker compose up swagger-ui`, porta 8081) só consulta o
  contrato.

## Não fazer

- Expor entidade de domínio ou JPA na resposta; serializar `Page` do Spring Data direto.
- Montar corpo de erro no controller; responder 200 com corpo de erro.
- Verbo no caminho fora da ação de negócio `POST /{id}/<verbo>`.
- Data e hora sem fuso no contrato.
- 403 para recurso de outro usuário (revela que ele existe).
- Endpoint novo sem `bearerAuth` e sem a resposta 401 no `docs/openapi.yaml`.
