# T15 — Circuit breaker e bulkhead

Aplicar um circuit breaker ao fluxo de voz inteiro e um limite de chamadas simultâneas (bulkhead) a cada etapa de IA,
com o Resilience4j, provando o comportamento com testes de falha (RES-03, RES-04, QA-03 parcial). A resiliência fica
desligada em todos os testes e só os testes dela a ligam. A tarefa também move a T24 (cache de consultas) para
melhorias futuras.

## Escopo

**Dentro:**
- Dependência `io.github.resilience4j:resilience4j-spring-boot4:2.4.0` no `build.gradle`; `resilience4j-circuitbreaker`
  e `resilience4j-bulkhead` explícitos só se o starter não os trouxer (conferido com `./gradlew dependencies`)
- Circuit breaker `voice-command`:
  - `@CircuitBreaker(name = "voice-command")` no `transcribe` do `TransactionController`
    (`infrastructure/http/`); o circuito aberto lança `CallNotPermittedException` antes do use case: sem trava de
    idempotência e sem IA
  - falha = só `AiProviderUnavailableException` (`record-exceptions`); `BulkheadFullException` ignorada
    (`ignore-exceptions`); qualquer outro desfecho conta como sucesso
  - `application.properties`, com comentário: `sliding-window-type=COUNT_BASED`, `sliding-window-size=10`,
    `minimum-number-of-calls=5`, `failure-rate-threshold=50`, `wait-duration-in-open-state=30s` (o comentário liga o
    valor ao `budgeting.errors.assistant-unavailable-retry-after=30s`), `permitted-number-of-calls-in-half-open-state=1`,
    `automatic-transition-from-open-to-half-open-enabled=false`
- Bulkhead por etapa:
  - `@Bulkhead` (semáforo) nos métodos de `SpringAiSpeechToTextGateway` (`speech-to-text`), `SpringAiAssistantGateway`
    (`assistant`) e `SpringAiTextToSpeechGateway` (`text-to-speech`)
  - `max-concurrent-calls=10` e `max-wait-duration=0` por instância, com comentário (o valor real seria calibrado pelo
    limite de requisições por minuto da conta na OpenAI e pela latência da etapa)
  - na síntese, o bulkhead fica dentro do `@Cacheable(sync = true)`: um acerto de cache e quem espera o `sync` não
    ocupam vaga; acertado pela ordem dos aspectos, com comentário do porquê
  - a `BulkheadFullException` passa intacta pelo `OpenAiExceptionTranslationAspect` e libera a trava de idempotência
    (já é o comportamento para qualquer `RuntimeException`)
- `ApiExceptionHandler`: `CallNotPermittedException` e `BulkheadFullException` → 503
  `urn:problem-type:budgeting:assistant-unavailable` com `Retry-After` de
  `budgeting.errors.assistant-unavailable-retry-after`, o mesmo corpo da `AiProviderUnavailableException`; logs sem
  stack trace:
  - `WARN "Circuit breaker open"` com `status` e `circuitBreaker` (`getCausingCircuitBreakerName()`)
  - `WARN "Bulkhead full"` com `status` e `bulkhead` (nome da instância, se a exceção o expuser; senão sem a chave)
- Métricas do Resilience4j no Micrometer pelo starter (`resilience4j.circuitbreaker.*`, `resilience4j.bulkhead.*`),
  em `/actuator/metrics` e no OTLP; health indicator desligado (padrão)
- Testes:
  - resiliência desligada em todos os `*IT`: `spring.autoconfigure.exclude` no `application-test.properties` com todas
    as auto-configurações do starter (lista conferida no `AutoConfiguration.imports` do jar), com comentário
  - `support/resilience/EnableResilience` (opt-in, no molde do `@AutoConfigureAiStub`):
    `@TestPropertySource(properties = "spring.autoconfigure.exclude=")` e
    `@ExtendWith(ResetCircuitBreakerExtension.class)`, que zera todos os circuitos do `CircuitBreakerRegistry` antes
    de cada teste
  - `AiStub` ganha `transcriptionCallCount()` e `givenDelayedTranscription(String, Duration)`, implementados no
    `OpenAiWireMockStub`
  - `AiBulkheadIT` (3 casos) e `VoiceCommandCircuitBreakerIT` (3 casos)
  - nenhum teste existente muda
