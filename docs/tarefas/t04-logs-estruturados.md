# T04 — Logs estruturados e proteção de dados nos logs

Adotar logs estruturados em JSON (ECS) com um ID de correlação por requisição, vindo do tracing do Micrometer com
OpenTelemetry e devolvido ao cliente no `X-Request-Id` (OBS-01). Impedir que transcrições, prompts, respostas do
modelo, tokens e dados pessoais cheguem aos logs (SEG-06).

## Escopo

**Dentro:**
- `spring-boot-starter-opentelemetry` e `spring-boot-starter-actuator` no `build.gradle`
- logs em JSON no formato ECS (`logging.structured.format.console=ecs`) em todos os ambientes, inclusive
  `bootRun` e testes
- `traceId`/`spanId` do Micrometer Tracing no MDC, presentes em toda linha de log da requisição; `traceparent`
  (W3C) recebido é continuado
- filtro em `infrastructure/http` que devolve o `traceId` no cabeçalho `X-Request-Id` de toda resposta; um
  `X-Request-Id` enviado pelo cliente é ignorado
- exportação de traces desligada enquanto nenhum endpoint OTLP estiver configurado (sem coletor nem container
  novo); endpoint configurável por variável de ambiente
- remoção de `logging.level.org.springframework.ai=DEBUG` e `spring.jpa.show-sql=true`
- opções de observação do Spring AI que escrevem conteúdo no log explicitamente em `false`
- eventos `INFO` com pares chave-valor (`log.atInfo().addKeyValue(...)`), só com metadados:
  - `TransactionController` (fluxo de voz): `Voice command received` (`audioSizeBytes`, `audioContentType`),
    `Audio transcribed` (`transcriptionLength`), `Assistant answered` (`answerLength`), `Speech synthesized`
    (`speechSizeBytes`)
  - `PersistTransactionUseCase`: `Transaction persisted` (`transactionId`)
  - `ListTransactionsByCategoryUseCase`: `Transactions listed` (`resultCount`)
- testes: `RequestCorrelationIT` (novo) e `VoiceCommandIT` (eventos e ausência de conteúdo), com
  `OutputCaptureExtension`
- regra "Logs" em `.claude/rules/error-handling.md`; ajustes em `api-conventions.md`, `ai-integration.md`,
  `CLAUDE.md` e `README.md`
- ADR `docs/adr/0002-observabilidade-e-correlacao.md`
- FUT-15 e FUT-16 em `docs/escopo.md` (Melhorias futuras)

**Fora:**
- access log na aplicação — coberto pela métrica `http.server.requests` (Actuator) e pelos spans
- `@RestControllerAdvice` e log de exceções em código (T05, que aplica a regra escrita aqui); até lá, um 500 é
  logado pelo Tomcat sem `traceId`
- exposição de métricas e Prometheus (T22)
- configuração de health e do Actuator (T18); fica o padrão do Actuator (só `/actuator/health` exposto)
- coletor local, X-Ray e ADOT (FUT-16)
- `x-fapi-interaction-id` no padrão Open Finance (FUT-15)
- mascaramento automático por padrão (CPF, chaves) no encoder
- IP do cliente nos logs (reavaliado na T19)
- mover os logs do fluxo de voz do controller para a aplicação (T09)

## Decisões

- Tracing com Micrometer Tracing + OpenTelemetry (`spring-boot-starter-opentelemetry`), exportação OTLP neutra de
  fornecedor — forma idiomática do Spring Boot 4; os SDKs clássicos do X-Ray estão em manutenção desde
  25/02/2026 e a AWS recomenda OpenTelemetry (ADOT). Em produção: app → OTLP → ADOT Collector / CloudWatch Agent →
  X-Ray, sem código específico do X-Ray (decisão do usuário: não reinventar a roda)
