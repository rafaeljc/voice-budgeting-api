# Budgeting — API de orçamento por voz

[![CI](https://github.com/rafaeljc/voice-budgeting-api/actions/workflows/ci.yml/badge.svg)](https://github.com/rafaeljc/voice-budgeting-api/actions/workflows/ci.yml)

## Visão geral

API REST que registra e consulta gastos a partir de comandos de voz. O cliente envia um áudio, como "gastei 80 reais
no mercado", e recebe de volta uma resposta falada:

```
áudio → transcrição → interpretação pela IA (tool calling) → proposta → confirmação → resposta falada
```

O princípio que guia o fluxo é **a IA propõe, o cliente decide**. A IA nunca grava nada: um comando de registro vira
uma proposta, que a resposta falada repete ("Um gasto de R$ 80,00 com mercado, na categoria mercado. Você confirma?") e que expira em 5 minutos. A transação só nasce
quando o cliente a confirma por uma chamada explícita à API, sem IA; se a IA entendeu errado, ele cancela e fala de
novo. Consultas ("quanto gastei com mercado?") respondem direto, só com a fala.

O projeto parte do código fornecido pelo desafio final do bootcamp "Itaú - Java com Inteligência Artificial", da DIO,
e eleva essa funcionalidade a um padrão de produção (segura, confiável, resiliente, observável e com custo
controlado), sem criar funcionalidades novas. O escopo está em [docs/escopo.md](docs/escopo.md), o plano de tarefas
em [docs/tarefas.md](docs/tarefas.md), e as camadas e os diagramas do fluxo de voz em
[docs/arquitetura.md](docs/arquitetura.md).

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

## Operação

Logs JSON com `traceId` (o mesmo do cabeçalho `X-Request-Id`), traces e métricas enviados ao Grafana local
(`grafana/otel-lgtm` do `compose.yml`), health checks de liveness e readiness e o encerramento gracioso estão em
[docs/operacao.md](docs/operacao.md).

## Testes

```bash
./gradlew test              # unitários (*Test), sem Docker
./gradlew integrationTest   # integração (*IT), exige Docker
./gradlew build             # formatação, Checkstyle, todos os testes e cobertura
```

Os testes não usam chave de IA: a OpenAI é simulada com WireMock. O relatório de cobertura (JaCoCo) fica em `build/reports/jacoco/test/html/index.html`.
