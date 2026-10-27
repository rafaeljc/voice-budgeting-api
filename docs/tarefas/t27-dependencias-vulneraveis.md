# T27 — Atualização das dependências vulneráveis

Zerar todos os alertas de vulnerabilidade do Dependabot (12 críticos, 47 altos, 52 médios e 8 baixos em
27/10/2026) atualizando as versões declaradas no build: Spring Boot 4.0.5 → 4.1.2, Checkstyle 13.4.0 → 13.11.0 e
Spring AI 2.0.0-M4 → 2.0.2 (SEG-07, correção dos achados).

## Escopo

**Dentro:**
- plugin `org.springframework.boot` 4.0.5 → 4.1.2 — corrige, pelo BOM, Netty, Tomcat, Spring Framework, Jackson,
  Micrometer, Spring Data, Logback e Log4j; e, no classpath do plugin (`spring-boot-buildpack-platform`),
  `httpclient5` e `httpcore5`/`httpcore5-h2`
- `checkstyle.toolVersion` 13.4.0 → 13.11.0 — elimina o `plexus-utils` vulnerável (vinha do doxia, removido do
  Checkstyle na 13.11.0)
- `spring-ai-bom` 2.0.0-M4 → 2.0.2 — o `spring-ai-openai` passou a usar o SDK `openai-java` (OkHttp) no lugar do
  `RestClient`/`WebClient`; ajustar propriedades, código e o stub de testes ao que mudar; o `mcp-core` 1.1.0
  (vulnerável) deve sair da árvore
- entrada da T27 em `docs/tarefas.md` (hoje só no working tree da `main`, sem commit) com a descrição nova
- `CLAUDE.md` (seção Stack) com as versões novas
- verificação local: versão resolvida de cada pacote alertado, em todas as configurações do Gradle e no
  `buildEnvironment`, maior ou igual à primeira versão corrigida

**Fora:**
- proteção da `main` — já configurada pelo usuário (ruleset `main` exige `build`, `secrets` e `dependencies`,
  com branch atualizado)
- atualizar o que não está vulnerável: Spotless, plugin do Lombok, JaCoCo, WireMock, Gradle wrapper,
  `io.spring.dependency-management` (1.1.7 já é a mais recente)
- Checkstyle 14.x
- tirar o Spring AI de `application`/controller (T09), timeouts e retries de produção (T10), logging `DEBUG` do
  Spring AI em `application.properties`
- smoke test contra a OpenAI real
- ADR

## Decisões

- Spring Boot 4.1.2, não 4.0.9 — o Spring AI 2.0.0-M4 foi compilado contra o Boot 4.1.0-M2 e o 2.0.2 contra o
  4.1.2; o suporte OSS do Boot 4.0 termina em dezembro de 2026; o plugin 4.1.2 já traz `httpclient5` 5.6.5 e
  `httpcore5` 5.4.4, dispensando constraint no `buildscript`
- Spring AI 2.0.2 (última versão estável; 2.1.0 ainda em RC) em vez de manter o milestone M4 — o M4 é a última
  versão com o cliente antigo, e qualquer atualização futura traria a migração para o `openai-java` de qualquer jeito
- Checkstyle 13.11.0 — primeira versão sem doxia/`plexus-utils`, sem mudar de versão maior
- Meta: zerar alertas de todas as severidades, não só altos e críticos — sai praticamente de graça com as
  atualizações acima; o que sobrar fica registrado no resumo da fase com o motivo
- Validação da migração do Spring AI só com WireMock — os payloads de `src/test/resources/ai/openai/` são
  conferidos contra a documentação da API da OpenAI (referência da API: chat completions com tool calls,
  audio transcriptions com `response_format=text`, audio speech), não contra a API real
- Fases separadas para Boot e Spring AI — o M4 deve rodar no Boot 4.1.2 (sua base é 4.1.0-M2); se não rodar, as
  fases 1 e 2 viram uma só, com o motivo registrado aqui
- Constraint de `commons-lang3` 3.20.0 no `buildscript` — o plugin do Boot 4.1.2 ainda traz `commons-lang3` 3.16.0
  (via `commons-compress` 1.27.1 do `buildpack-platform`), vulnerável (média, < 3.18.0); alerta não previsto na
  tabela, aprovado pelo usuário na Fase 1
- Sem ADR — atualização de versões, sem decisão arquitetural nova

## Definição de pronto