- `traceId` é o ID de correlação; sem `correlationId` próprio — evita dois IDs paralelos
- Cabeçalho de resposta `X-Request-Id` com o valor do `traceId` (convenção de mercado: `X-Request-Id`,
  `x-request-id` da OpenAI, `Request-Id` da Stripe); entrada só pelo `traceparent`. `x-fapi-interaction-id` (Open
  Finance, gerado pelo cliente, sobrevive a timeout) foi recomendado e preterido pelo usuário; vira a FUT-15
- Formato ECS — o mais adotado dos três nativos do Boot, incorporado às convenções do OpenTelemetry, aceito por
  CloudWatch, Datadog, Elastic e Loki; JSON em todos os ambientes, sem perfil `local`, para o `*IT` verificar a
  configuração real (decisão do usuário); leitura local com `jq`
- Sem exportação local de traces nem coletor (`grafana/otel-lgtm` vira FUT-16) — mínimo necessário para a tarefa
  (decisão do usuário)
- Conteúdo nunca nos logs — transcrição, prompt, resposta do modelo, texto sintetizado, argumentos de tools e
  áudio: texto livre com dados pessoais e sensíveis imprevisíveis (LGPD, art. 5º, II e art. 6º, III), dado
  financeiro sob sigilo; mascaramento não é confiável em texto livre. O conteúdo vai para a auditoria (T20)
  (decisão do usuário)
- Dados de negócio também fora dos logs (descrição, valor, categoria) — ficam no banco, alcançáveis pelo ID; a
  categoria ligada ao usuário (T08) revela hábitos (`PHARMA`). Logs só com IDs e metadados técnicos (tamanhos,
  contagens)
- Sem access log na aplicação — `http.server.requests` responde "quantas falharam e onde" e os spans respondem
  "quais"; o access log do Tomcat fica fora do Logback e sem `traceId`, e o `CommonsRequestLoggingFilter` emite
  par before/after em texto livre, com opção de corpo e cabeçalhos (decisão do usuário)
- Regra de log de exceções (aplicada na T05): inesperada (5xx) → `ERROR` com stack trace; falha de dependência
  externa → `WARN` no adaptador; 401 e 429 → `WARN` (sinais de segurança, refinados na T08 e T19); demais 4xx →
  `INFO`, uma linha sem stack trace, com status e tipo do problema (nomes dos campos em validação); nunca valores
  rejeitados, mensagem crua de exceção ou conteúdo do cliente (decisão do usuário: 4xx em `INFO` para atender o
  suporte por `X-Request-Id`)
- ADR 0002 registra as decisões de observabilidade e correlação — consultadas pela T05, T09, T20 e T22
- Checkstyle `ConstantName` ajustado para aceitar o logger `log` (static final, convenção do SLF4J), que o plano
  prevê e o padrão original reprovava (decisão do usuário)
- Pasta `infrastructure/http/filter/` para o `RequestIdFilter` (decisão do usuário); `CLAUDE.md` (Estrutura) é
  atualizado na fase 3
- Amostragem padrão (10%) mantida: o `traceId` entra no MDC também em spans não amostrados (confirmado pelo
  `RequestCorrelationIT`). O starter também liga a exportação OTLP de métricas, que tentava publicar em
  `localhost:4318`; desligada com `management.otlp.metrics.export.enabled=false` até a T22 (decisão do usuário).
  A exportação de traces não tenta enviar nada sem endpoint
- Três fases: correlação, proteção de dados e eventos, documentação

## Definição de pronto

- [ ] `./gradlew build` passa (Spotless, Checkstyle, `*Test`, `*IT` e JaCoCo), sem `OPENAI_API_KEY`
- [ ] Toda resposta HTTP traz `X-Request-Id` com o `traceId` (32 hex), igual ao `traceId` das linhas de log da
      requisição, que saem em JSON ECS
- [ ] Uma requisição com `traceparent` devolve no `X-Request-Id` o trace ID recebido
- [ ] Um comando de voz gera os quatro eventos do fluxo e `Transaction persisted`, com `traceId`, e nenhuma linha
      de log contém a transcrição ou a resposta do modelo
- [ ] Nenhum `org.springframework.ai=DEBUG`, `show-sql` ou `System.out` na configuração e no código versionados;
      opções de conteúdo do Spring AI explicitamente em `false`
