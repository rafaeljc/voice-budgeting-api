# T17 — Idempotência no envio de áudio

Impedir que o mesmo comando de voz, reenviado com a mesma chave de idempotência dentro de uma janela (ex.: envio
repetido por instabilidade da interface), seja processado de novo: nenhuma proposta duplicada e nenhuma nova cobrança
de IA. O critério para identificar um reenvio fica registrado em ADR (RES-06, QA-03 parcial, DOC-03).

## Escopo

**Dentro:**
- Cabeçalho `Idempotency-Key` no `POST /api/v1/transactions/ai` (rascunho da IETF
  `draft-ietf-httpapi-idempotency-key-header`):
  - opcional: sem ele, o comando é processado como hoje, sem trava
  - formato UUID, lido no `TransactionController` como `@RequestHeader(name = "Idempotency-Key", required = false)
    UUID`; fora do formato → `MethodArgumentTypeMismatchException`, já mapeada no `handleTypeMismatch` → 400
    `validation-error` com `{parameter: "Idempotency-Key", detail}`, sem código novo de erro
- Trava por usuário + chave, com o SHA-256 do áudio como fingerprint, para todos os comandos de voz (proposta,
  consulta e não entendido):
  - a trava é adquirida no início do `ProcessVoiceCommandUseCase.execute`, antes de qualquer chamada à IA
  - mesma chave e mesmo áudio dentro da janela (o primeiro ainda processando ou já concluído) → `409`
    `urn:problem-type:budgeting:duplicate-voice-command`, na hora (o perdedor não espera o primeiro), sem IA, sem
    gravar nada e sem `Retry-After` (repetir depois da janela processaria de novo)
  - mesma chave e outro áudio → `422` `urn:problem-type:budgeting:idempotency-key-reused`, sem IA
  - qualquer exceção do comando (503, 422 `invalid-command`, 500) libera a trava antes de relançar; o sucesso não
    libera, a trava vence com a janela
  - a mesma chave de outro usuário não colide
- Peças novas:
  - `application/input/IdempotencyKey` — `record IdempotencyKey(UUID uuid)`, construtor compacto rejeita `null`
  - `ProcessVoiceCommandInput` ganha `Optional<IdempotencyKey> idempotencyKey`; `VoiceCommandRequest.toInput` recebe a
    chave lida pelo controller
  - `application/VoiceCommandLock` — porta: `void acquire(UserId userId, IdempotencyKey key, Audio audio)` e
    `void release(UserId userId, IdempotencyKey key)`, sem `throws` (como as portas `*Gateway`)
  - `application/DuplicateVoiceCommandException` e `application/IdempotencyKeyReusedException` — unchecked
  - `infrastructure/cache/SpringCacheVoiceCommandLock` — adaptador com o `Cache` do Spring (`voice-command-locks`,
    obtido do `CacheManager`): chave do cache = usuário + `IdempotencyKey`; valor = SHA-256 do áudio em hexadecimal
    (`MessageDigest` do JDK); `acquire` faz `putIfAbsent` — `null` → adquiriu; valor igual → `Duplicate...`; valor
    diferente → `IdempotencyKeyReused...`; `release` faz `evict`
  - `ApiExceptionHandler`: 409 e 422 com os novos `ProblemType` (`DUPLICATE_VOICE_COMMAND`, `IDEMPOTENCY_KEY_REUSED`);
    log `INFO Request rejected` como os demais 4xx; textos em `messages.properties`
- Caches montados em Java, no `CachingConfiguration`:
  - `speech` e `voice-command-locks` registrados por um `CacheManagerCustomizer<CaffeineCacheManager>` com
    `registerCustomCache`, cada um com `maximumSize`, `expireAfterWrite` e `recordStats`; o tipo `Caffeine` só aparece
    nessa classe; o adaptador e o `SpringAiTextToSpeechGateway` só conhecem a abstração do Spring
  - `infrastructure/config/CachingProperties` (`budgeting.cache.*`): `speech.max-entries=500`,
    `speech.time-to-live=1h`, `voice-command-locks.max-entries=10000`, `voice-command-locks.time-to-live=2m`
  - saem `spring.cache.caffeine.spec` e `spring.cache.cache-names`; fica `spring.cache.type=caffeine`; o manager
    continua estático (um nome desconhecido no `@Cacheable` falha em vez de criar um cache sem limite)
