# T25 — Cache da síntese de voz

Reaproveitar o áudio já sintetizado para um mesmo texto de resposta, em cache local da instância, com TTL e limite de
tamanho, pela abstração de cache do Spring, de modo que a troca futura por um cache distribuído mude só dependência e
configuração. Registrar a estratégia em ADR.

## Escopo

**Dentro:**
- `build.gradle`: `spring-boot-starter-cache` e `com.github.ben-manes.caffeine:caffeine` (versão gerenciada pelo Boot)
- `infrastructure/config/CachingConfiguration` — `@Configuration` + `@EnableCaching`, vazia, no padrão de
  `AsyncConfiguration` (`@EnableAsync`) e `SchedulingConfiguration` (`@EnableScheduling`); nenhum bean nem import do
  Caffeine em Java
- `SpringAiTextToSpeechGateway.synthesize` — `@Cacheable(cacheNames = "speech", sync = true)` (cache-aside): chave = o
  texto completo vindo do `SpokenAnswers`, valor = o `byte[]` do áudio. Porta `TextToSpeechGateway`,
  `ProcessVoiceCommandUseCase` e `SpokenAnswers` não mudam
- `application.properties`, com comentário do porquê:
  - `spring.cache.type=caffeine`
  - `spring.cache.cache-names=speech` (cache criado na subida: entra nas métricas e um nome errado no `@Cacheable` falha)
  - `spring.cache.caffeine.spec=maximumSize=500,expireAfterWrite=1h,recordStats` — tamanho limita a memória (dezenas
    de KB por áudio → pior caso na casa de dezenas de MB); TTL limita por quanto tempo a frase de proposta (descrição e
    valor ditados) fica em memória e custa no máximo uma síntese por frase quente por hora por instância;
    `recordStats` faz o Actuator publicar `cache.gets{cache=speech,result=hit|miss}`, `cache.size`, `cache.evictions`
- Testes (infra em `src/test/java/dio/budgeting/support/`):
  - `support/cache/ResetCacheExtension` — `BeforeEachCallback` que limpa todos os caches pelo `CacheManager` do
    contexto (como o `ResetAiStubExtension` lê o `AiStub`), sem `*Cleaner`; registrado no `@ExtendWith` do
    `@IntegrationTest` (os `*IT` compartilham o contexto, e o cache quebraria as asserções de `speechText()`)
  - `AiStub.speechCallCount()` (par do `assistantCallCount()`) e
    `AiStub.givenDelayedSpeech(byte[] audio, Duration delay)` (WireMock `withFixedDelay`), implementados no
    `OpenAiWireMockStub`
  - `infrastructure/ai/SpeechCacheIT` (`@IntegrationTest`, `@AutoConfigureAiStub`), chamando a porta
    `TextToSpeechGateway` direto, sem HTTP:
    - `should_synthesizeOnce_when_sameTextIsSynthesizedTwice` — uma síntese no stub, mesmo áudio nas duas chamadas
    - `should_synthesizeEachText_when_textsDiffer` — duas sínteses
    - `should_synthesizeAgain_when_previousSynthesisFailed` — `givenFailure(SPEECH, …)` → exceção da porta; depois
      `givenSpeech` → a chamada seguinte vai ao provedor (`speechCallCount() == 2`)
    - `should_synthesizeOnce_when_sameTextIsSynthesizedConcurrently` — `givenDelayedSpeech` (~500 ms), N virtual
      threads liberadas juntas (latch) com a mesma frase → `speechCallCount() == 1` e o mesmo áudio para todas
  - `AiUsageMeasurementIT.should_notMeasureSpeech_when_speechComesFromCache` — dois comandos de voz com a mesma frase
    falada, mesmo usuário; depois de o `ASSISTANT` do segundo comando chegar (padrão do `testing.md` para ausência),
    há um único documento `SPEECH` do usuário
