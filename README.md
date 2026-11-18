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

## Como executar

### Pré-requisitos

- JDK 25
- Docker (MySQL, MongoDB e Grafana do `compose.yml` e Testcontainers nos testes)
- Python 3, `curl` e `jq`, para gerar o token e seguir o roteiro de teste
- Uma chave da OpenAI (ou um recurso do Azure OpenAI), só para executar a aplicação

### Configuração

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

Para usar o Azure OpenAI no lugar da OpenAI, siga o bloco comentado logo abaixo da `OPENAI_API_KEY` no [`.env.example`](.env.example):
deployments necessários e variáveis a descomentar.

### Execução

```bash
set -a && source .env && set +a
./gradlew bootRun
```

## Como testar o fluxo principal

Com a API no ar (seção anterior), em outro terminal na raiz do projeto. As respostas são gravadas em `build/`, pasta
ignorada pelo git.

1. Gere um token. Sem `--user-id`, cada token é de um usuário novo, então use o mesmo `TOKEN` em todos os passos:

   ```bash
   set -a && source .env && set +a
   TOKEN=$(python3 scripts/generate_token.py)
   ```

2. Envie o comando de voz `recording-1.m4a` e guarde a resposta:

   ```bash
   curl -s -X POST http://localhost:8080/api/v1/transactions/ai \
     -H "Authorization: Bearer $TOKEN" \
     -F file=@src/test/resources/audio/recording-1.m4a \
     -o build/resposta.json
   jq .transactionProposal build/resposta.json
   ```

   A resposta traz a proposta (`id`, `description`, `amount`, `currency`, `category` e `expiresAt`) e a fala em
   `speech.content`. Nenhuma transação foi gravada ainda.

3. Extraia a fala e ouça o arquivo `build/fala.mp3` em qualquer player: ela repete o que foi entendido e pergunta se
   você confirma.

   ```bash
   jq -r .speech.content build/resposta.json | base64 -d > build/fala.mp3
   ```

4. Confirme a proposta, em até 5 minutos:

   ```bash
   PROPOSAL_ID=$(jq -r .transactionProposal.id build/resposta.json)
   curl -i -X POST "http://localhost:8080/api/v1/transaction-proposals/$PROPOSAL_ID/confirm" \
     -H "Authorization: Bearer $TOKEN"
   ```

   A resposta é `201`, com a transação no corpo e o endereço dela no cabeçalho `Location`.

5. Liste as transações e veja a nova:

   ```bash
   curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/transactions | jq
   ```