- [ ] `./gradlew build` passa (Spotless, Checkstyle, `*Test`, `*IT` e JaCoCo), sem `OPENAI_API_KEY`
- [ ] Nenhum pacote alertado pelo Dependabot resolve, em nenhuma configuração do Gradle nem no `buildEnvironment`,
      para uma versão dentro de uma faixa vulnerável
- [ ] `mcp-core` fora da árvore de dependências (ou em versão corrigida, com o motivo em Decisões)
- [ ] `VoiceCommandIT` e `AiProviderIsolationIT` passam com o Spring AI 2.0.2, sem mudar o que os `*IT` verificam
- [ ] `docs/tarefas.md` com a entrada da T27 (descrição nova) e `CLAUDE.md` com Spring Boot 4.1.2 e Spring AI 2.0.2
- [ ] Não verificável localmente (fica para o PR e o pós-merge): job `dependencies` verde no PR; zero alertas
      abertos no Dependabot após o merge

## Para futuros agentes

Execute com `/executar-tarefa`. Durante a execução:

- marque `- [x]` no mesmo commit que conclui o item;
- antes do último commit de uma fase, rode o plano de verificação, escreva o resumo da fase com o resultado e
  mude o status para `Concluída` — tudo nesse mesmo commit;
- na última fase, no mesmo commit, confira a definição de pronto e escreva o resumo final (e o plano de deploy,
  se houver);
- mudança no plano só com aprovação do usuário, registrada em Decisões com o motivo.

Contexto para uma sessão nova:

- Branch `build/dependencias-vulneraveis`; commits `build: ...` (ou `docs: ...` para só documentação); título do
  PR `build: atualiza dependências vulneráveis`.
- O dependency review (`ci.yml`, job `dependencies`) só reprova o que o PR **introduz** em relação ao snapshot da
  `main`; os alertas existentes fecham sozinhos depois do merge, quando a `main` envia o novo snapshot.
- O Dependabot não abre security updates para transitivas gerenciadas por BOM (as tentativas na `main` falharam);
  por isso a correção é sempre pela versão declarada no `build.gradle`.
- Lista viva dos alertas: `gh api "repos/rafaeljc/voice-budgeting-api/dependabot/alerts?state=open&per_page=100" --paginate`.
  Primeira versão corrigida por pacote (maior `first_patched_version` entre os alertas, levantamento de 27/10/2026;
  a verificação usa a lista viva):

  | Pacote | Mínimo seguro | Origem |
  |---|---|---|
  | `io.netty:*` (handler, codec-http, codec-http2, codec-http3, codec-dns, codec-compression, codec-classes-quic, resolver-dns, transport-*-epoll) | 4.2.17.Final | Boot BOM |
  | `org.apache.tomcat.embed:tomcat-embed-core` | 11.0.25 | Boot BOM |
  | `org.springframework:spring-webmvc`, `spring-webflux` | 7.0.9 | Boot BOM |
  | `org.springframework:spring-web`, `spring-expression`, `spring-core` | 7.0.8 | Boot BOM |
  | `org.springframework.boot:spring-boot` / `spring-boot-autoconfigure` | 4.0.6 / 4.0.7 | Boot |
  | `org.springframework.data:spring-data-commons` | 4.0.6 | Boot BOM |
  | `tools.jackson.core:jackson-core`, `jackson-databind` | 3.1.7 | Boot BOM (também no classpath do plugin) |
  | `io.micrometer:micrometer-core` | 1.16.6 | Boot BOM |
  | `ch.qos.logback:logback-core` | 1.5.34 | Boot BOM |
  | `org.apache.logging.log4j:log4j-api` | 2.25.5 | Boot BOM |
  | `org.apache.httpcomponents.core5:httpcore5`, `httpcore5-h2` | 5.4.3 | classpath do plugin do Boot |
  | `org.apache.httpcomponents.client5:httpclient5` | 5.6.3 | classpath do plugin do Boot |
  | `org.codehaus.plexus:plexus-utils` | 3.6.1 | Checkstyle (doxia) |
  | `io.modelcontextprotocol.sdk:mcp-core` | 1.1.1 | Spring AI (`spring-ai-client-chat` → `mcp-json-jackson3`) |

