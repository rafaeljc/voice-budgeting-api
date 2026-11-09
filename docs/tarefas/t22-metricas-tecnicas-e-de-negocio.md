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
- Uma fase; tipo `feat`; branch `feat/metricas-tecnicas-e-de-negocio`

## Definição de pronto

- [ ] `./gradlew build` passa (Spotless, Checkstyle, testes e cobertura)
- [ ] `GET /actuator/metrics` responde 200 sem token e lista `budgeting.ai.interpretations`, `gen_ai.client.operation`,
  `http.server.requests`, `hikaricp.connections.active`, `jvm.memory.used` e `process.cpu.usage`
- [ ] depois de confirmar uma proposta e cancelar outra, `budgeting.ai.interpretations` tem `outcome=confirmed` e
  `outcome=canceled` com 1 cada
- [ ] o Prometheus do `grafana/otel-lgtm` (consultado pelo Grafana) retorna `budgeting_ai_interpretations_total`
- [ ] README documenta o endpoint, o Grafana, as métricas e as consultas das três taxas com os nomes reais
- [ ] `architecture.md`, `escopo.md`, `CLAUDE.md` e `tarefas.md` atualizados como no escopo

## Para futuros agentes

Execute com `/executar-tarefa`. Durante a execução:

- marque `- [x]` no mesmo commit que conclui o item;
- antes do último commit de uma fase, rode o plano de verificação, escreva o resumo da fase com o resultado e
  mude o status para `Concluída` — tudo nesse mesmo commit;
- na última fase, no mesmo commit, confira a definição de pronto e escreva o resumo final (e o plano de deploy,
  se houver);
- mudança no plano só com aprovação do usuário, registrada em Decisões com o motivo.

## Fase 1: Métricas expostas e visualizadas no Grafana

Status: Em andamento  <!-- Não iniciada | Em andamento | Concluída -->

- [x] [verificação] criar `infrastructure/event/DomainEventMetricsListener` (`@EventListener`, `switch` no
  `DomainEvent`, contador `budgeting.ai.interpretations` com `outcome` = `confirmed` | `canceled` | `not_understood`;
  `TransactionProposalCreatedEvent` não conta) — sem teste, por decisão do usuário
- [x] [verificação] `application.properties`: `management.endpoints.web.exposure.include=health,metrics` e
  `management.otlp.metrics.export.enabled=true`, com comentários
- [x] [verificação] `SecurityConfiguration`: `permitAll` para `/actuator/metrics` e `/actuator/metrics/**`, com o
  comentário sobre produção
- [x] [verificação] `compose.yml`: serviço `grafana/otel-lgtm` (tag fixa, portas `3000`, `4317`, `4318`)
- [ ] [verificação] rodar `./gradlew integrationTest` e, se o log mostrar falhas de exportação OTLP, desligar a
  exportação em `application-test.properties` com comentário
- [ ] [verificação] subir a aplicação, conferir os nomes reais das métricas no Prometheus (via Grafana) e escrever a
  seção de métricas do README, com as consultas das três taxas; ajustar o trecho dos traces
- [ ] [verificação] `.claude/rules/architecture.md`: regra "Listener de evento com lógica de negócio que não passa por
  um use case"
- [ ] [verificação] `docs/escopo.md`: justificativa do FUT-16
- [ ] [verificação] `CLAUDE.md`: stack de observabilidade com o Grafana LGTM local
- [ ] [verificação] `docs/tarefas.md`: nota na T15 sobre as métricas do Resilience4j
- [ ] [verificação] `docs/tarefas.md`: checkbox da T22 (último commit)

### Plano de verificação

- `./gradlew build` → `BUILD SUCCESSFUL`
- `./gradlew integrationTest 2>&1 | grep -i -E "otlp|failed to (publish|export)"` → sem saída
- `set -a && source .env && set +a && ./gradlew bootRun` (em segundo plano; exige `OPENAI_API_KEY`) → aplicação e
  container `grafana/otel-lgtm` no ar (`docker compose ps` lista o serviço)
- `curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/actuator/metrics` (sem token) → `200`
- `curl -s http://localhost:8080/actuator/metrics | jq -r '.names[]' | grep -E
  '^(budgeting.ai.interpretations|gen_ai.client.operation|http.server.requests|hikaricp.connections.active|jvm.memory.used|process.cpu.usage)$'`
  → as seis métricas (o `budgeting.ai.interpretations` aparece depois do primeiro incremento, e o
  `gen_ai.client.operation`, depois do primeiro comando de voz)
- dois comandos de voz com `src/test/resources/audio/recording-1.m4a` (`POST /api/v1/transactions/ai` com
  `TOKEN=$(python3 scripts/generate_token.py)`), um seguido de `POST .../confirm` e outro de `POST .../cancel` →
  `curl -s 'http://localhost:8080/actuator/metrics/budgeting.ai.interpretations?tag=outcome:confirmed' | jq
  '.measurements[0].value'` → `1.0`, e o mesmo com `outcome:canceled` → `1.0`
- depois do intervalo de exportação (~1 min): `curl -s -G
  http://localhost:3000/api/datasources/proxy/uid/prometheus/api/v1/query --data-urlencode
  'query=budgeting_ai_interpretations_total'` → `"status":"success"` com séries `outcome="confirmed"` e
  `outcome="canceled"` (o UID da fonte de dados é conferido na execução)
- cada consulta PromQL do README, pelo mesmo endpoint → `"status":"success"`

### Resumo da fase

_(preencher ao concluir a fase)_

## Resumo final

_(preencher ao concluir todas as fases)_
