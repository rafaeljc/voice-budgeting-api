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
- OpenAPI escrita à mão em `docs/openapi.yaml` (contract-first, 3.0.3, descrições em pt-BR), versionada e
  revisada a cada PR; o contrato descreve os quatro endpoints com respostas de sucesso e de erro. Spring REST Docs
  e springdoc (adotados e removidos durante a tarefa) não fazem parte da entrega
- Swagger UI no `compose.yml` (`swaggerapi/swagger-ui:v5.34.0`, porta 8081, `SWAGGER_JSON` lendo
  `docs/openapi.yaml`, `SUPPORTED_SUBMIT_METHODS: "[]"` — só leitura)
- exemplos: `docs/exemplos/transacoes.http` (HTTP Client do IntelliJ / REST Client do VS Code) e seção de API no
  README com `curl` mínimo
- documentação: `api-conventions.md`, `architecture.md`, `error-handling.md`, `CLAUDE.md`, README;
  notas da T08, T12 e T14 em `docs/tarefas.md`

**Fora:**
- filtro por período (melhoria futura)
- paginação por cursor; `sort` livre ou por outros campos
- agregação (`COUNT`/`SUM`) para a consulta por voz — nota na T14; a tool `list-transactions-by-category` e o
  `ListTransactionsByCategoryUseCase` ficam intocados
- remodelar o fluxo de voz como recurso, áudios e exemplos de voz (T12)
- CORS e "Try it out" do Swagger UI; geração da especificação a partir do código ou dos testes
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
- Listagem antecipada para a Fase 2 — `GET /{id}` e o `GET /{category}` antigo têm a mesma forma de caminho
  (ambiguidade de mapeamento: a aplicação não sobe); os três primeiros itens da Fase 3 (tipos de paginação, use
  case e `GET /api/v1/transactions` com o adaptador JPA, que remove o `GET /{category}`) passam a vir antes do `GET`
  por ID, na Fase 2. A Fase 3 mantém o restante (filtro, página, ordenação, limites e erros) (decisão do usuário)
- `TransactionFilter` é uma lista de critérios (`sealed interface TransactionCriterion`, hoje só `CategoryIs`) —
  critério novo é um `record` no domínio e um `case` no `switch` do adaptador, exaustivo pelo compilador; nenhum
  chamador muda (decisão do usuário; descartados `withX` e `@Builder`, que crescem um campo por critério)
- Filtros por `Specification` (`JpaSpecificationExecutor`) em vez de `findAllByCategory(Category, Pageable)` — o
  filtro por período é a evolução esperada, e um método derivado por combinação não escala; cada critério é uma
  especificação nomeada em `TransactionSpecifications`, composta com `Specification.allOf` (decisão do usuário)
- No item do use case o adaptador JPA só satisfaz a porta (`UnsupportedOperationException`); a implementação real
  vem no item do IT da listagem, depois do teste que falha
- Dois índices: `(category, created_at, id)` para a listagem filtrada e `(created_at, id)` para a listagem sem
  filtro, a padrão — o primeiro não serve sem `category` (coluna inicial ausente do `WHERE`); ordenar por `amount`
  fica sem índice até haver volume que o justifique (decisão do usuário)
- (Substituída por "Contract-first", abaixo) OpenAPI gerada pelo springdoc, Swagger UI embutido na aplicação — o `restdocs-api-spec` não representa o
  multipart do upload nem o áudio binário da voz (sem `requestBody`, schema `object` no `audio/mpeg`, exemplo com
  bytes crus) e o 413 roda em servidor real, fora do MockMvc; para o escopo do projeto a geração automática a
  partir do código é aceitável; sem anotações de OpenAPI no controller, para não acoplá-lo ao contrato (decisão do
  usuário). Substitui as decisões "OpenAPI gerada pelos testes", "`docs/openapi.yaml`
  versionado e conferido pelo build", "Swagger UI no compose, só leitura" e "`document(...)` no `*IT`"
  (decisão do usuário)