- ADR `docs/adr/0008-cache-local-da-sintese-de-voz.md` (MADR minimal, a partir de `docs/adr/template.md`), título
  "Guardar o áudio sintetizado em cache local da instância, pela abstração de cache do Spring":
  - opções: Caffeine local; Redis; MongoDB com índice TTL
  - decisão e motivo: simplicidade adequada ao momento do projeto (uma instância, frases curtas), sem serviço novo,
    e a troca para distribuído é só dependência e propriedades
  - consequências: memória do heap limitada por quantidade (não por bytes: `weigher` exigiria bean do Caffeine em
    Java); em várias instâncias, cada uma aquece o próprio cache (duplicação e hit ratio menor); cache perdido no
    reinício; W-TinyLFU protege as frases quentes das de proposta, por isso todo texto é guardado; consumo da T23
    passa a refletir só as sínteses reais
  - `sync = true`: no Caffeine, cálculo atômico por chave contra o stampede; falha não é gravada e quem esperava tenta
    de novo; chaves diferentes só se bloqueiam se caírem no mesmo compartimento interno do mapa (raro, só espera);
    restrições do `sync` (um cache, sem `unless`)
  - caminho para cache distribuído (só registrado, sem item FUT): trocar dependência e `spring.cache.type=redis` +
    `spring.cache.redis.time-to-live`; acrescentar `CacheErrorHandler` (via `CachingConfigurer`) para a falha do Redis
    contar como miss e decidir o health; o `sync` não tem efeito no Redis (writer padrão sem lock) e o writer com lock
    tranca o cache inteiro (`speech~lock`), então o stampede volta a ser decisão própria; as opções do modelo de voz
    (modelo, voz, velocidade) não estão na chave — hoje o reinício zera o cache, num cache distribuído passariam a
    importar
- Documentação:
  - `CLAUDE.md`: stack ganha Spring Cache + Caffeine (cache local da síntese de voz); estrutura — `config/` cita
    `CachingConfiguration`
  - `.claude/rules/architecture.md`: cache é detalhe do adaptador (`@Cacheable` no adaptador, nunca na porta nem no
    use case), configuração só em `application.properties`, sem tipo do provedor de cache em Java
  - `.claude/rules/ai-integration.md`: a síntese passa pelo cache `speech` (chave = texto do `SpokenAnswers`,
    `sync = true`, TTL e tamanho no properties, ver ADR 0008); testes do cache no `SpeechCacheIT`
  - `.claude/rules/testing.md`: `ResetCacheExtension` no `@IntegrationTest`; `speechCallCount()` e
    `givenDelayedSpeech` no `AiStub`; `SpeechCacheIT` chama a porta e o de concorrência falha sem o `sync`
  - `README.md`: linha `cache.gets` na tabela de métricas (nome no Prometheus `cache_gets_total`, tags `cache` e
    `result`)
  - `docs/arquitetura.md`: na seção "Fluxo de voz", a síntese reaproveita o áudio de uma frase já sintetizada
- `docs/tarefas.md`: checkbox da T25

**Fora:**
- Redis ou qualquer cache distribuído, `CacheErrorHandler`, health do cache — só na ADR, sem item FUT (decisão do
  usuário)
- limite por bytes (`maximumWeight` + `weigher`)
- teste de métricas do cache (é configuração, `testing.md`)
- dashboard e alertas (FUT-13)
- pré-aquecimento das frases fixas
- opções do modelo de voz na chave
- cache de consultas (T24)
- verificação com a OpenAI real e medição do tamanho real dos MP3
- deploy

## Decisões

- Cache local com Caffeine pela abstração do Spring, não Redis nem MongoDB — mais simples e adequado ao momento; a
  troca para distribuído fica em dependência e propriedades (decisão do usuário, revendo a escolha inicial do Redis)
- Cache-aside com `@Cacheable` no adaptador `SpringAiTextToSpeechGateway` — cache é detalhe de infraestrutura, como
  timeout e retry do SDK; porta, use case e `SpokenAnswers` intactos