- Script de conferência dos alertas (no scratchpad, não versionado): gera a árvore com
  `./gradlew -q dependencies buildEnvironment > <scratchpad>/deps.txt`, lê a lista viva de alertas com o `gh api`
  acima (`.dependency.package.name`, `.security_vulnerability.vulnerable_version_range`) e, para cada pacote,
  extrai as versões resolvidas (o valor depois de `->`, ou a versão declarada quando não há `->`) e acusa as que
  caem na faixa vulnerável. Saída esperada: nenhuma versão vulnerável.
- Spring AI 2.0.2 — o que já se sabe:
  - todas as propriedades `spring.ai.openai.*` hoje em `application.properties` existem no metadata do 2.0.2; o
    tipo de `spring.ai.openai.chat.options.response-format` mudou (`OpenAiChatModel.ResponseFormat`) — conferir se
    `response-format.type=TEXT` ainda faz bind;
  - os retries passaram para o cliente OpenAI: `spring.ai.openai.max-retries` (padrão 3), além de
    `spring.ai.openai.timeout`; o `spring.ai.retry.max-attempts=1` de `application-test.properties` provavelmente
    deixa de valer para a OpenAI;
  - o SDK `openai-java` costuma usar base URL com `/v1`; o `AiStubConfiguration` registra
    `spring.ai.openai.base-url` com a URL do WireMock e o `OpenAiWireMockStub` casa `/v1/...` — ajustar um ou outro
    para que os caminhos batam;
  - segundo `.claude/rules/testing.md` e `ai-integration.md`, só `support/ai/OpenAiWireMockStub`, a config do stub e
    `src/test/resources/ai/openai/` conhecem o protocolo; nenhum `*IT` muda o que verifica.

## Fase 1: Spring Boot 4.1.2 e Checkstyle 13.11.0

Status: Concluída  <!-- Não iniciada | Em andamento | Concluída -->

- [x] [verificação] Commitar a entrada da T27 em `docs/tarefas.md` (já presente no working tree: item na lista da
      Fase 2, seção `<a id="t27"></a>` e `SEG-07 → T02, T27` na tabela), com a descrição trocada por:
      "Atualizar o Spring Boot (4.0.5 → 4.1.2), o Spring AI (2.0.0-M4 → 2.0.2, que troca o cliente da OpenAI pelo
      SDK `openai-java`) e o Checkstyle (13.4.0 → 13.11.0), eliminando todos os alertas de vulnerabilidade das
      dependências, inclusive as do classpath dos plugins do Gradle. O Dependabot não atualiza dependências
      transitivas gerenciadas pelos BOMs, por isso a atualização é feita nas versões declaradas no build e conferida
      com o dependency review e com o `build`." — commit `docs: adiciona a tarefa de atualização das dependências vulneráveis`
- [x] [verificação] `build.gradle`: plugin `org.springframework.boot` 4.0.5 → 4.1.2; corrigir o que o `build`
      acusar (depreciações que viram erro, mudanças de comportamento nos `*IT`), sem mudar o Spring AI
- [x] [verificação] `build.gradle`: `checkstyle.toolVersion` 13.4.0 → 13.11.0; corrigir violações novas, se houver
- [x] [verificação] `build.gradle`: constraint de `commons-lang3` 3.20.0 no `buildscript` (item incluído com
      aprovação do usuário; ver Decisões)
- [x] [verificação] `CLAUDE.md` (Stack): "Spring Boot 4.0.5" → "Spring Boot 4.1.2"

### Plano de verificação

- `./gradlew build` → `BUILD SUCCESSFUL`, sem `OPENAI_API_KEY`
- `./gradlew -q buildEnvironment | grep -E 'httpcore5|httpclient5|jackson-databind'` → `httpcore5`/`httpcore5-h2`
  ≥ 5.4.3, `httpclient5` ≥ 5.6.3, `jackson-databind` ≥ 3.1.7
- `./gradlew -q dependencies --configuration checkstyle | grep -c plexus-utils` → `0`
- `./gradlew -q dependencies --configuration runtimeClasspath | grep -E 'netty-handler|tomcat-embed-core|spring-webmvc|jackson-databind|micrometer-core|spring-data-commons|logback-core|log4j-api'`
  → todas as versões resolvidas ≥ o mínimo seguro da tabela
- script de conferência dos alertas → só `mcp-core` 1.1.0 ainda vulnerável (resolvido na Fase 2)

### Resumo da fase

Spring Boot 4.0.5 → 4.1.2 e Checkstyle 13.4.0 → 13.11.0, sem nenhum ajuste de código: `./gradlew build` passou de
primeira (Spotless, Checkstyle, `*Test`, `*IT`, JaCoCo), sem `OPENAI_API_KEY`. A entrada da T27 foi commitada em
`docs/tarefas.md` e o `CLAUDE.md` aponta Spring Boot 4.1.2.