- Contract-first: `docs/openapi.yaml` escrito à mão, Swagger UI no compose apenas para consulta — o
  `restdocs-api-spec` não representa o multipart nem o áudio binário, e o springdoc sem anotações quebra o
  multipart quando achata os parâmetros da listagem (e exige anotação para não perder `201`/`Location` e os erros);
  as duas geram um contrato impreciso. Substitui as decisões "OpenAPI gerada pelos testes", "OpenAPI gerada pelo
  springdoc" e "`document(...)` no `*IT`" (decisão do usuário)
- Sem teste para a documentação — a especificação é só uma conveniência de consulta e não um comportamento da
  aplicação; `testing.md` não pede teste de configuração nem de comportamento de framework (decisão do usuário)
- Cinco fases: versão e base da documentação; consulta por ID; listagem paginada; voz, Swagger UI e exemplos; regras
  e notas

## Definição de pronto

- [ ] `./gradlew build` passa (Spotless, Checkstyle, `*Test`, `*IT`, JaCoCo), sem `OPENAI_API_KEY`
- [ ] Todos os endpoints sob `/api/v1`; nenhuma rota antiga responde (404)
- [ ] `POST` devolve 201 com `Location` que, seguido, devolve a mesma transação
- [ ] `GET /{id}` devolve 200; UUID inexistente e ID malformado devolvem o mesmo 404 `transaction-not-found`
- [ ] Listagem paginada com `content` + `page`, filtro `category`, ordenação padrão e por `sort`, limites de `size`
      e 400 `{parameter}` para parâmetros inválidos
- [ ] `grep -rn "org.springframework" src/main/java/dio/budgeting/domain` → nenhuma linha
- [ ] `docs/openapi.yaml` descreve os quatro endpoints com respostas de sucesso e de erro, descrições em pt-BR
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

Status: Concluída  <!-- Não iniciada | Em andamento | Concluída -->

- [x] [verificação] Build: Spring REST Docs + plugin `com.epages.restdocs-api-spec` + `restdocs-api-spec-mockmvc`;
      tarefa `openapi3` (YAML, título "Budgeting API", versão `v1`, saída em `build/api-spec/`) alimentada pelos
      snippets do `integrationTest` — prova de compatibilidade com o Boot 4 documentando o `POST` existente no
      `TransactionEndpointsIT`; se falhar, parar e consultar o usuário
- [x] [TDD] `TransactionEndpointsIT`, `ErrorResponseIT`, `VoiceCommandIT`, `UploadLimitIT`, `RequestCorrelationIT`
      com as URLs `/api/v1/...` (inclusive `instance`) — implementa `@RequestMapping("/api/v1/transactions")`
- [x] [TDD] `VoiceCommandIT`: resposta do comando de voz com `Content-Type` `audio/mpeg` — implementa o `produces`
- [x] [TDD] `TransactionEndpointsIT`: `POST` → 201 com `Location` terminando em `/api/v1/transactions/{id}` (o `id`
      do corpo), documentado com REST Docs (campos da requisição e da resposta, cabeçalho `Location`, descrições
      em pt-BR) — implementa `ResponseEntity.created(...)`
- [x] [verificação] Tarefas `updateOpenApi` (copia `build/api-spec/openapi3.yaml` para `docs/openapi.yaml`) e
      `verifyOpenApi` (falha se diverge; `check` depende dela); `docs/openapi.yaml` inicial gerado

### Plano de verificação

- `./gradlew build` → `BUILD SUCCESSFUL`, sem `OPENAI_API_KEY`
- `grep -rn '"/transactions' src/test/java` → nenhuma linha (todas com `/api/v1`)
- `grep -n "/api/v1/transactions" docs/openapi.yaml` → o caminho do `POST`, com a resposta 201 e o `Location`
- alterar à mão uma descrição em `docs/openapi.yaml` e rodar `./gradlew verifyOpenApi` → falha; desfazer
  (`git checkout docs/openapi.yaml`) → passa

