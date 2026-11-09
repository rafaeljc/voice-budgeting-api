# T22 — Métricas técnicas e de negócio

Tornar visíveis as métricas técnicas que a aplicação já coleta (latência por etapa da IA, erros e uso de recursos) e
criar a única métrica de negócio que falta, a contagem dos desfechos das interpretações da IA, para avaliar se a
funcionalidade de voz identifica corretamente o comando do usuário e propõe a ação certa. A visualização é local, no
Grafana do container `grafana/otel-lgtm`.

## Escopo

**Dentro:**
- Métricas técnicas (OBS-02), sem código novo, só expostas e documentadas:
  - latência por etapa da IA: `gen_ai.client.operation` (Spring AI), tag `gen_ai.operation.name` = `transcription`,
    `chat`, `text_to_speech`
  - erros: `http.server.requests` (tags `uri`, `status`, `outcome`), inclusive o 422 `invalid-command` e o total de
    comandos de voz (`/api/v1/transactions/ai`)
  - recursos: `hikaricp.connections.active`, `hikaricp.connections.pending`, `jvm.memory.used`, `process.cpu.usage`
- Métrica de negócio (OBS-03), o único código novo:
  - `infrastructure/event/DomainEventMetricsListener` — `@Component`, injeção do `MeterRegistry` pelo construtor,
    método `@EventListener void on(DomainEvent event)` com `switch` no evento selado que incrementa direto o contador
    (sem use case):
    - `TransactionProposalConfirmedEvent` → `outcome=confirmed`
    - `TransactionProposalCanceledEvent` → `outcome=canceled`
    - `VoiceCommandNotUnderstoodEvent` → `outcome=not_understood`
    - `TransactionProposalCreatedEvent` → não conta
  - contador único `budgeting.ai.interpretations`, tag `outcome` (no Prometheus,
    `budgeting_ai_interpretations_total{outcome="..."}`)
  - comentário do porquê do `@EventListener` (e não `@TransactionalEventListener`): não gera publicação no registro do
    Modulith
- Exposição:
  - `application.properties`: `management.endpoints.web.exposure.include=health,metrics`, com comentário
  - `SecurityConfiguration`: `/actuator/metrics` e `/actuator/metrics/**` públicos (`permitAll`, ao lado do
    `/actuator/health`), com comentário em inglês: públicos só porque o projeto não vai para produção; em produção,
    acesso controlado ou o Actuator numa porta de gerenciamento não exposta publicamente
- Visualização:
  - `compose.yml`: serviço `grafana/otel-lgtm` com tag fixa (versão mais recente, conferida na execução), portas
    `3000` (Grafana), `4317` e `4318` (OTLP); o `spring-boot-docker-compose` o reconhece e aponta a aplicação para o
    coletor (métricas e traces)
  - `application.properties`: `management.otlp.metrics.export.enabled=true` (hoje `false`), com comentário
  - `application-test.properties`: `management.otlp.metrics.export.enabled=false`, com comentário, só se os `*IT`
    tentarem exportar sem coletor e poluírem o log
- Documentação:
  - README: seção de métricas — `/actuator/metrics` com `curl` (sem token), Grafana em `http://localhost:3000` →
    Explore, lista das métricas acima e as consultas PromQL das taxas (nomes conferidos na aplicação rodando):
    - acerto das propostas = confirmadas ÷ (confirmadas + canceladas)
    - comandos não entendidos = `not_understood` ÷ respostas 200 de `/api/v1/transactions/ai`
    - comandos com argumentos inválidos = respostas 422 ÷ total de `/api/v1/transactions/ai`
  - README: o trecho "Os traces não são exportados enquanto não houver um endpoint OTLP configurado" passa a dizer que,
    localmente, vão para o container do `compose.yml` (Grafana); a variável de endpoint fica para os demais ambientes
  - `.claude/rules/architecture.md`: em "Não fazer", "Listener de evento que não passa por um use case" vira
    "Listener de evento **com lógica de negócio** que não passa por um use case"
  - `docs/escopo.md`: justificativa do FUT-16 ajustada — o coletor local `grafana/otel-lgtm` existe desde a T22; segue
    futuro só o envio ao X-Ray em produção (ADOT Collector ou CloudWatch Agent)
  - `CLAUDE.md`: a linha da stack de observabilidade cita o Grafana LGTM local (`compose.yml`)
  - `docs/tarefas.md`: nota na T15 (expor as métricas do Resilience4j com `resilience4j-micrometer` — estado do
    circuit breaker e do bulkhead — que o OBS-02 pede e a T22 não cobriu) e o checkbox da T22

