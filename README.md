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

## Contexto de negócio

O bootcamp é patrocinado pelo Itaú Unibanco, e as prioridades deste projeto foram tiradas de informações públicas do
banco, para que cada decisão técnica responda a uma necessidade real do negócio, e não só aplique uma tecnologia. Cada
necessidade abaixo aponta o que a API faz hoje para atendê-la (números das fontes ao final da seção).

- **Validar a intenção antes de executar.** O ia.i, assistente conversacional lançado pelo Itaú em julho de 2026,
  entende voz, analisa gastos e faz Pix, e a confirmação final da transação é sempre do cliente (fontes 1, 2 e 3).
  Aqui, a IA só cria uma proposta; a transação nasce na confirmação explícita, por uma chamada à API sem IA, e a
  proposta não decidida expira em 5 minutos.
- **Fraude com IA.** O banco alerta os clientes para golpes com deepfakes e clonagem de voz (fonte 7). Uma API que
  aceita comandos por áudio responde com identidade por token, isolamento dos dados de cada usuário, auditoria de cada
  comando e decisão em um banco separado e a confirmação explícita de toda escrita.
- **Valores que viram requisitos.** "Só é bom pra gente se for bom para o cliente", "Ética é inegociável" e a
  centralidade no cliente, da cultura do banco (fontes 5 e 6), orientam as regras de nunca gravar sem confirmação, de
  responder em frases fixas que repetem o que foi entendido e de manter transcrições e dados de negócio fora dos logs.
- **AI First com IA responsável.** A estratégia AI First trata a IA generativa como um novo paradigma de
  atendimento, e o banco ampliou em 84% as iniciativas de IA generativa em 2025, com uso responsável da tecnologia
  (fontes 1 e 4). A API mede o consumo da IA por usuário, contém o custo (validação do áudio antes da IA, cache da
  síntese de voz, idempotência do envio e limite de requisições por usuário) e restringe o assistente ao escopo
  financeiro, com proteção contra *prompt injection* falada.
- **Banco consultivo e confiável.** Segundo a pesquisa "Consciência e Prosperidade", do Itaú, 67% dos brasileiros
  esperam que seus bancos sejam mais consultivos e 53% desejam apoio para decisões mais inteligentes (fontes 8 e 9).
  Um assistente só é consultivo se for confiável: valores exatos em reais, consultas agregadas no próprio banco de
  dados, respostas claras e falha elegante quando a IA está fora do ar (timeouts, retries, circuit breaker e 503 com
  `Retry-After`).

### Fontes