### Resumo da fase

- **Feito:** REST Docs + `restdocs-api-spec` 0.20.2 funcionam no Boot 4.1.2/Gradle 9.4.1 (artefatos
  `spring-boot-restdocs` e `spring-restdocs-mockmvc`, `@AutoConfigureRestDocs` em
  `org.springframework.boot.restdocs.test.autoconfigure`). Controller em `/api/v1/transactions`, voz com
  `audio/mpeg`, `POST` devolvendo 201 + `Location` absoluto, documentado. `docs/openapi.yaml` gerado (só o `POST`;
  os demais endpoints entram nas fases 2 a 4); `updateOpenApi` e `verifyOpenApi` (ligada ao `check`).
- **Decisões:** testes usam as constantes de `dio.budgeting.support.ApiPaths` (`BASE_PATH`, `TRANSACTIONS_PATH`,
  `VOICE_COMMAND_PATH`) em vez de literais. O `Location` é montado por `locationOf(String)` no controller.
- **Para a próxima sessão:**
  - O plugin adiciona dependências do `openapi3` em `afterEvaluate` (inclusive `check`); o `build.gradle` as
    substitui também em `afterEvaluate` pelo `integrationTest`, senão `check -> verifyOpenApi -> openapi3 -> check`
    é um ciclo.
  - Exemplos da especificação saem da resposta real; para a saída ser determinística, `document(...)` usa
    `preprocessResponse(replacePattern(...), modifyHeaders())` para fixar `id`, `createdAt` e `Location`. Repita
    em todo `document(...)` que devolva esses valores.
  - O `@AutoConfigureRestDocs` fixa o host da requisição em `localhost:8080`; o teste do `Location` compara com
    `result.getRequest().getRequestURL() + "/" + id`.
  - `updateOpenApi` e `verifyOpenApi` não têm ordem entre si; rode `updateOpenApi` sozinha.
  - Snippets antigos em `build/generated-snippets` podem sobreviver a testes removidos; `./gradlew clean` limpa.
- **Verificação:** `./gradlew build` → `BUILD SUCCESSFUL`; `grep '"/transactions' src/test/java` só acha o sufixo
  em `ApiPaths`; `docs/openapi.yaml` traz `/api/v1/transactions`, 201 e `Location`; descrição alterada à mão →
  `verifyOpenApi` falha ("docs/openapi.yaml is out of date"), `updateOpenApi` restaura → passa.

## Fase 2: Consulta por ID

Status: Concluída  <!-- Não iniciada | Em andamento | Concluída -->

- [x] [TDD] `TransactionIdTest` (novo): `parse` devolve o ID para UUID válido e vazio para texto malformado, vazio
      ou nulo
- [x] [TDD] `GetTransactionUseCaseTest` (novo): transação encontrada → `TransactionOutput`; ID não encontrado ou
      malformado → `TransactionNotFoundException` — implementa `GetTransactionUseCase`
      (`@Transactional(readOnly = true)`), `TransactionNotFoundException` e `findById` na porta
- [x] [TDD] `PageResultTest` (novo, `domain`): `totalPages` (0 itens → 0; 41 itens com `size` 20 → 3) e `map`
      preserva os metadados; `PageQuery` rejeita `number` < 0 e `size` < 1
- [x] [TDD] `ListTransactionsUseCaseTest` (novo): repassa filtro, ordenação e página à porta e devolve
      `PageResult<TransactionOutput>` — implementa `ListTransactionsUseCase` (`@Transactional(readOnly = true)`),
      `ListTransactionsInput`, `TransactionFilter`, `TransactionSort` e `findAll` na porta