**Fora:**
- métricas de circuit breaker e bulkhead (T15)
- contagem de propostas expiradas e criadas
- métrica de acerto das consultas (resumo por categoria)
- dashboard provisionado no Grafana (FUT-13)
- acesso controlado ao Actuator, porta de gerenciamento interna, exportação OTLP para coletor de produção
- tokens e custo de IA (T23)
- publicações de auditoria `FAILED`, métricas por usuário, percentis e histogramas
- testes automatizados (unitários e `*IT`)
- mudanças no `security.md`, no ADR 0002 e ADR novo
- deploy

## Decisões

- Sem métricas de circuit breaker e bulkhead — a T15 (dependência da T22) ainda não foi feita; a T15 ganha a nota para
  expor as do Resilience4j (decisão do usuário)
- Critério do projeto: implementar só o mínimo para a funcionalidade; o que uma aplicação robusta em produção exigiria a
  mais é só documentado (decisão do usuário)
- Métricas técnicas sem código — latência por etapa (`gen_ai.client.operation`), erros (`http.server.requests`) e
  recursos (Hikari, JVM, processo) já são coletadas pelo Spring AI e pelo Actuator
- `/actuator/metrics` público, sem JWT; a observação sobre produção fica só no comentário do `SecurityConfiguration`,
  e o `security.md` não muda (decisão do usuário). A regra "`permitAll` além do `/actuator/health`" do `security.md`
  continua escrita; a exceção é deliberada
- Visualização pelo `grafana/otel-lgtm` no `compose.yml` + exportação OTLP de métricas ligada — uma imagem com Grafana,
  Prometheus e coletor já ligados, reconhecida pelo `spring-boot-docker-compose`, sem código nem dependência nova
  (`micrometer-registry-otlp` já está no classpath); antecipa a parte local do FUT-16 (decisão do usuário)
- Intervalo de exportação OTLP explícito em `application.properties` (`management.otlp.metrics.export.step=1m`, o
  padrão do Spring Boot) — o tempo até uma métrica aparecer no Grafana fica visível na configuração (decisão do usuário)
- Sem dashboard; o Explore do Grafana e as consultas no README bastam — dashboard é o FUT-13 (decisão do usuário)
- O objetivo da métrica de negócio é avaliar se a IA identifica corretamente o comando e propõe a ação certa: confirmada
  = acertou, cancelada = errou na proposta, não entendido = não identificou o comando (decisão do usuário)
- Sem contador de expiradas — desvia do OBS-03: a expiração na grande maioria das vezes é falha externa (rede, cliente),
  e um cancelamento que não chega também vira expiração; não diz nada sobre a interpretação (decisão do usuário)
- Sem contador de criadas — só serviria de denominador junto com as expiradas; o acerto usa confirmadas + canceladas,
  e o total de comandos vem do `http.server.requests`
- Um contador com tag (`budgeting.ai.interpretations`, `outcome`), não três contadores — convenção do Micrometer e do
  Prometheus para a mesma grandeza com variações; nome pelo que se conta (interpretações da IA com desfecho conhecido),
  valores no vocabulário do projeto (`confirmed`/`canceled`, como os endpoints e os eventos) (decisão do usuário)
- Listener fala direto com o Micrometer, sem use case — métrica é preocupação técnica, de infraestrutura, sem regra de
  negócio; a regra do `architecture.md` é ajustada para enfatizar "lógica de negócio" (decisão do usuário)
- Listener em `infrastructure/event/`, ao lado do `DomainEventAuditListener` — convenção do `CLAUDE.md` (adaptadores
  agrupados pelo mecanismo que os aciona) (decisão do usuário)
- `@EventListener`, não `@TransactionalEventListener` — com este último, cada evento ganharia uma publicação no registro
  do Modulith (nota da T21). Consequência aceita: o contador é incrementado antes do commit, então uma decisão cuja
  transação falhe no commit ainda é contada
- Sem testes — desvia do QA-01 transversal ("cada tarefa inclui os testes do que altera"); a verificação sobe a
  aplicação e confere as métricas (decisão do usuário)
