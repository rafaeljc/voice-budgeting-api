# T08 — Identidade mínima e isolamento por usuário

Proteger a API com autenticação por JWT (HS256, validado pelo Spring Security como *resource server*), com tokens
de desenvolvimento gerados por um script (SEG-01), e restringir cada usuário às próprias transações, inclusive no
fluxo de voz (SEG-02).

## Escopo

**Dentro:**
- dependência `spring-boot-starter-oauth2-resource-server`
- segredo `budgeting.security.jwt.secret=${JWT_SECRET}` em `JwtProperties` (`@ConfigurationProperties`
  `budgeting.security.jwt`, `@Validated`, `@NotBlank` e `@Size(min = 32)` — o HS256 exige 256 bits); a aplicação
  não sobe sem ele; `JWT_SECRET` com valor de exemplo no `.env.example`; segredo fixo no `application-test.properties`
- `SecurityConfiguration` (`infrastructure/security/`):
  - bean `JwtDecoder` = `NimbusJwtDecoder.withSecretKey(...)` (HS256) com os validadores padrão mais
    `JwtClaimValidator` de `exp` presente e de `sub` em UUID — o token carrega só `sub` e `exp`; sem `iss`/`aud`
  - `SecurityFilterChain`: `/actuator/health` público, qualquer outra rota autenticada (negar por padrão);
    `oauth2ResourceServer(jwt)`, sessão `STATELESS`, sem CSRF, *form login* e HTTP Basic
  - `AuthenticationEntryPoint` que repassa a `AuthenticationException` ao `HandlerExceptionResolver`, para o
    `ApiExceptionHandler` montar a resposta
- 401 em Problem Details: `urn:problem-type:budgeting:unauthenticated` (`ProblemType.UNAUTHENTICATED`),
  `problem.unauthenticated.title` "Não autenticado" e `.detail` "Envie um token válido no cabeçalho
  Authorization.", cabeçalho `WWW-Authenticate: Bearer`; um só corpo para token ausente, malformado, com assinatura
  errada, expirado, sem `exp` ou com `sub` que não é UUID; log `WARN` `Authentication failed` (`status`,
  `problemType`), nunca o token
- `RequestIdFilter` antes da cadeia do Spring Security (e depois da observação, que cria o *span*), para o 401
  também levar `X-Request-Id`
- `UserId` (`domain`): `record` sobre `UUID`, construtor compacto rejeita `null`, `parse(String)` →
  `Optional<UserId>` (como `TransactionId.parse`)
- `Transaction` ganha `userId` (obrigatório no construtor de criação); `TransactionEntity` ganha `user_id`
  `NOT NULL`; índices passam a `(user_id, category, created_at, id)` e `(user_id, created_at, id)`
- porta `TransactionRepository` com o usuário explícito no nome e na assinatura:
  - `Optional<Transaction> findByIdAndUserId(TransactionId id, UserId userId)`
  - `PageResult<Transaction> findAllByUserId(UserId userId, TransactionFilter filter, TransactionSort sort,
    PageQuery page)`
  - `List<Transaction> findAllByUserIdAndCategory(UserId userId, Category category)`
- use cases recebem o usuário como primeiro parâmetro: `execute(UserId userId, ...)` (`Persist`, `Get`, `List`,
  `ListByCategory`); `request.toInput()` não muda
- `TransactionController`: `@AuthenticationPrincipal Jwt` → `UserId` em cada endpoint; no de voz, o `userId` vai no
  `toolContext` do `ChatClient`
- `TransactionTools`: `ToolContext` como parâmetro das duas tools (o Spring AI o exclui do schema enviado ao
  modelo); gravam e listam para o `UserId` do contexto
- transação de outro usuário → 404 `transaction-not-found` (mesmo corpo do ID inexistente); listagem só com as do
  usuário
- script `scripts/generate_token.py` (só biblioteca padrão, como o `scripts/merge.py`): lê `JWT_SECRET` do
  ambiente, `--user-id <uuid>` (padrão: UUID aleatório) e `--expires-in` (padrão `24h`; aceita `s`, `m`, `h`, `d`),
  imprime só o token; erro claro se `JWT_SECRET` faltar ou tiver menos de 32 bytes
