# T23 — Medição de consumo de IA

Registrar no MongoDB, por usuário e por requisição, o consumo de cada resposta dos modelos de IA (segundos de áudio
transcritos, tokens do assistente e caracteres sintetizados), para análises de custo mais precisas do que o painel do
provedor permite.

## Escopo

**Dentro:**
- `application.properties`: `spring.ai.openai.audio.transcription.options.response-format=verbose_json` (hoje `text`),
  com comentário: é o único formato em que o `whisper-1` devolve a duração do áudio
- `infrastructure/ai/AiUsageObservationHandler` — `ObservationHandler` (`@Component`, registrado pelo Spring Boot) que
  trata só `TranscriptionModelObservationContext`, `ChatModelObservationContext` e
  `TextToSpeechModelObservationContext`. No `onStop` de uma chamada que recebeu resposta, lê na thread da requisição o
  `userId` (JWT do `SecurityContext`, `sub`) e o `traceId` (`Tracer`, como o `MongoAuditEntryRepository`) e publica
  `AiUsageMeasuredEvent` pelo `ApplicationEventPublisher`:
  - transcrição: resposta presente → `Transcription(audioSeconds)` da duração do metadado da resposta
  - assistente: resposta presente → `Assistant(inputTokens, cachedInputTokens, outputTokens)` do `Usage` da resposta
    (`cachedInputTokens` = `getCacheReadInputTokens()`, zero quando nulo)
  - síntese: o Spring AI não guarda a resposta no contexto, então "recebeu resposta" = observation sem erro →
    `Speech(characters)` = tamanho do texto da requisição
  - modelo: atributos da observation, `gen_ai.response.model` quando existe (no assistente, a versão exata), senão
    `gen_ai.request.model`
  - falha do provedor (sem resposta / com erro) → nada publicado
  - qualquer erro dentro do handler → `WARN` só com tipos (etapa e tipo do erro) e engolido: a chamada à IA nunca é
    afetada
- `infrastructure/ai/AiUsageMeasuredEvent` — evento de aplicação (não de domínio, fora do `DomainEvent`):
  ```java
  record AiUsageMeasuredEvent(UserId userId, String traceId, Instant occurredAt, String model, Usage usage) {
      sealed interface Usage {
          record Transcription(double audioSeconds) implements Usage {}
          record Assistant(long inputTokens, long cachedInputTokens, long outputTokens) implements Usage {}
          record Speech(int characters) implements Usage {}
      }
  }
  ```
  Comentário no `Speech`: caracteres são uma aproximação do custo do `gpt-4o-mini-tts` (cobrado por tokens de texto e
  de áudio); a síntese não devolve consumo
- `infrastructure/event/AiUsageMeasuredEventListener` — `@Async @EventListener` (sem `@TransactionalEventListener`,
  então sem publicação no registro do Modulith): converte com `AiUsageEntryDocument.from(event)` e insere pelo
  `AiUsageEntryDocumentRepository`, sem porta nem use case. Falha → `WARN` só com tipos (etapa e tipo do erro); o
  registro se perde (melhor esforço)
- `infrastructure/persistence/document/AiUsageEntryDocument` — coleção `ai_usage_entries`, um documento por resposta do
  modelo, plano (campo nulo não é gravado, como o `AuditEntryDocument`): `id` (UUID aleatório), `userId`, `traceId`,
  `occurredAt`, `stage`, `model`, `transcribedAudioSeconds`, `inputTokens`, `cachedInputTokens`, `outputTokens`,
  `synthesizedCharacters`; `from(event)` com `switch` no `Usage` selado
- `infrastructure/persistence/document/AiStage` — `TRANSCRIPTION`, `ASSISTANT`, `SPEECH` (mesmo vocabulário do
  `AssistantGateway` e do `AiStage` de teste)
- `infrastructure/persistence/repository/AiUsageEntryDocumentRepository` — Spring Data (`MongoRepository`)
- Teste `infrastructure/ai/AiUsageMeasurementIT` (`@IntegrationTest`, `@AutoConfigureAiStub`, Awaitility, usuário
  aleatório por teste, documentos lidos crus com `MongoTemplate`):
  - comando de voz concluído → três documentos (`TRANSCRIPTION` com `transcribedAudioSeconds`, `ASSISTANT` com tokens,
    tokens em cache e modelo, `SPEECH` com `synthesizedCharacters` = tamanho de `AiStub.speechText()`), todos com o
    `userId` do token e o `traceId` do cabeçalho `X-Request-Id`
  - 422 `invalid-command` (argumentos inválidos da tool) → documento `ASSISTANT` existe
