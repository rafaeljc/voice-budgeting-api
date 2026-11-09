# Budgeting — API de orçamento por voz

[![CI](https://github.com/rafaeljc/voice-budgeting-api/actions/workflows/ci.yml/badge.svg)](https://github.com/rafaeljc/voice-budgeting-api/actions/workflows/ci.yml)

API que registra e consulta transações financeiras a partir de comandos de voz. O áudio é transcrito, interpretado
pela IA (tool calling) e executado pelos use cases da aplicação, e a resposta volta falada. Escopo e plano em
[docs/escopo.md](docs/escopo.md) e [docs/tarefas.md](docs/tarefas.md).
Camadas e fluxo de voz em [docs/arquitetura.md](docs/arquitetura.md).

## Pré-requisitos

- JDK 25
- Docker (MySQL e MongoDB do `compose.yml` e Testcontainers nos testes)

## Configuração

Copie o modelo e ajuste os valores:

```bash
cp .env.example .env
```

| Variável              | Uso                                                      |
|-----------------------|----------------------------------------------------------|
| `MYSQL_ROOT_PASSWORD` | senha do `root` do MySQL local (`compose.yml`)           |
| `MYSQL_USER`          | usuário da aplicação no MySQL local                      |
| `MYSQL_PASSWORD`      | senha do usuário da aplicação                            |
| `OPENAI_API_KEY`      | chave da OpenAI, necessária só para executar a aplicação |
| `MONGO_USERNAME`      | usuário `root` do MongoDB de auditoria local (`compose.yml`) |
| `MONGO_PASSWORD`      | senha desse usuário                                      |
| `JWT_SECRET`          | segredo (mínimo de 32 bytes) que assina e valida os tokens; a aplicação não sobe sem ele |

## Execução

```bash
set -a && source .env && set +a
./gradlew bootRun
```

## API

Base `/api/v1`. Todas as rotas exigem um token JWT (`Authorization: Bearer`); veja como gerá-lo e usá-lo em
[docs/autenticacao.md](docs/autenticacao.md). O contrato OpenAPI está em [`docs/openapi.yaml`](docs/openapi.yaml) e é revisado a cada PR. Para navegar por ele, suba o Swagger UI (somente leitura, não chama a API): `docker compose up swagger-ui` e abra
`http://localhost:8081`.

| Método | Caminho                      | Descrição                                                          |
|--------|------------------------------|--------------------------------------------------------------------|
| `POST` | `/transactions`              | Cria uma transação (201 + `Location`)                              |
| `GET`  | `/transactions`              | Lista paginada; filtros `category`, `page`, `size`, `sort`         |
| `GET`  | `/transactions/{id}`         | Consulta uma transação (404 se não existir)                        |
| `POST` | `/transactions/ai`           | Comando de voz: `file` M4A, MP3 ou WAV (até 1 MB); resposta JSON com a fala (MP3 em base64) e, no registro, a proposta |
| `POST` | `/transaction-proposals/{id}/confirm` | Confirma a proposta e cria a transação (201 + `Location`) |
| `POST` | `/transaction-proposals/{id}/cancel`   | Cancela a proposta (204)                                           |

Um comando de voz de registro não grava a transação: a resposta traz a fala e uma `transactionProposal`, e quem
decide é o usuário, por `confirm` ou `cancel`. A proposta expira em 5 minutos (`budgeting.transaction-proposals.time-to-live`):
depois de `expiresAt`, confirmar ou cancelar responde 404. Consultas respondem direto, só com a fala.

Exemplos de requisição em [`docs/exemplos/transacoes.http`](docs/exemplos/transacoes.http) e
[`docs/exemplos/voz.http`](docs/exemplos/voz.http). Com `curl`:

```bash
TOKEN=$(python3 scripts/generate_token.py)

curl -i -X POST http://localhost:8080/api/v1/transactions \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"description": "Mercado", "category": "GROCERIES", "amount": "80.00", "currency": "BRL"}'

curl -H "Authorization: Bearer $TOKEN" 'http://localhost:8080/api/v1/transactions?category=GROCERIES&sort=amount,desc'

# comando de voz: devolve a fala (base64) e a proposta; confirme ou cancele pelo id da proposta
PROPOSAL_ID=$(curl -s -X POST http://localhost:8080/api/v1/transactions/ai \
  -H "Authorization: Bearer $TOKEN" \
  -F file=@src/test/resources/audio/recording-1.m4a | jq -r .transactionProposal.id)

curl -i -X POST "http://localhost:8080/api/v1/transaction-proposals/$PROPOSAL_ID/confirm" \
  -H "Authorization: Bearer $TOKEN"
```

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
| `budgeting.ai.interpretations`          | `budgeting_ai_interpretations_total`             | desfecho das interpretações da IA, tag `outcome`: `confirmed`, `canceled`, `not_understood` |

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

## Testes

```bash
./gradlew test              # unitários (*Test), sem Docker
./gradlew integrationTest   # integração (*IT), exige Docker
./gradlew build             # formatação, Checkstyle, todos os testes e cobertura
```

Os testes não usam chave de IA: a OpenAI é simulada com WireMock. O relatório de cobertura (JaCoCo) fica em `build/reports/jacoco/test/html/index.html`.