Decisão: a conferência achou um alerta fora da tabela — `commons-lang3` 3.16.0 no classpath do plugin do Boot (via
`commons-compress`). Com aprovação do usuário, o `buildscript` ganhou uma constraint para 3.20.0.

Verificação: `httpclient5` 5.6.5, `httpcore5`/`httpcore5-h2` 5.4.4, `jackson-databind` 3.1.7; `plexus-utils` fora do
Checkstyle; Netty 4.2.19, Tomcat 11.0.27, spring-webmvc 7.0.10, logback 1.5.38, log4j-api 2.25.5, micrometer 1.17.2 e
spring-data-commons 4.1.2 no `runtimeClasspath`. O script de conferência só acusa `mcp-core` 1.1.0 (Fase 2); as linhas
`{strictly ...}` de constraints que ele também lista são falsos positivos do parser (resolvem para versões corrigidas).

Para uma sessão nova: o script de conferência vive no scratchpad e não é versionado; ao reescrevê-lo, ignore as linhas
de constraint (`(c)`, `{strictly ...}`) e considere também pacotes fora da tabela (ex.: `commons-lang3`, `netty-handler-proxy`).

## Fase 2: Spring AI 2.0.2

Status: Em andamento  <!-- Não iniciada | Em andamento | Concluída -->

- [x] [verificação] `build.gradle`: `spring-ai-bom` 2.0.0-M4 → 2.0.2; ajustar `TransactionController` só se a API
      de `TranscriptionModel`, `ChatClient` ou `TextToSpeechModel` mudar, mantendo o comportamento
- [x] [verificação] `application.properties`: ajustar as propriedades `spring.ai.*` que mudaram de nome ou tipo no
      2.0.2 (conferir o bind de `chat.options.response-format.type=TEXT` e das opções de transcrição e voz),
      mantendo os mesmos valores
- [x] [verificação] `application-test.properties`: trocar `spring.ai.retry.max-attempts=1` por
      `spring.ai.openai.max-retries=0` (manter o `spring.ai.retry.*` só se ainda for usado), para o
      `AiProviderIsolationIT` continuar falhando rápido com `ConnectException` na causa raiz
- [x] [verificação] Stub da OpenAI: alinhar base URL (`AiStubConfiguration`) e caminhos (`OpenAiWireMockStub`) ao
      SDK `openai-java`, e conferir `chat-tool-call.json`, `chat-answer.json`, a resposta de transcrição
      (`text/plain`) e a de voz (`audio/mpeg`) contra a documentação da API da OpenAI; `VoiceCommandIT` passa sem
      mudar o que verifica
- [x] [verificação] `mcp-core`: conferir que saiu da árvore; se continuar, adicionar constraint para a versão
      corrigida mais recente da linha 1.1.x e registrar em Decisões
- [ ] [verificação] `CLAUDE.md` (Stack): "Spring AI 2.0.0-M4" → "Spring AI 2.0.2"

### Plano de verificação

- `./gradlew build` → `BUILD SUCCESSFUL`, sem `OPENAI_API_KEY`
- `./gradlew integrationTest --tests "dio.budgeting.infrastructure.http.VoiceCommandIT"` → passa
- `./gradlew integrationTest --tests "dio.budgeting.support.ai.AiProviderIsolationIT"` → passa, em poucos segundos
  (sem retries com backoff)
- `./gradlew -q dependencies --configuration runtimeClasspath | grep -c mcp-core` → `0` (ou versão ≥ 1.1.1, se
  houver constraint)
- `./gradlew -q dependencies --configuration runtimeClasspath | grep 'spring-ai-openai'` → `2.0.2`
- script de conferência dos alertas → nenhuma versão vulnerável em nenhuma configuração
- `grep -nE '4\.0\.5|2\.0\.0-M4' CLAUDE.md build.gradle` → nenhuma linha

### Resumo da fase

_(preencher ao concluir a fase)_

## Resumo final

_(preencher ao concluir todas as fases)_

## Plano de deploy

_(preencher ao concluir todas as fases)_ — passos do usuário após o merge: conferir o job `dependencies` verde no
PR e, depois do merge, `gh api "repos/rafaeljc/voice-budgeting-api/dependabot/alerts?state=open" --jq length` → `0`.
