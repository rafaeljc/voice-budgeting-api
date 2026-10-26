# T02 — Integração contínua e base de testes

Configurar a integração contínua (build, padrão de código, testes, segredos e vulnerabilidades das dependências)
e criar a base de testes unitários e de integração, que rodam sem acesso a nenhum provedor de IA. Remover as
credenciais fixas da configuração versionada e documentar a execução da aplicação e dos testes (QA-05, QA-01,
QA-02, SEG-07, SEG-08, DOC-01).

## Escopo

**Dentro:**
- `compose.yml` sem credenciais, com `.env` local (não versionado) e `.env.example` versionado
- padrão de código garantido no build: Spotless (palantir-java-format) para formatação, Checkstyle para
  convenções, e um hook do Claude Code que formata cada `.java` editado pelo agente
- separação `*Test` (task `test`) / `*IT` (task `integrationTest`) no Gradle, as duas no `check`, mais o
  relatório de cobertura JaCoCo, só como relatório
- infraestrutura de testes de integração: MySQL via Testcontainers, limpeza do banco antes de cada teste e IA
  simulada com WireMock por trás de uma fixture neutra
- testes de exemplo: `PersistTransactionUseCaseTest`, `TransactionEndpointsIT`, `VoiceCommandIT`,
  `MySqlCleanerIT`, `AiProviderIsolationIT`
- remoção dos cinco `*IT` que chamam a OpenAI real e do `BudgetingApplicationTests`
- workflow `.github/workflows/ci.yml` com os jobs `build`, `secrets` (gitleaks) e `dependencies`
  (dependency-submission e dependency-review)
- README reescrito em pt-BR (pré-requisitos, configuração, execução, testes e CI)
- atualização de `.claude/rules/testing.md`, `.claude/rules/ai-integration.md` e `CLAUDE.md`
- T01 e T02 marcadas em `docs/tarefas.md`

**Fora:**
- `logging.level.org.springframework.ai=DEBUG` e `spring.jpa.show-sql=true` (T04)
- correção de centavos em `TransactionOutput` (T03): nenhum teste verifica o valor convertido
- testes de falha da IA (timeout, erro, conexão derrubada) (T10, T15, T16)
- exemplos de requisição e áudio (T07, T12)
- seções finais do README: visão geral, tecnologias, melhorias, aprendizados e contexto de negócio (T26)
- `.github/dependabot.yml` (atualizações de versão)
- mínimo de cobertura obrigatório
- gitleaks como pre-commit local
- avaliação ou troca do provedor de IA
- migrações de schema (`ddl-auto=update` permanece)
- regras de arquitetura com ArchUnit (T09: o código atual viola as camadas, por exemplo com `@Tool` em `application`)
- pre-commit de formatação (`.githooks/`)

## Decisões

- Os cinco `*IT` que chamam a OpenAI real são removidos — a regra `testing.md` proíbe teste que chama a OpenAI
  ou exige chave; o `VoiceCommandIT` cobre transcrição, chat com tools e voz com a IA simulada
- `test` roda `*Test` e `integrationTest` roda `*IT`, no mesmo source set `src/test/java`, e o `check` depende
  das duas — o ciclo TDD fica rápido e sem Docker, e o `build` (local e CI) roda tudo
- Provedor de IA isolado na fixture de testes: os testes usam a interface neutra `AiStub`, e só a
  `OpenAiWireMockStub` e os payloads em `src/test/resources/ai/openai/` conhecem o protocolo da OpenAI. Motivo:
  o usuário quer poder trocar de provedor (a chave da OpenAI é paga) sem reescrever os testes
- Composição por anotações, não por classe base: `@IntegrationTest` traz só o que todo IT precisa (contexto,
  MySQL e limpeza do banco); `@AutoConfigureAiStub` é opt-in, porque nem todo IT usa IA
- Classes de suporte com nome de tecnologia (`support/mysql/`, `OpenAiWireMockStub`); neutralidade só no que os
  testes usam (`@IntegrationTest`, `@AutoConfigureAiStub`, `AiStub`) — o código de suporte é específico da
  tecnologia, e a T20 traz um segundo banco (auditoria), então "Database" seria ambíguo
