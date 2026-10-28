# Budgeting — API de orçamento por voz

API que registra e consulta transações financeiras a partir de comandos de voz: áudio → transcrição →
interpretação pela IA (tool calling) → proposta → confirmação → resposta falada. Toda escrita exige
confirmação explícita do usuário; consultas respondem direto.
Escopo e plano: `docs/escopo.md`, `docs/tarefas.md`. Plan files das tarefas: `docs/tarefas/`. Decisões: `docs/adr/`.

## Stack

- Java 25 (toolchain), Spring Boot 4.1.2, Gradle 9.4.1 (Groovy DSL, wrapper)
- Spring Web MVC, Spring Data JPA, MySQL 9.6 (`compose.yml`, iniciado pelo `spring-boot-docker-compose`)
- Spring AI 2.0.2 + OpenAI: `whisper-1` (transcrição), `gpt-4o-mini` (chat + tools), `gpt-4o-mini-tts` (voz)
- Spring Boot Actuator e Micrometer Tracing com OpenTelemetry (logs JSON ECS com `traceId`, `X-Request-Id`)
- Lombok (`io.freefair.lombok`), Jackson 3 (`tools.jackson.*`)
- JUnit 5, AssertJ, Mockito, Testcontainers, WireMock
- Spotless (palantir-java-format) e Checkstyle (convenções, sem formatação), verificados no `build`
- Configuração em `application.properties`; segredos só por variável de ambiente (`OPENAI_API_KEY`, credenciais do
  MySQL), modelo em `.env.example` e `.env` fora do versionamento

## Arquitetura

DDD em camadas. Dependências apontam para dentro: `infrastructure` → `application` → `domain`.

- `domain` — entidades, value objects, IDs tipados, portas de repositório. Sem Spring, JPA ou Spring AI.
- `application` — use cases, `input/`, `output/`. Sem web, JPA ou Spring AI.
- `infrastructure` — HTTP, persistência, IA, segurança e configuração.

## Estrutura

```
src/main/java/dio/budgeting/
  BudgetingApplication.java
  domain/                       # Transaction, TransactionId, Money, Category, TransactionRepository
  application/                  # *UseCase
    input/  output/
  infrastructure/
    http/                       # *Controller
      request/  response/  filter/   # *Filter
    persistence/
      entity/  repository/      # *Entity, Jpa*Repository, *EntityRepository
src/main/resources/
  application.properties
  prompts/                      # prompts do sistema (*.st, UTF-8)
src/test/resources/audio/       # áudios de exemplo para testes
src/test/resources/ai/          # payloads da IA simulada, por provedor (ai/openai/)
```

## Nomenclatura

- Use case: `<Verbo><Substantivo>UseCase`, um único método público `execute(...)`
- DTOs da aplicação: `<Verbo><Substantivo>Input`, `<Substantivo>Output`
- DTOs HTTP: `<Verbo?><Substantivo>Request`, `<Substantivo>Response`; controller: `<Substantivo>Controller`
- Persistência: porta `<X>Repository` (domain) → adaptador `Jpa<X>Repository` → Spring Data `<X>EntityRepository`;
  entidade JPA `<X>Entity`
- ID tipado: `<X>Id` (record sobre `UUID`)
- Exceções: `<Condição>Exception` (ex.: `TransactionNotFoundException`)
- Testes: `<Classe>Test` (unitário), `<Funcionalidade>IT` (integração)
- Documentos: kebab-case minúsculo (ADR: `docs/adr/NNNN-<titulo>.md`); maiúsculas só em arquivos reconhecidos
  por ferramentas (`README.md`, `CLAUDE.md`, `LICENSE`)

## Padrões que usamos

- `record` para IDs, value objects e DTOs; classe para entidades de domínio
- Injeção por construtor com campos `private final`
- Forma idiomática do Spring: usar o que o framework oferece (injeção por construtor, anotações, auto-configuração,
  `@ConfigurationProperties`, `@Transactional`) em vez de reimplementar à mão; nos testes, `@ExtendWith(MockitoExtension.class)`
  com `@Mock` e `@InjectMocks`, não `mock(...)` e `new` manuais