- testes: helper `support/security/AccessTokens` (assina com `NimbusJwtEncoder` e o segredo do perfil `test`);
  todos os `*IT` HTTP enviam `Authorization: Bearer`; `AuthenticationIT`; casos de isolamento
- documentação: `docs/autenticacao.md` (uso do token e orientação para produção), uma linha no README apontando
  para ele, `docs/openapi.yaml` (`bearerAuth`, `security` global, 401 em cada endpoint), `docs/exemplos/transacoes.http`
  com o token; atualização de `CLAUDE.md` e das regras em `.claude/rules/` (orientação ao agente, não documentação);
  nota da T09 em `docs/tarefas.md`

**Fora:**
- ADR (JWT é padrão de mercado)
- `iss`, `aud`, papéis, 403
- cadastro ou lista de usuários na API, endpoint de login, *refresh*, revogação
- migração de dados: linhas antigas do banco local ficam sem dono real (UUID zerado), invisíveis
- `userId` nos logs
- CORS e "Try it out" do Swagger UI (continua só leitura)
- rate limiting (T19); deploy

## Decisões

- JWT HS256 com segredo compartilhado, validado pelo *resource server* do Spring; tokens gerados por script Python
  só com biblioteca padrão — a opção mais simples para o escopo; um provedor externo (Keycloak) seria o ideal, mas
  não para este projeto (decisão do usuário)
- Em produção, um provedor de identidade (Keycloak ou outro), com a API validando pela chave pública do emissor
  (`issuer-uri`/`jwk-set-uri`) — documentado em `docs/autenticacao.md` (decisão do usuário)
- Sem ADR — JWT é padrão de segurança, não decisão não óbvia (decisão do usuário)
- API stateless sem lista de usuários: o token carrega tudo; qualquer JWT bem assinado, dentro da validade e com
  `sub` UUID identifica um usuário (decisão do usuário)
- Só `sub` e `exp`, ambos obrigatórios (decisão do usuário)
- Script com `--user-id` (padrão aleatório) e `--expires-in` (padrão 24 h), ambos opcionais (decisão do usuário)
- Documentação oficial em `docs/autenticacao.md` + uma linha no README; `.claude/` é só orientação ao harness
  (decisão do usuário)
- `/api/v1/**` protegido, negar por padrão, só `/actuator/health` público; stateless, sem CSRF; 401 com URN própria,
  um só corpo, `WWW-Authenticate: Bearer`, log `WARN` (decisão do usuário)
- Usar o que o Spring oferece, sem reinventar: `NimbusJwtDecoder` + `JwtClaimValidator`, `AuthenticationEntryPoint`
  delegando ao `HandlerExceptionResolver`, `@AuthenticationPrincipal Jwt`, `ToolContext` do Spring AI,
  `NimbusJwtEncoder` nos testes; o único bean próprio é o `JwtDecoder`, porque o Boot não tem propriedade para
  segredo HMAC (decisão do usuário)
- Sem migração; coluna `user_id` (não `owner_id`) e campo `userId` (decisão do usuário)
- Métodos da porta nomeiam o usuário (`findByIdAndUserId`, `findAllByUserId`, `findAllByUserIdAndCategory`) —
  `findById` sozinho seria ambíguo com dois IDs (decisão do usuário)
- Usuário como parâmetro obrigatório da porta e do `execute`, não como critério opcional do `TransactionFilter` — o
  compilador impede esquecer o isolamento; o usuário é contexto da requisição, por isso fica fora do `Input`
- Testes com token real assinado (não o `jwt()` do `spring-security-test`) — um mecanismo só, que também serve ao
  `UploadLimitIT` (servidor real) e exercita o `JwtDecoder` em todos os `*IT`
- Sem CORS nem "Try it out" (decisão do usuário)
- Três fases: autenticação; isolamento; documentação

## Definição de pronto