- [x] [TDD] `TransactionEndpointsIT`: `GET /api/v1/transactions` sem parâmetros → `content` em `createdAt`
      decrescente e `page` `{size: 20, number: 0, totalElements, totalPages}`, documentado (query params e campos
      da página) — implementa `ListTransactionsRequest`, `TransactionPageResponse`, o adaptador JPA (`Pageable`,
      `Sort` com desempate) e o índice; remove o `GET /{category}`
- [x] [TDD] `TransactionEndpointsIT`: `GET /api/v1/transactions/{id}` de uma transação criada → 200 com o recurso,
      documentado — implementa o endpoint e o `findById` no adaptador JPA
- [x] [TDD] `TransactionEndpointsIT` (parametrizado): UUID inexistente e ID malformado (`abc`) → 404
      `application/problem+json`, `type` `urn:problem-type:budgeting:transaction-not-found`, `title` e `detail` em
      pt-BR, documentado — implementa `ProblemType.TRANSACTION_NOT_FOUND`, `problem.transaction-not-found.*` e o
      handler
- [x] [TDD] `ErrorResponseIT`: o 404 gera `INFO` `Request rejected` com `status` 404 e o `problemType`, com o
      `traceId` da requisição
- [x] [verificação] `@Transactional` no `PersistTransactionUseCase` (sem comportamento observável a testar)

### Plano de verificação

- `./gradlew build` → `BUILD SUCCESSFUL`, sem `OPENAI_API_KEY`
- `./gradlew test --tests "dio.budgeting.application.GetTransactionUseCaseTest"` → passa
- `grep -n "transaction-not-found" docs/openapi.yaml src/main/resources/messages.properties` → linhas nos dois
- `grep -rn "@Transactional" src/main/java/dio/budgeting/application` → `PersistTransactionUseCase` e
  `GetTransactionUseCase`

### Resumo da fase

- **Feito:** `GET /api/v1/transactions/{id}` (200, e 404 `transaction-not-found` para UUID inexistente ou ID
  malformado) com `TransactionId.parse`, `GetTransactionUseCase`, `TransactionNotFoundException` e `findById` na
  porta. A listagem paginada foi antecipada para esta fase (ver Decisões): `GET /api/v1/transactions` com
  `content` + `page`, filtro `category`, ordenação padrão `createdAt,desc` com desempate por `id`, e o
  `GET /{category}` antigo removido. `@Transactional` nos use cases. `docs/openapi.yaml` com os caminhos
  documentados até aqui (`POST`, `GET` da lista, `GET` por ID e o 404).
- **Decisões:** `TransactionFilter` é uma lista de critérios (`sealed interface TransactionCriterion`, hoje
  `CategoryIs`); adaptador JPA com `Specification` (`TransactionSpecifications`); dois índices
  `(category, created_at, id)` e `(created_at, id)`; `ParameterNumber` do Checkstyle suprimido para
  `*Controller.java` (construtores de controller crescem com a API).
- **Para a próxima sessão:**
  - A Fase 3 já tem pronto: tipos de paginação, `ListTransactionsUseCase`, `ListTransactionsRequest` (padrões e
    limites de `page`/`size`, `@Pattern` do `sort`), `TransactionPageResponse` e a distinção corpo × query no
    `ApiExceptionHandler` (`parameter` em vez de `pointer`). Falta cobrir com `*IT` o que o plano lista (filtro,
    página 2, ordenações, desempate, limites, erros 400 parametrizados com log).
  - `document(...)` mascara `id`, `createdAt`, `Location` e `instance` com `replacePattern`/`modifyHeaders` para a
    especificação ser determinística; reaproveite `UUID_PATTERN`, `TIMESTAMP_PATTERN` e `INSTANCE_PATTERN` do
    `TransactionEndpointsIT`.
  - `ListTransactionsByCategoryUseCase` e o `findAllByCategory` da porta continuam em uso pela tool de IA.
  - O `getTransaction` recebe o ID como `String` de propósito (ID inválido = 404, não 400).