- [ ] Nenhuma tentativa de exportar spans nos testes (sem endpoint OTLP)
- [ ] ADR 0002 aceito; regras, `CLAUDE.md`, README e FUT-15/16 atualizados; checkbox da T04 marcado em
      `docs/tarefas.md` no último commit

## Para futuros agentes

Execute com `/executar-tarefa`. Durante a execução:

- marque `- [x]` no mesmo commit que conclui o item;
- antes do último commit de uma fase, rode o plano de verificação, escreva o resumo da fase com o resultado e
  mude o status para `Concluída` — tudo nesse mesmo commit;
- na última fase, no mesmo commit, confira a definição de pronto e escreva o resumo final (e o plano de deploy,
  se houver);
- mudança no plano só com aprovação do usuário, registrada em Decisões com o motivo.

Contexto para uma sessão nova:

- Branch `feat/logs-estruturados`; título do PR `feat: adiciona logs estruturados com correlação e proteção de
  dados`. Commits: `build:`, `feat:`, `test:`, `docs:`.
- Estado antes da tarefa: nenhum `Logger` em `src/main/java`; `application.properties` tem
  `logging.level.org.springframework.ai=DEBUG` (loga prompts, transcrições, argumentos de tools e respostas) e
  `spring.jpa.show-sql=true` (SQL via `System.out`, fora do logger).
- Spring Boot 4.1.2: logs estruturados nativos; o formatter ECS do Logback inclui o MDC (`getMDCPropertyMap`) e
  os pares chave-valor do SLF4J 2 (`getKeyValuePairs`). Conferir na saída real os nomes dos campos (ex.:
  `traceId` ou `trace.id`) antes de fixar as asserções.
- Tracing: o Micrometer Tracing coloca `traceId`/`spanId` no MDC dentro do `ServerHttpObservationFilter` (ordem
  `Ordered.HIGHEST_PRECEDENCE + 1`). O filtro do `X-Request-Id` precisa rodar depois dele (um `@Component` filtro
  tem ordem `LOWEST_PRECEDENCE` por padrão) e lê o ID com `io.micrometer.tracing.Tracer#currentSpan()`
  (`span.context().traceId()`), definindo o cabeçalho antes de chamar a chain (resposta ainda não commitada).
- Amostragem: o padrão é 10%. Confirmar que o `traceId` aparece no MDC também em spans não amostrados; se não
  aparecer, definir `management.tracing.sampling.probability=1.0` e registrar em Decisões.
- Exportação: confirmar que, sem endpoint OTLP (`management.opentelemetry.tracing.export.otlp.endpoint`), nenhum
  exportador tenta enviar spans (nada como "Failed to export spans" na saída dos `*IT`); se tentar, desligar por
  propriedade e registrar em Decisões. O README documenta a variável de ambiente correspondente ao endpoint.
- Opções de conteúdo do Spring AI 2.0.2 (conferidas no `spring-ai-autoconfigure`), todas em `false`:
  `spring.ai.chat.client.observations.log-prompt`, `spring.ai.chat.client.observations.log-completion`,
  `spring.ai.chat.observations.log-prompt`, `spring.ai.chat.observations.log-completion`,
  `spring.ai.audio.transcription.observations.log-transcript`, `spring.ai.audio.speech.observations.log-text`,
  `spring.ai.tools.observations.include-content`.
- Testes de log: `@ExtendWith(OutputCaptureExtension.class)` e parâmetro `CapturedOutput` no método de teste;
  cada linha é um JSON, lido com o Jackson 3 (`tools.jackson.*`). Com `@AutoConfigureMockMvc`, o MockMvc inclui
  os filtros do contexto (conferir que o `ServerHttpObservationFilter` está entre eles).
- No `VoiceCommandIT`, a transcrição simulada é `"gastei 80 reais no mercado"` e a resposta `"Registrei 80 reais
  em mercado."`; a asserção de não vazamento procura esses textos em toda a saída capturada.
