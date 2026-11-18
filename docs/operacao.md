# Operação

Logs, rastreamento, métricas, health checks e encerramento da API. Como executar e testar está no
[README](../README.md).

## Logs e rastreamento

Os logs saem em JSON no formato ECS, em todos os ambientes. Cada linha de uma requisição traz o `traceId`, o mesmo
valor que a resposta devolve no cabeçalho `X-Request-Id`: com ele, uma requisição é localizada nos logs. Um
`traceparent` (W3C) recebido é continuado, e um `X-Request-Id` enviado pelo cliente é ignorado.

Os logs nunca contêm transcrição, prompt, resposta do modelo, áudio nem dados de negócio, só IDs e metadados
técnicos. Para ler no terminal:

```bash
./gradlew bootRun | jq 'select(.traceId != null) | {"@timestamp", message, traceId}'
```

Localmente, os traces vão para o container `grafana/otel-lgtm` do `compose.yml`: o `spring-boot-docker-compose` aponta a
aplicação para o coletor. Em Grafana → Explore → Tempo, procure pelo serviço `budgeting`. Em outros ambientes, defina o
endpoint OTLP (por exemplo, para um ADOT Collector ou CloudWatch Agent, que os repassam ao X-Ray):

```bash
export MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT=http://localhost:4318/v1/traces
```

## Métricas

O `GET /actuator/metrics` lista as métricas:

```bash
curl -s http://localhost:8080/actuator/metrics | jq -r '.names[]'
curl -s 'http://localhost:8080/actuator/metrics/budgeting.ai.interpretations?tag=outcome:confirmed' | jq '.measurements'
```

As métricas também são enviadas ao container `grafana/otel-lgtm` (a cada 1 minuto, `management.otlp.metrics.export.step`).
O Grafana fica em http://localhost:3000 (`admin`/`admin`): Explore → fonte de dados Prometheus. O contador e as
consultas abaixo só dão resultado depois de o primeiro valor ser exportado.

| Métrica (Actuator)                      | Nome no Prometheus                               | O que mostra                                         |
|-----------------------------------------|--------------------------------------------------|------------------------------------------------------|
| `gen_ai.client.operation`               | `gen_ai_client_operation_milliseconds_*`         | latência por etapa da IA (`gen_ai_operation_name` = `transcription`, `chat`, `text_to_speech`) |
| `http.server.requests`                  | `http_server_requests_milliseconds_*`            | requisições e erros, por `uri`, `status` e `outcome` |
| `hikaricp.connections.active`/`pending` | `hikaricp_connections_active`/`_pending`         | conexões do pool do banco                            |
| `jvm.memory.used`                       | `jvm_memory_used_bytes`                          | memória da JVM                                       |
| `process.cpu.usage`                     | `process_cpu_usage`                              | uso de CPU                                           |
| `cache.gets`                            | `cache_gets_total`                               | acertos e erros do cache da síntese de voz, tags `cache` (`speech`) e `result` (`hit`, `miss`); consulte sempre com `cache="speech"` |
| `budgeting.ai.interpretations`          | `budgeting_ai_interpretations_total`             | desfecho das interpretações da IA, tag `outcome`: `confirmed`, `canceled`, `not_understood` |
| `resilience4j.circuitbreaker.state`     | `resilience4j_circuitbreaker_state`              | estado do circuito do fluxo de voz, tags `name` (`voice-command`) e `state` (`closed`, `open`, `half_open`); vale 1 no estado atual |
| `resilience4j.bulkhead.available.concurrent.calls` | `resilience4j_bulkhead_available_concurrent_calls` | vagas livres de cada etapa da IA, tag `name` (`speech-to-text`, `assistant`, `text-to-speech`) |

O contador conta, desde que a aplicação subiu, quantas interpretações da IA terminaram em cada desfecho: a proposta
confirmada (a IA acertou), a cancelada (errou a proposta) e o comando não entendido (não identificou o comando). Ele é
incrementado antes do commit da decisão; propostas expiradas não contam.

Taxas (valores acumulados desde que a aplicação subiu; sem dados, a consulta devolve vazio ou `NaN`):

```promql
# acerto das propostas: confirmadas / (confirmadas + canceladas)
sum(budgeting_ai_interpretations_total{outcome="confirmed"}) / sum(budgeting_ai_interpretations_total{outcome=~"confirmed|canceled"})

# comandos não entendidos / respostas 200 de /api/v1/transactions/ai
sum(budgeting_ai_interpretations_total{outcome="not_understood"}) / sum(http_server_requests_milliseconds_count{uri="/api/v1/transactions/ai",status="200"})

# comandos com argumentos inválidos (422) / total de /api/v1/transactions/ai
sum(http_server_requests_milliseconds_count{uri="/api/v1/transactions/ai",status="422"}) / sum(http_server_requests_milliseconds_count{uri="/api/v1/transactions/ai"})
```

## Health checks e encerramento

| Endpoint                         | O que verifica                                                                  |
| -------------------------------- | ------------------------------------------------------------------------------- |
| `GET /actuator/health`           | Saúde geral: MySQL, MongoDB, disco etc., com detalhes                           |
| `GET /actuator/health/liveness`  | Só o estado da própria aplicação, sem dependência externa                       |
| `GET /actuator/health/readiness` | Estado da aplicação e MySQL, sem o qual nenhuma rota funciona                   |

Os três são públicos (sem token) para orquestradores e monitoramento:

```bash
curl -s http://localhost:8080/actuator/health/readiness | jq
```

A readiness olha só o MySQL. O MongoDB fica de fora porque a auditoria tem outbox: com ele fora do ar a API segue
atendendo e o registro é reenviado depois. A OpenAI fica de fora porque cada verificação teria custo e latência e
derrubaria até as consultas, que não usam IA; a queda dela já vira 503 com `Retry-After`. A liveness não depende de
banco algum, para o orquestrador não reiniciar a instância por um banco fora do ar.

`management.endpoint.health.show-details=always` mostra os detalhes (produto do banco, erro, disco) para facilitar o
diagnóstico; em produção o endpoint estaria protegido ou em uma porta de gerenciamento não exposta publicamente.

No desligamento, a readiness passa a `REFUSING_TRAFFIC` e a aplicação:

- espera até 100 s as requisições em andamento (cobre o pior comando de voz, cerca de 96 s); sem requisição em
  andamento, desliga na hora;
- espera até 10 s as entregas da auditoria (o que não terminar é reenviado pelo outbox);
- espera até 10 s o lote em andamento dos jobs agendados.

O orquestrador precisa de um prazo de término maior que 100 s (no Kubernetes, `terminationGracePeriodSeconds`; o
padrão é 30 s).
