# T19 — Rate limiting por usuário

Limitar as requisições de cada usuário em toda rota autenticada, com um limite próprio para as rotas de IA, recusando o
excesso com 429 e `Retry-After` dinâmico (SEG-03). O limitador fica atrás de uma interface neutra (canal e
armazenamento), com o Resilience4j e o Caffeine hoje e espaço para Redis no futuro (ADR 0010).

## Escopo

**Dentro:**
- Dois grupos de limite por usuário, independentes:
  - `AI` — rotas marcadas com `@AiRateLimit` (hoje só o `POST /api/v1/transactions/ai`): 10 por minuto
  - `DEFAULT` — toda outra rota autenticada, inclusive uma inexistente com token válido: 60 por minuto
  - um comando de voz conta só no grupo `AI`; valores provisórios, com comentário no `application.properties`
- Núcleo neutro em `infrastructure/security/ratelimit/` (não conhece HTTP):
  - `UserRateLimiter` — interface com `acquirePermission(UserId, RateLimitGroup)`; recusa lançando
    `RateLimitExceededException`
  - `Resilience4jUserRateLimiter` — adaptador: um `RateLimiter.of(...)` do Resilience4j por usuário e grupo, fora do
    `RateLimiterRegistry` (sem série de métrica por usuário), guardado no cache `rate-limiters` pela abstração do
    Spring (`Cache.get(chave, criador)`); `timeoutDuration` zero (recusa na hora, como o bulkhead); espera pelo
    `AtomicRateLimiter.AtomicRateLimiterMetrics.getNanosToWait()`, arredondada para cima em segundos, mínimo 1
  - `RateLimitGroup` — enum `AI`, `DEFAULT`
  - `RateLimitExceededException` — unchecked, com `UserId`, `RateLimitGroup` e a espera (`Duration`)
  - `RateLimitProperties` — `budgeting.rate-limit.enabled` e `budgeting.rate-limit.groups.<grupo>.max-requests` /
    `.period` (`Map<RateLimitGroup, Limit>`, validado: limite para todo grupo, valores positivos)
  - `@AiRateLimit` — anotação marcadora de método (marca o ponto de entrada, não a rota HTTP)
- Adaptador HTTP:
  - `infrastructure/http/interceptor/RateLimitInterceptor` — `HandlerInterceptor`; lê o `sub` do
    `JwtAuthenticationToken` do `SecurityContextHolder` (sem ele, passa direto: rotas públicas); grupo `AI` se o
    `HandlerMethod` tem `@AiRateLimit`, senão `DEFAULT`; `@Component` com
    `@ConditionalOnBooleanProperty("budgeting.rate-limit.enabled")`
  - `infrastructure/config/WebMvcConfiguration` — `WebMvcConfigurer` que registra o interceptor se ele existir
    (`ObjectProvider<RateLimitInterceptor>.ifAvailable`)
  - roda antes do controller e de seus aspectos: a recusa não chama a IA nem conta no circuito `voice-command`;
    requisições que depois dariam 400 ou 422 também contam
- 429 pelo `ApiExceptionHandler`: `urn:problem-type:budgeting:rate-limit-exceeded` (`ProblemType.RATE_LIMIT_EXCEEDED`),
  textos `problem.rate-limit-exceeded.*` em `messages.properties`, mesmo corpo para os dois grupos, `Retry-After` com a
  espera; `WARN "Rate limit exceeded"` com `status`, `rateLimitGroup` (`ai` ou `default`), `userId` e
  `retryAfterSeconds`, sem stack trace
- Cache `rate-limiters` no `CachingConfiguration`, com `CachingProperties.IdlePolicy(maxEntries, timeToIdle)`
  (`expireAfterAccess`): `budgeting.cache.rate-limiters.max-entries=10000`, `time-to-idle=1m`, com comentário (tem de
  ser pelo menos o maior período dos grupos; um limitador descartado pelo teto recomeça cheio)
- Testes: `budgeting.rate-limit.enabled=false` no `application-test.properties`; `RateLimitIT` liga pelo próprio
  `@TestPropertySource` (a `@EnableResilience` não muda); 4 casos; nenhum teste existente muda
- Documentação: ADR 0010, `docs/openapi.yaml`, regras (`error-handling.md`, `api-conventions.md`, `security.md`,
  `architecture.md`, `testing.md`), `CLAUDE.md` (Stack e Estrutura), `docs/escopo.md` (FUT-20 e FUT-21) e checkbox da
  T19 em `docs/tarefas.md`