Os áudios `recording-2.m4a` a `recording-6.m4a`, na mesma pasta `src/test/resources/audio/`, também são comandos de
registro: para testá-los, repita os passos 2 a 5 trocando o arquivo.

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
[`docs/exemplos/voz.http`](docs/exemplos/voz.http); o fluxo de voz com `curl` está em
[Como testar o fluxo principal](#como-testar-o-fluxo-principal).

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

## Processo de desenvolvimento

O projeto foi desenvolvido com agentes de IA (Claude Code), com as decisões e a revisão sob responsabilidade humana.
Cada tarefa de [docs/tarefas.md](docs/tarefas.md) tem um plan file em [docs/tarefas/](docs/tarefas/), escrito com a
skill `/planejar-tarefa` a partir de uma entrevista até não restar ambiguidade: escopo, decisões, definição de pronto e
fases com plano de verificação. A skill `/executar-tarefa` executa o plano em uma sessão nova, com TDD e um commit por
ciclo, e para ao fim de cada fase para a revisão; desvios só entram com aprovação e ficam registrados no próprio plano.
Cada tarefa vira um PR, com squash merge feito pelo autor. As instruções dos agentes estão em [CLAUDE.md](CLAUDE.md) e
em `.claude/rules/`, as decisões importantes em ADRs ([docs/adr/](docs/adr/)) e os commits seguem o Conventional
Commits, validado por hook.

## Aprendizados

O desafio partia de um fluxo que já funcionava: falar um gasto e vê-lo registrado. O que mais aprendi foi a olhar para
ele com os olhos de quem o banco quer atender. O Itaú diz nos próprios valores que "só é bom pra gente se for bom para o
cliente" e que "ética é inegociável", coloca o cliente no centro da cultura e quer ser visto como um banco consultivo e
confiável, que usa IA de forma responsável. Com isso em mente, o código original deixou de ter só problemas técnicos. Um
valor exibido errado não é um bug qualquer, é o cliente deixando de confiar no que vê. Uma IA que grava sozinha o que
entendeu tira dele o controle sobre o próprio dinheiro.

O mesmo vale para os dados. Num banco, informação pessoal e financeira só pode ser vista por quem precisa, e esse acesso
tem de ser controlado. Um log com a transcrição do que a pessoa falou escapa desse controle, porque logs são copiados,
ficam guardados por muito tempo e passam pelas mãos de muita gente e de várias ferramentas. O que precisa ser guardado
foi para um registro de auditoria separado, e os logs ficaram só com identificadores, como pedem a LGPD e a segurança da
informação.

Foi daí que veio a decisão de não criar nenhuma funcionalidade nova. Cada melhoria tenta fazer o cliente sentir o que o
banco promete: que o dinheiro dele está certo, que nada acontece sem ele concordar, que ele entende o que ouve e que,
quando algo dá errado do lado do banco, recebe um aviso honesto em vez de um erro confuso. Melhorar o que já existe
acabou sendo, em boa parte, traduzir em comportamento do sistema a imagem que a empresa quer passar. O mesmo filtro
ajudou a decidir o que deixar para depois: o que não aproximava o projeto dessa imagem podia esperar. E ver que o ia.i,
do próprio banco, só conclui uma transação depois da confirmação do cliente, e que o banco alerta abertamente sobre
golpes com voz clonada, mostrou que confirmação, identidade e rastreabilidade não eram excesso de zelo, e sim o mínimo
para um assistente de voz que lida com dinheiro.

Colocar IA no meio do fluxo trouxe situações que uma API tradicional não tem. Sem IA, validar uma requisição é conferir
os campos de um JSON. Aqui, ela traz um áudio, que passa por duas transformações antes de virar dado, a transcrição e a
interpretação, e qualquer uma delas pode sair errada sem que nada acuse o problema. A validação passou a acontecer em
mais de um ponto: o arquivo é conferido antes de ir para a IA, e o que o modelo extraiu é tratado com a mesma
desconfiança de qualquer dado vindo de fora.

A própria fala também pode trazer ordens para o assistente. Tudo o que está no prompt é uma sugestão: o modelo é
estatístico, e sempre existe a chance de ele se desviar, por mais explícita que seja a instrução. Ficou clara para mim a
diferença entre sugerir e impedir. A garantia não pode depender da obediência do modelo; ela precisa vir de barreiras no
código, antes e depois dele. Antes, a transcrição vai entre tags e sem os caracteres que poderiam fechá-las, para que
nada do que foi dito se passe por instrução. Depois, a aplicação não aceita texto livre: o modelo só pode chamar uma das
duas ferramentas disponíveis, uma única vez, e os argumentos são verificados um a um; o que fugir disso vira "não
entendi". Uma ordem escondida na fala pode até desviar o modelo, mas não cria uma ação que não existe nem alcança os
dados de outra pessoa, porque a identidade do usuário vem do token, e não do modelo.

Também precisei mudar a forma de pensar no erro. Num sistema sem IA, uma resposta errada é um bug: você encontra a
causa, corrige e ele não volta. Num modelo estatístico, errar faz parte de como ele funciona. Não dá para zerar o erro,
então a pergunta muda: como torná-lo raro e o que fazer quando ele acontecer. Para torná-lo raro, o modelo recebe a
menor tarefa possível, com instruções e exemplos claros: entender a intenção e extrair os dados, e mais nada. A frase
que o cliente ouve é montada pela aplicação.

Quando o erro acontece, a aplicação precisa falhar com elegância, sem que a experiência do cliente piore. Se o modelo
não entende o comando, a pessoa ouve uma frase simples pedindo para repetir, e não uma mensagem técnica. Se entende
errado, o engano aparece na resposta falada antes de ter qualquer efeito, e desfazê-lo custa só um cancelamento. Se o
problema é o próprio provedor, lento ou fora do ar, a resposta vem rápido e diz quando tentar de novo, em vez de deixar
o cliente esperando por algo que não vai chegar; enquanto isso, o resto da API continua funcionando. Em nenhum desses
casos um erro da IA vira um gasto gravado sem querer ou uma espera sem fim.

E, se o erro é esperado, é preciso saber com que frequência ele acontece. Uma requisição em que a IA entendeu tudo
errado responde `200` como qualquer outra, então tempo de resposta e taxa de erros dizem pouco sobre a qualidade. O que
mostra a qualidade é quantas propostas são confirmadas, quantas são canceladas e quantos comandos a IA não consegue
entender.

O custo também muda de natureza. Numa API tradicional, uma requisição a mais sai praticamente de graça. Com IA, cada
áudio é cobrado em segundos de transcrição, tokens e caracteres de voz sintetizada, e o provedor manda a conta do total,
sem dizer quem gastou. Isso trouxe o consumo para dentro do desenho da solução: medir quanto cada usuário gasta, recusar
o áudio inválido antes da transcrição, não processar duas vezes o mesmo comando, limitar quantos comandos cada um pode
enviar e reaproveitar as falas que se repetem. No fim, a otimização que mais importa com IA é evitar a chamada, e não
deixá-la mais rápida.

## Melhorias futuras

O que ficou de fora e por quê. A lista completa, com o que cada item é, está na tabela de
[melhorias futuras do escopo](docs/escopo.md#melhorias-futuras).

**IA e áudio**

- Outro provedor de IA como alternativa automática: o circuit breaker e o `503` com `Retry-After` já cobrem a queda do
  provedor, e um segundo modelo traria outra fonte de variação na interpretação.
- IA em serviço gerenciado na nuvem, com a identidade da plataforma no lugar de chave: a chave basta para rodar
  localmente, e a troca fica nos adaptadores e na configuração.
- Validação completa do áudio (duração, silêncio, arquivo corrompido): exige decodificar o áudio, com uma dependência
  ou um processo externo.
- Detecção de voz sintética ou clonada: exige tecnologia especializada; a autenticação e a confirmação já reduzem o
  risco.
- Tools em servidor MCP e microsserviço de áudio: mudam a arquitetura sem deixar o fluxo atual mais confiável.

**Identidade e segurança**

- Cadastro e login próprios: em produção, a identidade viria de um provedor corporativo.
- Consulta a um antifraude antes da confirmação: faz mais sentido em transferências do que no registro de gastos.
- Limite de requisições por IP: cabe à borda (API gateway, ingress ou WAF), que conhece o IP real do cliente.
- ID de interação gerado pelo cliente (`x-fapi-interaction-id`, do Open Finance): o `X-Request-Id` já correlaciona as
  requisições; ele só cobriria o caso da resposta que não chega.

**Escala e operação**

- Migrations versionadas do banco: a geração automática das tabelas basta enquanto não há dados reais a preservar.
- Limite de requisições distribuído (Redis): com uma instância, a memória local basta, e a troca é um adaptador novo.
- Deploy na nuvem e envio dos traces a um backend de produção: são infraestrutura do ambiente; a aplicação já exporta
  por OTLP.
- Cota de consumo de IA por usuário e alertas de gasto no provedor: o limite por usuário já contém abusos, e a medição
  do consumo permite calibrar uma cota depois.
- Cache das consultas: hoje não há necessidade real.
- Painel pronto no Grafana: as métricas já chegam ao Grafana local, e as consultas estão em
  [docs/operacao.md](docs/operacao.md).

**Qualidade e avaliação**

- Avaliação da acurácia da IA com um conjunto de comandos e resultados esperados: a confirmação impede que um erro
  chegue ao banco, e a métrica de desfechos já indica a qualidade.
- Teste de carga: o comportamento sob falha já é coberto pelos testes de falha.
- Demonstração em vídeo: o roteiro deste README permite executar o fluxo de ponta a ponta.