- Documentação: `docs/openapi.yaml` (503), `README.md` (métricas), regras (`error-handling.md`, `ai-integration.md`,
  `architecture.md`, `testing.md`), `CLAUDE.md` (Stack), `docs/tarefas.md` (nota na T19, T24 para melhorias futuras,
  checkbox) e `docs/escopo.md` (CUS-02 → FUT-19)

**Fora:**
- ADR (decisões ficam neste plano e nas regras); o ADR 0003 não muda
- `docs/arquitetura.md`
- `Retry-After` dinâmico (tempo restante do circuito aberto)
- circuito por etapa; 429 ou `ProblemType` próprio para as recusas; espera no bulkhead
- health indicator do Resilience4j; log das mudanças de estado do circuito
- teste da recuperação (meio-aberto → fechado), dos valores de configuração, da `ignore-exceptions` e das métricas
- atualizar o benefício do RES-04 no `docs/escopo.md`
- deploy; chamada à OpenAI real em qualquer teste ou verificação

## Decisões

- Resilience4j com `resilience4j-spring-boot4:2.4.0`, não `@ConcurrencyLimit` do Spring 7 + Spring Cloud Circuit
  Breaker — uma biblioteca para os dois mecanismos, configuração por properties e as métricas pedidas na nota da T22
  (decisão do usuário)
- O bulkhead aqui não protege threads (as requisições rodam em virtual threads): limita as chamadas simultâneas ao
  provedor por etapa (evita rajadas de 429 que abririam o circuito) e recusa na hora o excesso, liberando memória e
  conexões (explicado ao usuário)