- [ ] `./gradlew build` passa (Spotless, Checkstyle, `*Test`, `*IT`, JaCoCo), sem `OPENAI_API_KEY` nem `JWT_SECRET`
- [ ] Sem token, com token inválido, expirado, sem `exp` ou com `sub` que não é UUID → 401 Problem Details
      `unauthenticated` com `WWW-Authenticate: Bearer` e `X-Request-Id`; `/actuator/health` responde sem token
- [ ] Transação de outro usuário → 404 `transaction-not-found`; listagem e tool de IA só veem as do usuário; voz
      grava para o usuário do token
- [ ] `scripts/generate_token.py` gera um token aceito pela API rodando (`bootRun`)
- [ ] `grep -rn "org.springframework" src/main/java/dio/budgeting/domain` → nenhuma linha
- [ ] `docs/autenticacao.md`, README, `docs/openapi.yaml`, `docs/exemplos/transacoes.http`, `.env.example`,
      `CLAUDE.md`, regras e nota da T09 atualizados; checkbox da T08 marcado em `docs/tarefas.md` no último commit

## Para futuros agentes

Execute com `/executar-tarefa`. Durante a execução:

- marque `- [x]` no mesmo commit que conclui o item;
- antes do último commit de uma fase, rode o plano de verificação, escreva o resumo da fase com o resultado e
  mude o status para `Concluída` — tudo nesse mesmo commit;
- na última fase, no mesmo commit, confira a definição de pronto e escreva o resumo final (e o plano de deploy,
  se houver);
- mudança no plano só com aprovação do usuário, registrada em Decisões com o motivo.

Contexto para uma sessão nova:

- Branch `feat/identidade-e-isolamento`; título do PR `feat: adiciona autenticação por token e isolamento por
  usuário`. Commits: `feat:`, `test:`, `refactor:`, `build:`, `docs:`.
- Estado antes da tarefa: sem Spring Security; `Transaction` sem dono; porta `TransactionRepository` com `save`,
  `findById`, `findAll(filter, sort, page)` e `findAllByCategory`; use cases `execute(...)` sem usuário;
  `TransactionTools` com `persist-transaction` e `list-transactions-by-category`; o `ChatClient` é montado e chamado
  no `TransactionController` (a T09 move a orquestração).
- Adicionar o starter liga a segurança em todas as rotas: os `*IT` existentes (`TransactionEndpointsIT`,
  `ErrorResponseIT`, `VoiceCommandIT`, `RequestCorrelationIT`, `UploadLimitIT`) passam a enviar o token no mesmo
  ciclo. `UploadLimitIT` usa servidor real (`RANDOM_PORT` + `HttpClient`): cabeçalho `Authorization` no request.
  `ErrorResponseIT` testa 404 de `/nowhere` e 405 de `DELETE`: com a regra "negar por padrão", só respondem assim
  com token (sem token é 401).
- Ordem dos filtros: o `RequestIdFilter` hoje não tem `@Order` e roda depois do `FilterChainProxy` (ordem `-100`);
  precisa rodar antes dele e depois do `ServerHttpObservationFilter` (que cria o *span* lido pelo filtro).
- `AuthenticationEntryPoint`: lambda que chama `handlerExceptionResolver.resolveException(request, response, null,
  ex)` (bean `@Qualifier("handlerExceptionResolver")`); o `ApiExceptionHandler` ganha
  `@ExceptionHandler(AuthenticationException.class)` com o cabeçalho `WWW-Authenticate: Bearer`. No
  `handleExceptionInternal`, o 401 loga `WARN` `Authentication failed` em vez do `INFO` `Request rejected`.
- `JwtClaimValidator`: `new JwtClaimValidator<Instant>(JwtClaimNames.EXP, Objects::nonNull)` e
  `new JwtClaimValidator<String>(JwtClaimNames.SUB, sub -> UserId.parse(sub).isPresent())`, combinados com
  `JwtValidators.createDefaultWithValidators(...)` — conferir a API exata no Spring Security da versão do Boot 4.1.2.
