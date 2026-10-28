# Budgeting — API de orçamento por voz

[![CI](https://github.com/rafaeljc/voice-budgeting-api/actions/workflows/ci.yml/badge.svg)](https://github.com/rafaeljc/voice-budgeting-api/actions/workflows/ci.yml)

API que registra e consulta transações financeiras a partir de comandos de voz. O áudio é transcrito, interpretado
pela IA (tool calling) e executado pelos use cases da aplicação, e a resposta volta falada. Escopo e plano em
[docs/escopo.md](docs/escopo.md) e [docs/tarefas.md](docs/tarefas.md).

## Pré-requisitos

- JDK 25
- Docker (MySQL do `compose.yml` e Testcontainers nos testes)

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

## Execução

```bash
set -a && source .env && set +a
./gradlew bootRun
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

Os traces não são exportados enquanto não houver um endpoint OTLP configurado. Para enviá-los a um coletor (por
exemplo, ADOT Collector ou CloudWatch Agent, que os repassam ao X-Ray), defina a variável de ambiente:

```bash
export MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT=http://localhost:4318/v1/traces
```

## Testes

```bash
./gradlew test              # unitários (*Test), sem Docker
./gradlew integrationTest   # integração (*IT), exige Docker
./gradlew build             # formatação, Checkstyle, todos os testes e cobertura
```

Os testes não usam chave de IA: a OpenAI é simulada com WireMock. O relatório de cobertura (JaCoCo) fica em `build/reports/jacoco/test/html/index.html`.