- **Verificação:** `./gradlew build` → `BUILD SUCCESSFUL` (sem `OPENAI_API_KEY`); `GetTransactionUseCaseTest`
  passa; `transaction-not-found` aparece em `docs/openapi.yaml` e em `messages.properties`; `@Transactional` em
  `PersistTransactionUseCase`, `GetTransactionUseCase` e `ListTransactionsUseCase`.

## Fase 3: Listagem paginada e ordenada

Status: Concluída  <!-- Não iniciada | Em andamento | Concluída -->

- [x] [TDD] `TransactionEndpointsIT`: `?category=GROCERIES` traz só as da categoria; `?page=1&size=2` traz a
      segunda página com os metadados certos
- [x] [TDD] `TransactionEndpointsIT` (parametrizado): `sort=amount,asc`, `amount,desc`, `createdAt,asc` ordenam
      como pedido; mesmo `createdAt` desempata por `id`
- [x] [TDD] `TransactionEndpointsIT` (parametrizado): `size=500` → `page.size` 100; `size=0` → 20; `page=-1` → 0
- [x] [TDD] `ErrorResponseIT` (parametrizado): `category=FOO`, `sort=description,asc`, `sort=amount`,
      `size=abc` → 400 `validation-error` com `errors[0].parameter` (`category`, `sort`, `size`) e sem `pointer`,
      documentado; log `Request rejected` com `invalidFields` pelo nome do parâmetro — implementa a distinção corpo
      x query no `ApiExceptionHandler` e `transaction.sort.invalid` em `messages.properties`
- [x] [TDD] `ErrorResponseIT`: os testes de 500 passam a usar a listagem nova (mock do `findAll` da porta)

### Plano de verificação

- `./gradlew build` → `BUILD SUCCESSFUL`, sem `OPENAI_API_KEY`
- `./gradlew integrationTest --tests "dio.budgeting.infrastructure.http.TransactionEndpointsIT"` → passa
- `grep -rn "org.springframework" src/main/java/dio/budgeting/domain` → nenhuma linha
- `grep -n "name: sort\|name: page\|name: size\|name: category" docs/openapi.yaml` → os quatro parâmetros
- `grep -n "/transactions/{category}" docs/openapi.yaml` → nenhuma linha

### Resumo da fase

- **Feito:** a implementação da listagem veio na Fase 2 (antecipada); esta fase a cobriu com `*IT`: filtro
  `category`, página 2 com metadados, ordenação (`amount,asc|desc`, `createdAt,asc`), desempate por `id`, ajuste
  silencioso de `size`/`page` fora da faixa e os 400 `validation-error` com `errors[].parameter` (sem `pointer`)
  para `category=FOO`, `sort=description,asc`, `sort=amount` e `size=abc`, com log `Request rejected` trazendo só o
  nome do parâmetro. O 400 de parâmetro está documentado em `docs/openapi.yaml`.
- **Decisões:** nenhuma nova. O teste de desempate foi validado removendo o `id` do `Sort`: o caso
  `createdAt,desc` falha, então o teste protege o comportamento.
- **Para a próxima sessão:**
  - A Fase 4 documenta o `POST /api/v1/transactions/ai` (voz) nos `VoiceCommandIT`/`UploadLimitIT`/
    `ErrorResponseIT`, adiciona o Swagger UI ao `compose.yml`, os exemplos `.http` e a seção de API do README.
  - `@AutoConfigureRestDocs` já está no `TransactionEndpointsIT` e no `ErrorResponseIT`; os demais `*IT` precisam
    dele para chamar `document(...)`.
  - Há três `document(...)` do `ErrorResponseIT` e do `TransactionEndpointsIT` com o mesmo identificador por
    parâmetro de `@ParameterizedTest`; o último caso executado vence no exemplo da especificação.