- Logger: `private static final Logger log = LoggerFactory.getLogger(...)` (SLF4J, permitido em `application`).
- Regras: `.claude/rules/testing.md` (o que testar), `architecture.md`, `api-conventions.md`, `ai-integration.md`,
  `error-handling.md`.
- FUT-15: `x-fapi-interaction-id` (Open Finance, Payments API 4.0.0) — UUID gerado pelo cliente e espelhado na
  resposta; correlaciona mesmo quando a resposta não chega (timeout). FUT-16: backend de traces — coletor local
  `grafana/otel-lgtm` no `compose.yml` (reconhecido pelo `spring-boot-docker-compose`) e X-Ray via ADOT Collector
  ou CloudWatch Agent em produção.

## Fase 1: Correlação e logs estruturados

Status: Concluída  <!-- Não iniciada | Em andamento | Concluída -->

- [x] [verificação] `spring-boot-starter-opentelemetry` e `spring-boot-starter-actuator` no `build.gradle`;
      `logging.structured.format.console=ecs` no `application.properties`
- [x] [TDD] `RequestCorrelationIT` (novo, `infrastructure/http`): `GET /transactions/GROCERIES` responde com
      `X-Request-Id` (32 hex) e as linhas de log da requisição saem em JSON com o mesmo `traceId` — implementa o
      filtro do `X-Request-Id` em `infrastructure/http` e o evento `Transactions listed` (`resultCount`) em
      `ListTransactionsByCategoryUseCase`
- [x] [TDD] `RequestCorrelationIT`: requisição com `traceparent` válido devolve no `X-Request-Id` o trace ID
      recebido; `X-Request-Id` enviado pelo cliente é ignorado
- [x] [verificação] Confirmar amostragem (`traceId` no MDC) e ausência de exportação sem endpoint OTLP (ver
      "Contexto para uma sessão nova"); ajustar propriedades só se necessário

### Plano de verificação

- `./gradlew build` → `BUILD SUCCESSFUL`, sem `OPENAI_API_KEY`
- `./gradlew integrationTest --tests "dio.budgeting.infrastructure.http.RequestCorrelationIT"` → passa
- `grep -il "failed to export" build/test-results/integrationTest/*.xml` → nenhum arquivo

### Resumo da fase

Feito: starters do Actuator e do OpenTelemetry, logs em JSON ECS em todos os ambientes, `RequestIdFilter`
(`infrastructure/http/filter/`) que devolve o `traceId` em `X-Request-Id`, evento `Transactions listed`
(`resultCount`) e `RequestCorrelationIT` com três testes (correlação com os logs, `traceparent` continuado,
`X-Request-Id` do cliente ignorado). Decisões: ver Decisões (Checkstyle aceita o logger `log`; pasta `filter/`;
métricas OTLP desligadas). Os testes de `traceparent` e `X-Request-Id` do cliente passaram sem RED, pois o
comportamento já vinha do filtro e do Micrometer. O `application.properties` foi organizado em seções com
comentários (`# Logging`, `# AI: chat` etc.).

Verificação: `./gradlew build` → `BUILD SUCCESSFUL`; `integrationTest --tests RequestCorrelationIT` → passa;
`grep -il "failed to export" build/test-results/integrationTest/*.xml` → nenhum arquivo.

Para uma sessão nova: nos logs ECS, `traceId` e os pares chave-valor (ex.: `resultCount`) são campos de topo do JSON.
O `RequestCorrelationIT` tem o helper `logEvents` (parse das linhas JSON do `CapturedOutput`) que a fase 2 pode
reaproveitar no `VoiceCommandIT`.

## Fase 2: Proteção de dados e eventos do fluxo de voz

Status: Em andamento  <!-- Não iniciada | Em andamento | Concluída -->

- [x] [TDD] `VoiceCommandIT`: nenhuma linha da saída capturada contém a transcrição nem a resposta do modelo —
      remove `logging.level.org.springframework.ai=DEBUG` e `spring.jpa.show-sql=true` e define em `false` as
      opções de conteúdo do Spring AI (se o teste não falhar no RED, registrar no resumo da fase)