**Fora:**
- limite por IP e em rotas não autenticadas (health, métricas, 401) — prática de produção na borda (FUT-21)
- rate limiting distribuído (Redis) — só documentado (ADR 0010, FUT-20)
- `@RateLimiter` do Resilience4j e `RateLimiterRegistry`; métricas próprias do rate limiting (o
  `http.server.requests` com `status=429` já existe)
- testes do log, das rotas públicas e unitário do adaptador
- `README.md` e `docs/arquitetura.md`
- a divergência do `/actuator/metrics` público nos documentos (decisão do usuário: ignorar)
- deploy; chamada à OpenAI real em qualquer teste ou verificação

## Decisões

- Toda rota autenticada tem limite, e as rotas de IA têm um limite diferente das demais, por causa do custo e da
  latência (decisão do usuário)
- `HandlerInterceptor`, não `@RateLimiter` anotado — cobre por construção toda rota autenticada, inclusive as futuras;
  roda antes dos aspectos do controller (não conta no circuit breaker); o `@RateLimiter` foi feito para proteger um
  recurso com limite fixo, não para limite por cliente (decisão do usuário)
- Rotas não autenticadas fora — o interceptor roda depois do Spring Security; health checks não podem ser limitados
  (o orquestrador derrubaria a instância); limite por IP exige confiar no `X-Forwarded-For` e cabe ao gateway, ingress
  ou WAF (decisão do usuário)
- Grupos independentes: o comando de voz conta só no `AI` (decisão do usuário)
- 10 e 60 por minuto, "por enquanto" — período de um minuto para não recusar requisições legítimas seguidas (decisão
  do usuário)
- `Retry-After` dinâmico, não fixo — valor exato para o cliente, ao custo de um cast para a classe interna do
  Resilience4j e de uma exceção própria (decisão do usuário)
- Um `type` próprio para os dois grupos; o log distingue o grupo (decisão do usuário)
- Limitadores no Caffeine, fora do registry — memória limitada e sem série de métrica por usuário (com o ID nas tags)
  (decisão do usuário)
- Interface neutra e propriedades `budgeting.rate-limit.*`, não `resilience4j.ratelimiter.*` — o Resilience4j não tem
  backend Redis; migrar é um adaptador novo que lê as mesmas chaves (decisão do usuário)
- Chave `enabled` desligada no perfil `test` e ligada só na classe do teste, não pela `@EnableResilience` — não acoplar
  o rate limiting aos outros testes de resiliência (decisão do usuário)
- `WARN` com `status`, `rateLimitGroup`, `userId` e `retryAfterSeconds` (decisão do usuário)
- Cache com `max-entries=10000` e `time-to-idle=1m`; expiração por inatividade, porque um limitador parado por um
  período inteiro já está cheio (decisão do usuário)
- Grupo da rota por anotação (`@AiRateLimit`), não por lista de caminhos — a rota declara o grupo; esquecer a anotação
  cai no limite mais folgado, nunca em nenhum (decisão do usuário)
- ADR 0010 (decisão do usuário)
- Só testes essenciais: um por requisito (decisão do usuário)
- Nomes revisados: `@AiRateLimit` (não `@RateLimited`, que sugere que as outras rotas não têm limite, nem `@RateLimit`,
  a duas letras do `@RateLimiter` do Resilience4j); `acquirePermission` (não `acquire`, que no projeto forma par com
  `release`); `groups.<grupo>.max-requests`; `IdlePolicy` (não reaproveitar o `timeToLive` do `Policy`) (decisão do
  usuário)
- Núcleo em `security/ratelimit/` e interceptor em `http/interceptor/` — o limitador é proteção (SEG-03, como a
  autenticação) e neutro de canal; cada canal (HTTP hoje, gRPC ou GraphQL no futuro) tem o próprio adaptador e a
  própria tradução da recusa; as dependências apontam para o núcleo (decisão do usuário)
- Recusa na hora (timeout zero); requisições inválidas contam; `Retry-After` arredondado para cima, mínimo 1 s
  (decisão do usuário)
- Sem `README.md` nesta tarefa (decisão do usuário)

## Definição de pronto

- [ ] A 3ª requisição de um usuário a uma rota comum, com limite 2, responde 429 `rate-limit-exceeded` com
      `Retry-After` entre 1 e 3600 (`RateLimitIT`)
- [ ] O 2º comando de voz, com limite 1, responde 429 sem chamar a IA (`RateLimitIT`)
- [ ] Os grupos são independentes e o limite é por usuário (`RateLimitIT`)
- [ ] Com a aplicação no ar, a 61ª requisição em um minuto responde 429 e o log tem `WARN "Rate limit exceeded"`
- [ ] Nenhum teste existente alterado; rate limiting desligado nos testes, fora do `RateLimitIT`
- [ ] ADR 0010, OpenAPI, regras, `CLAUDE.md` e escopo atualizados
- [ ] `./gradlew build` passa