- Stub da IA (`support/ai/OpenAiWireMockStub` e `src/test/resources/ai/openai/`): transcrição responde `verbose_json`
  (`text`, `duration`, `language`); payloads do chat com tokens diferentes de zero, inclusive
  `prompt_tokens_details.cached_tokens`
- Instruções do agente:
  - `CLAUDE.md`: estrutura — `ai/` ganha `AiUsageObservationHandler` e `AiUsageMeasuredEvent`; `event/` passa a
    "ouvem eventos de domínio e de aplicação"; `persistence/document/` cita o `AiUsageEntryDocument`
  - `.claude/rules/architecture.md`: parágrafo de precedente — o handler publica um evento de aplicação (não de
    domínio: o fato só é conhecido dentro do Spring AI, e um `DomainEvent` iria para a auditoria e para o outbox) e o
    listener grava direto pelo Spring Data, sem porta nem use case, porque consumo é dado técnico, sem regra de negócio
  - `.claude/rules/ai-integration.md`: o consumo é capturado pela observation do Spring AI, não pelas portas (cobre o
    422, cuja resposta do chat não chega ao gateway); transcrição em `verbose_json` por causa da duração
  - `.claude/rules/testing.md`: o stub devolve duração e tokens; o `AiUsageMeasurementIT` espera com Awaitility
- `docs/tarefas.md`: checkbox da T23

**Fora:**
- logs e métricas novos (o custo total se acompanha na plataforma do provedor)
- README e ADR
- garantia de entrega (outbox, reenvio), índices e retenção (TTL) da coleção `ai_usage_entries`
- cálculo de custo e cotas por usuário (FUT-09)
- consumo de chamadas sem resposta (falha do provedor)
- limites de taxa, id da resposta e demais detalhes de tokens (escrita em cache, áudio, raciocínio)
- teste unitário do listener (falha do MongoDB)
- verificação com a OpenAI real
- deploy

## Decisões

- Custo total acompanhado na plataforma do provedor/nuvem; o MongoDB guarda o detalhe por usuário e por requisição
  para análises mais precisas — sem logs nem métricas novos (decisão do usuário)
- Registrar toda vez que um modelo responde: um documento por resposta, não por comando; os documentos de um comando
  se ligam pelo `traceId` (decisão do usuário)
- Captura pela observation do Spring AI, não pelas portas — no chat a resposta entra no contexto antes da execução
  das tools, então o 422 `invalid-command` é registrado; pelas portas, a resposta do chat se perde quando a tool lança
  exceção (decisão do usuário)
- Evento de aplicação em `infrastructure/ai/`, não de domínio — o fato só é conhecido dentro do Spring AI (a regra é
  "quem conhece o fato publica, no use case"), e um `DomainEvent` viraria registro de auditoria e publicação no outbox
  (decisão do usuário)
- Sem use case nem porta — consumo é dado técnico, sem regra de negócio, como o `DomainEventMetricsListener` e o
  `EventPublicationResubmissionJob`; a regra do `architecture.md` exige use case só para listener com lógica de negócio
  (decisão do usuário)
- Gravação assíncrona e de melhor esforço (`@Async @EventListener`) — não segura a requisição nem a derruba com o
  MongoDB fora; a perda só ocorre com o MongoDB indisponível (decisão do usuário)
- `userId` e `traceId` lidos no handler, na thread da requisição — o `SecurityContext` não acompanha a thread assíncrona
- `_id` UUID aleatório — gravação sem reenvio, sem duplicata a evitar
- Campos: consumos + modelo de cada etapa + tokens de entrada em cache — o preço depende do modelo e o cache é cobrado
  mais barato (decisão do usuário)
- `synthesizedCharacters` mantido como aproximação — é o que o CUS-01 pede e custa uma linha; a síntese não devolve
  consumo (decisão do usuário)
- Nomes: etapa `ASSISTANT`, não `CHAT` (vocabulário do projeto); família `AiUsageEntry*` espelhando `AuditEntry*`;
  `AiUsageMeasuredEventListener` diz o que ouve; campos planos com unidade e sem ambiguidade
  (`transcribedAudioSeconds`, `synthesizedCharacters`) (decisão do usuário)
- Testes: um `*IT` com o comando concluído e o 422; sem teste unitário do listener (decisão do usuário)
- Sem README nem ADR; a documentação fica nas instruções do agente (decisão do usuário)
- Sem a chave da OpenAI — o comportamento contra a OpenAI real (duração no `verbose_json` real, versão do modelo,
  tokens em cache) fica não verificado (decisão do usuário)