- **Verificação:** `./gradlew build` → `BUILD SUCCESSFUL` (sem `OPENAI_API_KEY`); `TransactionEndpointsIT` passa;
  `grep -rn "org.springframework" src/main/java/dio/budgeting/domain` → nenhuma linha; `docs/openapi.yaml` traz os
  quatro parâmetros (`category`, `page`, `size`, `sort`) e nenhum `/transactions/{category}`.

## Fase 4: Voz na especificação, Swagger UI e exemplos

Status: Concluída  <!-- Não iniciada | Em andamento | Concluída -->

- [x] [verificação] Remover o REST Docs e o `docs/openapi.yaml` gerado (`f9f7d10`)
- [x] [verificação] Springdoc adicionado (`3e72ecd`) e descartado: ver Decisões
- [x] [verificação] Remover o springdoc: dependência, `OpenApiConfiguration` e propriedades `springdoc.*`
- [x] [verificação] `docs/openapi.yaml` à mão (contract-first, 3.0.3): `POST /api/v1/transactions` (201 com
      `Location`; 400, 415), `GET /api/v1/transactions` (parâmetros `category`, `page`, `size`, `sort`; 400),
      `GET /api/v1/transactions/{id}` (200; 404) e `POST /api/v1/transactions/ai` (`multipart/form-data` com
      `file` binário; 200 `audio/mpeg`; 400, 413, 422 `invalid-command`, 503 `assistant-unavailable` com
      `Retry-After`); 500 genérico em todos; schemas reutilizáveis em `components` (incluindo o Problem Details
      e a lista `errors` com `pointer`/`parameter`); descrições em pt-BR; validar a sintaxe do contrato
- [x] [verificação] `compose.yml`: serviço `swagger-ui` (`swaggerapi/swagger-ui:v5.34.0`, `8081:8080`,
      `SWAGGER_JSON=/spec/openapi.yaml`, volume `./docs/openapi.yaml:/spec/openapi.yaml:ro`,
      `SUPPORTED_SUBMIT_METHODS: "[]"`)
- [x] [verificação] `docs/exemplos/transacoes.http`: `@baseUrl`; criar (guardando o `id`), consultar por ID,
      listar sem filtro, com `category`, com `page`/`size`, com `sort=amount,desc`; erros 400 de validação, 404 de
      ID inexistente e 400 de `sort` inválido
- [x] [verificação] README: seção "API" (versão inicial; ajustada para o contrato no item abaixo) — base `/api/v1`, tabela dos endpoints, especificação em
      `/v3/api-docs`, Swagger UI (`http://localhost:8080/swagger-ui.html`), exemplos em
      `docs/exemplos/transacoes.http` e um `curl` mínimo

- [x] [verificação] README: seção "API" aponta o contrato `docs/openapi.yaml` e o Swagger UI do compose
      (`docker compose up swagger-ui` → `http://localhost:8081`)

### Plano de verificação

- `./gradlew build` → `BUILD SUCCESSFUL`, sem `OPENAI_API_KEY`
- `grep -rn "springdoc" build.gradle src` → nenhuma linha
- `grep -c "^  /api/v1" docs/openapi.yaml` → 3 caminhos (`/transactions`, `/transactions/{id}`, `/transactions/ai`)
- `docker compose config --services` → `database` e `swagger-ui`
- `docker compose up -d swagger-ui && curl -s localhost:8081/ | grep -i swagger` → página do Swagger UI;
  `docker compose stop swagger-ui`
- `grep -c "^###" docs/exemplos/transacoes.http` → ao menos 9 requisições

### Resumo da fase

- **Feito:** o Spring REST Docs (`f9f7d10`) e o springdoc (`15d6973`) foram adotados e removidos: o primeiro não
  representa o multipart nem o áudio binário; o segundo, sem anotações, quebra o multipart ao achatar os
  parâmetros da listagem e perde o `201`/`Location` e os erros. O contrato ficou em `docs/openapi.yaml`
  (contract-first, 3.0.3), com os quatro endpoints, respostas de sucesso e de erro (Problem Details, `errors` com
  `pointer`/`parameter`, `Retry-After`) e descrições em pt-BR. Swagger UI no `compose.yml` (porta 8081, somente
  leitura). `docs/exemplos/transacoes.http` com 9 requisições e a seção "API" do README.