- Segredo: bytes UTF-8 da string (`new SecretKeySpec(secret.getBytes(UTF_8), "HmacSHA256")`); o script usa a mesma
  regra. HS256 pede no mínimo 32 bytes.
- `ToolContext`: `chatClient.prompt().user(...).toolContext(Map.of(TransactionTools.USER_ID, userId)).call()`; na
  tool, `(UserId) toolContext.getContext().get(USER_ID)`. Confirmar que o `@Validated` do adaptador continua
  validando os argumentos com o parâmetro extra.
- `TransactionEndpointsIT.saveAt(...)` grava entidades direto pelo `TransactionEntityRepository`: passa a receber o
  `user_id`.
- Testes unitários: `@ExtendWith(MockitoExtension.class)`, `@Mock`/`@InjectMocks`; verificar que o use case repassa
  o `UserId` à porta.
- Script Python: código e mensagens em inglês; docstring de uso como no `scripts/merge.py`; executável
  (`chmod +x`, *shebang* `#!/usr/bin/env python3`).
- Checkstyle limita métodos a 50 linhas, inclusive em testes.
- Regras: `.claude/rules/security.md` (rascunho a reescrever), `architecture.md`, `api-conventions.md`,
  `error-handling.md`, `ai-integration.md`, `testing.md`.

## Fase 1: Autenticação

Status: Concluída  <!-- Não iniciada | Em andamento | Concluída -->

- [x] [TDD] `UserIdTest` (novo, `domain`): `parse` devolve o ID para UUID válido e vazio para texto malformado,
      vazio ou nulo; construtor rejeita `null` — implementa `UserId`
- [x] [TDD] `AuthenticationIT` (novo): `GET /api/v1/transactions` sem token → 401 `application/problem+json`, `type`
      `urn:problem-type:budgeting:unauthenticated`, `title`/`detail` em pt-BR e `WWW-Authenticate` começando com
      `Bearer`; com token válido → 200 — implementa a dependência, `JwtProperties`, `SecurityConfiguration`
      (`JwtDecoder`, `SecurityFilterChain`, `AuthenticationEntryPoint`), `ProblemType.UNAUTHENTICATED`,
      `problem.unauthenticated.*`, o handler, o helper `support/security/AccessTokens`, o segredo de teste, e o
      cabeçalho `Authorization` em todos os `*IT` existentes
- [x] [TDD] `AuthenticationIT` (parametrizado): assinatura com outro segredo, expirado, sem `exp`, `sub` ausente,
      `sub` que não é UUID e token malformado (`abc`) → o mesmo 401 — implementa os `JwtClaimValidator`
- [x] [TDD] `AuthenticationIT`: rota inexistente sem token → 401 (negar por padrão); `GET /actuator/health` sem
      token → 200
- [x] [TDD] `AuthenticationIT`: o 401 traz `X-Request-Id` e gera `WARN` `Authentication failed` com `status` 401 e
      `problemType`, com o `traceId` da requisição e sem o token no log — implementa a ordem do `RequestIdFilter` e
      o log
- [x] [verificação] `scripts/generate_token.py` (`--user-id`, `--expires-in`, `JWT_SECRET`) e `JWT_SECRET` no
      `.env.example` e em `budgeting.security.jwt.secret` do `application.properties`

### Plano de verificação

- `./gradlew build` → `BUILD SUCCESSFUL`, sem `OPENAI_API_KEY` nem `JWT_SECRET` no ambiente
- `JWT_SECRET=$(printf 'x%.0s' {1..32}) scripts/generate_token.py --user-id 0b7e2f0a-1c3d-4e5f-8a9b-0c1d2e3f4a5b
  --expires-in 30m` → três partes separadas por ponto; payload (base64url) com esse `sub` e `exp` ≈ agora + 1800
- `JWT_SECRET=curto scripts/generate_token.py` → erro, código de saída ≠ 0
- com o `.env` carregado (`set -a && source .env && set +a`, `OPENAI_API_KEY` qualquer valor), `./gradlew bootRun`
  em segundo plano; `curl -s -o /dev/null -w '%{http_code}' localhost:8080/api/v1/transactions` → `401`; com
  `-H "Authorization: Bearer $(scripts/generate_token.py)"` → `200`; parar o `bootRun`