- Uma fase; tipo `feat`; branch `feat/medicao-de-consumo-de-ia`

## Definição de pronto

- [ ] `./gradlew build` passa (Spotless, Checkstyle, testes e cobertura)
- [ ] um comando de voz concluído grava três documentos em `ai_usage_entries` (`TRANSCRIPTION`, `ASSISTANT`, `SPEECH`)
  com o `userId` e o `traceId` da requisição, modelo e consumos da etapa
- [ ] um 422 `invalid-command` grava o documento `ASSISTANT`
- [ ] a transcrição usa `verbose_json` e o `VoiceCommandIT` continua passando
- [ ] `CLAUDE.md`, `architecture.md`, `ai-integration.md`, `testing.md` e `tarefas.md` atualizados como no escopo

## Para futuros agentes

Execute com `/executar-tarefa`. Durante a execução:

- marque `- [x]` no mesmo commit que conclui o item;
- antes do último commit de uma fase, rode o plano de verificação, escreva o resumo da fase com o resultado e
  mude o status para `Concluída` — tudo nesse mesmo commit;
- na última fase, no mesmo commit, confira a definição de pronto e escreva o resumo final (e o plano de deploy,
  se houver);
- mudança no plano só com aprovação do usuário, registrada em Decisões com o motivo.

Fatos conferidos no Spring AI 2.0.2 durante o planejamento (reconfirmar ao implementar):
- `OpenAiAudioTranscriptionModel` só preenche a duração (`OpenAiAudioTranscriptionResponseMetadata.getDuration()`)
  quando a resposta é `verbose_json`
- `OpenAiChatModel` chama `ChatModelObservationContext.setResponse` dentro da observation, antes da execução das tools
- `OpenAiAudioSpeechModel` não chama `setResponse` no `TextToSpeechModelObservationContext`
- `Usage` expõe `getPromptTokens()`, `getCompletionTokens()` e `getCacheReadInputTokens()`
- a leitura do modelo pelos atributos da observation no `onStop` (convenção aplicada antes dos handlers) e o registro
  automático do `ObservationHandler` pelo Spring Boot são confirmados pelo `AiUsageMeasurementIT`

## Fase 1: Consumo de IA registrado no MongoDB

Status: Em andamento  <!-- Não iniciada | Em andamento | Concluída -->

- [x] [verificação] transcrição em `verbose_json` no `application.properties` e no stub (`givenTranscription`
  responde JSON com `text`, `duration` e `language`); payloads do chat com tokens diferentes de zero, inclusive
  `cached_tokens` — `VoiceCommandIT` continua passando
- [x] [TDD] `AiUsageMeasurementIT`: comando de voz concluído grava três documentos em `ai_usage_entries` com `userId`,
  `traceId`, modelo e consumos da etapa — implementar `AiUsageObservationHandler`, `AiUsageMeasuredEvent`,
  `AiUsageMeasuredEventListener`, `AiUsageEntryDocument`, `AiStage` e `AiUsageEntryDocumentRepository`
- [x] [TDD] `AiUsageMeasurementIT`: 422 `invalid-command` grava o documento `ASSISTANT`
- [ ] [verificação] `CLAUDE.md`: estrutura (`ai/`, `event/`, `persistence/document/`)
- [ ] [verificação] `.claude/rules/architecture.md`: precedente do evento de aplicação e do listener sem use case
- [ ] [verificação] `.claude/rules/ai-integration.md`: captura pela observation e `verbose_json`
- [ ] [verificação] `.claude/rules/testing.md`: stub com duração e tokens e o `AiUsageMeasurementIT`
- [ ] [verificação] `docs/tarefas.md`: checkbox da T23 (último commit)

### Plano de verificação

- `./gradlew integrationTest --tests "dio.budgeting.infrastructure.ai.AiUsageMeasurementIT"` → `BUILD SUCCESSFUL`,
  dois testes passando
- `./gradlew integrationTest --tests "dio.budgeting.infrastructure.http.VoiceCommandIT"` → `BUILD SUCCESSFUL`
- `./gradlew build` → `BUILD SUCCESSFUL`
- `grep -n "response-format=verbose_json" src/main/resources/application.properties` → uma linha
- `grep -rn "AiUsage" CLAUDE.md .claude/rules/` → citações em `CLAUDE.md`, `architecture.md`, `ai-integration.md` e
  `testing.md`

### Resumo da fase

_(preencher ao concluir a fase)_

## Resumo final

_(preencher ao concluir todas as fases)_