## Para futuros agentes

Execute com `/executar-tarefa`. Durante a execução:

- marque `- [x]` no mesmo commit que conclui o item;
- antes do último commit de uma fase, rode o plano de verificação, escreva o resumo da fase com o resultado e
  mude o status para `Concluída` — tudo nesse mesmo commit;
- na última fase, no mesmo commit, confira a definição de pronto e escreva o resumo final;
- mudança no plano só com aprovação do usuário, registrada em Decisões com o motivo.

## Fase 1: Limite das demais rotas, por usuário

Status: Em andamento  <!-- Não iniciada | Em andamento | Concluída -->

- [x] [verificação] `application.properties`: `budgeting.rate-limit.enabled=true`,
      `budgeting.rate-limit.groups.ai.max-requests=10`, `.ai.period=1m`, `.default.max-requests=60`,
      `.default.period=1m`, com comentário (valores provisórios: o real viria do uso observado e, na IA, do limite da
      conta na OpenAI; o limite vale por instância; migração para Redis na ADR 0010);
      `budgeting.cache.rate-limiters.max-entries=10000` e `.time-to-idle=1m`, com comentário (pelo menos o maior período;
      um limitador descartado pelo teto recomeça cheio)
- [x] [verificação] `application-test.properties`: `budgeting.rate-limit.enabled=false`, com comentário (o rate limiting
      não influencia nenhum teste; o `RateLimitIT` liga na própria classe)
- [x] [verificação] `CachingProperties.IdlePolicy(@Positive long maxEntries, @NotNull Duration timeToIdle)` e campo
      `rateLimiters`; `CachingConfiguration` registra `rate-limiters` com `expireAfterAccess` e expõe o bean
      `rateLimitersCache`, como o `voiceCommandLocksCache`
- [x] [TDD] `RateLimitIT` (`infrastructure/http/`; `@IntegrationTest`, `@AutoConfigureMockMvc`, `@AutoConfigureAiStub`,
      `@TestPropertySource` com `budgeting.rate-limit.enabled=true`, `groups.default.max-requests=2`,
      `groups.ai.max-requests=1` e os dois `period=1h`; um `UserId` aleatório por teste com
      `AccessTokens.authorization(userId)`): `should_rejectWithTooManyRequests_when_defaultLimitIsExceeded` — 3 ×
      `GET /api/v1/transactions` → a 3ª é 429 com `type` `urn:problem-type:budgeting:rate-limit-exceeded` e
      `Retry-After` entre 1 e 3600 (`RateLimitGroup`, `RateLimitProperties`, `UserRateLimiter`,
      `Resilience4jUserRateLimiter`, `RateLimitExceededException`, `RateLimitInterceptor` com grupo `DEFAULT`,
      `WebMvcConfiguration`, `ProblemType.RATE_LIMIT_EXCEEDED`, `problem.rate-limit-exceeded.*`, handler do 429 com
      `Retry-After` e `WARN "Rate limit exceeded"` sem stack trace no `handleExceptionInternal`)
- [ ] [TDD] `RateLimitIT`: `should_keepOtherUsersAvailable_when_userExceedsLimit` — um usuário esgota o limite e outro
      recebe 200; se já passar, confirmar que falha com a chave do cache sem o usuário

### Plano de verificação

- `./gradlew integrationTest --tests "dio.budgeting.infrastructure.http.RateLimitIT"` → passa
- `git diff main --stat -- src/test/java src/test/resources` → só o `RateLimitIT` novo e o `application-test.properties`
- Com `set -a && source .env && set +a && ./gradlew bootRun` em segundo plano e `TOKEN=$(python3 scripts/generate_token.py)`:
  `for i in $(seq 61); do curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOKEN" localhost:8080/api/v1/transactions; done | sort | uniq -c`
  → `60 200` e `1 429`; `curl -si -H "Authorization: Bearer $TOKEN" localhost:8080/api/v1/transactions | grep -i retry-after`
  → um valor entre 1 e 60; o log da aplicação tem `Rate limit exceeded` com `rateLimitGroup` `default`; encerrar a
  aplicação em seguida (nenhum comando de voz é enviado)
- `./gradlew build` → `BUILD SUCCESSFUL`

### Resumo da fase

_(preencher ao concluir a fase)_