- Sem ADR — nenhuma decisão importante e não óbvia de arquitetura; o ADR 0002 ("exportação OTLP de métricas desligada
  até a T22") fica como histórico
- Sem a verificação com a chave da OpenAI — não há chave disponível: os comandos de voz reais
  (`/api/v1/transactions/ai`) saem do plano de verificação, e confirmar e cancelar usam propostas inseridas direto no
  MySQL. Ficam sem verificação o `outcome=not_understood` e os valores das taxas de não entendidos e de 422 (as
  consultas são validadas só na sintaxe) (decisão do usuário)
- Uma fase; tipo `feat`; branch `feat/metricas-tecnicas-e-de-negocio`

## Definição de pronto

- [x] `./gradlew build` passa (Spotless, Checkstyle, testes e cobertura)
- [x] `GET /actuator/metrics` responde 200 sem token e lista `budgeting.ai.interpretations`, `gen_ai.client.operation`,
  `http.server.requests`, `hikaricp.connections.active`, `jvm.memory.used` e `process.cpu.usage`
- [x] depois de confirmar uma proposta e cancelar outra, `budgeting.ai.interpretations` tem `outcome=confirmed` e
  `outcome=canceled` com 1 cada
- [x] o Prometheus do `grafana/otel-lgtm` (consultado pelo Grafana) retorna `budgeting_ai_interpretations_total`
- [x] README documenta o endpoint, o Grafana, as métricas e as consultas das três taxas com os nomes reais
- [x] `architecture.md`, `escopo.md`, `CLAUDE.md` e `tarefas.md` atualizados como no escopo

## Para futuros agentes

Execute com `/executar-tarefa`. Durante a execução:

- marque `- [x]` no mesmo commit que conclui o item;
- antes do último commit de uma fase, rode o plano de verificação, escreva o resumo da fase com o resultado e
  mude o status para `Concluída` — tudo nesse mesmo commit;
- na última fase, no mesmo commit, confira a definição de pronto e escreva o resumo final (e o plano de deploy,
  se houver);
- mudança no plano só com aprovação do usuário, registrada em Decisões com o motivo.

## Fase 1: Métricas expostas e visualizadas no Grafana

Status: Concluída  <!-- Não iniciada | Em andamento | Concluída -->

- [x] [verificação] criar `infrastructure/event/DomainEventMetricsListener` (`@EventListener`, `switch` no
  `DomainEvent`, contador `budgeting.ai.interpretations` com `outcome` = `confirmed` | `canceled` | `not_understood`;
  `TransactionProposalCreatedEvent` não conta) — sem teste, por decisão do usuário
- [x] [verificação] `application.properties`: `management.endpoints.web.exposure.include=health,metrics` e
  `management.otlp.metrics.export.enabled=true`, com comentários
- [x] [verificação] `SecurityConfiguration`: `permitAll` para `/actuator/metrics` e `/actuator/metrics/**`, com o
  comentário sobre produção
- [x] [verificação] `compose.yml`: serviço `grafana/otel-lgtm` (tag fixa, portas `3000`, `4317`, `4318`)
- [x] [verificação] rodar `./gradlew integrationTest` e, se o log mostrar falhas de exportação OTLP, desligar a
  exportação em `application-test.properties` com comentário
- [x] [verificação] subir a aplicação, conferir os nomes reais das métricas no Prometheus (via Grafana) e escrever a
  seção de métricas do README, com as consultas das três taxas; ajustar o trecho dos traces
- [x] [verificação] `.claude/rules/architecture.md`: regra "Listener de evento com lógica de negócio que não passa por
  um use case"
- [x] [verificação] `docs/escopo.md`: justificativa do FUT-16
- [x] [verificação] `CLAUDE.md`: stack de observabilidade com o Grafana LGTM local
- [x] [verificação] `docs/tarefas.md`: nota na T15 sobre as métricas do Resilience4j
- [x] [verificação] `docs/tarefas.md`: checkbox da T22 (último commit)

### Plano de verificação

- `./gradlew build` → `BUILD SUCCESSFUL`
- `./gradlew integrationTest 2>&1 | grep -i -E "otlp|failed to (publish|export)"` → sem saída
- `set -a && source .env && set +a && ./gradlew bootRun` (em segundo plano) → aplicação e
  container `grafana/otel-lgtm` no ar (`docker compose ps` lista o serviço)
- `curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/actuator/metrics` (sem token) → `200`
- `curl -s http://localhost:8080/actuator/metrics | jq -r '.names[]' | grep -E
  '^(budgeting.ai.interpretations|gen_ai.client.operation|http.server.requests|hikaricp.connections.active|jvm.memory.used|process.cpu.usage)$'`
  → as seis métricas (o `budgeting.ai.interpretations` aparece depois do primeiro incremento, e o
  `gen_ai.client.operation`, depois da primeira chamada à IA)
- duas propostas inseridas direto na tabela `transaction_proposal_entity` do MySQL (sem chamar a IA), uma seguida de
  `POST /api/v1/transaction-proposals/{id}/confirm` e outra de `.../cancel`, com
  `TOKEN=$(python3 scripts/generate_token.py)` →
  `curl -s 'http://localhost:8080/actuator/metrics/budgeting.ai.interpretations?tag=outcome:confirmed' | jq
  '.measurements[0].value'` → `1.0`, e o mesmo com `outcome:canceled` → `1.0`
- depois do intervalo de exportação (~1 min): `curl -s -G
  http://localhost:3000/api/datasources/proxy/uid/prometheus/api/v1/query --data-urlencode
  'query=budgeting_ai_interpretations_total'` → `"status":"success"` com séries `outcome="confirmed"` e
  `outcome="canceled"` (o UID da fonte de dados é conferido na execução)
- cada consulta PromQL do README, pelo mesmo endpoint → `"status":"success"`

### Resumo da fase

Feito: `DomainEventMetricsListener` (`@EventListener`, `switch` no `DomainEvent`, contador
`budgeting.ai.interpretations` com `outcome` = `confirmed` | `canceled` | `not_understood`); `/actuator/metrics` exposto e
público; serviço `grafana/otel-lgtm:0.40.0` no `compose.yml` (a tag mais recente na execução, mesmo digest do `latest`);
exportação OTLP de métricas ligada, com o intervalo explícito (`management.otlp.metrics.export.step=1m`) e desligada em
`application-test.properties` (sem isso, o fechamento de cada contexto de teste logava `Failed to publish metrics to OTLP
receiver` com stack trace); README com a seção de métricas, as três consultas PromQL e o trecho dos traces; regra do
`architecture.md`, FUT-16, `CLAUDE.md` e notas do `tarefas.md` ajustados.

Decisões da execução (usuário): intervalo de exportação explícito; sem a verificação com a chave da OpenAI (ver
Decisões).

Verificação: `./gradlew build` → `BUILD SUCCESSFUL`; `integrationTest` sem falha de OTLP; `/actuator/metrics` → `200` sem
token, com as seis métricas; `outcome=confirmed` e `outcome=canceled` com `1.0` cada (propostas inseridas no MySQL);
`budgeting_ai_interpretations_total` no Prometheus com as duas séries; traces do serviço `budgeting` no Tempo; as três
consultas PromQL com `"status":"success"`, só a de acerto com valor (0,5). Não verificados, por falta de chave:
`not_understood`, `gen_ai.client.operation` com sucesso e as taxas de não entendidos e de 422.

Para uma sessão nova: no Prometheus os nomes diferem do Actuator e vêm em milissegundos
(`http_server_requests_milliseconds_count`, `gen_ai_client_operation_milliseconds_*`), com os rótulos `uri` e `status`;
a fonte de dados tem `uid` `prometheus`. A verificação deixou no MySQL de desenvolvimento uma transação "teste" de
R$ 10,00 (GROCERIES) e os containers do `compose.yml` no ar.

## Resumo final

A T22 expõe as métricas técnicas (latência por etapa da IA, erros HTTP e recursos, já coletadas pelo Spring AI e pelo
Actuator) e cria a única métrica de negócio, `budgeting.ai.interpretations{outcome}`, para avaliar se a IA identifica o
comando e propõe a ação certa. O Grafana local (`grafana/otel-lgtm`) recebe métricas e traces por OTLP; as taxas de
acerto, de não entendidos e de comandos inválidos estão em consultas PromQL no README. Sem testes automatizados e sem
circuit breaker e bulkhead (T15), por decisão do escopo. Lacuna: o fluxo de voz real não foi exercitado, por falta de
chave da OpenAI.

Definição de pronto conferida: todos os critérios marcados; o terceiro foi verificado com propostas inseridas no banco
(ver Decisões).