- Container MySQL singleton da JVM (campo estático importado com `@ImportTestcontainers`), mesma imagem do
  `compose.yml` (`mysql:9.6`) — contextos diferentes (com e sem o stub de IA) usam o mesmo container
- Banco zerado antes de cada teste com `TRUNCATE` das tabelas listadas em `information_schema`, não com rollback
  — o rollback esconderia problemas de commit e de fronteira transacional e falha com outras threads; ler o schema
  cobre sozinho as tabelas futuras. A limpeza roda antes, e não depois, para preservar os dados de um teste que
  falhou até o próximo
- Perfil `test` com `api-key` fictícia, `base-url` inválida (`http://localhost:1`) e uma única tentativa de
  retry — um IT sem `@AutoConfigureAiStub` que acione a IA falha na hora, sem chegar a provedor real
- Aplicação 12-factor: `application.properties` só referencia variáveis de ambiente, sem
  `spring.config.import` de `.env`. O `.env` é lido pelo Docker Compose sozinho e carregado no shell para o
  `bootRun` (`set -a && source .env && set +a`) — um arquivo no diretório de trabalho não deve ser carregado em
  silêncio pela aplicação, e a sintaxe de `.env` difere da de `.properties`
- Vulnerabilidades com recursos nativos do GitHub: dependency-submission (árvore completa do Gradle) e
  dependency-review nos PRs, falhando com severidade alta ou crítica; as vulnerabilidades já existentes viram
  Dependabot alerts. Sem `dependabot.yml` — os alerts e os security updates (ativados nas configurações do
  repositório) já avisam e corrigem as vulnerabilidades. Repositório público, sem custo
- gitleaks em job próprio do CI, em todo o histórico — detecta o vazamento logo, mas não o impede, porque roda
  depois do push
- Actions fixadas por tag de versão maior, não por SHA — sem o `dependabot.yml`, SHAs fixos nunca seriam
  atualizados
- JaCoCo só como relatório (artefato do CI), sem mínimo — o código atual é pequeno e será reescrito nas próximas
  tarefas
- Testes de comportamento que já existe (use case e endpoints): o RED é feito quebrando temporariamente o código
  ou a asserção, para provar que o teste detecta a falha; a quebra não é commitada
- A alteração não commitada de `docs/tarefas.md` (T01 marcada) é guardada com `git stash` antes de criar a
  branch e reaplicada com `git stash pop` na Fase 6; daqui em diante, o último commit de cada tarefa marca o
  checkbox dela
- Formatação com Spotless + palantir-java-format (4 espaços, 120 colunas) — o estilo mais próximo do código
  atual, determinístico e com correção automática (`spotlessApply`); o build e o CI só verificam
- Checkstyle com configuração própria e enxuta, sem regras de formatação — partir das configurações do Google ou
  da Sun conflitaria com o formatador; o Checkstyle cobre só convenções (nomes, `System.out`, `@Autowired`,
  logger do Lombok, asserts do JUnit) e higiene. Violação é erro
- Formatação local por hook do Claude Code (PostToolUse) no `.java` editado, não por pre-commit — o código do
  agente já sai formatado, sem pagar a inicialização do Gradle a cada commit do ciclo TDD; o código manual é
  barrado pelo `build` e pelo CI
- ArchUnit fica para a T09 — as regras de camadas já são violadas hoje, e a correção pertence à T09

## Definição de pronto

- [ ] Nenhuma credencial em arquivos versionados: `compose.yml` sem senha e gitleaks local sem achados novos
- [ ] `./gradlew build` passa sem `OPENAI_API_KEY` e sem o `compose.yml` no ar (exige só o Docker)
- [ ] `./gradlew build` falha com código desformatado ou que viola o Checkstyle; o hook formata o `.java` editado
  pelo agente