## Fase 2: Limite próprio das rotas de IA

Status: Não iniciada  <!-- Não iniciada | Em andamento | Concluída -->

- [ ] [TDD] `RateLimitIT`: `should_rejectWithoutCallingAi_when_aiLimitIsExceeded` — IA saudável no `AiStub`, 2
      comandos de voz → o 1º é 200 e o 2º é 429 `rate-limit-exceeded`, com `transcriptionCallCount()` igual a 1
      (`@AiRateLimit` no `transcribe` do `TransactionController`; o interceptor escolhe `AI` pelo `HandlerMethod`)
- [ ] [TDD] `RateLimitIT`: `should_keepDefaultRoutesAvailable_when_aiLimitIsExceeded` — com o limite de IA esgotado,
      `GET /api/v1/transactions` → 200; se já passar, confirmar que falha com o comando de voz consumindo também o
      grupo `DEFAULT`

### Plano de verificação

- `./gradlew integrationTest --tests "dio.budgeting.infrastructure.http.RateLimitIT"` → passa (4 casos)
- `./gradlew integrationTest --tests "dio.budgeting.infrastructure.http.VoiceCommandCircuitBreakerIT"` → passa
- `git diff main --stat -- src/test/java src/test/resources` → só o `RateLimitIT` novo e o `application-test.properties`
- `./gradlew build` → `BUILD SUCCESSFUL`

### Resumo da fase

_(preencher ao concluir a fase)_

## Fase 3: Documentação

Status: Não iniciada  <!-- Não iniciada | Em andamento | Concluída -->

- [ ] [verificação] `docs/adr/0010-limite-de-requisicoes-por-usuario-em-memoria-local.md` (do `template.md`, `aceito`):
      opções `@RateLimiter` anotado; interceptor com o Resilience4j e o Caffeine atrás de uma interface; Redis já agora;
      limite por IP na aplicação. Consequências: limite por instância (com N instâncias, N vezes o valor), perdido no
      reinício, migração = um adaptador novo; métricas sem série por usuário
- [ ] [verificação] `docs/openapi.yaml`: resposta reutilizável `TooManyRequests` (cabeçalho `Retry-After`, exemplo
      `rate-limit-exceeded`) referenciada nas 6 rotas autenticadas; a descrição do `POST /transactions/ai` cita o limite
      próprio das rotas de IA
- [ ] [verificação] Regras: `error-handling.md` (linha da `RateLimitExceededException` → 429 com `Retry-After` e o
      `WARN`); `api-conventions.md` (dois grupos por usuário, `@AiRateLimit` em rota de IA nova, `Retry-After` dinâmico);
      `security.md` (troca "Rate limiting fica para a T19" por uma seção curta: por usuário, grupos, rotas públicas fora,
      limite por IP na borda); `architecture.md` (núcleo neutro em `security/ratelimit/`, um adaptador por canal, cache
      `rate-limiters`, Redis como adaptador novo); `testing.md` (chave desligada no perfil `test`, ligada só pelo
      `@TestPropertySource` do `RateLimitIT`, sem `@EnableResilience`)
- [ ] [verificação] `CLAUDE.md`: Stack (Caffeine também guarda os limitadores por usuário; Resilience4j também no rate
      limiting por usuário) e Estrutura (`http/interceptor/`, `security/ratelimit/`)
- [ ] [verificação] `docs/escopo.md`: FUT-20 — "Rate limiting distribuído" (limitadores no Redis atrás da mesma
      interface, para várias instâncias; ADR 0010) e FUT-21 — "Rate limiting por IP na borda" (API gateway, ingress ou
      WAF para o tráfego anônimo), cada um com descrição e motivo
- [ ] [verificação] `docs/tarefas.md`: no último commit, checkbox da T19

### Plano de verificação

- `ls docs/adr/0010-*` → o ADR
- `grep -c "TooManyRequests" docs/openapi.yaml` → 7 (definição e 6 referências)
- `grep -n "RateLimitExceededException" .claude/rules/error-handling.md` → a linha da tabela
- `grep -n "T19" .claude/rules/security.md` → nenhuma ocorrência
- `grep -n "ratelimit" CLAUDE.md .claude/rules/architecture.md` → ocorrências nos dois
- `grep -n "FUT-20\|FUT-21" docs/escopo.md` → as duas linhas
- `grep -n "\[x\] \[T19" docs/tarefas.md` → checkbox marcado
- `./gradlew build` → `BUILD SUCCESSFUL`

### Resumo da fase

_(preencher ao concluir a fase)_

## Resumo final

_(preencher ao concluir todas as fases)_