- Testes novos: `ProcessVoiceCommandUseCaseTest` (2 casos), `SpringCacheVoiceCommandLockTest`,
  `VoiceCommandIdempotencyIT`, `ErrorResponseIT` (409, 422, 400). Nenhum teste existente muda: sem chave a trava não é
  chamada
- Documentação: ADR 0009, `docs/openapi.yaml`, `docs/exemplos/voz.http`, `docs/arquitetura.md`, `README.md`,
  `docs/escopo.md` (RES-06), regras (`architecture.md`, `api-conventions.md`, `error-handling.md`,
  `ai-integration.md`, `testing.md`), `CLAUDE.md`, `docs/tarefas.md` (nota da T10 e checkbox)

**Fora:**
- cabeçalho obrigatório
- guardar e devolver a resposta do primeiro envio (idempotência ≠ cache)
- trava durável ou distribuída (MySQL, MongoDB com TTL, Redis) — só no ADR, como caminho para produção
- retry no `AssistantGateway` (nota da T10) — só a nota e a linha do `ai-integration.md` mudam
- ADR para a mudança da configuração dos caches; o ADR 0008 não muda
- teste do vencimento da janela e das propriedades (configuração)
- deploy; chamada à OpenAI real em qualquer teste ou verificação

## Decisões

- A chave do cliente (`Idempotency-Key`) identifica o reenvio, não o hash do áudio — com o hash, o servidor deduz a
  intenção pelos bytes, e testes e exemplos reutilizam o mesmo arquivo para intenções diferentes; com a chave, quem
  declara a intenção é o cliente, por requisição (decisão do usuário)
- Chave + SHA-256 do áudio como fingerprint — o rascunho da IETF recomenda 422 para a chave reutilizada com outro
  conteúdo; o erro aponta o bug do cliente em vez de um 409 genérico, a poucas linhas de custo (decisão do usuário)
- Cabeçalho opcional — não quebra o contrato, os testes nem o `voz.http`; o cliente que quer proteção envia a chave,
  como no Stripe (decisão do usuário)
- Chave em UUID — recomendação da IETF, padrão de identificadores do projeto, marca de tamanho fixo (decisão do usuário)
- Cobre todos os comandos de voz — todo comando paga transcrição e chat; o escopo pede "nem nova cobrança de IA"
  (decisão do usuário)
- Repetido → erro, sem processar, sem guardar a resposta — o objetivo é não reprocessar, não servir a resposta de
  novo; guardar a resposta seria cache (decisão do usuário)
- `409 Conflict` `duplicate-voice-command`, sem `Retry-After` — conflito com o estado atual (status do artigo e da
  IETF); 429 é do rate limiting (T19) e 422 é do conteúdo interpretado (decisão do usuário)
- Trava em Caffeine local com `putIfAbsent`, não MySQL, MongoDB ou Redis — janela curta: a perda no reinício e a
  instância única são aceitáveis; o `putIfAbsent` é atômico e devolve na hora, então o perdedor recebe o erro sem
  esperar, ao contrário de `get(key, loader)` e `@Cacheable(sync = true)` (decisão do usuário)
- Os dois caches montados no `CachingConfiguration` — o Boot tem uma só `spring.cache.caffeine.spec` para todos os
  caches; com mais de um cache, todos são definidos na configuração, com o Caffeine só ali (decisão do usuário)
- Janela de 2 min e até 10.000 marcas — a trava nasce no início; a janela cobre o pior comando (~96 s, ver
  `application.properties`) com folga; marcas de poucos bytes (decisão do usuário)