- [ ] `./gradlew test` roda só `*Test`; `./gradlew integrationTest` roda só `*IT`
- [ ] Cada `*IT` começa com o banco vazio e, quando usa `@AutoConfigureAiStub`, com o stub sem estado
- [ ] Nenhum teste chama provedor de IA real; nenhum `*IT` cita a OpenAI
- [ ] `.github/workflows/ci.yml` válido (actionlint), com os jobs `build`, `secrets` e `dependencies`
- [ ] README em pt-BR documenta configuração, execução, testes e CI, sem links quebrados
- [ ] `testing.md`, `ai-integration.md` e `CLAUDE.md` refletem os comandos, o padrão de código e a
  infraestrutura de testes
- [ ] T01 e T02 marcadas em `docs/tarefas.md`

## Para futuros agentes

Execute com `/executar-tarefa`. Durante a execução:

- marque `- [x]` no mesmo commit que conclui o item;
- antes do último commit de uma fase, rode o plano de verificação, escreva o resumo da fase com o resultado e
  mude o status para `Concluída` — tudo nesse mesmo commit;
- na última fase, no mesmo commit, confira a definição de pronto e escreva o resumo final (e o plano de deploy,
  se houver);
- mudança no plano só com aprovação do usuário, registrada em Decisões com o motivo.

Notas técnicas (confirme na versão em uso antes de aplicar):

- Spring Boot 4 gerencia o Testcontainers 2.x: módulos `org.testcontainers:testcontainers-mysql` e
  `org.testcontainers:testcontainers-junit-jupiter`, mais `org.springframework.boot:spring-boot-testcontainers`;
  `MySQLContainer` fica em `org.testcontainers.mysql`. MockMvc vem de `spring-boot-starter-webmvc-test`
  (`org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`).
- WireMock: `org.wiremock:wiremock-standalone` (Jetty embutido e sombreado, sem conflito com o do Spring Boot),
  versão estável mais recente, declarada explicitamente.
- Spring AI 2.0.0-M4 aceita `spring.ai.openai.base-url`, que vale para chat, transcrição e voz. A transcrição
  usa `response-format=text` (resposta em texto puro) e a voz, `mp3` (bytes). O tool calling faz duas chamadas a
  `/v1/chat/completions` (o `tool_calls` e, depois do resultado da tool, a resposta final), o que exige cenário
  com estados no WireMock.
- `src/test/resources/application.properties` substituiria o principal no classpath; use
  `application-test.properties` com `@ActiveProfiles("test")` na `@IntegrationTest`.
- O `@ServiceConnection` cria a conexão do datasource a partir do container; o `spring-boot-docker-compose` é
  `developmentOnly` e não entra nos testes.
- JaCoCo: versão com suporte a Java 25 (`toolVersion`), com `executionData` de `test` e de `integrationTest`.
- O healthcheck do `compose.yml` usa `-uapp -papp` fixos; passe a ler do ambiente do container (`$$MYSQL_USER`,
  `$$MYSQL_PASSWORD`).
- O volume local `transaction_data` pode ter sido criado com as credenciais antigas (`app/app`, `root`). Se o
  `bootRun` falhar por autenticação, pergunte ao usuário antes de remover o volume.
- O histórico do git contém as senhas antigas do `compose.yml`. Se o gitleaks as apontar, registre-as em
  `.gitleaksignore` com aprovação do usuário (são credenciais locais sem valor, e o histórico da `main` não é
  reescrito).
- Spotless: plugin `com.diffplug.spotless` e Checkstyle (plugin `checkstyle` do Gradle) em versões com suporte à
  sintaxe do Java 25. O modo `-PspotlessIdeHook=<caminho absoluto>` formata um único arquivo. Se o
  palantir-java-format não suportar o Java 25, pare e pergunte.
- O primeiro PR com dependency-review pode avisar que não há snapshot da `main` para comparar; use
  `retry-on-snapshot-warnings` e registre o aviso no resumo, se ocorrer.

## Fase 1: Padrão de código

Status: Concluída  <!-- Não iniciada | Em andamento | Concluída -->

- [x] [verificação] Remover `OpenAiChatClientIT`, `OpenAiChatModelIT`, `OpenAiSpeechModelIT`,
  `OpenAiTranscriptionModelIT`, `ToolCallingIT` e `BudgetingApplicationTests` — chamam a OpenAI real, usam
  `System.out` (violariam o Checkstyle) e o `contextLoads` exige o MySQL no ar; sem eles, o `build` passa sem
  infraestrutura até a Fase 3 trazer os novos testes
