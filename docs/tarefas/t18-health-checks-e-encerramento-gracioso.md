# T18 — Health checks e encerramento gracioso

Expor indicadores de vida (*liveness*) e de prontidão (*readiness*) que considerem as dependências, públicos para
orquestradores e monitoramento, e concluir as requisições, os registros de auditoria e os jobs em andamento antes de
desligar a aplicação.

## Escopo

**Dentro:**
- `application.properties`, cada bloco com comentário do porquê:
  - readiness: `management.endpoint.health.group.readiness.include=readinessState,db` — só o MySQL, sem o qual
    nenhuma rota funciona. MongoDB fora: a auditoria tem outbox (T21), a API segue atendendo com ele fora do ar e o
    registro é reenviado depois; incluí-lo tiraria todas as instâncias do ar por causa da auditoria. OpenAI fora:
    custo e latência a cada verificação, derrubaria até as consultas (que não usam IA), e a degradação controlada
    (T10, 503 com `Retry-After`) já cobre a queda
  - liveness: grupo padrão do Boot (só `livenessState`), sem dependência externa, para o orquestrador não reiniciar a
    instância por um banco fora do ar; nenhuma propriedade nova (probes ligados por padrão no Boot 4.1), só citado no
    comentário
  - `/actuator/health` geral segue com `db`, `mongo`, `diskSpace` etc. (nota da T20)
  - `management.endpoint.health.show-details=always` — os detalhes (produto do banco, erro, disco) ajudam no
    troubleshooting; em produção o endpoint estaria protegido ou em uma porta de gerenciamento não exposta publicamente
  - `server.shutdown=graceful` (explícito, já é o padrão) e `spring.lifecycle.timeout-per-shutdown-phase=100s` — cobre
    o pior caso do comando de voz (~96 s, conta já no comentário da IA); sem requisição em andamento o desligamento é
    imediato; o orquestrador precisa de prazo de término maior que este (no Kubernetes,
    `terminationGracePeriodSeconds`, padrão 30 s)
  - `spring.task.execution.shutdown.await-termination=true` e `spring.task.execution.shutdown.await-termination-period=10s`
    — auditoria `@Async`: cobre a pior entrega ao MongoDB (3 s + 5 s); a garantia vem do outbox (o que não terminar
    vira `FAILED` e é reenviado), a espera só evita o reenvio; substitui o padrão implícito de 2 s do Modulith
  - `spring.task.scheduling.shutdown.await-termination=true` e
    `spring.task.scheduling.shutdown.await-termination-period=10s` — os jobs terminam o lote em andamento (limite de
    resposta da consulta 5 s) sem exceção no log por pool fechado; o restante dos lotes volta na próxima execução
- `SecurityConfiguration`: `permitAll` ganha `/actuator/health/liveness` e `/actuator/health/readiness`, listados
  explicitamente (sem `/actuator/health/**`); comentário ajustado (orquestradores chamam os probes sem token)
- Testes, em `AuthenticationIT`, ao lado de `should_answerHealthWithoutToken_when_actuatorHealthIsRequested`:
  - `should_answerLivenessWithoutToken_when_livenessIsRequested` → 200
  - `should_answerReadinessWithoutToken_when_readinessIsRequested` → 200
- Documentação:
  - `README.md`: seção "Health checks e encerramento" — os três endpoints, o que cada um verifica (e por que a
    readiness olha só o MySQL), `curl` de exemplo, `show-details=always` e o motivo, comportamento no desligamento
    (100 s, 10 s, 10 s) e o prazo do orquestrador
  - `.claude/rules/security.md`, `.claude/rules/api-conventions.md`, `docs/autenticacao.md`: rotas públicas passam a
    ser `/actuator/health`, `/actuator/health/liveness` e `/actuator/health/readiness`
- `docs/tarefas.md`: checkbox da T18 (último commit)

**Fora:**
- teste do encerramento gracioso (decisão do usuário)
- testes da composição dos grupos (componentes da readiness/liveness), dos detalhes e da readiness `DOWN`
- health indicator da OpenAI
- ADR (decisão do usuário: o porquê fica nos comentários e no README)
- porta de gerenciamento separada, proteção do health em produção
- Dockerfile, Kubernetes, deploy (FUT-07)
- edição das notas da T20/T21 em `docs/tarefas.md` (histórico)

## Decisões

- Readiness = `readinessState` + `db`; MongoDB e OpenAI fora — motivos no escopo (decisão do usuário)
- Liveness sem dependência externa, grupo padrão (decisão do usuário)
- Probes liberados por caminho explícito, não `/**` — mantém o "negar por padrão" (decisão do usuário)
- `show-details=always`, documentado na configuração — troubleshooting; em produção, endpoint protegido ou porta não
  exposta (decisão do usuário, contra a recomendação de `show-components`)
- `timeout-per-shutdown-phase=100s` — leitura literal do RES-10, cobre o pior caso da voz (decisão do usuário)
- Espera explícita de 10 s para `@Async` e para `@Scheduled` — sai do padrão escondido do Modulith; cobre uma entrega
  e um lote (decisão do usuário)