1. [Itaú Unibanco lança ia.i, nova experiência conversacional com IA generativa (27/07/2026)](https://www.itau.com.br/media/dam/m/6faa1448dd30b9a0/original/27-07-2026-Itau-Unibanco-lanc-a-ia-i-nova-experie-ncia-conversacional-que-levara-a-IA-generativa-a-todos-os-produtos-e-servic-os-do-banco.pdf)
2. [Let's Money — ia.i, o assistente de IA do Itaú, e a confirmação explícita de Pix](https://www.letsmoney.com.br/noticias/itau-ia-i-assistente-app/)
3. [Cartões e Viagens — Itaú coloca IA no app que entende voz, analisa gastos e faz Pix](https://cartoeseviagens.com.br/artigos/itau-ia-app-voz-analisa-gastos-faz-pix)
4. [Itaú avança 35% em velocidade de implantações tecnológicas e amplia uso de IA generativa (09/02/2026)](https://www.itau.com.br/media/dam/m/11c5a26e8fc3cd99/original/09-02-2026-itau-avanca-35-por-cento-em-velocidade-de-implantacoes-tecnologicas-em-2025-e-amplia-uso-de-ia-generativa-na-experiencia-dos-clientes.pdf)
5. [Itaú — Cultura Itubers e nosso novo conjunto de valores](https://www.itau.com.br/relacoes-com-investidores/noticias/cultura-itubers-e-nosso-novo-conjunto-de-valores/)
6. [Forbes — CEO do Itaú prioriza cultura centrada no cliente](https://forbes.com.br/forbes-money/2023/12/ceo-do-itau-prioriza-cultura-centrada-no-cliente-em-cenario-ultracompetitivo/)
7. [Itaú alerta para golpes com deepfakes e reforça orientações de segurança digital](https://www.itau.com.br/relacoes-com-investidores/noticias/itau-unibanco-alerta-para-golpes-com-deepfakes-e-reforca-orientacoes-de-seguranca-digital/)
8. [Itaú lança Feito.Itaú e pesquisa "Consciência e Prosperidade" (09/02/2026)](https://www.itau.com.br/media/dam/m/1c23db1493e188a/original/09-02-2026-itau-unibanco-lanca-feito-itau-hub-de-conteudo-proprietario-que-amplia-o-acesso-ao-conhecimento-financeiro-e-reforca-o-papel-do-banco-como-parceiro-estrategico-na-vida-das-pessoas-e-empresas.pdf)
9. [Seu Crédito Digital — Itaú lança programa de alívio financeiro](https://seucreditodigital.com.br/itau-lanca-programa-alivio-financeiro/)

## Melhorias implementadas

Todas as melhorias fortalecem a mesma funcionalidade, o registro e a consulta de gastos por voz. As decisões
importantes e não óbvias têm ADR em [docs/adr/](docs/adr/).

### Fluxo e regras de negócio

| Melhoria | O que garante | ADR |
|---|---|---|
| Valor monetário correto | O valor registrado é o valor falado, em reais e sem arredondamento: antes, o valor saía 100 vezes maior. | [0001](docs/adr/0001-representacao-de-valores-monetarios.md) |
| Data e hora da transação | Toda transação registra o instante da criação, definido pelo servidor. | |
| Regras de negócio validadas | Valor positivo e dentro de um teto, descrição presente e categoria válida, venha o dado da IA ou do cadastro manual. | |
| Confirmação em duas etapas | A IA só cria uma proposta, guardada no banco; a transação nasce na confirmação explícita do cliente, e o cancelamento descarta a proposta. | [0005](docs/adr/0005-proposta-de-transacao-em-entidade-propria.md) |
| Expiração da proposta | Uma proposta não decidida em 5 minutos não pode mais ser confirmada, e uma faxina periódica a apaga. | |
| Validação do áudio | Áudio vazio, acima de 1 MB ou fora de M4A, MP3 e WAV (pelos bytes, não pela extensão) é recusado antes de qualquer custo de IA. | [0004](docs/adr/0004-validacao-basica-do-audio.md) |
| Respostas faladas claras | A frase é montada pela aplicação, não pelo modelo: frases fixas, valores em R$ e repetição do que foi entendido. | [0006](docs/adr/0006-resposta-falada-montada-pela-aplicacao.md) |
| Proteções da IA | O assistente fica restrito a registrar e consultar gastos; a transcrição vai delimitada, uma única ação por comando e saída limitada em tokens, e um comando falado não muda as instruções. | |
| Orquestração fora do controller | O fluxo de voz é um caso de uso com uma porta por etapa da IA, e só os adaptadores conhecem o provedor. | |

### API

| Melhoria | O que garante | ADR |
|---|---|---|
| Padrão REST e versionamento | Rotas sob `/api/v1`, `201` com `Location` nas criações, consulta por ID e a proposta como recurso com as ações `confirm` e `cancel`. | |
| Dinheiro sem ponto flutuante | Valores trafegam como string decimal com duas casas e o código da moeda, sem a leitura como `double` nos clientes. | [0001](docs/adr/0001-representacao-de-valores-monetarios.md) |
| Paginação e ordenação | A listagem devolve no máximo 100 itens por página, com filtro por categoria e ordenação por data ou valor. | |
| Contrato de erro padronizado | Todo erro, inclusive da IA, sai em Problem Details (RFC 9457), em pt-BR e sem detalhes internos. | [0003](docs/adr/0003-contrato-de-erro.md) |
| Contrato OpenAPI e exemplos | O contrato fica em `docs/openapi.yaml`, navegável pelo Swagger UI, com exemplos de requisição prontos. | |

### Resiliência

| Melhoria | O que garante | ADR |
|---|---|---|
| Timeouts e retries | Toda chamada externa tem tempo máximo, e uma falha transitória da IA é repetida uma vez, com backoff exponencial e jitter; o pior caso do comando de voz é de cerca de 96 s. | |
| Circuit breaker | Com a IA falhando, o comando de voz falha na hora com `503` e `Retry-After`, sem gastar com as etapas anteriores e dando tempo para o provedor se recuperar. | |
| Bulkhead | Cada etapa da IA atende no máximo 10 chamadas simultâneas, então a lentidão do provedor não esgota a aplicação. | |
| Idempotência no envio do áudio | Com o cabeçalho `Idempotency-Key`, o reenvio do mesmo comando não gera outra proposta nem nova cobrança de IA. | [0009](docs/adr/0009-trava-de-idempotencia-em-caffeine-local.md) |
| Idempotência na confirmação | Confirmar a mesma proposta duas vezes, mesmo ao mesmo tempo, cria uma única transação. | |
| Estado persistido | As propostas ficam no MySQL e sobrevivem a um reinício da aplicação. | [0005](docs/adr/0005-proposta-de-transacao-em-entidade-propria.md) |
| Health checks | Liveness e readiness para o orquestrador e o monitoramento detectarem falhas. | |
| Encerramento gracioso | No desligamento, as requisições em andamento terminam antes de a aplicação parar. | |

### Segurança e rastreabilidade

| Melhoria | O que garante | ADR |
|---|---|---|
| Identidade | Toda rota exige um token JWT, então toda operação tem um autor. | |
| Isolamento por usuário | Cada usuário vê, propõe e confirma só os próprios dados; o dado de outro usuário responde `404`, como se não existisse. | |
| Limite de requisições por usuário | 10 comandos de voz e 60 demais requisições por minuto, com `429` e `Retry-After`, contendo abuso e custo de IA. | [0010](docs/adr/0010-limite-de-requisicoes-por-usuario-em-memoria-local.md) |
| Auditoria em banco NoSQL | Cada proposta criada, comando não entendido, confirmação e cancelamento fica registrado no MongoDB, com autor, instante, `traceId`, metadados do áudio e transcrição. | [0007](docs/adr/0007-auditoria-em-banco-de-documentos-separado.md) |
| Garantia de entrega da auditoria | Nenhum registro se perde (outbox transacional com reenvio), e o MongoDB fora do ar não interrompe o cliente. | |
| Dados sensíveis fora dos logs | Os logs têm só IDs e metadados, nunca transcrição, prompt, resposta do modelo ou dados de negócio. | [0002](docs/adr/0002-observabilidade-e-correlacao.md) |
| Dependências sem vulnerabilidades conhecidas | O CI revisa as dependências de cada PR, e as versões com alertas foram atualizadas. | |
| Segredos fora do repositório | Credenciais só por variável de ambiente, com varredura de segredos no CI. | |

### Custo e desempenho

| Melhoria | O que garante | ADR |
|---|---|---|
| Medição de consumo por usuário | Tokens, segundos de áudio transcritos e caracteres sintetizados ficam registrados por usuário e requisição, dado que o provedor não tem. | |
| Cache da síntese de voz | A mesma frase não é sintetizada duas vezes: menos custo e latência nas respostas repetidas, sem nunca usar o áudio do usuário como chave. | [0008](docs/adr/0008-cache-local-da-sintese-de-voz.md) |

### Observabilidade

| Melhoria | O que garante | ADR |
|---|---|---|
| Logs estruturados com correlação | Logs em JSON com o `traceId` em cada linha, o mesmo devolvido em `X-Request-Id`, para achar uma requisição de ponta a ponta. | [0002](docs/adr/0002-observabilidade-e-correlacao.md) |
| Métricas técnicas | Latência por etapa da IA, erros HTTP, estado do circuit breaker e do bulkhead e uso de recursos, com métricas e traces no Grafana local. | |
| Métrica de negócio | O desfecho de cada interpretação da IA (confirmada, cancelada, não entendida) mede quanto a IA acerta. | |

### Qualidade

| Melhoria | O que garante | ADR |
|---|---|---|
| Testes unitários e de integração sem OpenAI | A IA é simulada com WireMock, e o MySQL e o MongoDB rodam em containers: testes determinísticos e gratuitos, sem chave de IA. | |
| Testes de falha | Falha de cada etapa da IA, circuito aberto, etapa cheia, envios e confirmações simultâneos e auditoria com o MongoDB falhando comprovam a resiliência. | |
| Integração contínua | Cada PR passa por formatação, Checkstyle, testes, cobertura, varredura de segredos e revisão de dependências. | |

## Tecnologias

| Tecnologia | Para que serve |
|---|---|
| Java 25, Spring Boot 4.1, Gradle 9 | Linguagem, framework e build; requisições, tarefas assíncronas e agendadas em virtual threads |
| Spring Web MVC | Endpoints REST e upload do áudio |
| Spring Data JPA + MySQL 9.6 | Transações e propostas (banco transacional) |
| Spring Data MongoDB + MongoDB 9.0 | Auditoria e medição de consumo da IA, separadas do banco transacional |
| Spring Modulith (registro de publicação de eventos) | Outbox transacional da auditoria no MySQL, com reenvio |
| Spring AI 2.0 + OpenAI | Transcrição (`whisper-1`), interpretação com tool calling (`gpt-4o-mini`) e síntese de voz (`gpt-4o-mini-tts`) |
| Spring Cache + Caffeine | Cache local da síntese de voz, trava de idempotência do comando de voz e limitadores por usuário |
| Resilience4j | Circuit breaker do fluxo de voz, bulkhead por etapa da IA e limite de requisições por usuário |
| Spring Security (resource server) | Autenticação por JWT HS256 |
| Spring Boot Actuator, Micrometer, OpenTelemetry | Health checks, métricas e traces exportados por OTLP |
| Grafana LGTM (`grafana/otel-lgtm`) | Grafana local com Prometheus e Tempo para métricas e traces |
| Docker Compose (`spring-boot-docker-compose`) | Sobe MySQL, MongoDB e Grafana junto com a aplicação |
| JUnit 5, AssertJ, Mockito, Testcontainers, WireMock | Testes unitários e de integração, com bancos em containers e a OpenAI simulada |
| Spotless, Checkstyle, JaCoCo | Formatação, convenções e cobertura, verificadas no build |
| GitHub Actions, gitleaks, dependency review | CI, varredura de segredos e revisão das dependências vulneráveis em cada PR |
| Lombok, Jackson 3 | Menos código repetitivo e serialização JSON |

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