- [x] [verificação] Spotless no `build.gradle`: Java com palantir-java-format, ordem de imports, remoção de
  imports não usados e nova linha no fim; `*.gradle`, `*.yml`, `*.md` e `*.properties` só com remoção de espaços
  no fim da linha e nova linha no fim. Rodar `./gradlew spotlessApply` no projeto em um commit próprio, sem outras
  mudanças
- [x] [verificação] Checkstyle: plugin `checkstyle` no `build.gradle` (`maxWarnings = 0`, `src/main` e
  `src/test`) e `config/checkstyle/checkstyle.xml` com as regras abaixo, sem regras de formatação:
  - convenções: proibir `System.out`/`System.err`; proibir `@Autowired` em `src/main`; proibir
    `lombok.extern.*`; proibir `org.junit.jupiter.api.Assertions` (usar AssertJ)
  - nomes: tipos, métodos, variáveis, constantes, parâmetros e pacotes no padrão Java; métodos de teste no
    padrão `should_<resultado>_when_<condição>` permitidos só em `src/test` (supressão ou configuração própria)
  - higiene: `AvoidStarImport`, `EmptyCatchBlock`, `EqualsHashCode`, `StringLiteralEquality`, `NeedBraces`,
    `MultipleVariableDeclarations`, `OneStatementPerLine`, `FallThrough`, `ModifiedControlVariable`,
    `SimplifyBooleanExpression`
  - tamanho: `MethodLength` (máx. 50) e `ParameterNumber` (máx. 7)
- [x] [verificação] `.claude/hooks/format_java.py` (código em inglês), registrado em `.claude/settings.json`
  como `PostToolUse` com matcher `Edit|Write`: se o arquivo editado for `.java` dentro do projeto, roda
  `./gradlew spotlessApply -PspotlessIdeHook=<caminho absoluto>`; se a formatação falhar, sai com código 2 e a
  mensagem em stderr (o agente vê o erro); nos demais casos, sai com 0 sem fazer nada

### Plano de verificação

Os arquivos temporários são removidos ao fim de cada comando.

- `./gradlew spotlessCheck checkstyleMain checkstyleTest` → `BUILD SUCCESSFUL`
- arquivo temporário em `src/main/java` com `System.out.println` → `./gradlew checkstyleMain` falha citando a
  regra
- arquivo temporário em `src/main/java` com indentação errada → `./gradlew spotlessCheck` falha indicando o arquivo
- o mesmo arquivo desformatado + payload de `PostToolUse` com o `file_path` dele, gerado por um script Python e
  enviado ao `format_java.py` → exit 0 e arquivo formatado; payload com um `.md` → exit 0 e arquivo inalterado
- `./gradlew build` (sem MySQL e sem `OPENAI_API_KEY`) → `BUILD SUCCESSFUL`

### Resumo da fase

Removidos os cinco `*IT` que chamavam a OpenAI real e o `BudgetingApplicationTests`; Spotless (palantir-java-format
8.4.0 do plugin, funcionou com Java 25) aplicado ao projeto em commit próprio; Checkstyle 13.4.0 com
`config/checkstyle/checkstyle.xml` e `suppressions.xml`; hook `format_java.py` registrado em `PostToolUse`.

- Decisões de implementação: os nomes de método são validados por duas regras com `id` (`MainMethodName` e
  `TestMethodName`), e o `suppressions.xml` ativa cada uma só no seu source set; `@Autowired` é proibido só em
  `src/main` (`NoFieldInjection` suprimida em `src/test`). O formato do teste também aceita métodos auxiliares em
  camelCase. O Checkstyle apontou imports com `*` em `TransactionController`, trocados por imports explícitos.
