# T10 — Timeouts e retries

Dar a todas as chamadas externas um tempo máximo explícito e repetir as falhas transitórias com backoff
exponencial e jitter (RES-01, RES-02), configurando o timeout e o retry nativos do SDK da OpenAI por etapa e os
timeouts do banco no Hikari e no driver MySQL, com teto de pior caso garantido por construção.

## Escopo

**Dentro:**
- `src/main/resources/application.properties`, IA: timeout e retry do SDK **por etapa**, nunca pela chave global
  `spring.ai.openai.*`:
  - `spring.ai.openai.audio.transcription.timeout=10s` e `spring.ai.openai.audio.transcription.max-retries=1`
  - `spring.ai.openai.chat.timeout=12s` e `spring.ai.openai.chat.max-retries=1`
  - `spring.ai.openai.audio.speech.timeout=10s` e `spring.ai.openai.audio.speech.max-retries=1`
- `application.properties`, banco (sem retry):
  - `spring.datasource.hikari.connection-timeout=3000` (espera por conexão do pool)
  - `spring.datasource.hikari.data-source-properties.connectTimeout=3000` (abertura da conexão TCP, driver)
  - `spring.datasource.hikari.data-source-properties.socketTimeout=5000` (espera pela resposta de cada query, driver)
- Comentário (em inglês, só o porquê) com a conta do teto por construção: ≈ 128 s no pior caso
  - transcrição 2 × 10 s + 8 s = 28 s
  - chat 2 × (2 × 12 s + 8 s) = 64 s (duas requisições quando há tool)
  - voz 28 s
  - tool no banco 3 s + 5 s = 8 s
- `src/test/resources/application-test.properties`: `spring.ai.openai.max-retries=0` (global, sobrescrito pelas
  chaves por etapa) vira `max-retries=0` em cada etapa (`audio.transcription`, `chat`, `audio.speech`)
- `.claude/rules/ai-integration.md`:
  - "Etapas": timeout e retry de cada etapa ficam no SDK, por requisição HTTP, com chaves por etapa no
    `application.properties`
  - "Não fazer": retry sobre o laço de tools (`interpret` inteiro) enquanto houver tool de escrita não idempotente
- `.claude/rules/testing.md`: o perfil `test` também desliga o retry do SDK por etapa
- `docs/tarefas.md`:
  - T10: sai a frase "Incluir testes que simulem lentidão e falhas." e o item "QA-03 — Testes de falha (parcial)"
  - rastreabilidade: `QA-03 → T15, T16, T17, T21`
  - nota da T09 na T10 reescrita: timeout e retry ficam no SDK, por etapa, não nos adaptadores
  - nota da T10 na T15: o circuit breaker vê a falha depois dos retries do SDK; timeout e retry por etapa no
    `application.properties`
  - nota da T10 na T17: com a escrita idempotente, o retry pode subir para o `AssistantGateway` (laço de tools)

**Fora:**
- testes novos: o retry e o timeout são do SDK, e aqui só configuramos valores (`testing.md`: não testar
  configuração nem comportamento de biblioteca)
- `@Retryable` do Spring, Resilience4j, prazo total em tempo de execução
- ADR
- mudança no contrato HTTP (`docs/openapi.yaml`, exemplos), no código Java ou no README
- resposta própria para timeout do banco (continua 500 genérico)
- circuit breaker e bulkhead (T15), degradação (T16), idempotência (T12, T13, T17)
- deploy

## Decisões

- Retry e timeout nativos do SDK da OpenAI, não `@Retryable` nos gateways, misto nem Resilience4j — o SDK já faz
  backoff exponencial (0,5 s → 8 s) com jitter e respeita `Retry-After`, e repete **por requisição HTTP**: no chat,
  reenvia só a requisição que falhou, sem reexecutar a tool. Repetir `interpret` inteiro refaria a conversa e
  executaria de novo `persist-transaction`, gravando em dobro (decisão do usuário)
- Repetir o laço de tools só é seguro com tools de escrita idempotentes; hoje não são. Quando forem (proposta da
  T12, critério de reenvio da T17), o retry no `AssistantGateway` volta a ser opção — nota na T17 (decisão do
  usuário)
- Chaves por etapa, não a global — cada dependência ajustada sem afetar as outras, base para a T15 (decisão do
  usuário)
- Teto por construção (~2 min, ≈ 128 s com o banco), não prazo em tempo de execução — o SDK garante cada parcela
  (timeout por tentativa, espera de no máximo 8 s); um prazo no use case deixaria trabalho órfão (tool gravando
  depois do 503) e exigiria propagar o contexto de trace (decisão do usuário)
- 1 retry por requisição em todas as etapas; timeouts de 10 s (transcrição, voz) e 12 s (chat) — cobre a falha
  momentânea; a falha persistente é papel do circuit breaker da T15 (decisão do usuário)
- Banco: timeouts no Hikari (pool) e no driver (`connectTimeout`, `socketTimeout`), sem retry — o `socketTimeout`
  vale para qualquer query sem anotar nada; o cadastro não é idempotente (decisão do usuário)
- Sem testes novos: só configuramos valores de código de terceiros; testes de falha voltam onde o comportamento for
  nosso (T15, T16). QA-03 sai da T10 (decisão do usuário)
- Perfil `test` com `max-retries=0` por etapa — os `*IT` não dependem do retry do SDK nem pagam o backoff (decisão
  do usuário)
- Sem ADR; a decisão fica neste plano, em `ai-integration.md` e nas notas de `docs/tarefas.md` (decisão do usuário)
- Uma fase; tipo `feat` (muda o comportamento das chamadas externas)

## Definição de pronto

