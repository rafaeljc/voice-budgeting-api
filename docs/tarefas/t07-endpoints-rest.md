# T07 — Endpoints manuais no padrão REST

Adequar os endpoints manuais de cadastro e consulta às boas práticas REST: versão no caminho, consulta individual,
filtro por query param, paginação e ordenação, status e cabeçalhos corretos (API-01, API-03, API-05). Publicar a
especificação OpenAPI gerada pelos testes (DOC-05) e exemplos de requisição dos endpoints manuais (DOC-06).

## Escopo

**Dentro:**
- versão no caminho: `@RequestMapping("/api/v1/transactions")` no `TransactionController`; rotas antigas
  (`/transactions`, `/transactions/{category}`, `/transactions/ai`) removidas sem redirecionamento
- `POST /api/v1/transactions` → 201 + `Location` absoluto (`.../api/v1/transactions/{id}`) + recurso criado
- `GET /api/v1/transactions/{id}` → 200 com o `TransactionResponse`; ID malformado ou inexistente → 404
  `urn:problem-type:budgeting:transaction-not-found` ("Transação não encontrada" / "A transação solicitada não
  existe.")
  - `TransactionId.parse(String)` → `Optional<TransactionId>` (UUID malformado = vazio)
  - `TransactionNotFoundException` em `domain/`; `Optional<Transaction> findById(TransactionId)` na porta
  - `GetTransactionUseCase` recebe o ID em texto; vazio ou não encontrado lança a exceção
- `GET /api/v1/transactions?category=&page=&size=&sort=` → 200 com
  `{content: [...], page: {size, number, totalElements, totalPages}}`
  - `category` opcional (sem ela, todas as transações)
  - `page` padrão 0; `size` padrão 20, máximo 100; valores numéricos fora da faixa ajustados em silêncio
    (`size` > 100 → 100; `size` < 1 → 20; `page` < 0 → 0); `page.size`/`page.number` mostram o valor aplicado
  - `sort` = `createdAt|amount` + `,asc|desc`; padrão `createdAt,desc`; desempate sempre por `id` (mesma direção)
  - `category` inexistente, `sort` fora da lista, `page`/`size` não numérico → 400 `validation-error` com
    `{parameter, detail}`
- tipos de paginação próprios no `domain` (sem Spring): `PageQuery(number, size)`, `PageResult<T>(content,
  number, size, totalElements)` com `totalPages()` e `map(...)`, `TransactionFilter(Category category)`,
  `TransactionSort(Field {CREATED_AT, AMOUNT}, Direction {ASC, DESC})` com `DEFAULT`; porta
  `PageResult<Transaction> findAll(TransactionFilter, TransactionSort, PageQuery)`
- `application`: `ListTransactionsUseCase` (`ListTransactionsInput` → `PageResult<TransactionOutput>`) e
  `GetTransactionUseCase` com `@Transactional(readOnly = true)`; `@Transactional` no `PersistTransactionUseCase`
- `infrastructure/http`: `ListTransactionsRequest` (record ligado aos query params, `@Pattern` no `sort`,
  `toInput()` com padrões e limites), `TransactionPageResponse`; `ApiExceptionHandler` com o 404 e com
  `{parameter}` (em vez de `pointer`) para erros de validação/conversão fora do corpo
- `infrastructure/persistence`: adaptador traduz para `org.springframework.data.domain.PageRequest`/`Sort`/`Page`;
  `TransactionEntityRepository` passa a `ListPagingAndSortingRepository` (ou equivalente) com
  `Page<TransactionEntity> findAllByCategory(Category, Pageable)`; índice `(category, created_at, id)` via
  `@Table(indexes = ...)`
- endpoint de voz: só o prefixo (`POST /api/v1/transactions/ai`) e `produces = "audio/mpeg"`; comportamento igual
- OpenAPI gerada pelos testes: Spring REST Docs + `restdocs-api-spec` (`com.epages`, 0.20.x) nos `*IT` existentes
  (manuais, voz e erros); `docs/openapi.yaml` versionado; `updateOpenApi` (copia o gerado) e `verifyOpenApi`
  (falha se diverge; ligado ao `check`)
- Swagger UI no `compose.yml` (`swaggerapi/swagger-ui:v5.34.0`, porta 8081, `SWAGGER_JSON` lendo
  `docs/openapi.yaml`, `SUPPORTED_SUBMIT_METHODS: "[]"` — só leitura)
- exemplos: `docs/exemplos/transacoes.http` (HTTP Client do IntelliJ / REST Client do VS Code) e seção de API no
  README com `curl` mínimo
- documentação: `api-conventions.md`, `architecture.md`, `testing.md`, `error-handling.md`, `CLAUDE.md`, README;
  notas da T08, T12 e T14 em `docs/tarefas.md`

**Fora:**
- filtro por período (melhoria futura)
- paginação por cursor; `sort` livre ou por outros campos
- agregação (`COUNT`/`SUM`) para a consulta por voz — nota na T14; a tool `list-transactions-by-category` e o
  `ListTransactionsByCategoryUseCase` ficam intocados
- remodelar o fluxo de voz como recurso, áudios e exemplos de voz (T12)
- CORS e "Try it out" do Swagger UI; springdoc
- ADR; deploy; migração de dados (o índice entra por `ddl-auto=update`)

## Decisões

- Versão por `@RequestMapping("/api/v1/transactions")` no controller — um controller só; versionamento nativo do
  Spring 7 ou prefixo global seriam complexidade sem uso (decisão do usuário)
- Endpoint de voz já sob `/api/v1` e com `audio/mpeg` — API inteira versionada; `audio/mp3` não é tipo registrado
  (decisão do usuário)
- Só `category` como filtro; período fica para depois (decisão do usuário)
- Paginação por offset com corpo `content` + `page` (formato do `PagedModel` do Spring Data) — exceção à regra
  "sem envelope" só para listagens paginadas (decisão do usuário)
- Tamanho padrão 20, máximo 100, valores fora da faixa ajustados em silêncio, como o Spring Data faz (decisão do
  usuário)
- `sort` restrito a `createdAt`/`amount` com `asc`/`desc`, desempate por `id` — ordenar por valor é útil e a lista
  impede vazar nomes da entidade no contrato (decisão do usuário)
- Tipos de paginação próprios no `domain`, adaptador traduz para `Pageable`/`Page` — a assinatura da porta também
  importa tipos; `Pageable` no `domain` quebraria "domain sem Spring" e admitiria `unpaged()` e `sort` arbitrário;
  mesmo princípio do repositório de referência do curso (`dio-spring-boot-learning-track`, "Repository Pattern")
  (decisão do usuário)
- Nomes `PageQuery`/`PageResult` — evitam colisão com `PageRequest`/`Page` do Spring no adaptador
- `ListTransactionsRequest` (record ligado aos query params, `@Valid`) em vez de `Pageable` resolvido pelo Spring —
  mesmo padrão do `TransactionRequest` (`toInput()`), lista do `sort` declarativa e erro pelo handler existente
- Consulta por voz não usa a listagem paginada: o caso certo é uma agregação no banco, que é outro use case; fica
  para a T14 (nota), lida como forma melhor de responder à consulta existente, não consulta nova (decisão do
  usuário)
- 404 com `type` próprio `transaction-not-found` — resultado de negócio com semântica própria; a T08 reaproveita
  para transação de outro usuário (decisão do usuário)
- ID inválido = recurso inexistente: mesmo 404 `transaction-not-found` para UUID malformado e inexistente, via
  `TransactionId.parse` → `Optional` — um significado, um corpo (decisão do usuário)
- `TransactionNotFoundException` em `domain/` — mesmo lugar das exceções do repositório de referência do curso
- OpenAPI gerada pelos testes (REST Docs + `restdocs-api-spec`) — produção sem anotação nem dependência de
  documentação; o teste falha se o código diverge; mesma técnica do módulo `01-spring-web` do curso; springdoc
  acoplaria (anotações nos controllers/DTOs) (decisão do usuário)
- Endpoint de voz também documentado agora — a regra exige todo endpoint na especificação (decisão do usuário)
- `docs/openapi.yaml` versionado e conferido pelo build — o contrato entra na revisão de cada PR (decisão do usuário)
- Swagger UI no compose, só leitura — navegável sem tocar no backend; "Try it out" exigiria CORS (decisão do
  usuário)
- Exemplos em arquivo `.http` (decisão do usuário)
- `@Transactional` também no `PersistTransactionUseCase` — a regra vale para todos os use cases; a transação cobre
  só o `save`, a chamada à IA fica fora (decisão do usuário)
- Sem ADR — nenhuma das decisões justifica (decisão do usuário)
- `document(...)` no `*IT` que já cobre cada caso, sem classe só de documentação — nenhum comportamento testado duas
  vezes
- Cinco fases: versão e base da documentação; consulta por ID; listagem paginada; voz, Swagger UI e exemplos; regras
  e notas

## Definição de pronto

- [ ] `./gradlew build` passa (Spotless, Checkstyle, `*Test`, `*IT`, JaCoCo e `verifyOpenApi`), sem `OPENAI_API_KEY`
- [ ] Todos os endpoints sob `/api/v1`; nenhuma rota antiga responde (404)
- [ ] `POST` devolve 201 com `Location` que, seguido, devolve a mesma transação
- [ ] `GET /{id}` devolve 200; UUID inexistente e ID malformado devolvem o mesmo 404 `transaction-not-found`
- [ ] Listagem paginada com `content` + `page`, filtro `category`, ordenação padrão e por `sort`, limites de `size`
      e 400 `{parameter}` para parâmetros inválidos
- [ ] `grep -rn "org.springframework" src/main/java/dio/budgeting/domain` → nenhuma linha
- [ ] `docs/openapi.yaml` descreve os quatro endpoints com respostas de sucesso e erro, descrições em pt-BR
- [ ] `docker compose up swagger-ui` serve a especificação em `http://localhost:8081`, sem "Try it out"
- [ ] `docs/exemplos/transacoes.http`, README, regras, `CLAUDE.md` e notas da T08/T12/T14 atualizados; checkbox da
      T07 marcado em `docs/tarefas.md` no último commit

## Para futuros agentes

Execute com `/executar-tarefa`. Durante a execução:

- marque `- [x]` no mesmo commit que conclui o item;
- antes do último commit de uma fase, rode o plano de verificação, escreva o resumo da fase com o resultado e
  mude o status para `Concluída` — tudo nesse mesmo commit;
- na última fase, no mesmo commit, confira a definição de pronto e escreva o resumo final (e o plano de deploy,
  se houver);
- mudança no plano só com aprovação do usuário, registrada em Decisões com o motivo.

Contexto para uma sessão nova:

- Branch `feat/endpoints-rest`; título do PR `feat: adequa os endpoints manuais ao padrão rest`. Commits: `feat:`,
  `test:`, `refactor:`, `build:`, `docs:`.
- Estado antes da tarefa: `TransactionController` em `/transactions` com `POST` (201 sem `Location`),
  `GET /{category}` (lista sem ordem nem limite, via `ListTransactionsByCategoryUseCase`) e `POST /ai`
  (`produces = "audio/mp3"`). Porta `TransactionRepository` com `save` e `findAllByCategory`;
  `TransactionEntityRepository extends CrudRepository`. Nenhum use case tem `@Transactional`. Sem OpenAPI.
- URLs antigas nos testes: `TransactionEndpointsIT`, `ErrorResponseIT` (inclusive `instance` esperado e os testes
  de 500 que mockam `findAllByCategory` — passam a mockar o método novo da porta e usar a listagem nova; o teste de
  `category` inexistente no caminho vira query param), `VoiceCommandIT`, `UploadLimitIT`, `RequestCorrelationIT`.
- **Risco principal — Fase 1, primeiro item:** `restdocs-api-spec` 0.20.x avisa nas release notes de possíveis
  incompatibilidades com o Spring Boot 4. Plugin Gradle `com.epages.restdocs-api-spec` e
  `testImplementation 'com.epages:restdocs-api-spec-mockmvc'`; nos testes, `MockMvcRestDocumentationWrapper.document`
  com `ResourceSnippetParameters`. No Boot 4 o suporte de teste do REST Docs (`@AutoConfigureRestDocs`) fica em
  módulo próprio — conferir artefato e pacote. Os snippets são gerados pelo `integrationTest` (os `*IT`), então a
  tarefa `openapi3` depende dele. Se a integração não funcionar, **parar e consultar o usuário** (alternativa
  discutida: `docs/openapi.yaml` escrito à mão e validado nos `*IT`).
- `restdocs-api-spec` traz Jackson 2 no classpath de teste; o projeto usa Jackson 3 (`tools.jackson.*`) — pacotes
  diferentes, devem coexistir; conferir.
- Descrições na documentação (`description(...)`, resumo, tags) em pt-BR: são conteúdo para o usuário; o código do
  teste segue em inglês.
- `verifyOpenApi` roda depois do `openapi3` e compara com `docs/openapi.yaml`; `updateOpenApi` regrava o arquivo
  (modelo `spotlessApply`/`spotlessCheck`). Saída determinística (ordem estável) para o diff não oscilar.
- Record de query params com `@Valid` (sem `@RequestBody`): falha de validação e de conversão (`category=FOO`,
  `size=abc`) chegam como `MethodArgumentNotValidException`/`BindException` com `FieldError`; conversão tem
  `isBindingFailure()` → mensagem `problem.invalid-value`. O handler hoje transforma todo `FieldError` em
  `pointer` (`#/campo`); distinguir corpo (`ex.getParameter()` com `@RequestBody`) de query → `{parameter}`.
  O log `Request rejected` usa o nome do parâmetro em `invalidFields`.
- `Location`: `ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")` +
  `ResponseEntity.created(uri)`; no MockMvc o host é `http://localhost`.
- Desempate por `id` no `Sort` do adaptador: `Sort.by(direction, campo).and(Sort.by(direction, "id"))`. Para testar
  o desempate, gravar entidades com o mesmo `createdAt` direto pelo `TransactionEntityRepository`.
- `@Transactional` nos use cases: `org.springframework.transaction.annotation.Transactional` é permitido em
  `application` pela regra de arquitetura (a camada já usa `@Service`).
- `TransactionNotFoundException`: `ProblemType.TRANSACTION_NOT_FOUND`, textos `problem.transaction-not-found.*` em
  `messages.properties`, log `INFO` `Request rejected` (status 404, `problemType`); a mensagem da exceção não vai ao
  corpo nem ao log.
- Swagger UI no compose: o `spring-boot-docker-compose` sobe todos os serviços do `compose.yml` no `bootRun`; o
  serviço não tem healthcheck nem é reconhecido pelo Boot — conferir que o `bootRun` não espera por ele.
- Checkstyle limita métodos a 50 linhas, inclusive em testes.
- Regras: `.claude/rules/architecture.md`, `api-conventions.md`, `error-handling.md`, `testing.md`.

## Fase 1: Versão no caminho e base da documentação OpenAPI

Status: Não iniciada  <!-- Não iniciada | Em andamento | Concluída -->

- [ ] [verificação] Build: Spring REST Docs + plugin `com.epages.restdocs-api-spec` + `restdocs-api-spec-mockmvc`;
      tarefa `openapi3` (YAML, título "Budgeting API", versão `v1`, saída em `build/api-spec/`) alimentada pelos
      snippets do `integrationTest` — prova de compatibilidade com o Boot 4 documentando o `POST` existente no
      `TransactionEndpointsIT`; se falhar, parar e consultar o usuário
- [ ] [TDD] `TransactionEndpointsIT`, `ErrorResponseIT`, `VoiceCommandIT`, `UploadLimitIT`, `RequestCorrelationIT`
      com as URLs `/api/v1/...` (inclusive `instance`) — implementa `@RequestMapping("/api/v1/transactions")`
- [ ] [TDD] `VoiceCommandIT`: resposta do comando de voz com `Content-Type` `audio/mpeg` — implementa o `produces`
- [ ] [TDD] `TransactionEndpointsIT`: `POST` → 201 com `Location` terminando em `/api/v1/transactions/{id}` (o `id`
      do corpo), documentado com REST Docs (campos da requisição e da resposta, cabeçalho `Location`, descrições
      em pt-BR) — implementa `ResponseEntity.created(...)`
- [ ] [verificação] Tarefas `updateOpenApi` (copia `build/api-spec/openapi3.yaml` para `docs/openapi.yaml`) e
      `verifyOpenApi` (falha se diverge; `check` depende dela); `docs/openapi.yaml` inicial gerado

### Plano de verificação

- `./gradlew build` → `BUILD SUCCESSFUL`, sem `OPENAI_API_KEY`
- `grep -rn '"/transactions' src/test/java` → nenhuma linha (todas com `/api/v1`)
- `grep -n "/api/v1/transactions" docs/openapi.yaml` → o caminho do `POST`, com a resposta 201 e o `Location`
- alterar à mão uma descrição em `docs/openapi.yaml` e rodar `./gradlew verifyOpenApi` → falha; desfazer
  (`git checkout docs/openapi.yaml`) → passa

### Resumo da fase

_(preencher ao concluir a fase)_

## Fase 2: Consulta por ID

Status: Não iniciada  <!-- Não iniciada | Em andamento | Concluída -->

- [ ] [TDD] `TransactionIdTest` (novo): `parse` devolve o ID para UUID válido e vazio para texto malformado, vazio
      ou nulo
- [ ] [TDD] `GetTransactionUseCaseTest` (novo): transação encontrada → `TransactionOutput`; ID não encontrado ou
      malformado → `TransactionNotFoundException` — implementa `GetTransactionUseCase`
      (`@Transactional(readOnly = true)`), `TransactionNotFoundException` e `findById` na porta
- [ ] [TDD] `TransactionEndpointsIT`: `GET /api/v1/transactions/{id}` de uma transação criada → 200 com o recurso,
      documentado — implementa o endpoint e o `findById` no adaptador JPA
- [ ] [TDD] `TransactionEndpointsIT` (parametrizado): UUID inexistente e ID malformado (`abc`) → 404
      `application/problem+json`, `type` `urn:problem-type:budgeting:transaction-not-found`, `title` e `detail` em
      pt-BR, documentado — implementa `ProblemType.TRANSACTION_NOT_FOUND`, `problem.transaction-not-found.*` e o
      handler
- [ ] [TDD] `ErrorResponseIT`: o 404 gera `INFO` `Request rejected` com `status` 404 e o `problemType`, com o
      `traceId` da requisição
- [ ] [verificação] `@Transactional` no `PersistTransactionUseCase` (sem comportamento observável a testar)

### Plano de verificação

- `./gradlew build` → `BUILD SUCCESSFUL`, sem `OPENAI_API_KEY`
- `./gradlew test --tests "dio.budgeting.application.GetTransactionUseCaseTest"` → passa
- `grep -n "transaction-not-found" docs/openapi.yaml src/main/resources/messages.properties` → linhas nos dois
- `grep -rn "@Transactional" src/main/java/dio/budgeting/application` → `PersistTransactionUseCase` e
  `GetTransactionUseCase`

### Resumo da fase

_(preencher ao concluir a fase)_

## Fase 3: Listagem paginada e ordenada

Status: Não iniciada  <!-- Não iniciada | Em andamento | Concluída -->

- [ ] [TDD] `PageResultTest` (novo, `domain`): `totalPages` (0 itens → 0; 41 itens com `size` 20 → 3) e `map`
      preserva os metadados; `PageQuery` rejeita `number` < 0 e `size` < 1
- [ ] [TDD] `ListTransactionsUseCaseTest` (novo): repassa filtro, ordenação e página à porta e devolve
      `PageResult<TransactionOutput>` — implementa `ListTransactionsUseCase` (`@Transactional(readOnly = true)`),
      `ListTransactionsInput`, `TransactionFilter`, `TransactionSort` e `findAll` na porta
- [ ] [TDD] `TransactionEndpointsIT`: `GET /api/v1/transactions` sem parâmetros → `content` em `createdAt`
      decrescente e `page` `{size: 20, number: 0, totalElements, totalPages}`, documentado (query params e campos
      da página) — implementa `ListTransactionsRequest`, `TransactionPageResponse`, o adaptador JPA (`Pageable`,
      `Sort` com desempate) e o índice; remove o `GET /{category}`
- [ ] [TDD] `TransactionEndpointsIT`: `?category=GROCERIES` traz só as da categoria; `?page=1&size=2` traz a
      segunda página com os metadados certos
- [ ] [TDD] `TransactionEndpointsIT` (parametrizado): `sort=amount,asc`, `amount,desc`, `createdAt,asc` ordenam
      como pedido; mesmo `createdAt` desempata por `id`
- [ ] [TDD] `TransactionEndpointsIT` (parametrizado): `size=500` → `page.size` 100; `size=0` → 20; `page=-1` → 0
- [ ] [TDD] `ErrorResponseIT` (parametrizado): `category=FOO`, `sort=description,asc`, `sort=amount`,
      `size=abc` → 400 `validation-error` com `errors[0].parameter` (`category`, `sort`, `size`) e sem `pointer`,
      documentado; log `Request rejected` com `invalidFields` pelo nome do parâmetro — implementa a distinção corpo
      x query no `ApiExceptionHandler` e `transaction.sort.invalid` em `messages.properties`
- [ ] [TDD] `ErrorResponseIT`: os testes de 500 passam a usar a listagem nova (mock do `findAll` da porta)

### Plano de verificação

- `./gradlew build` → `BUILD SUCCESSFUL`, sem `OPENAI_API_KEY`
- `./gradlew integrationTest --tests "dio.budgeting.infrastructure.http.TransactionEndpointsIT"` → passa
- `grep -rn "org.springframework" src/main/java/dio/budgeting/domain` → nenhuma linha
- `grep -n "name: sort\|name: page\|name: size\|name: category" docs/openapi.yaml` → os quatro parâmetros
- `grep -n "/transactions/{category}" docs/openapi.yaml` → nenhuma linha

### Resumo da fase

_(preencher ao concluir a fase)_

## Fase 4: Voz na especificação, Swagger UI e exemplos

Status: Não iniciada  <!-- Não iniciada | Em andamento | Concluída -->

- [ ] [verificação] `VoiceCommandIT`/`UploadLimitIT`/`ErrorResponseIT`: documentar o `POST /api/v1/transactions/ai`
      (200 `audio/mpeg`, 400 sem `file`, 413, 422 `invalid-command`, 503 `assistant-unavailable` com
      `Retry-After`); se o `restdocs-api-spec` não representar multipart ou corpo binário, registrar a limitação em
      Decisões e consultar o usuário
- [ ] [verificação] `compose.yml`: serviço `swagger-ui` (`swaggerapi/swagger-ui:v5.34.0`, `8081:8080`,
      `SWAGGER_JSON=/spec/openapi.yaml`, volume `./docs/openapi.yaml:/spec/openapi.yaml:ro`,
      `SUPPORTED_SUBMIT_METHODS: "[]"`)
- [ ] [verificação] `docs/exemplos/transacoes.http`: `@baseUrl`; criar (guardando o `id`), consultar por ID,
      listar sem filtro, com `category`, com `page`/`size`, com `sort=amount,desc`; erros 400 de validação, 404 de
      ID inexistente e 400 de `sort` inválido
- [ ] [verificação] README: seção "API" — base `/api/v1`, tabela dos endpoints, especificação em
      `docs/openapi.yaml`, Swagger UI (`docker compose up swagger-ui` → `http://localhost:8081`), exemplos em
      `docs/exemplos/transacoes.http` e um `curl` mínimo

### Plano de verificação

- `./gradlew build` → `BUILD SUCCESSFUL`, sem `OPENAI_API_KEY`
- `grep -n "/api/v1/transactions/ai" docs/openapi.yaml` → o caminho de voz
- `docker compose config --services` → `database` e `swagger-ui`
- `docker compose up -d swagger-ui && curl -s localhost:8081/ | grep -i swagger` → página do Swagger UI;
  `docker compose stop swagger-ui`
- `grep -c "^###" docs/exemplos/transacoes.http` → ao menos 9 requisições

### Resumo da fase

_(preencher ao concluir a fase)_

## Fase 5: Regras e notas

Status: Não iniciada  <!-- Não iniciada | Em andamento | Concluída -->

- [ ] [verificação] `.claude/rules/api-conventions.md`: listagem paginada com `content` + `page` (exceção ao "sem
      envelope"); `page`/`size` (padrão 20, máximo 100, ajuste em silêncio); `sort` restrito por lista com
      desempate por `id`; ID malformado = 404 do recurso; OpenAPI gerada pelos `*IT` com REST Docs, descrições em
      pt-BR, `docs/openapi.yaml` atualizado por `updateOpenApi`
- [ ] [verificação] `.claude/rules/architecture.md`: paginação com tipos próprios do `domain` (`PageQuery`,
      `PageResult`), adaptador traduz para o Spring Data; exceções de negócio em `domain/`
- [ ] [verificação] `.claude/rules/testing.md`: `document(...)` no `*IT` que cobre o caso; endpoint ou resposta
      nova só entra na especificação se documentada; `verifyOpenApi` no `build`
- [ ] [verificação] `.claude/rules/error-handling.md`: linha `TransactionNotFoundException` → 404
      `transaction-not-found`; `{parameter}` para erros de query param via `MethodArgumentNotValidException`
- [ ] [verificação] `CLAUDE.md`: comandos `./gradlew updateOpenApi` e Swagger UI; Estrutura com `docs/openapi.yaml`
      e `docs/exemplos/`
- [ ] [verificação] `docs/tarefas.md`: nota na T14 (trocar a tool `list-transactions-by-category` por agregação
      `COUNT`/`SUM` no banco, como forma melhor da consulta existente — decisão do usuário), na T12 (atualizar a
      especificação e os exemplos do fluxo de voz) e na T08 (404 `transaction-not-found` para transação de outro
      usuário)
- [ ] [verificação] Marcar a T07 em `docs/tarefas.md` (último commit)

### Plano de verificação

- `./gradlew build` → `BUILD SUCCESSFUL`
- `grep -n "content\|updateOpenApi" .claude/rules/api-conventions.md` → ao menos uma linha cada
- `grep -n "PageQuery" .claude/rules/architecture.md` → ao menos uma linha
- `grep -n "transaction-not-found" .claude/rules/error-handling.md` → uma linha na tabela
- `grep -n "Nota (T07)" docs/tarefas.md` → notas na T08, T12 e T14

### Resumo da fase

_(preencher ao concluir a fase)_

## Resumo final

_(preencher ao concluir todas as fases)_