- O `spotlessApply` também ajustou espaços no fim da linha e nova linha no fim em `README.md` e `compose.yml`.
- Verificação: todos os comandos do plano passaram — `spotlessCheck checkstyleMain checkstyleTest` com sucesso;
  `System.out` temporário falha no `checkstyleMain` (`RegexpSinglelineJava`); indentação errada falha no
  `spotlessCheck` citando o arquivo; o hook formata o `.java` (exit 0) e deixa o `.md` inalterado (exit 0);
  `./gradlew build` sem `OPENAI_API_KEY` e sem MySQL passa (ainda sem testes: a Fase 3 os traz).
- Para uma sessão nova: o `build.gradle` ainda não tem a separação `test`/`integrationTest` nem o JaCoCo (Fase 3).
  O `gradlew spotlessApply` é o comando de correção de formatação.

## Fase 2: Segredos fora do versionamento

Status: Não iniciada  <!-- Não iniciada | Em andamento | Concluída -->

- [ ] [verificação] `compose.yml`: `MYSQL_ROOT_PASSWORD`, `MYSQL_USER` e `MYSQL_PASSWORD` por `${VAR:?mensagem}`;
  healthcheck lendo usuário e senha do ambiente do container; `MYSQL_DATABASE` permanece fixo (não é segredo)
- [ ] [verificação] `.env.example` com `MYSQL_ROOT_PASSWORD`, `MYSQL_USER`, `MYSQL_PASSWORD` e `OPENAI_API_KEY`,
  com valores de exemplo e comentários; `!.env.example` no `.gitignore`

### Plano de verificação

- `env -u MYSQL_ROOT_PASSWORD -u MYSQL_USER -u MYSQL_PASSWORD docker compose --env-file /dev/null config` → falha
  com a mensagem de variável obrigatória ausente
- `docker compose --env-file .env.example config` → exit 0, credenciais vindas do `.env.example`
- `grep -nE 'PASSWORD: *[^$ ]|-papp|-uapp' compose.yml` → nenhuma linha
- `git check-ignore .env; git check-ignore .env.example` → só `.env` impresso
- `cp .env.example .env` (se não existir) e `set -a && source .env && set +a && OPENAI_API_KEY=dummy timeout 120 ./gradlew bootRun`
  → log com `Started BudgetingApplication` (encerrado pelo timeout)
- `./gradlew build` → `BUILD SUCCESSFUL`

### Resumo da fase

_(preencher ao concluir a fase)_

## Fase 3: Divisão dos testes e banco nos testes de integração

Status: Não iniciada  <!-- Não iniciada | Em andamento | Concluída -->

- [ ] [verificação] `build.gradle`: `test` filtrando `*Test`; task `integrationTest` (mesmo source set, filtrando
  `*IT`, `shouldRunAfter test`); `check` dependendo de `integrationTest` e de `jacocoTestReport`;
  `jacocoTestReport` (HTML e XML) com os dados das duas tasks; dependências de teste (webmvc-test,
  spring-boot-testcontainers, testcontainers-mysql, testcontainers-junit-jupiter)
- [ ] [verificação] `src/test/resources/application-test.properties`: `spring.ai.openai.api-key` fictícia,
  `spring.ai.openai.base-url=http://localhost:1` e `spring.ai.retry.max-attempts=1` (confirmar o nome da
  propriedade)
- [ ] [verificação] `support/IntegrationTest` (meta-anotação: `@SpringBootTest`, `@ActiveProfiles("test")`,
  `@ImportTestcontainers(MySqlTestContainer.class)`, `@ExtendWith(ResetMySqlExtension.class)`) e
  `support/mysql/MySqlTestContainer` (interface com o container `mysql:9.6` estático, `@Container` e
  `@ServiceConnection`)
- [ ] [TDD] `support/mysql/MySqlCleaner` (lista as tabelas do schema em `information_schema.tables` e roda
  `TRUNCATE` em cada uma, com `FOREIGN_KEY_CHECKS=0` durante a limpeza) e `ResetMySqlExtension`
  (`BeforeEachCallback` que obtém o cleaner do contexto via `SpringExtension.getApplicationContext`) —
  `MySqlCleanerIT.should_removeAllRows_when_cleaned`