- [ ] [TDD] `VoiceCommandIT`: o comando de voz registra `Voice command received` (`audioSizeBytes`,
      `audioContentType`), `Audio transcribed` (`transcriptionLength` = 26), `Assistant answered`
      (`answerLength`), `Speech synthesized` (`speechSizeBytes` = 4) e `Transaction persisted` (`transactionId`
      igual ao da transação gravada), todos com o `traceId` da requisição — eventos no `TransactionController` e
      no `PersistTransactionUseCase`

### Plano de verificação

- `./gradlew build` → `BUILD SUCCESSFUL`, sem `OPENAI_API_KEY`
- `./gradlew integrationTest --tests "dio.budgeting.infrastructure.http.VoiceCommandIT"` → passa
- `grep -nE 'show-sql|org\.springframework\.ai=DEBUG' src/main/resources/application.properties` → nenhuma linha
- `grep -rn 'System\.\(out\|err\)' src/main/java` → nenhuma linha
- `grep -cE 'observations\.(log-prompt|log-completion|log-transcript|log-text|include-content)=false' src/main/resources/application.properties`
  → `7`

### Resumo da fase

_(preencher ao concluir a fase)_

## Fase 3: Documentação

Status: Não iniciada  <!-- Não iniciada | Em andamento | Concluída -->

- [ ] [verificação] `.claude/rules/error-handling.md`, seção "Logs": o que nunca logar (conteúdo, dados de
      negócio, cabeçalhos, query string, valores rejeitados, mensagem crua de exceção), campos permitidos (IDs e
      metadados técnicos, como pares chave-valor), nível por tipo de exceção (tabela das Decisões) e opções de
      conteúdo do Spring AI sempre desligadas
- [ ] [verificação] `.claude/rules/api-conventions.md` (Cabeçalhos: `X-Request-Id` com o `traceId`; correlação de
      entrada pelo `traceparent`); `.claude/rules/ai-integration.md` (Não fazer: ligar as opções de observação que
      logam conteúdo); `CLAUDE.md` (Stack: Actuator e Micrometer Tracing com OpenTelemetry; Padrões: logs
      estruturados com pares chave-valor, sem conteúdo)
- [ ] [verificação] `README.md`: seção de logs e rastreamento — formato ECS, `./gradlew bootRun | jq`,
      `X-Request-Id`, como configurar o endpoint OTLP (ex.: ADOT Collector ou CloudWatch Agent para o X-Ray)
- [ ] [verificação] ADR `docs/adr/0002-observabilidade-e-correlacao.md` (a partir de `docs/adr/template.md`,
      `status: aceito`): contexto (OBS-01, SEG-06); opções (SDK do X-Ray, ID de correlação próprio,
      OpenTelemetry com `traceId`; `X-Request-Id`, `x-fapi-interaction-id`, `traceresponse`; access log); decisão
      e consequências — conteúdo fora dos logs, sem access log, exportação OTLP neutra
- [ ] [verificação] `docs/escopo.md`, Melhorias futuras: FUT-15 (`x-fapi-interaction-id`) e FUT-16 (backend de
      traces: coletor local e X-Ray via ADOT), com o motivo de ficarem para depois
- [ ] [verificação] Marcar a T04 em `docs/tarefas.md` (último commit)

### Plano de verificação

- `./gradlew build` → `BUILD SUCCESSFUL` (inclui Spotless nos `.md`)
- `grep -n "A definir na T04" .claude/rules/error-handling.md` → nenhuma linha
- `grep -nE "FUT-15|FUT-16" docs/escopo.md` → duas linhas
- `grep -n "X-Request-Id" .claude/rules/api-conventions.md README.md docs/adr/0002-observabilidade-e-correlacao.md`
  → ao menos uma linha em cada arquivo

### Resumo da fase

_(preencher ao concluir a fase)_

## Resumo final

_(preencher ao concluir todas as fases)_