- Mapeamento manual: `Response.from(output)`, `request.toInput()`, `entity.toDomain()`
- Invariantes validadas no construtor (compact constructor nos records)
- Logger SLF4J declarado explicitamente (`LoggerFactory.getLogger`); logs estruturados com pares chave-valor
  (`log.atInfo().addKeyValue(...)`), só com IDs e metadados, nunca conteúdo ou dados de negócio
- Opções dos modelos de IA em `application.properties`, nunca fixas no código
- Comentários só para o porquê (decisão, restrição, caso não óbvio), nunca para o que o código faz

## Padrões que não usamos

- Injeção por campo (`@Autowired` em atributo)
- MapStruct / ModelMapper
- Entidade JPA fora de `infrastructure`; anotações do Spring AI fora de `infrastructure`
- `System.out` / `System.err` — usar o logger
- Segredos, chaves ou IPs em arquivos versionados
- Escrita executada direto pela IA — a IA só cria propostas; o usuário confirma

## Comandos

```bash
git config core.hooksPath .githooks   # uma vez por clone: ativa a validação de commits
./gradlew build                       # formatação, Checkstyle, todos os testes e cobertura
./gradlew test                        # unitários (*Test), sem Docker
./gradlew integrationTest             # integração (*IT), exige Docker
./gradlew test --tests "<FQN>"        # roda um teste específico (integrationTest para um *IT)
./gradlew spotlessApply               # formata o código
set -a && source .env && set +a       # carrega o .env (cp .env.example .env) antes do bootRun
./gradlew bootRun                     # sobe a API (MySQL via compose.yml; exige OPENAI_API_KEY)
```

## Fluxo de trabalho

- Cada tarefa de `docs/tarefas.md` é planejada com `/planejar-tarefa`, executada com `/executar-tarefa` e entregue
  em um PR. O primeiro commit do PR é o plan file `docs/tarefas/t<nn>-<slug>.md` (ex.: `t03-valor-monetario.md`);
  o último commit marca o checkbox da tarefa em `docs/tarefas.md`.
- Branch: `<tipo>/<descrição>` — tipo igual ao do título do PR; descrição em kebab-case, pt-BR sem acentos,
  sem número de tarefa (ex.: `fix/valor-monetario`).
- Commits: Conventional Commits, sem escopo — `<tipo>: <descrição>`.
  Tipos: feat, fix, refactor, perf, test, docs, build, ci, chore.
- Descrição: minúscula, sem ponto final, até 72 caracteres, verbo no presente (ex.: `feat: adiciona ...`).
- Commits de trabalho: só título, nunca corpo.
- Nunca adicionar `Co-authored-by` nem qualquer linha de atribuição.
- PR pela skill `/pr`, criado ou editado só com aprovação do usuário. `git push` e merge são exclusivos do
  usuário (bloqueados por hook); o squash usa `scripts/merge.py`.
- ADR (MADR minimal): só para decisões importantes e não óbvias, criado na tarefa em que a decisão é tomada, a
  partir de `docs/adr/template.md`, com numeração sequencial. ADR aceito não é reescrito: a mudança vira um novo
  ADR, e o antigo passa a `substituído por NNNN` (ou `obsoleto`, se a decisão deixou de valer sem substituta).
- Idioma: código em qualquer pasta, inclusive hooks e scripts (com comentários, logs, mensagens e testes), em
  inglês; documentação, commits, PRs, instruções do agente e conteúdo para o usuário em pt-BR.

## Regras por tema

Carregadas automaticamente ao trabalhar nos arquivos indicados entre parênteses:

- [architecture.md](.claude/rules/architecture.md) (`src/main/java`) — camadas, portas e adaptadores, modelagem, transações de banco
- [api-conventions.md](.claude/rules/api-conventions.md) (`infrastructure/http`) — URLs, status, JSON, listagens, cabeçalhos, áudio
- [error-handling.md](.claude/rules/error-handling.md) (`src/main/java`) — Problem Details, exceções, mapeamento, logs de erro
- [ai-integration.md](.claude/rules/ai-integration.md) (`prompts`, `infrastructure/ai`) — etapas, prompts, tools, regras de produto, testes
- [security.md](.claude/rules/security.md) (`infrastructure/security`) — identidade, senhas, isolamento por usuário
- [testing.md](.claude/rules/testing.md) (`src/test`) — tipos de teste, OpenAI simulada, escrita