- `./gradlew bootRun` sem `JWT_SECRET` → falha na subida citando `budgeting.security.jwt.secret`

### Resumo da fase

**O que foi feito**

- `UserId` no domínio (record sobre `UUID`, `parse` como `TransactionId.parse`).
- Starter `oauth2-resource-server`; `JwtProperties` (`budgeting.security.jwt.secret`, `@NotBlank`, `@Size(min = 32)`);
  `SecurityConfiguration` com `JwtDecoder` HS256 (validadores padrão + `exp` presente + `sub` UUID),
  `SecurityFilterChain` (só `/actuator/health` público, stateless, sem CSRF/form login/Basic) e
  `AuthenticationEntryPoint` que delega ao `HandlerExceptionResolver`.
- 401 em Problem Details (`ProblemType.UNAUTHENTICATED`, mensagens em `messages.properties`, `WWW-Authenticate: Bearer`),
  log `WARN` `Authentication failed` com `status` e `problemType`, sem token.
- `RequestIdFilter` com `@Order(SecurityFilterProperties.DEFAULT_FILTER_ORDER - 1)`: o 401 leva `X-Request-Id`.
- Testes: `UserIdTest`, `AuthenticationIT` (sem token, inválidos parametrizados, rota inexistente, health, request id e
  log), helper `support/security/AccessTokens`; todos os `*IT` existentes enviam `Authorization: Bearer`.
- `scripts/generate_token.py` e `JWT_SECRET` no `.env.example` e no `application.properties`.

**Decisões e detalhes**

- Em Boot 4.1.2 a constante da ordem do filtro está em
  `org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties`.
- `createDefaultWithValidators` recebe `List.of(...)` (a forma varargs gera aviso de array genérico).
- O `AuthenticationEntryPoint` é configurado só em `oauth2ResourceServer`, que também cobre o token ausente.
- Os casos "rota inexistente sem token" e "health público" passaram de imediato: já decorriam da configuração do
  item 2; ficam como teste de regressão.

**Verificação:** `./gradlew build` → `BUILD SUCCESSFUL`, sem `OPENAI_API_KEY` nem `JWT_SECRET` no ambiente; script
gera três partes com `sub` e `exp` ≈ agora + 1800 e falha (≠ 0) com segredo curto; `bootRun` → 401 sem token, 200
com token, 200 em `/actuator/health`; sem `JWT_SECRET` a subida falha citando `budgeting.security.jwt.secret`.

**Para uma sessão nova:** o `.env` local precisa de `JWT_SECRET` (mínimo de 32 bytes) para o `bootRun`. Nesta fase
os `*IT` usam o usuário fixo `AccessTokens.DEFAULT_USER`; `Transaction` ainda não tem dono (fase 2).

## Fase 2: Isolamento por usuário

Status: Em andamento  <!-- Não iniciada | Em andamento | Concluída -->

- [x] [TDD] `TransactionTest`: construtor de criação rejeita `userId` nulo; `PersistTransactionUseCaseTest`: grava
      com o `UserId` recebido — implementa `Transaction.userId`, `user_id` na entidade (`NOT NULL`),
      `execute(UserId, PersistTransactionInput)`, o `UserId` do `Jwt` no controller e o `ToolContext` na tool
      `persist-transaction`
- [x] [TDD] `GetTransactionUseCaseTest`: consulta a porta com o `UserId`; `TransactionEndpointsIT`: transação de
      outro usuário → 404 `transaction-not-found`, mesmo corpo do ID inexistente — implementa `findByIdAndUserId`
      (porta e adaptador) e `execute(UserId, String)`
- [x] [TDD] `ListTransactionsUseCaseTest`: repassa o `UserId` à porta; `TransactionEndpointsIT`: listagem (com e
      sem `category`) traz só as do usuário, com `totalElements` dele — implementa `findAllByUserId` (adaptador com
      `Specification` do usuário somada às do filtro) e os índices com `user_id`