- [ ] [TDD] `AiProviderIsolationIT.should_failWithoutExternalCall_when_aiIsNotStubbed`: sem
  `@AutoConfigureAiStub`, chamar o `TranscriptionModel` lança exceção cuja causa raiz é `ConnectException`
- [ ] [TDD] `PersistTransactionUseCaseTest.should_saveTransactionWithInputData_when_executed`: Mockito na porta
  `TransactionRepository`, verifica descrição, valor (centavos, sem conversão) e categoria gravados e o id da saída
- [ ] [TDD] `TransactionEndpointsIT.should_returnCreatedTransaction_when_listingByCategory`
  (`@IntegrationTest` + `@AutoConfigureMockMvc`): `POST /transactions` responde 201, e
  `GET /transactions/{category}` devolve a transação com id, descrição e categoria (o valor não é verificado — T03)

### Plano de verificação

- `./gradlew test` → `BUILD SUCCESSFUL`; `ls build/test-results/test/` → só arquivos de classes `*Test`
- `env -u OPENAI_API_KEY ./gradlew integrationTest` (com o `compose.yml` parado) → `BUILD SUCCESSFUL`;
  `ls build/test-results/integrationTest/` → só arquivos de classes `*IT`
- `env -u OPENAI_API_KEY ./gradlew build` → `BUILD SUCCESSFUL`, com `build/reports/jacoco/test/html/index.html`
  gerado
- `grep -rlnE 'EnabledIfEnvironmentVariable|System\.(out|err)' src/test` → nenhum arquivo

### Resumo da fase

_(preencher ao concluir a fase)_

## Fase 4: IA simulada e fluxo de voz

Status: Não iniciada  <!-- Não iniciada | Em andamento | Concluída -->

- [ ] [TDD] `VoiceCommandIT.should_persistTransactionAndReturnSpeech_when_commandRegistersExpense`
  (`@IntegrationTest` + `@AutoConfigureMockMvc` + `@AutoConfigureAiStub`): o stub transcreve "gastei 80 reais no
  mercado", o chat pede `persist-transaction` e depois devolve a resposta final, e a voz devolve bytes fixos;
  `POST /transactions/ai` (multipart com `audio/recording-1.m4a`) responde 200 `audio/mp3` com esses bytes, e a
  transação fica gravada (porta `TransactionRepository`). Para fazê-lo passar, criar o suporte de IA:
  - `support/ai/AiStub` — interface neutra: `givenTranscription(String)`, `givenToolCall(String tool, String argumentsJson)`,
    `givenAnswer(String)`, `givenSpeech(byte[])`, `reset()`
  - `support/ai/OpenAiWireMockStub` — implementação com WireMock (rotas `/v1/audio/transcriptions`,
    `/v1/chat/completions` com cenário por estados, `/v1/audio/speech`), payloads em `src/test/resources/ai/openai/`
  - `support/ai/AutoConfigureAiStub` — meta-anotação: `@Import` de uma configuração de teste com o
    `WireMockServer` em porta dinâmica, o `DynamicPropertyRegistrar` de `spring.ai.openai.base-url` e o bean
    `AiStub`, mais `@ExtendWith(ResetAiStubExtension.class)`
  - `support/ai/ResetAiStubExtension` — `BeforeEachCallback` que chama `aiStub.reset()`
  - dependência `org.wiremock:wiremock-standalone`

### Plano de verificação

- `env -u OPENAI_API_KEY ./gradlew integrationTest --tests "dio.budgeting.infrastructure.http.VoiceCommandIT"`
  (pacote conforme criado) → `BUILD SUCCESSFUL`
- `grep -rli 'openai' src/test/java --include='*IT.java'` → nenhum arquivo
- `env -u OPENAI_API_KEY ./gradlew build` → `BUILD SUCCESSFUL`

### Resumo da fase

_(preencher ao concluir a fase)_

## Fase 5: Integração contínua

Status: Não iniciada  <!-- Não iniciada | Em andamento | Concluída -->