- Só os testes de exposição dos probes; sem teste de encerramento (decisão do usuário)
- Sem ADR (decisão do usuário)
- Duas fases; tipo `feat`; branch `feat/health-checks-e-encerramento-gracioso`

## Definição de pronto

- [ ] `./gradlew build` passa (Spotless, Checkstyle, testes e cobertura)
- [ ] `/actuator/health/liveness` e `/actuator/health/readiness` respondem 200 sem token
- [ ] readiness inclui só `readinessState` e `db`; liveness só `livenessState`; health geral com detalhes
- [ ] propriedades de encerramento (`server.shutdown`, `timeout-per-shutdown-phase`, `task.execution.shutdown.*`,
  `task.scheduling.shutdown.*`) definidas e comentadas
- [ ] `README.md`, `security.md`, `api-conventions.md`, `docs/autenticacao.md` e `tarefas.md` atualizados como no escopo

## Para futuros agentes

Execute com `/executar-tarefa`. Durante a execução:

- marque `- [x]` no mesmo commit que conclui o item;
- antes do último commit de uma fase, rode o plano de verificação, escreva o resumo da fase com o resultado e
  mude o status para `Concluída` — tudo nesse mesmo commit;
- na última fase, no mesmo commit, confira a definição de pronto e escreva o resumo final (e o plano de deploy,
  se houver);
- mudança no plano só com aprovação do usuário, registrada em Decisões com o motivo.

Fatos conferidos durante o planejamento (reconfirmar ao implementar):
- Boot 4.1.2: `management.endpoint.health.probes.enabled=true` por padrão, então `/actuator/health/liveness` e
  `/readiness` já existem (hoje 401 pela segurança); grupo `readiness` padrão só com `readinessState`, `liveness` só
  com `livenessState`; `server.shutdown=graceful` e `timeout-per-shutdown-phase=30s` por padrão; no fechamento a
  readiness vira `REFUSING_TRAFFIC` sozinha
- Spring Modulith 2.1.2 (`EventPublicationAutoConfiguration$AsyncPropertiesDefaulter`): se nenhuma
  `spring.task.execution.shutdown.*` estiver definida, liga `await-termination` com 2 s; definindo as duas, o padrão
  não se aplica (`spring.modulith.default-async-termination`)
- Com virtual threads, o Boot monta `SimpleAsyncTaskExecutor` / `SimpleAsyncTaskScheduler` e passa o
  `await-termination-period` como `taskTerminationTimeout` (`TaskExecutorConfigurations`,
  `TaskSchedulingConfigurations`)
- O teste da readiness falha antes da mudança (401); o de liveness também

## Fase 1: Probes e encerramento gracioso

Status: Em andamento  <!-- Não iniciada | Em andamento | Concluída -->

- [x] [TDD] `AuthenticationIT.should_answerLivenessWithoutToken_when_livenessIsRequested` — liberar
  `/actuator/health/liveness` na `SecurityConfiguration` e ajustar o comentário
- [x] [TDD] `AuthenticationIT.should_answerReadinessWithoutToken_when_readinessIsRequested` — liberar
  `/actuator/health/readiness` e definir `management.endpoint.health.group.readiness.include=readinessState,db` com
  comentário
- [x] [verificação] `management.endpoint.health.show-details=always` com comentário
- [ ] [verificação] `server.shutdown=graceful` e `spring.lifecycle.timeout-per-shutdown-phase=100s` com comentário
- [ ] [verificação] `spring.task.execution.shutdown.*` e `spring.task.scheduling.shutdown.*` (`true`, `10s`) com
  comentário

### Plano de verificação

- `./gradlew integrationTest --tests "dio.budgeting.infrastructure.http.AuthenticationIT"` → `BUILD SUCCESSFUL`, os
  dois testes novos passando
- `grep -nE "^(server.shutdown|spring.lifecycle.timeout-per-shutdown-phase|spring.task.(execution|scheduling).shutdown|management.endpoint.health)" src/main/resources/application.properties`
  → 8 linhas, com `graceful`, `100s`, `true`/`10s` (×2), `readinessState,db` e `always`
- `./gradlew build` → `BUILD SUCCESSFUL`

### Resumo da fase

_(preencher ao concluir a fase)_

## Fase 2: Documentação

Status: Não iniciada  <!-- Não iniciada | Em andamento | Concluída -->

- [ ] [verificação] `README.md`: seção "Health checks e encerramento"
- [ ] [verificação] `.claude/rules/security.md`, `.claude/rules/api-conventions.md`, `docs/autenticacao.md`: rotas
  públicas
- [ ] [verificação] `docs/tarefas.md`: checkbox da T18 (último commit)

### Plano de verificação

- `grep -n "Health checks e encerramento" README.md` → uma linha
- `grep -ln "/actuator/health/readiness" .claude/rules/security.md .claude/rules/api-conventions.md docs/autenticacao.md README.md`
  → os quatro arquivos
- `grep -n "\[x\] \[T18" docs/tarefas.md` → uma linha
- `./gradlew build` → `BUILD SUCCESSFUL` (Spotless cobre os `.md`)

### Resumo da fase

_(preencher ao concluir a fase)_

## Resumo final

_(preencher ao concluir todas as fases)_