- Um circuito para o fluxo de voz inteiro, não por gateway — falha antes da primeira chamada à IA e não dilui a taxa
  de falha com os sucessos das outras etapas (nota da T09, revisão do escopo #23)
- `@CircuitBreaker` no `transcribe` do `TransactionController` — uma anotação em `infrastructure`, sem classe nova;
  na `application` faria a camada importar o Resilience4j, o que o projeto evita (decisão do usuário)
- Só `AiProviderUnavailableException` conta como falha — é a falha transitória que sobra depois dos retries do SDK;
  4xx do nosso lado (`AiProviderFailedException`) não se resolvem com espera; timeout já chega como falha, sem
  contagem de chamadas lentas (decisão do usuário)
- `Retry-After` fixo, igual ao tempo de circuito aberto (30 s) — sem código novo; o dinâmico teria pouco ganho
  (decisão do usuário)
- Configuração do circuito: `COUNT_BASED`, 10/5/50%, 30 s aberto, 1 chamada no meio-aberto — previsível com pouco
  tráfego e fácil de testar; "por hora" (decisão do usuário)
- Bulkhead de semáforo, 10 por etapa, sem espera; dentro do `@Cacheable(sync = true)` na síntese — "por hora"
  (decisão do usuário)
- Recusa do bulkhead → o mesmo 503 `assistant-unavailable` com `Retry-After` — para o cliente, o assistente está
  temporariamente indisponível; 429 é do rate limiting por usuário (T19) (decisão do usuário)
- `BulkheadFullException` ignorada pelo circuito — o provedor não foi chamado; contada como sucesso, diluiria a taxa
  de falha justamente quando o provedor fica lento (decisão do usuário)
- Resiliência desligada em todos os testes, ligada só pelos testes dela — o usuário não quer que circuit breaker,
  bulkhead e, depois, o rate limiter influenciem de forma alguma qualquer outro teste; o estado `DISABLED` não basta
  (o aspecto continua no caminho e o bulkhead não tem estado desligado), então as auto-configurações do starter são
  excluídas no perfil `test` e a `@EnableResilience` as devolve, mantendo as anotações (decisão do usuário)
- `WARN` por recusa, com mensagens próprias (`Circuit breaker open`, `Bulkhead full`), sem stack trace e sem log das
  mudanças de estado — padrão do `AI provider unavailable`; o estado aparece na métrica (decisão do usuário)
- Métricas ligadas; health indicator desligado — com o circuito aberto, a instância sairia da prontidão e derrubaria
  os endpoints manuais (mesmo motivo da OpenAI fora da readiness na T18) (decisão do usuário)
- Sem ADR (decisão do usuário)
- Sem `docs/arquitetura.md` (decisão do usuário)
- T24 (CUS-02) vira FUT-19, na Fase 3 desta tarefa, não num PR próprio; motivo: o foco atual é evoluir a
  funcionalidade e não há necessidade real no momento (decisão do usuário)

## Definição de pronto

- [ ] Com 5 falhas transitórias, o comando seguinte responde 503 `assistant-unavailable` com `Retry-After: 30` sem
      chamar a IA (`VoiceCommandCircuitBreakerIT`)
- [ ] Falhas definitivas não abrem o circuito (`VoiceCommandCircuitBreakerIT`)
- [ ] Etapa cheia responde 503 `assistant-unavailable` com `Retry-After: 30`; quem espera o cache da síntese não
      ocupa vaga (`AiBulkheadIT`)
- [ ] Os dois `WARN` sem stack trace (`Circuit breaker open`, `Bulkhead full`)
- [ ] Nenhum teste existente alterado; resiliência desligada fora dos testes com `@EnableResilience`
- [ ] Métricas `resilience4j.circuitbreaker.state` e `resilience4j.bulkhead.available.concurrent.calls` em
      `/actuator/metrics`
- [ ] T24 em melhorias futuras (FUT-19) e documentação atualizada
- [ ] `./gradlew build` passa

## Para futuros agentes

Execute com `/executar-tarefa`. Durante a execução:

- marque `- [x]` no mesmo commit que conclui o item;
- antes do último commit de uma fase, rode o plano de verificação, escreva o resumo da fase com o resultado e
  mude o status para `Concluída` — tudo nesse mesmo commit;
- na última fase, no mesmo commit, confira a definição de pronto e escreva o resumo final;
- mudança no plano só com aprovação do usuário, registrada em Decisões com o motivo.

## Fase 1: Resilience4j desligado nos testes e bulkhead por etapa

Status: Concluída  <!-- Não iniciada | Em andamento | Concluída -->

- [x] [verificação] `build.gradle`: `resilience4j-spring-boot4:2.4.0`; `resilience4j-circuitbreaker` e
      `resilience4j-bulkhead` explícitos só se não vierem pelo starter
- [x] [verificação] `application-test.properties`: `spring.autoconfigure.exclude` com todas as auto-configurações do
      starter (lista do `META-INF/spring/...AutoConfiguration.imports` do jar) e comentário do porquê (resiliência
      não influencia nenhum teste; quem precisa usa `@EnableResilience`)
- [x] [verificação] `support/resilience/EnableResilience`: `@TestPropertySource(properties =
      "spring.autoconfigure.exclude=")`
- [x] [TDD] `AiBulkheadIT` (`@IntegrationTest(webEnvironment = RANDOM_PORT)`, `@AutoConfigureAiStub`,
      `@EnableResilience`, `@TestPropertySource` com `speech-to-text` e `text-to-speech` em 1 vaga; `RestClient` e
      chamadas sobrepostas como no `VoiceCommandIdempotencyIT`):
      `should_rejectWithServiceUnavailable_when_stageIsFull` — transcrição atrasada
      (`AiStub.givenDelayedTranscription`, implementado no `OpenAiWireMockStub`) e 2 comandos simultâneos → um 200 e
      um 503 `assistant-unavailable` com `Retry-After: 30` (`@Bulkhead` nos três `SpringAi*Gateway`, instâncias em
      `resilience4j.bulkhead.instances.*` no `application.properties` com comentário, `BulkheadFullException` no
      handler do 503)
- [x] [TDD] `AiBulkheadIT`: `should_logBulkheadFullAsWarning_when_stageIsFull` — `WARN "Bulkhead full"` com
      `status=503` (e `bulkhead`, se a exceção expuser o nome), sem stack trace (`handleExceptionInternal`)
- [x] [TDD] `AiBulkheadIT`: `should_notHoldSpeechPermit_when_waitingForCachedSpeech` — chama a porta
      `TextToSpeechGateway` direto (como o `SpeechCacheIT`), síntese atrasada, 2 chamadas simultâneas com o mesmo
      texto → as duas devolvem o áudio e `speechCallCount() == 1` (ordem dos aspectos: bulkhead dentro do cache, por
      `@EnableCaching(order = ...)` ou `resilience4j.bulkhead.bulkhead-aspect-order`, com comentário); confirmar que
      falha com a ordem invertida

### Plano de verificação

- `./gradlew dependencies --configuration runtimeClasspath | grep resilience4j` → `resilience4j-circuitbreaker`,
  `resilience4j-bulkhead` e `resilience4j-micrometer` 2.4.0
- `./gradlew integrationTest --tests "dio.budgeting.infrastructure.ai.AiBulkheadIT"` → passa
- `git diff main --stat -- src/test/java/dio/budgeting/infrastructure` → só o `AiBulkheadIT` novo
- `./gradlew build` → `BUILD SUCCESSFUL`

### Resumo da fase

- `resilience4j-spring-boot4:2.4.0` entrou no `build.gradle`; o starter já traz `circuitbreaker`, `bulkhead` e
  `micrometer` (2.4.0), sem dependências explícitas.
- `application-test.properties` exclui as 22 auto-configurações do starter (lista do `AutoConfiguration.imports` do
  jar); `@EnableResilience` as devolve com `@TestPropertySource(properties = "spring.autoconfigure.exclude=")`. A
  `ResetCircuitBreakerExtension` fica para a Fase 2.
- `@Bulkhead` (`speech-to-text`, `assistant`, `text-to-speech`) nos três `SpringAi*Gateway`; instâncias com 10 chamadas
  e sem espera no `application.properties`. `BulkheadFullException` entrou no handler do 503
  (`handleAssistantUnavailable`) e tem `WARN "Bulkhead full"` com `status` e `bulkhead`, sem stack trace; as duas
  exceções expõem o nome da instância (`getBulkheadName()`, `getCausingCircuitBreakerName()`).
- Ordem dos aspectos na síntese: o bulkhead do Resilience4j tem ordem padrão `LOWEST_PRECEDENCE - 1` (lida no
  bytecode de `BulkheadConfigurationProperties`) e o cache, `LOWEST_PRECEDENCE`, então o bulkhead ficava por fora e
  recusava quem esperava o `sync`. Fica `@EnableCaching(order = Ordered.LOWEST_PRECEDENCE - 2)`, um passo por fora do
  bulkhead, com comentário. O teste falhou com `BulkheadFullException` na ordem padrão (RED) e passa com o ajuste.
  Alternativas descartadas pelo usuário: colaborador separado com o `@Bulkhead` (muda o plano) e valores de ordem
  arbitrários.
- `AiStub` ganhou `givenDelayedTranscription`; `transcriptionCallCount()` fica para a Fase 2, onde é usado.
- Verificação: dependências conferidas; `AiBulkheadIT` (3 casos) e `SpeechCacheIT` passam; `git diff main --stat --
  src/test/java/dio/budgeting/infrastructure` mostra só o `AiBulkheadIT` novo; `./gradlew build` → `BUILD SUCCESSFUL`.
- Para uma sessão nova: uma execução do `should_logBulkheadFullAsWarning_when_stageIsFull` falhou uma vez com
  `ResourceAccessException: I/O error on POST ... : null` no cliente do teste; as 4 rodadas seguintes e o build
  passaram, e a causa-raiz não foi investigada. Se voltar a aparecer, ler o `Caused by` completo do relatório.

## Fase 2: Circuit breaker do fluxo de voz

Status: Em andamento  <!-- Não iniciada | Em andamento | Concluída -->

- [x] [TDD] `VoiceCommandCircuitBreakerIT` (`@IntegrationTest`, `@AutoConfigureAiStub`, `@EnableResilience`, MockMvc):
      `should_rejectWithoutCallingAi_when_circuitIsOpen` — 5 comandos com falha transitória na transcrição, depois a IA
      saudável → 503 `assistant-unavailable` com `Retry-After: 30` e `transcriptionCallCount()` sem aumentar
      (`AiStub.transcriptionCallCount()`; `@CircuitBreaker(name = "voice-command")` no `transcribe` — se o aspecto não
      se aplicar ao método package-private, torná-lo `public`; `resilience4j.circuitbreaker.instances.voice-command.*`
      com comentário; `CallNotPermittedException` no handler do 503; `ResetCircuitBreakerExtension` registrada pela
      `@EnableResilience`)
- [x] [TDD] `VoiceCommandCircuitBreakerIT`: `should_keepCircuitClosed_when_providerFailsDefinitively` — 5 comandos com
      `REQUEST_REJECTED` (500), depois um comando válido → 200 (`record-exceptions` com
      `AiProviderUnavailableException`)
- [x] [TDD] `VoiceCommandCircuitBreakerIT`: `should_logCircuitOpenAsWarning_when_circuitIsOpen` —
      `WARN "Circuit breaker open"` com `status=503` e `circuitBreaker=voice-command`, sem stack trace
- [ ] [verificação] `ignore-exceptions` com `BulkheadFullException` na instância `voice-command`, com comentário (o
      provedor não foi chamado; como sucesso, diluiria a taxa de falha)

### Plano de verificação

- `./gradlew integrationTest --tests "dio.budgeting.infrastructure.http.VoiceCommandCircuitBreakerIT"` → passa
- `./gradlew integrationTest --tests "dio.budgeting.infrastructure.ai.AiBulkheadIT"` → passa
- `git diff main --stat -- src/test/java/dio/budgeting/infrastructure` → só o `AiBulkheadIT` e o
  `VoiceCommandCircuitBreakerIT` novos
- Com `set -a && source .env && set +a && ./gradlew bootRun` em segundo plano (MySQL, MongoDB e LGTM pelo compose):
  `curl -s localhost:8080/actuator/metrics/resilience4j.circuitbreaker.state | jq -r .name` →
  `resilience4j.circuitbreaker.state`; `curl -s localhost:8080/actuator/metrics/resilience4j.bulkhead.available.concurrent.calls | jq -r .name`
  → `resilience4j.bulkhead.available.concurrent.calls`; encerrar a aplicação em seguida (nenhum comando de voz é
  enviado)
- `./gradlew build` → `BUILD SUCCESSFUL`

### Resumo da fase

_(preencher ao concluir a fase)_

## Fase 3: Documentação e T24 para melhorias futuras

Status: Não iniciada  <!-- Não iniciada | Em andamento | Concluída -->

- [ ] [verificação] `docs/openapi.yaml`: descrição do 503 do `POST /transactions/ai` cobre o circuito aberto e a
      capacidade esgotada
- [ ] [verificação] `README.md`, seção "Métricas": linhas de `resilience4j.circuitbreaker.state` e
      `resilience4j.bulkhead.available.concurrent.calls`, com o nome no Prometheus
- [ ] [verificação] Regras: `error-handling.md` (linhas da `CallNotPermittedException` e da `BulkheadFullException`
      → 503 com `Retry-After`, os dois `WARN`); `ai-integration.md` (bulkhead por gateway com `@Bulkhead`, dentro do
      `@Cacheable` na síntese; circuito no fluxo inteiro e o que conta como falha); `architecture.md` (`@CircuitBreaker`
      no `transcribe` do controller, `@Bulkhead` nos adaptadores, nunca na `application`); `testing.md` (resiliência
      desligada em todos os `*IT` pela exclusão no perfil `test`; `@EnableResilience` liga e zera os circuitos;
      `AiBulkheadIT`, `VoiceCommandCircuitBreakerIT`; `transcriptionCallCount()` e `givenDelayedTranscription`)
- [ ] [verificação] `CLAUDE.md`: Stack ganha o Resilience4j (circuit breaker do fluxo de voz e bulkhead por etapa,
      métricas no Micrometer)
- [ ] [verificação] `docs/tarefas.md`: nota da T15 na T19 (o rate limiter segue o mesmo padrão: desligado nos testes,
      ligado pela `@EnableResilience`); T24 sai do sumário, da seção e da rastreabilidade (`CUS-01 → T23 · CUS-03 →
      T25`)
- [ ] [verificação] `docs/escopo.md`: a linha CUS-02 sai da seção de custo e desempenho; entra a FUT-19 — "Cache de
      consultas" | "Nos endpoints convencionais (sem IA), resultados de consultas reaproveitados e invalidados quando
      uma transação é registrada (cadastro manual ou confirmação de proposta)." | "O foco atual é evoluir a
      funcionalidade, e no momento não há necessidade real de reaproveitar os resultados das consultas."
- [ ] [verificação] `docs/tarefas.md`: no último commit, checkbox da T15

### Plano de verificação

- `grep -n "T24\|CUS-02" docs/tarefas.md docs/escopo.md` → nenhuma ocorrência
- `grep -n "FUT-19" docs/escopo.md` → a linha do cache de consultas
- `grep -n "CallNotPermittedException\|BulkheadFullException" .claude/rules/error-handling.md` → as duas linhas
- `grep -n "EnableResilience" .claude/rules/testing.md docs/tarefas.md` → ocorrências nos dois
- `grep -n "resilience4j" README.md` → as duas métricas
- `grep -n "\[x\] \[T15" docs/tarefas.md` → checkbox marcado
- `./gradlew build` → `BUILD SUCCESSFUL`

### Resumo da fase

_(preencher ao concluir a fase)_

## Resumo final

_(preencher ao concluir todas as fases)_