- **Decisões:** contract-first (ver Decisões); sem teste para a documentação.
- **Para a próxima sessão:**
  - O contrato não é gerado nem conferido pelo build: ele acompanha o código só por revisão de PR. Quem muda um
    endpoint, um status ou um erro atualiza `docs/openapi.yaml` no mesmo PR.
  - O Redocly (`docker run --rm -v "$PWD/docs:/spec:ro" redocly/cli lint /spec/openapi.yaml`) valida a estrutura;
    restam `security-defined` (a autenticação chega na T08), licença e servidor `localhost`.
  - O `bootRun` sobe o `swagger-ui` junto com o MySQL (o serviço não tem healthcheck e o Boot não espera por ele).
  - `docker compose` exige o `.env` por causa do `MYSQL_*` do serviço `database`, mesmo para `up swagger-ui`.
- **Verificação:** `./gradlew build` → `BUILD SUCCESSFUL`; `grep -rn springdoc build.gradle src` → nenhuma linha;
  `grep -c "^  /api/v1" docs/openapi.yaml` → 3; `docker compose config --services` → `database` e `swagger-ui`;
  `docker compose up -d swagger-ui` + `curl localhost:8081/` → 200 com a página do Swagger UI; `bootRun` com
  `OPENAI_API_KEY=dummy` sobe com os dois serviços (API 200 em `/api/v1/transactions`, UI 200); 9 exemplos.

## Fase 5: Regras e notas

Status: Em andamento  <!-- Não iniciada | Em andamento | Concluída -->

- [x] [verificação] `.claude/rules/api-conventions.md`: listagem paginada com `content` + `page` (exceção ao "sem
      envelope"); `page`/`size` (padrão 20, máximo 100, ajuste em silêncio); `sort` restrito por lista com
      desempate por `id`; ID malformado = 404 do recurso; contrato OpenAPI escrito à mão em `docs/openapi.yaml`
      (contract-first), atualizado no mesmo PR que muda o endpoint, descrições em pt-BR
- [x] [verificação] `.claude/rules/architecture.md`: paginação com tipos próprios do `domain` (`PageQuery`,
      `PageResult`), adaptador traduz para o Spring Data; exceções de negócio em `domain/`
- [ ] [verificação] `.claude/rules/error-handling.md`: linha `TransactionNotFoundException` → 404
      `transaction-not-found`; `{parameter}` para erros de query param via `MethodArgumentNotValidException`
- [ ] [verificação] `CLAUDE.md`: Swagger UI (`docker compose up swagger-ui`); Estrutura com `docs/openapi.yaml`
      e `docs/exemplos/`
- [ ] [verificação] `docs/tarefas.md`: nota na T14 (trocar a tool `list-transactions-by-category` por agregação
      `COUNT`/`SUM` no banco, como forma melhor da consulta existente — decisão do usuário), na T12 (atualizar a
      especificação e os exemplos do fluxo de voz) e na T08 (404 `transaction-not-found` para transação de outro
      usuário)
- [ ] [verificação] Marcar a T07 em `docs/tarefas.md` (último commit)

### Plano de verificação

- `./gradlew build` → `BUILD SUCCESSFUL`
- `grep -n "content\|openapi" .claude/rules/api-conventions.md` → ao menos uma linha cada
- `grep -n "PageQuery" .claude/rules/architecture.md` → ao menos uma linha
- `grep -n "transaction-not-found" .claude/rules/error-handling.md` → uma linha na tabela
- `grep -n "Nota (T07)" docs/tarefas.md` → notas na T08, T12 e T14

### Resumo da fase

_(preencher ao concluir a fase)_

## Resumo final

_(preencher ao concluir todas as fases)_