- [ ] `./gradlew build` passa (Spotless, Checkstyle, `*Test`, `*IT`, JaCoCo), sem `OPENAI_API_KEY` nem `JWT_SECRET`
- [ ] `application.properties` com timeout e `max-retries` das três etapas, os três timeouts do banco e o comentário
      com a conta do teto
- [ ] as chaves `spring.ai.openai.*` usadas existem nos metadados do Spring AI 2.0.2 (não são ignoradas em silêncio)
- [ ] perfil `test` sem a chave global de retry e com `max-retries=0` por etapa
- [ ] `ai-integration.md`, `testing.md` e `docs/tarefas.md` atualizados; checkbox da T10 marcado em `docs/tarefas.md`
      no último commit

## Para futuros agentes

Execute com `/executar-tarefa`. Durante a execução:

- marque `- [x]` no mesmo commit que conclui o item;
- antes do último commit de uma fase, rode o plano de verificação, escreva o resumo da fase com o resultado e
  mude o status para `Concluída` — tudo nesse mesmo commit;
- na última fase, no mesmo commit, confira a definição de pronto e escreva o resumo final (e o plano de deploy,
  se houver);
- mudança no plano só com aprovação do usuário, registrada em Decisões com o motivo.

Contexto para uma sessão nova:

- Branch `feat/timeouts-e-retries`; título do PR `feat: define timeouts e retries das chamadas externas`. Commits:
  `feat:` (configuração), `docs:` (regras e tarefas).
- Estado antes da tarefa: nada de timeout ou retry configurado. Valem os padrões do Spring AI 2.0.2
  (`AbstractOpenAiProperties.DEFAULT_TIMEOUT` = 60 s, `DEFAULT_MAX_RETRIES` = 3) para as três etapas; Hikari com
  `connection-timeout` de 30 s; driver MySQL com `connectTimeout` e `socketTimeout` 0 (infinito).
- O Spring AI passa `timeout` ao SDK como `Timeout.request` (cada tentativa: conexão, envio e leitura). O
  `RetryingHttpClient` do SDK repete IO, 408, 409, 429 e ≥ 500, com backoff `0,5 s × 2^(n-1)` (teto 8 s) e jitter
  de até −25%; o `Retry-After`/`Retry-After-Ms` também é limitado a 8 s.
- Chave por etapa (`spring.ai.openai.chat.max-retries`) tem precedência sobre a global (`spring.ai.openai.max-retries`):
  por isso o perfil `test` precisa das chaves por etapa.
- Hikari: `connection-timeout` em milissegundos (`3000`, não `3s`). `data-source-properties` vai cru ao driver e
  diferencia maiúsculas: `connectTimeout`, `socketTimeout` (nomes do Connector/J).
- O `AiProviderIsolationIT` continua falhando com `ConnectException` (retry 0 por etapa no perfil `test`).
- O timeout do provedor chega como `OpenAIIoException` e o aspecto já o traduz para `AiProviderUnavailableException`
  (503); timeout do banco cai no 500 genérico. Nenhum código Java muda.
- Comentários de código (inclusive `.properties`) em inglês, só o porquê; Spotless verifica trailing whitespace e
  newline final dos `.properties` e `.md`.

## Fase 1: Configuração e documentação

Status: Em andamento  <!-- Não iniciada | Em andamento | Concluída -->

- [x] [verificação] `application.properties`: timeout e `max-retries` por etapa (transcrição 10s/1, chat 12s/1,
      voz 10s/1) junto das opções de cada modelo; timeouts do banco (Hikari 3000, `connectTimeout` 3000,
      `socketTimeout` 5000) numa seção `# Persistence`; comentário com a conta do teto (≈ 128 s);
      `application-test.properties` com `max-retries=0` por etapa no lugar da chave global
- [x] [verificação] `.claude/rules/ai-integration.md` ("Etapas" e "Não fazer") e `.claude/rules/testing.md`
      (retry desligado por etapa no perfil `test`)
- [ ] [verificação] `docs/tarefas.md`: descrição e itens da T10 sem QA-03; rastreabilidade de QA-03; nota da T09
      na T10 reescrita; notas da T10 na T15 e na T17

### Plano de verificação

- `env -u OPENAI_API_KEY -u JWT_SECRET ./gradlew build` → `BUILD SUCCESSFUL`
- `grep -nE "^spring\.ai\.openai\.(audio\.transcription|chat|audio\.speech)\.(timeout|max-retries)=" src/main/resources/application.properties`
  → 6 linhas: `10s`/`1`, `12s`/`1`, `10s`/`1`
- `grep -nE "^spring\.datasource\.hikari\.(connection-timeout|data-source-properties\.(connectTimeout|socketTimeout))=" src/main/resources/application.properties`
  → 3 linhas: `3000`, `3000`, `5000`
- `unzip -p "$(find ~/.gradle/caches/modules-2 -name 'spring-ai-autoconfigure-model-openai-2.0.2.jar' | head -1)" META-INF/spring-configuration-metadata.json | grep -oE '"spring\.ai\.openai\.(audio\.transcription|chat|audio\.speech)\.(timeout|max-retries)"' | sort -u`
  → as 6 chaves usadas
- `grep -n "^spring.ai.openai.max-retries" src/test/resources/application-test.properties` → nenhuma linha;
  `grep -cE "^spring\.ai\.openai\.(audio\.transcription|chat|audio\.speech)\.max-retries=0" src/test/resources/application-test.properties` → `3`
- `grep -n "QA-03" docs/tarefas.md` → nenhuma ocorrência na seção da T10; rastreabilidade `QA-03 → T15, T16, T17, T21`
- `grep -n "Nota (T10)" docs/tarefas.md` → notas na T15 e na T17

### Resumo da fase

_(preencher ao concluir a fase)_

## Resumo final

_(preencher ao concluir todas as fases)_