- Qualquer falha libera a trava — uma falha não deixa efeito (a proposta é gravada por último), e o 503 com
  `Retry-After` convida a repetir (decisão do usuário)
- Checagem no use case, por uma porta — o use case já orquestra e tem o ponto de saída na falha; a porta isola a
  tecnologia e deixa a regra testável sem Spring (decisão do usuário)
- Nomes (`VoiceCommandLock.acquire`/`release`, `IdempotencyKey`, `SpringCacheVoiceCommandLock`,
  `DuplicateVoiceCommandException`, `IdempotencyKeyReusedException`, `voice-command-locks`, `max-entries`,
  `time-to-live`) — revisados com princípios de clean code: `time-to-live` é o termo já usado no projeto,
  `max-entries` diz a unidade; porta sem `throws`, como as `*Gateway` (decisão do usuário)
- Retry no `AssistantGateway` fora — desde a T12 as tools não gravam, e com `returnDirect` há uma única chamada ao
  chat, já repetida pelo SDK (decisão do usuário)
- README: a linha `cache.gets` só ganha o filtro `cache="speech"`, sem citar a trava — ela usa o Caffeine por
  simplicidade e pelo escopo, é detalhe de implementação e não entra como cache documentado (decisão do usuário, na
  Fase 2)
- Um ADR só (0009), apenas sobre a idempotência; o ADR 0008 não muda — a mudança da configuração dos caches não é
  decisão para ADR; o estado vigente fica no `architecture.md` e no `application.properties` (decisão do usuário)

## Definição de pronto

- [ ] Com a mesma chave e o mesmo áudio, dois envios simultâneos resultam em um `200` e um `409` imediato, uma
      chamada ao chat e uma proposta (`VoiceCommandIdempotencyIT`)
- [ ] Depois de um `503`, a mesma chave é aceita (`VoiceCommandIdempotencyIT`)
- [ ] Mesma chave com outro áudio → `422` `idempotency-key-reused`; chave malformada → `400` (`ErrorResponseIT`)
- [ ] Sem chave, nenhum teste existente muda e todos passam
- [ ] `Caffeine` só no `CachingConfiguration`; sem `spring.cache.caffeine.spec` nem `spring.cache.cache-names`
- [ ] ADR 0009 e documentação atualizados
- [ ] `./gradlew build` passa

## Para futuros agentes

Execute com `/executar-tarefa`. Durante a execução:

- marque `- [x]` no mesmo commit que conclui o item;
- antes do último commit de uma fase, rode o plano de verificação, escreva o resumo da fase com o resultado e
  mude o status para `Concluída` — tudo nesse mesmo commit;
- na última fase, no mesmo commit, confira a definição de pronto e escreva o resumo final;
- mudança no plano só com aprovação do usuário, registrada em Decisões com o motivo.

## Fase 1: Caches montados no `CachingConfiguration`

Status: Concluída  <!-- Não iniciada | Em andamento | Concluída -->

- [x] [verificação] `CachingProperties` (`budgeting.cache.speech.*` e `budgeting.cache.voice-command-locks.*`:
      `max-entries`, `time-to-live`) e `CachingConfiguration` com `CacheManagerCustomizer<CaffeineCacheManager>`
      registrando os dois caches por `registerCustomCache` (`maximumSize`, `expireAfterWrite`, `recordStats`),
      manager estático; comentário do porquê (uma `spec` só no Boot)
- [x] [verificação] `application.properties`: sai `spring.cache.caffeine.spec` e `spring.cache.cache-names`; entram
      as quatro propriedades com o comentário que hoje explica a `spec` (tamanho, TTL, `recordStats`) e o da janela
      (cobre o pior comando, ~96 s)

### Plano de verificação

- `./gradlew integrationTest --tests "dio.budgeting.infrastructure.ai.SpeechCacheIT"` → passa sem mudança no teste
- `grep -n "spring.cache.caffeine.spec\|spring.cache.cache-names" src/main/resources/application.properties` →
  nenhuma ocorrência