- `sync = true` contra stampede — no Caffeine é atômico por chave, sem lock global; custa um atributo (decisão do
  usuário)
- `maximumSize=500,expireAfterWrite=1h,recordStats`, só em propriedades (decisão do usuário)
- Todo texto vai para o cache, inclusive frases de proposta — o escopo pede "mesmo texto de resposta" e o W-TinyLFU
  protege as frases quentes
- Evolução para cache distribuído só na ADR, sem item FUT no `escopo.md` (decisão do usuário)
- Teste de concorrência na porta, sem HTTP — mais simples e determinístico que requisições paralelas no MockMvc
  (decisão do usuário)
- Nomes revisados com o usuário: `CachingConfiguration` (padrão `@EnableX` → `XConfiguration`), `givenDelayedSpeech`
  (sobrecarga com `Duration` não dizia o que era), `SpeechCacheIT` (funcionalidade, como `AiUsageMeasurementIT`),
  cache `speech` (vocabulário do projeto), `ResetCacheExtension` em `support/cache/` (padrão `Reset<X>Extension`)
- Tamanho dos MP3 estimado (dezenas de KB), não medido — sem a chave da OpenAI
- Duas fases; tipo `feat`; branch `feat/cache-da-sintese-de-voz`

## Definição de pronto

- [ ] `./gradlew build` passa (Spotless, Checkstyle, testes e cobertura)
- [ ] a mesma frase sintetizada duas vezes chama o provedor uma vez e devolve o mesmo áudio
- [ ] frases diferentes não compartilham áudio; uma síntese que falhou não fica em cache
- [ ] chamadas concorrentes com a mesma frase chamam o provedor uma vez; o teste falha sem `sync = true`
- [ ] um acerto do cache não gera documento `SPEECH` em `ai_usage_entries`
- [ ] os testes de falha da síntese do `VoiceCommandIT` continuam passando (exceções atravessam o `sync` sem embrulho)
- [ ] nenhum import do Caffeine em `src/main/java`
- [ ] ADR 0008 aceito; `CLAUDE.md`, `architecture.md`, `ai-integration.md`, `testing.md`, `README.md`,
  `docs/arquitetura.md` e `tarefas.md` atualizados como no escopo

## Para futuros agentes

Execute com `/executar-tarefa`. Durante a execução:

- marque `- [x]` no mesmo commit que conclui o item;
- antes do último commit de uma fase, rode o plano de verificação, escreva o resumo da fase com o resultado e
  mude o status para `Concluída` — tudo nesse mesmo commit;
- na última fase, no mesmo commit, confira a definição de pronto e escreva o resumo final (e o plano de deploy,
  se houver);
- mudança no plano só com aprovação do usuário, registrada em Decisões com o motivo.

Fatos conferidos durante o planejamento (reconfirmar ao implementar):
- Spring Boot 4.1: `spring.cache.type`, `spring.cache.cache-names`, `spring.cache.caffeine.spec`; com `recordStats` o
  Actuator registra as métricas dos caches criados na subida
- `@Cacheable(sync = true)` chama `Cache.get(key, valueLoader)`; no `CaffeineCache` isso é `cache.get(key, fn)`,
  atômico por chave; exceção do loader volta ao chamador sem embrulho (o `CacheInterceptor` desembrulha)
- `OpenAiExceptionTranslationAspect` usa `execution(* dio.budgeting.application.*Gateway+.*(..))`, então continua no
  `synthesize` do adaptador junto com o interceptor de cache
- Spring Data Redis 4.0 (para a ADR): `RedisCache.get(key, loader)` sem lock no writer padrão; o writer com lock usa
  uma única chave `<cache>~lock` por cache e, por padrão, sem TTL
- Os testes que passam de primeira (frases diferentes, falha não gravada, consumo) protegem a chave e o cache-aside;
  confira-os removendo o `@Cacheable` ou trocando a chave

