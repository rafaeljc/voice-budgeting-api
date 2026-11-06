# Tarefas — Assistente de Orçamento por Voz com Padrão de Produção

> Plano de execução dos itens definidos em [escopo.md](escopo.md). Cada tarefa corresponde, a princípio, a uma pull request.
> Ordem definida por **dependência**, **urgência** e **valor gerado**.

## Sumário

**Fase 1 — Fundação**
- [x] [T01 — Processo de desenvolvimento com agentes](#t01)
- [x] [T02 — Integração contínua e base de testes](#t02)

**Fase 2 — Correções urgentes**
- [x] [T27 — Atualização das dependências vulneráveis](#t27)
- [x] [T03 — Correção do valor monetário](#t03)
- [x] [T04 — Logs estruturados e proteção de dados nos logs](#t04)

**Fase 3 — Base para evolução**
- [x] [T05 — Contrato de erro padronizado](#t05)
- [x] [T06 — Integridade da transação](#t06)
- [x] [T07 — Endpoints manuais no padrão REST](#t07)

**Fase 4 — Identidade**
- [x] [T08 — Identidade mínima e isolamento por usuário](#t08)

**Fase 5 — Fluxo de voz confiável**
- [x] [T09 — Orquestração de IA na camada de aplicação](#t09)
- [x] [T10 — Timeouts e retries](#t10)
- [x] [T11 — Validação do áudio recebido](#t11)
- [x] [T12 — Confirmação em duas etapas](#t12)
- [x] [T13 — Ciclo de vida da proposta](#t13)
- [x] [T14 — Respostas claras e proteções da IA](#t14)

**Fase 6 — Resiliência e recuperação de falhas**
- [ ] [T15 — Circuit breaker e bulkhead](#t15)
- [ ] [T16 — Degradação controlada](#t16)
- [ ] [T17 — Idempotência no envio de áudio](#t17)
- [ ] [T18 — Health checks e encerramento gracioso](#t18)

**Fase 7 — Segurança e rastreabilidade**
- [ ] [T19 — Rate limiting por usuário](#t19)
- [x] [T20 — Auditoria em banco NoSQL](#t20)
- [x] [T21 — Garantia de entrega da auditoria](#t21)

**Fase 8 — Observabilidade e custo**
- [ ] [T22 — Métricas técnicas e de negócio](#t22)
- [ ] [T23 — Medição de consumo de IA](#t23)

**Fase 9 — Desempenho**
- [ ] [T24 — Cache de consultas](#t24)
- [ ] [T25 — Cache da síntese de voz](#t25)

**Fase 10 — Entrega**
- [ ] [T26 — Conclusão do README](#t26)

---

## Itens transversais

- **QA-01 e QA-02 (testes unitários e de integração):** base criada na T02; cada tarefa inclui os testes do que altera.
- **Documentação (README, ADRs, diagramas, OpenAPI e exemplos):** criada junto com a tarefa que a justifica e atualizada pelas tarefas que alteram o que ela descreve. ADRs apenas para decisões importantes e não óbvias.
- **DOC-07 (processo com agentes):** definido na T01 e seguido em todas as tarefas.
- **Fundação:** todas as tarefas a partir da T03 dependem da T01 (processo) e da T02 (CI e base de testes); essa dependência não é repetida nas tarefas.

---

## Fase 1 — Fundação

<a id="t01"></a>
### T01 — Processo de desenvolvimento com agentes

**Depende de:** —

Definir o processo de desenvolvimento com agentes: instruções do projeto para os agentes, planejamento e execução de cada tarefa a partir de um plan file, convenções de branch, commit e pull request, e modelo de ADR.

**Itens do escopo:**
- DOC-07 — Processo de desenvolvimento com agentes
- DOC-03 — ADRs (modelo)

<a id="t02"></a>
### T02 — Integração contínua e base de testes

**Depende de:** T01

Configurar integração contínua com build, testes e verificação de vulnerabilidades das dependências a cada alteração. Criar a base para testes unitários e de integração que rodem sem acesso à OpenAI. Remover as credenciais fixas da configuração versionada. Documentar no README como executar a aplicação e os testes.

**Itens do escopo:**
- QA-05 — Integração contínua (CI)
- QA-01 — Testes unitários (base)
- QA-02 — Testes de integração sem OpenAI (base)
- SEG-07 — Análise de vulnerabilidades das dependências
- SEG-08 — Gestão de segredos
- DOC-01 — README completo (execução e testes)

## Fase 2 — Correções urgentes

<a id="t03"></a>
### T03 — Correção do valor monetário

**Depende de:** —

Corrigir a conversão entre centavos e reais e adotar uma representação exata e consistente de valores monetários em toda a API.

**Itens do escopo:**
- FN-01 — Correção do valor monetário
- API-02 — Valores monetários sem ponto flutuante

<a id="t04"></a>
### T04 — Logs estruturados e proteção de dados nos logs

**Depende de:** —

Adotar logs estruturados com identificador de correlação por requisição e impedir o registro de conteúdo sensível (transcrições, prompts, tokens e dados pessoais).

**Itens do escopo:**
- OBS-01 — Logs estruturados com ID de correlação
- SEG-06 — Dados sensíveis fora dos logs

<a id="t27"></a>
### T27 — Atualização das dependências vulneráveis

**Depende de:** T02

Atualizar o Spring Boot (4.0.5 → 4.1.2), o Spring AI (2.0.0-M4 → 2.0.2, que troca o cliente da OpenAI pelo SDK `openai-java`) e o Checkstyle (13.4.0 → 13.11.0), eliminando todos os alertas de vulnerabilidade das dependências, inclusive as do classpath dos plugins do Gradle. O Dependabot não atualiza dependências transitivas gerenciadas pelos BOMs, por isso a atualização é feita nas versões declaradas no build e conferida com o dependency review e com o `build`. Se o job `dependencies` passar, passa a ser exigido na proteção da `main`.

**Itens do escopo:**
- SEG-07 — Análise de vulnerabilidades das dependências (correção dos achados)

## Fase 3 — Base para evolução

<a id="t05"></a>
### T05 — Contrato de erro padronizado

**Depende de:** —

Padronizar todas as respostas de erro da API no formato *Problem Details* (RFC 9457).

**Itens do escopo:**
- API-04 — Contrato de erro padronizado

<a id="t06"></a>
### T06 — Integridade da transação

**Depende de:** —

Registrar a data e hora de cada transação e validar as regras de negócio antes de gravar (valor positivo e dentro de um teto, descrição presente, categoria válida), qualquer que seja a origem dos dados.

**Itens do escopo:**
- FN-02 — Data e hora da transação
- FN-06 — Validação de regras de negócio

<a id="t07"></a>
### T07 — Endpoints manuais no padrão REST

**Depende de:** T06

Adequar os endpoints de cadastro e consulta às boas práticas REST: consulta de transação individual, filtros como parâmetros de consulta, status e cabeçalhos corretos, paginação, ordenação e versionamento. Publicar a documentação OpenAPI e criar exemplos de requisição para os endpoints.

**Itens do escopo:**
- API-01 — Adequação às boas práticas REST (endpoints manuais)
- API-03 — Paginação e ordenação
- API-05 — Versionamento da API
- DOC-05 — Documentação OpenAPI
- DOC-06 — Exemplos de requisição e áudio (endpoints manuais)

## Fase 4 — Identidade

<a id="t08"></a>
### T08 — Identidade mínima e isolamento por usuário

**Depende de:** —

Proteger os endpoints com autenticação por token, usando usuários de demonstração pré-configurados, e restringir cada usuário aos próprios dados.

**Itens do escopo:**
- SEG-01 — Identidade mínima
- SEG-02 — Isolamento de dados por usuário

**Nota (T07):** a transação de outro usuário responde 404 `transaction-not-found`, o mesmo corpo de um ID inexistente.

## Fase 5 — Fluxo de voz confiável

<a id="t09"></a>
### T09 — Orquestração de IA na camada de aplicação

**Depende de:** —

Mover a orquestração do fluxo de voz (transcrição → interpretação → resposta → voz) do controller para a camada de aplicação, sem alterar o comportamento. Criar os diagramas de arquitetura e de sequência do fluxo de voz.

**Itens do escopo:**
- FN-09 — Orquestração de IA fora do controller
- DOC-04 — Diagramas (arquitetura e sequência do fluxo de voz)

**Nota (T05):** o `ApiExceptionHandler` trata hoje as falhas da IA direto pelas exceções do SDK (`com.openai.errors.*`). Ao criar o adaptador de IA, ele deve lançar uma exceção neutra da porta (transitória x definitiva) e o handler do SDK sai do advice.

**Nota (T06):** as tools (`@Tool`/`@ToolParam`) já estão em `infrastructure/ai/tool/` desde a T06, fora da camada de aplicação, com os argumentos validados no adaptador; a T09 só move a orquestração (transcrição, `ChatClient`, voz). O `ApiExceptionHandler` também trata `ConstraintViolationException` e `DatabindException` (422 `invalid-command`).

**Nota (T08):** o `UserId` do token chega às tools pelo `toolContext` do `ChatClient` (`TransactionTools.USER_ID`), montado hoje no `TransactionController`. Ao mover a orquestração, o use case de voz recebe o `UserId` como primeiro parâmetro de `execute` e o repassa ao `toolContext`; a tool continua lendo o usuário do `ToolContext`, nunca dos argumentos do modelo.

<a id="t10"></a>
### T10 — Timeouts e retries

**Depende de:** T09

Definir tempo máximo para todas as chamadas externas e repetir automaticamente as que falharem por erros transitórios, com backoff exponencial e jitter.

**Itens do escopo:**
- RES-01 — Timeouts
- RES-02 — Retries com backoff exponencial e jitter

**Nota (T09):** timeout e retry ficam no SDK da OpenAI, por etapa, não nos adaptadores `SpringAi*Gateway`.

<a id="t11"></a>
### T11 — Validação do áudio recebido

**Depende de:** —

Rejeitar, antes de qualquer chamada à IA, o áudio vazio, acima de 1 MB ou cujos bytes não sejam de M4A/MP3/WAV (validação básica, *sanity check*). Duração, áudio inaudível e integridade ficam em FUT-17. Registrar em ADR a limitação e as opções mais robustas ([ADR 0004](adr/0004-validacao-basica-do-audio.md)).

**Itens do escopo:**
- FN-05 — Validação do áudio recebido (versão básica)
- DOC-03 — ADRs (validação básica do áudio)

<a id="t12"></a>
### T12 — Confirmação em duas etapas

**Depende de:** —

Fazer com que comandos de registro gerem uma proposta persistida, que o cliente apenas confirma ou cancela por chamada explícita à API, sem IA; consultas continuam em etapa única. A proposta é um recurso com as ações `confirm` e `cancel`, e o comando de voz continua no endpoint atual. Registrar em ADR (0005) o modelo da proposta, criar os diagramas de sequência do comando de voz e da decisão e adicionar exemplos de requisição para o fluxo de voz.

**Itens do escopo:**
- FN-03 — Confirmação em duas etapas
- RES-08 — Estado persistido
- API-01 — Adequação às boas práticas REST (fluxo de voz)
- DOC-03 — ADRs (modelo da proposta: [ADR 0005](adr/0005-proposta-de-transacao-em-entidade-propria.md))
- DOC-04 — Diagramas (sequência da decisão sobre a proposta, no lugar dos estados: a proposta não tem status)
- DOC-06 — Exemplos de requisição e áudio (fluxo de voz)

**Nota (T06):** a tool de escrita (`propose-transaction`) devolve ao modelo só o necessário para a resposta falada, sem o `TransactionOutput`.

**Nota (T07):** o `docs/openapi.yaml`, a seção "API" do README e `docs/exemplos/voz.http` foram atualizados para o fluxo de proposta.

<a id="t13"></a>
### T13 — Ciclo de vida da proposta

**Depende de:** T12

Expirar propostas não confirmadas após um tempo definido e garantir que confirmar a mesma proposta mais de uma vez não crie transações duplicadas.

**Itens do escopo:**
- FN-04 — Expiração de propostas
- RES-07 — Idempotência na confirmação

**Nota (T12):** a proposta é apagada na decisão, então expirar é apagar a proposta vencida (uma rotina agendada). Confirmar e cancelar já buscam a proposta com bloqueio (`SELECT ... FOR UPDATE`) e a apagam: uma segunda decisão encontra 404 e nenhuma transação nasce em dobro. Falta o teste de confirmação concorrente.

<a id="t14"></a>
### T14 — Respostas claras e proteções da IA

**Depende de:** T12

Padronizar as respostas do assistente (frases fixas, formato único de valores em R$, linguagem simples, repetição do que foi entendido e sem dados que identifiquem o usuário) e restringi-lo ao escopo financeiro, com proteção contra *prompt injection*.

**Itens do escopo:**
- FN-08 — Respostas mais claras
- FN-07 — Proteções da IA (guardrails)

**Nota (T06):** ao trocar a tool de consulta, avaliar o que ela devolve ao modelo (um `ToolResponse`), como na T12.

**Nota (T07):** trocar a tool de consulta por categoria por uma agregação (`COUNT`/`SUM`) feita no banco, em outro use case: é a forma melhor de responder à consulta por voz que já existe, não uma consulta nova. A listagem paginada da T07 não serve à voz, e a tool atual lista tudo de uma categoria sem limite.

## Fase 6 — Resiliência e recuperação de falhas

<a id="t15"></a>
### T15 — Circuit breaker e bulkhead

**Depende de:** T09

Aplicar circuit breaker e limite de chamadas simultâneas a cada dependência de IA. Incluir testes de falha.

**Itens do escopo:**
- RES-03 — Circuit breaker
- RES-04 — Bulkhead
- QA-03 — Testes de falha (parcial)

**Nota (T09):** um circuit breaker e um bulkhead por gateway (`SpeechToTextGateway`, `AssistantGateway`, `TextToSpeechGateway`), pois as três dependências falham de forma independente.

**Nota (T10):** o circuit breaker vê a falha depois dos retries do SDK; timeout e retry por etapa no `application.properties`.

<a id="t16"></a>
### T16 — Degradação controlada

**Depende de:** T09

Definir a resposta para cada cenário de falha: texto quando a geração de voz falhar, mensagem clara quando a IA estiver indisponível e endpoints manuais funcionando normalmente. Gerar uma única vez as mensagens comuns (erros e indisponibilidade) e servi-las como áudio estático. Incluir testes de falha.

**Itens do escopo:**
- RES-05 — Degradação controlada
- QA-03 — Testes de falha (parcial)

**Nota (T09):** o use case de voz (`ProcessVoiceCommandUseCase`) captura a falha do `TextToSpeechGateway` para responder em texto quando a voz falhar; as falhas da transcrição e do assistente seguem como `AiProviderUnavailableException` (503).

**Nota (T12):** ao responder em texto quando a voz falhar, manter a ordem voz → gravação da proposta: a proposta só é gravada depois de a resposta (em texto, nesse caso) estar pronta.

**Nota (T06):** o 422 `invalid-command` (argumentos inválidos da tool) não gera voz hoje e responde só com Problem Details; servir uma mensagem falada como áudio estático.

**Nota (T14):** o texto da resposta falada já é montado pela aplicação (`SpokenAnswers`), não pelo modelo.

<a id="t17"></a>
### T17 — Idempotência no envio de áudio

**Depende de:** T12

Garantir que o reenvio do mesmo comando não gere proposta duplicada nem nova cobrança de IA. Incluir testes de falha. Registrar em ADR o critério para identificar um reenvio.

**Itens do escopo:**
- RES-06 — Idempotência no envio de áudio
- QA-03 — Testes de falha (parcial)
- DOC-03 — ADRs (critério para identificar um reenvio)

**Nota (T10):** com a escrita idempotente, o retry pode subir para o `AssistantGateway` (laço de tools).

<a id="t18"></a>
### T18 — Health checks e encerramento gracioso

**Depende de:** —

Expor indicadores de vida e de prontidão que considerem as dependências, e concluir as requisições em andamento antes de encerrar a aplicação.

**Itens do escopo:**
- RES-09 — Health checks
- RES-10 — Encerramento gracioso

**Nota (T20):** a gravação da auditoria roda em um executor assíncrono (`@Async`) que não espera as tarefas pendentes ao desligar; o encerramento gracioso precisa decidir se espera os registros de auditoria em andamento. O health passa a incluir o MongoDB.

**Nota (T21):** o desligamento não perde registro de auditoria: a publicação pendente fica no MySQL (registro de publicação de eventos do Spring Modulith) e é reenviada depois. Esperar as tarefas `@Async` em andamento passa a ser só uma otimização.

## Fase 7 — Segurança e rastreabilidade

<a id="t19"></a>
### T19 — Rate limiting por usuário

**Depende de:** T08

Limitar a quantidade de requisições por usuário, com limite específico para o endpoint de voz.

**Itens do escopo:**
- SEG-03 — Rate limiting por usuário

<a id="t20"></a>
### T20 — Auditoria em banco NoSQL

**Depende de:** T08, T13

Registrar, em um banco NoSQL separado do transacional, os eventos do fluxo: autor, data e hora, canal/dispositivo, metadados do áudio, transcrição, proposta gerada e decisão do cliente (confirmada, cancelada ou expirada). Registrar em ADR a decisão de manter a auditoria em um banco separado do transacional.

**Itens do escopo:**
- SEG-04 — Auditoria em banco NoSQL
- DOC-03 — ADRs (auditoria em banco separado do transacional)

**Nota (T12):** a proposta é apagada na decisão, então a decisão (confirmada, cancelada ou expirada) precisa ser registrada aqui como evento; o banco transacional não guarda o histórico.

**Nota (T13):** a faxina das propostas vencidas só registra a quantidade apagada, sem evento por proposta. Para auditar cada expiração, a faxina precisa apagar por ID e registrar uma a uma.

<a id="t21"></a>
### T21 — Garantia de entrega da auditoria

**Depende de:** T20

Garantir que nenhum evento de auditoria seja perdido e que falhas no banco de auditoria não interrompam o fluxo do cliente. Incluir testes de falha. Registrar em ADR a estratégia de garantia de entrega.

**Itens do escopo:**
- SEG-05 — Garantia de entrega da auditoria
- QA-03 — Testes de falha (parcial)
- DOC-03 — ADRs (estratégia de garantia de entrega dos eventos)

**Nota (T20):** os pontos a reforçar são o `DomainEventAuditListener` (hoje só registra um `WARN` quando a gravação falha) e o `MongoAuditEntryRepository`. Os registros de auditoria podem se perder em três situações: MongoDB fora do ar, queda da aplicação entre o commit e a gravação, e tarefas assíncronas pendentes no desligamento. Os testes de falha com o MongoDB fora do ar ficam aqui; a T20 cobre só o listener, em teste unitário.

## Fase 8 — Observabilidade e custo

<a id="t22"></a>
### T22 — Métricas técnicas e de negócio

**Depende de:** T13, T15

Expor métricas técnicas (latência por etapa, taxa de erros, estado dos circuit breakers e uso de recursos) e de negócio (taxas de propostas confirmadas, canceladas e expiradas).

**Itens do escopo:**
- OBS-02 — Métricas técnicas
- OBS-03 — Métricas de negócio

**Nota (T12):** a proposta é apagada na decisão, então as taxas de confirmada, cancelada e expirada vêm do evento da decisão (T20) ou de contadores incrementados na decisão, e não de uma contagem por status.

**Nota (T13):** a faxina registra só a quantidade de propostas expiradas (log `Transaction proposals expired`, com `count`). Uma métrica de expiradas por proposta exige apagar por ID e contar cada uma na faxina.

**Nota (T20):** os `DomainEvent` (proposta criada, confirmada e cancelada, e comando não entendido) existem para contar as taxas: `confirmadas / (confirmadas + canceladas + não entendidos)`. A expiração não tem evento, então a taxa de expiradas vem de um contador na faxina.

**Nota (T21):** os listeners de métrica usam `@EventListener`, não `@TransactionalEventListener`: com este último, cada evento ganharia uma publicação no registro do Modulith. A quantidade de publicações `FAILED` é uma métrica candidata.

<a id="t23"></a>
### T23 — Medição de consumo de IA

**Depende de:** T08

Registrar o consumo de IA por requisição e por usuário (tokens, segundos de áudio transcritos e caracteres sintetizados).

**Itens do escopo:**
- CUS-01 — Medição de consumo por usuário

**Nota (T14):** há uma única chamada ao chat por comando (antes eram duas, quando havia tool), e `max-completion-tokens` limita a saída do chat a 500 tokens.

## Fase 9 — Desempenho

<a id="t24"></a>
### T24 — Cache de consultas

**Depende de:** T08, T12

Nos endpoints convencionais (sem IA), reaproveitar os resultados de consultas de transações, por usuário, invalidando-os quando uma transação for registrada (cadastro manual ou confirmação de proposta).

**Itens do escopo:**
- CUS-02 — Cache de consultas

<a id="t25"></a>
### T25 — Cache da síntese de voz

**Depende de:** T14

Reaproveitar o áudio gerado para um mesmo texto de resposta, usando como chave o texto completo e padronizado, com TTL para limitar o armazenamento. Registrar em ADR a estratégia de cache no fluxo de voz.

**Itens do escopo:**
- CUS-03 — Cache da síntese de voz
- DOC-03 — ADRs (estratégia de cache no fluxo de voz)

**Nota (T14):** as frases fixas de `SpokenAnswers` são a chave do cache; a recusa ("não entendido") e a consulta sem gastos tendem a se repetir.

## Fase 10 — Entrega

<a id="t26"></a>
### T26 — Conclusão do README

**Depende de:** T03–T25

Concluir o README com a visão geral do projeto, tecnologias, melhorias implementadas e aprendizados, incluindo a seção de contexto de negócio, que conecta as decisões ao Itaú com as fontes, e a lista de melhorias futuras.

**Itens do escopo:**
- DOC-01 — README completo (conclusão)
- DOC-02 — Contexto de negócio
- DOC-08 — Melhorias futuras documentadas

---

## Rastreabilidade: item → tarefa

| Seção | Itens |
|---|---|
| Fluxo e regras de negócio | FN-01 → T03 · FN-02 → T06 · FN-03 → T12 · FN-04 → T13 · FN-05 → T11 · FN-06 → T06 · FN-07 → T14 · FN-08 → T14 · FN-09 → T09 |
| API | API-01 → T07, T12 · API-02 → T03 · API-03 → T07 · API-04 → T05 · API-05 → T07 |
| Resiliência | RES-01 → T10 · RES-02 → T10 · RES-03 → T15 · RES-04 → T15 · RES-05 → T16 · RES-06 → T17 · RES-07 → T13 · RES-08 → T12 · RES-09 → T18 · RES-10 → T18 |
| Segurança e rastreabilidade | SEG-01 → T08 · SEG-02 → T08 · SEG-03 → T19 · SEG-04 → T20 · SEG-05 → T21 · SEG-06 → T04 · SEG-07 → T02, T27 · SEG-08 → T02 |
| Custo e desempenho | CUS-01 → T23 · CUS-02 → T24 · CUS-03 → T25 |
| Observabilidade | OBS-01 → T04 · OBS-02 → T22 · OBS-03 → T22 |
| Qualidade | QA-01 → T02 + transversal · QA-02 → T02 + transversal · QA-03 → T15, T16, T17, T21 · QA-05 → T02 |
| Documentação e apresentação | DOC-01 → T02, T26 + transversal · DOC-02 → T26 · DOC-03 → T01, T11, T12, T17, T20, T21, T25 · DOC-04 → T09, T12 + transversal · DOC-05 → T07 + transversal · DOC-06 → T07, T12 · DOC-07 → T01 + transversal · DOC-08 → T26 |