- `grep -rln "Caffeine" src/main/java` → só `CachingConfiguration.java`
- `./gradlew build` → `BUILD SUCCESSFUL`

### Resumo da fase

Os caches `speech` e `voice-command-locks` agora são montados em Java: `CachingProperties` (`budgeting.cache.*`, com
`Policy(maxEntries, timeToLive)` validada) e `CachingConfiguration` com um
`CacheManagerCustomizer<CaffeineCacheManager>` que chama `registerCustomCache` com `maximumSize`, `expireAfterWrite` e
`recordStats`. Saíram `spring.cache.caffeine.spec` e `spring.cache.cache-names` do `application.properties`; entraram as
quatro propriedades com os comentários da `spec` e da janela. O manager continua estático.

Decisão de implementação: no Boot 4 o `CacheManagerCustomizer` está em `org.springframework.boot.cache.autoconfigure`
(não em `boot.autoconfigure.cache`). O `voice-command-locks` é registrado, mas ainda sem uso (o adaptador vem na fase 2).

Verificação: `SpeechCacheIT` passou sem mudança; nenhuma ocorrência de `spring.cache.caffeine.spec` ou
`spring.cache.cache-names`; `Caffeine` só em `CachingConfiguration.java`; `./gradlew build` → `BUILD SUCCESSFUL`.

## Fase 2: Trava do comando de voz pela chave de idempotência

Status: Em andamento  <!-- Não iniciada | Em andamento | Concluída -->

- [x] [TDD] `ProcessVoiceCommandUseCaseTest`: com chave, `acquire` lança `DuplicateVoiceCommandException` → a
      exceção sobe, sem interação com transcrição, assistente, voz e repositório (`IdempotencyKey`,
      `Optional<IdempotencyKey>` no `ProcessVoiceCommandInput`, porta `VoiceCommandLock`, exceção, `acquire` no
      início do `execute`)
- [x] [TDD] `ProcessVoiceCommandUseCaseTest`: com chave, falha em uma etapa → `release(userId, key)` chamado e a
      mesma exceção relançada
- [x] [TDD] `SpringCacheVoiceCommandLockTest` (unitário, com `ConcurrentMapCache` do Spring): mesma chave e mesmo
      áudio → `DuplicateVoiceCommandException`; mesma chave e outro áudio → `IdempotencyKeyReusedException`; depois do
      `release`, a mesma chave é aceita; a mesma chave de outro usuário é aceita
- [x] [TDD] `ErrorResponseIT`: dois envios com a mesma chave e o mesmo áudio → o segundo é `409`
      `duplicate-voice-command` (`title`, `detail`, sem `Retry-After`); mesma chave e outro áudio → `422`
      `idempotency-key-reused`; chave malformada → `400` `validation-error` com `parameter: "Idempotency-Key"`
      (`@RequestHeader` no controller, `ProblemType`, handler, `messages.properties`)
- [ ] [TDD] `VoiceCommandIdempotencyIT` (`@IntegrationTest`, `RANDOM_PORT` + `RestClient`, como o
      `TransactionProposalConcurrencyIT`; voz atrasada com `AiStub.givenDelayedSpeech`): dois envios simultâneos com
      a mesma chave e o mesmo áudio → um `200` e um `409` que chega antes de o primeiro terminar;
      `assistantCallCount() == 1`; uma proposta no banco
- [ ] [TDD] `VoiceCommandIdempotencyIT`: primeiro envio com `AiStub.givenFailure` (503) → o mesmo envio com a mesma
      chave em seguida → `200`

### Plano de verificação

