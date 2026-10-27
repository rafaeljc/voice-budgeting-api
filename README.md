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

## Testes

```bash
./gradlew test              # unitários (*Test), sem Docker
./gradlew integrationTest   # integração (*IT), exige Docker
./gradlew build             # formatação, Checkstyle, todos os testes e cobertura
```

Os testes não usam chave de IA: a OpenAI é simulada com WireMock. O relatório de cobertura (JaCoCo) fica em `build/reports/jacoco/test/html/index.html`.