- [ ] [TDD] `JpaTransactionRepositoryIT` (novo): `findAllByUserIdAndCategory` não traz transações de outro usuário;
      `ListTransactionsByCategoryUseCase` com `execute(UserId, Category)` — implementa o método da porta e o
      `ToolContext` na tool `list-transactions-by-category`
- [ ] [TDD] `VoiceCommandIT`: o comando de voz grava a transação para o usuário do token (não visível para outro)

### Plano de verificação

- `./gradlew build` → `BUILD SUCCESSFUL`, sem `OPENAI_API_KEY` nem `JWT_SECRET`
- `grep -rn "org.springframework" src/main/java/dio/budgeting/domain` → nenhuma linha
- `grep -rnE "findById\(|findAll\(|findAllByCategory\(" src/main/java/dio/budgeting/domain src/main/java/dio/budgeting/application`
  → nenhuma linha (só os métodos com `UserId`)
- `grep -rn "ToolContext" src/main/java/dio/budgeting/infrastructure/ai/tool/TransactionTools.java` → as duas tools

### Resumo da fase

_(preencher ao concluir a fase)_

## Fase 3: Documentação

Status: Não iniciada  <!-- Não iniciada | Em andamento | Concluída -->

- [ ] [verificação] `docs/autenticacao.md` (novo): como gerar o token (`scripts/generate_token.py`, `JWT_SECRET`,
      argumentos), como enviar (`Authorization: Bearer`), o que a API valida (`sub` UUID, `exp`, assinatura HS256),
      o 401; e a orientação para produção: script e segredo compartilhado só para desenvolvimento, provedor de
      identidade (Keycloak ou outro) emitindo os tokens e a API validando pela chave pública (`issuer-uri` ou
      `jwk-set-uri`), sem mudar domínio nem use cases
- [ ] [verificação] README: `JWT_SECRET` na configuração e uma linha na execução/API apontando para
      `docs/autenticacao.md`
- [ ] [verificação] `docs/openapi.yaml`: `components.securitySchemes.bearerAuth` (`http`, `bearer`, `JWT`, descrição
      apontando para `docs/autenticacao.md`), `security` global e resposta 401 (Problem Details `unauthenticated`,
      `WWW-Authenticate`) em cada endpoint; nota de isolamento no 404 do `GET /{id}`
- [ ] [verificação] `docs/exemplos/transacoes.http`: variável `@token` com instrução para gerar, `Authorization` em
      todas as requisições e um exemplo de 401 sem token
- [ ] [verificação] `CLAUDE.md` (stack, estrutura `infrastructure/security/`, `scripts/generate_token.py` nos
      comandos, `JWT_SECRET` nos segredos); `.claude/rules/security.md` reescrito (JWT HS256 de desenvolvimento,
      `sub`/`exp`, sem senha/BCrypt, `@AuthenticationPrincipal Jwt` → `UserId`, porta com `UserId`); linhas do 401
      em `error-handling.md` e `api-conventions.md` (`Authorization`, `WWW-Authenticate`); `ToolContext` em
      `ai-integration.md`; `AccessTokens` em `testing.md`; assinaturas da porta em `architecture.md`
- [ ] [verificação] `docs/tarefas.md`: nota na T09 (o `userId` chega às tools pelo `toolContext` do `ChatClient`;
      ao mover a orquestração, o use case recebe o `UserId`)

### Plano de verificação

- `./gradlew build` → `BUILD SUCCESSFUL` (inclui o Spotless dos `.md`)
- `grep -n "bearerAuth\|unauthenticated" docs/openapi.yaml` → esquema, `security` global e 401 nos quatro endpoints
- `grep -n "autenticacao.md" README.md docs/openapi.yaml` → uma linha em cada
- `grep -n "A definir\|BCrypt" .claude/rules/security.md` → nenhuma linha
- `docker compose up -d swagger-ui && curl -s localhost:8081/ | grep -i swagger` → página servida;
  `docker compose stop swagger-ui`

### Resumo da fase

_(preencher ao concluir a fase)_

## Resumo final

_(preencher ao concluir todas as fases)_