## Fase 1: Cache da síntese de voz

Status: Em andamento  <!-- Não iniciada | Em andamento | Concluída -->

- [x] [verificação] `AiStub.speechCallCount()` e `AiStub.givenDelayedSpeech(byte[], Duration)` no
  `OpenAiWireMockStub` — `VoiceCommandIT` continua passando
- [x] [TDD] `SpeechCacheIT.should_synthesizeOnce_when_sameTextIsSynthesizedTwice` — implementar dependências,
  `CachingConfiguration`, propriedades `spring.cache.*`, `@Cacheable(cacheNames = "speech")` no
  `SpringAiTextToSpeechGateway` e `ResetCacheExtension` no `@IntegrationTest`
- [ ] [TDD] `SpeechCacheIT.should_synthesizeEachText_when_textsDiffer`
- [ ] [TDD] `SpeechCacheIT.should_synthesizeAgain_when_previousSynthesisFailed`
- [ ] [TDD] `SpeechCacheIT.should_synthesizeOnce_when_sameTextIsSynthesizedConcurrently` — acrescentar `sync = true`
- [ ] [TDD] `AiUsageMeasurementIT.should_notMeasureSpeech_when_speechComesFromCache`

### Plano de verificação

- `./gradlew integrationTest --tests "dio.budgeting.infrastructure.ai.SpeechCacheIT"` → `BUILD SUCCESSFUL`, 4 testes
- `./gradlew integrationTest --tests "dio.budgeting.infrastructure.ai.AiUsageMeasurementIT"` → `BUILD SUCCESSFUL`,
  3 testes
- `./gradlew integrationTest --tests "dio.budgeting.infrastructure.http.VoiceCommandIT"` → `BUILD SUCCESSFUL`
- `grep -rn "caffeine" -i src/main/java` → nenhuma linha
- `./gradlew build` → `BUILD SUCCESSFUL`

### Resumo da fase

_(preencher ao concluir a fase)_

## Fase 2: ADR e documentação

Status: Não iniciada  <!-- Não iniciada | Em andamento | Concluída -->

- [ ] [verificação] ADR `docs/adr/0008-cache-local-da-sintese-de-voz.md`, status `aceito`, com o conteúdo do escopo
- [ ] [verificação] `CLAUDE.md`: stack e estrutura (`config/`)
- [ ] [verificação] `.claude/rules/architecture.md`: cache como detalhe do adaptador
- [ ] [verificação] `.claude/rules/ai-integration.md`: cache `speech` na síntese e seus testes
- [ ] [verificação] `.claude/rules/testing.md`: `ResetCacheExtension`, `speechCallCount`, `givenDelayedSpeech`,
  `SpeechCacheIT`
- [ ] [verificação] `README.md`: linha `cache.gets` na tabela de métricas
- [ ] [verificação] `docs/arquitetura.md`: reaproveitamento do áudio no fluxo de voz
- [ ] [verificação] `docs/tarefas.md`: checkbox da T25 (último commit)

### Plano de verificação

- `ls docs/adr/0008-cache-local-da-sintese-de-voz.md` → arquivo existe; `grep -n "^status: aceito" ` nele → uma linha
- `grep -rln "Caffeine\|CachingConfiguration\|ResetCacheExtension\|SpeechCacheIT" CLAUDE.md .claude/rules/` →
  `CLAUDE.md`, `architecture.md`, `ai-integration.md`, `testing.md`
- `grep -n "cache.gets" README.md` → uma linha na tabela de métricas
- `grep -n "\[x\] \[T25" docs/tarefas.md` → uma linha
- `./gradlew build` → `BUILD SUCCESSFUL` (Spotless cobre os `.md`)

### Resumo da fase

_(preencher ao concluir a fase)_

## Resumo final

_(preencher ao concluir todas as fases)_