- [ ] [verificação] `.github/workflows/ci.yml`: gatilhos `pull_request` e `push` na `main`; `permissions:
  contents: read` no topo; jobs:
  - `build` — `actions/checkout`, `actions/setup-java` (Temurin 25), `gradle/actions/setup-gradle`,
    `./gradlew build` (inclui `spotlessCheck` e Checkstyle pelo `check`); `actions/upload-artifact` com os relatórios de teste e de cobertura (sempre, inclusive em
    falha)
  - `secrets` — checkout com `fetch-depth: 0` e `gitleaks/gitleaks-action` com `GITHUB_TOKEN`
  - `dependencies` — `contents: write`; `gradle/actions/dependency-submission` nos dois gatilhos e
    `actions/dependency-review-action` só em `pull_request`, com `fail-on-severity: high` e
    `retry-on-snapshot-warnings: true`
- [ ] [verificação] Rodar o gitleaks no histórico; se houver achado que não é segredo real, registrar em
  `.gitleaksignore` com aprovação do usuário

### Plano de verificação

- `docker run --rm -v "$PWD:/repo" -w /repo rhysd/actionlint:latest` → exit 0, sem saída
- `docker run --rm -v "$PWD:/repo" zricethezav/gitleaks:latest git /repo` → `no leaks found`
- `env -u OPENAI_API_KEY ./gradlew build` → `BUILD SUCCESSFUL` (o comando do job `build`)

### Resumo da fase

_(preencher ao concluir a fase)_

## Fase 6: Documentação

Status: Não iniciada  <!-- Não iniciada | Em andamento | Concluída -->

- [ ] [verificação] Reescrever o `README.md` em pt-BR: título, resumo em 2–3 frases e badge do CI;
  pré-requisitos (JDK 25, Docker); configuração (`.env.example` → `.env`, tabela das variáveis, nenhuma credencial
  versionada); execução (`set -a && source .env && set +a && ./gradlew bootRun`, MySQL pelo `compose.yml`,
  volume criado com credenciais antigas); testes (`test`, `integrationTest`, `build`, relatório de cobertura, sem
  chave de IA); padrão de código (`./gradlew spotlessApply` antes do commit; Checkstyle no `build`); CI (o que
  cada job verifica). Remover a parte didática do curso e os links `../README.md`
- [ ] [verificação] `.claude/rules/testing.md`: remover o aviso de rascunho e o "A definir na T02"; documentar
  tasks, CI, `@IntegrationTest`, `@AutoConfigureAiStub`/`AiStub`, limpeza do banco e payloads em
  `src/test/resources/ai/<provedor>/`. `.claude/rules/ai-integration.md`: seção "Testes" com a fixture neutra
- [ ] [verificação] `CLAUDE.md`: comandos `./gradlew test`, `./gradlew integrationTest` e
  `./gradlew spotlessApply`; em "Stack", Spotless (palantir-java-format) e Checkstyle; `bootRun` com `.env`;
  em "Stack", segredos por variável de ambiente (`.env.example`); em "Estrutura", `src/test/resources/ai/`; em
  "Fluxo de trabalho", o último commit da tarefa marca o checkbox dela em `docs/tarefas.md`
- [ ] [verificação] `git stash pop` (T01 marcada) e marcar a T02 em `docs/tarefas.md`

### Plano de verificação

- `grep -nE 'Rascunho|A definir na T02' .claude/rules/testing.md` → nenhuma linha
- `grep -n '\.\./README' README.md` → nenhuma linha
- `grep -c 'integrationTest' README.md CLAUDE.md .claude/rules/testing.md` → ≥ 1 em cada
- `grep -nE '^- \[x\] \[T0[12] ' docs/tarefas.md` → duas linhas
- `git stash list` → sem a entrada desta tarefa
- `env -u OPENAI_API_KEY ./gradlew build` → `BUILD SUCCESSFUL`

### Resumo da fase

_(preencher ao concluir a fase)_

## Resumo final

_(preencher ao concluir todas as fases)_

## Plano de deploy

_(preencher ao concluir todas as fases)_ Passos do usuário no GitHub, após o merge:

- ativar Dependabot alerts e Dependabot security updates (Settings → Code security)
- criar a regra de proteção da `main` exigindo os jobs `build`, `secrets` e `dependencies`
- conferir o primeiro run do CI na `main`