- `./gradlew test --tests "dio.budgeting.application.ProcessVoiceCommandUseCaseTest"` → passa
- `./gradlew test --tests "dio.budgeting.infrastructure.cache.SpringCacheVoiceCommandLockTest"` → passa
- `./gradlew integrationTest --tests "dio.budgeting.infrastructure.http.VoiceCommandIdempotencyIT"` → passa
- `./gradlew integrationTest --tests "dio.budgeting.infrastructure.http.ErrorResponseIT"` → passa
- `git diff main --stat -- src/test` → nenhum teste existente alterado além do `ErrorResponseIT` e do
  `ProcessVoiceCommandUseCaseTest` (só casos novos)
- `./gradlew build` → `BUILD SUCCESSFUL`

### Resumo da fase

_(preencher ao concluir a fase)_

## Fase 3: ADR e documentação

Status: Não iniciada  <!-- Não iniciada | Em andamento | Concluída -->

- [ ] [verificação] `docs/adr/0009-chave-de-idempotencia-no-comando-de-voz.md` (a partir de `docs/adr/template.md`):
      critério (`Idempotency-Key` opcional, UUID, por usuário, com SHA-256 do áudio como fingerprint), trava no
      Caffeine com `putIfAbsent`, 409 imediato, 422 para chave reutilizada, janela de 2 min, liberação na falha;
      opções consideradas: só o hash do áudio (mesmos bytes não são a mesma intenção), MySQL, MongoDB com TTL, Redis
      (`SET NX EX`), guardar a resposta (idempotência ≠ cache); consequências: memória local por instância, perdida
      no reinício; em produção, Redis
- [ ] [verificação] `docs/openapi.yaml`: cabeçalho `Idempotency-Key` (opcional, `format: uuid`) no
      `POST /transactions/ai`; respostas `409` e `422` (`idempotency-key-reused` ao lado do `invalid-command`); `400`
      da chave malformada; descrições em pt-BR
- [ ] [verificação] `docs/exemplos/voz.http`: dois envios com a mesma chave fixa, o segundo `409`
- [ ] [verificação] `docs/arquitetura.md`: parágrafo da trava e o passo no diagrama de sequência do comando de voz
- [ ] [verificação] `README.md`: frase sobre o cabeçalho, o 409 e o 422; na linha `cache.gets`, só o filtro `cache="speech"`
- [ ] [verificação] `docs/escopo.md`: RES-06 → "Reenvio do mesmo comando com a mesma chave de idempotência (ex.:
      envio repetido por instabilidade da interface) não gera proposta duplicada nem nova cobrança de IA."
- [ ] [verificação] Regras: `architecture.md` (porta `VoiceCommandLock`, adaptador em `infrastructure/cache/`,
      Caffeine só no `CachingConfiguration`); `api-conventions.md` (cabeçalho `Idempotency-Key`); `error-handling.md`
      (linhas do 409 e do 422); `ai-integration.md` (linha do retry sobre o laço de tools: retry é do SDK, uma única
      chamada ao chat); `testing.md` (`VoiceCommandIdempotencyIT`, `SpringCacheVoiceCommandLockTest`, o
      `ResetCacheExtension` também zera as travas)
- [ ] [verificação] `CLAUDE.md`: `infrastructure/cache/` na estrutura; a linha do Spring Cache + Caffeine inclui a
      trava do comando de voz
- [ ] [verificação] `docs/tarefas.md`: nota da T10 na T17 com a conclusão; no último commit, checkbox da T17

### Plano de verificação

- `ls docs/adr/0009-*` → o ADR existe, com `status: aceito`
- `grep -n "Idempotency-Key" docs/openapi.yaml docs/exemplos/voz.http README.md .claude/rules/api-conventions.md` →
  ocorrências nos quatro
- `grep -n "duplicate-voice-command\|idempotency-key-reused" .claude/rules/error-handling.md` → as duas linhas
- `grep -n "RES-06" docs/escopo.md` → texto com a chave de idempotência
- `grep -n "\[x\] \[T17" docs/tarefas.md` → checkbox marcado
- `./gradlew build` → `BUILD SUCCESSFUL`

### Resumo da fase

_(preencher ao concluir a fase)_

## Resumo final

_(preencher ao concluir todas as fases)_
