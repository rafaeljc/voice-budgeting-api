# T12 — Confirmação em duas etapas

Fazer com que o comando de voz de registro gere uma proposta de transação persistida, que o usuário apenas confirma
ou cancela por chamada explícita à API, sem IA (FN-03, RES-08). Consultas continuam em etapa única. A IA nunca grava
uma transação.

## Escopo

**Dentro:**
- Comando de voz no mesmo endpoint (`POST /api/v1/transactions/ai`, mesmo multipart); só a resposta muda:
  - sucesso → sempre `200` `application/json`:
    `{"speech": {"contentType": "audio/mpeg", "content": "<base64>"}, "transactionProposal": {...}}`
  - `transactionProposal` (`id`, `description`, `amount`, `currency`, `category`) só quando nasce uma proposta;
    consulta ou conteúdo fora do escopo (ex.: um poema) → só `speech`
  - erros como hoje: 400, 413, 422 `invalid-command`, 503, 500
- Tool `persist-transaction` → `propose-transaction`:
  - valida os argumentos como hoje (inválido → 422 `invalid-command`)
  - não grava: anota a proposta num coletor do comando, passado pelo `toolContext` junto do `UserId`
  - devolve ao modelo só descrição, valor, categoria e "aguardando confirmação do usuário" (sem ID e sem
    `TransactionOutput`)
  - uma proposta por comando, com idempotência dentro do comando: segunda chamada com os mesmos argumentos (após a
    normalização do domínio: `strip` da descrição, valor com 2 casas) → mesma proposta, mesma resposta ao modelo;
    com argumentos diferentes → 422 `invalid-command`, nada gravado
- Ordem do `ProcessVoiceCommandUseCase`: transcrição → interpretação → síntese de voz → gravação da proposta (por
  último), com comentário do porquê: não se registra nada antes de a resposta que o anuncia estar pronta, e uma
  falha de voz não deixa proposta órfã
- Prompt do sistema: um gasto por comando; o modelo diz o que entendeu e pede a confirmação, nunca diz que registrou
- `POST /api/v1/transaction-proposals/{id}/confirm` → `201` + `Location: /api/v1/transactions/{tid}` + a transação
  (`TransactionResponse`). Em uma transação de banco: busca da proposta com bloqueio (`SELECT ... FOR UPDATE`), cria
  a `Transaction` com `createdAt` = instante da confirmação, apaga a proposta
- `POST /api/v1/transaction-proposals/{id}/cancel` → `204`. Em uma transação de banco: busca com bloqueio, apaga
- Proposta inexistente, malformada, de outro usuário, já confirmada ou já cancelada → 404
  `urn:problem-type:budgeting:transaction-proposal-not-found` (um único corpo)
- Domínio: `TransactionProposal` (`id`, `userId`, `description`, `amount`, `category`, `createdAt`), sem status — se
  existe, está pendente; tabela própria no MySQL
- Logs só com IDs: `Transaction proposal created` (`transactionProposalId`), `Transaction proposal confirmed`
  (`transactionProposalId`, `transactionId`), `Transaction proposal canceled` (`transactionProposalId`)
- `docs/openapi.yaml`, README, `docs/exemplos/voz.http`, ADR 0005, diagramas de sequência em `docs/arquitetura.md`,
  regras, `docs/tarefas.md` e `docs/escopo.md`

**Fora:**
- expiração da proposta e teste de confirmação concorrente (T13)
- frases fixas, guardrails e troca da tool de consulta (T14)
- resposta em texto quando a voz falha (T16)
- reenvio do mesmo áudio (T17)
- `GET`, listagem e edição de proposta; confirmação por voz
- mudança no `POST /transactions` manual e na tool de consulta
- novo caminho para o comando de voz
- chamada à OpenAI real em qualquer teste ou verificação
- deploy

## Decisões

- Confirmação e cancelamento por chamada explícita à API, sem IA — a decisão é determinística, sem custo de IA e sem
  repetir os erros de transcrição e interpretação que a confirmação barra (decisão do usuário)
- Uma proposta por comando; segunda chamada idêntica é idempotente, diferente é 422 — fluxo simples (um áudio, uma
  proposta) sem punir a repetição do modelo (decisão do usuário)
- A tool só anota; a proposta é gravada por último, depois da voz — nada é registrado antes de a resposta estar
  pronta; justificado por comentário no código, sem ADR (decisão do usuário)
- Transação de banco só na gravação, nunca em volta da síntese de voz — segurar uma conexão do pool durante uma
  chamada externa viola `architecture.md`, e gravar por último dá o mesmo tudo ou nada
- `TransactionProposal` como entidade própria, não `status` na `Transaction` — a tabela de transações guarda só
  gastos reais, e nenhuma leitura precisa filtrar estado (decisão do usuário)
- MySQL, não Redis — confirmar (criar a transação e apagar a proposta) é uma transação local atômica (decisão do
  usuário)
- Proposta apagada na decisão, sem status, sem 409 — uma proposta que existe está pendente; depois de confirmada,
  cancelada ou expirada (T13), qualquer ação é 404 (decisão do usuário)
- `SELECT ... FOR UPDATE` (`@Lock(PESSIMISTIC_WRITE)`) na busca da confirmação e do cancelamento — uma segunda
  decisão simultânea espera e encontra 404; nenhuma transação duplicada (decisão do usuário)
- `createdAt` da transação = instante da confirmação — mantém o significado da T06 (decisão do usuário)
- Endpoint de voz mantido; resposta sempre `200` JSON com a fala em base64 e a proposta — o mínimo de mudança, sem
  `201`/`Location`/`GET` (decisão do usuário)
- Recurso `transaction-proposals` com ações `POST /{id}/confirm` e `/{id}/cancel` (padrão Stripe) — `proposals` é
  genérico e `confirmation`/`cancellation` é artificial; `api-conventions.md` passa a aceitar ações de negócio fora
  do CRUD (decisão do usuário)
- Diagramas de sequência (voz atualizado e decisão novo), não de estados — sem status, não há estados a desenhar; o
  item DOC-04 da T12 é ajustado (decisão do usuário)
- Exemplo de voz aponta para `src/test/resources/audio/recording-1.m4a` (decisão do usuário)
- Os use cases recebem o ID tipado (`TransactionProposalId`, `TransactionId`); quem valida o ID da URL é a borda: o
  controller faz o `parse` do `@PathVariable String` e lança a exceção de "não encontrado" se malformado, para manter
  o 404 do contrato. O `GetTransactionUseCase` foi ajustado da mesma forma (decisão do usuário, durante a execução)
- O adaptador de persistência da proposta (entidade, repositório Spring Data e `JpaTransactionProposalRepository`) foi
  antecipado para o commit do `ConfirmTransactionProposalUseCase`: sem ele o contexto Spring não sobe e todo `*IT`
  falha. Os testes de integração dele continuam no item de persistência (decisão do usuário)
- Três fases; tipo `feat` (muda o contrato do endpoint de voz)
- ADR 0005 só para a proposta em entidade própria (opções `status` na `Transaction`, MySQL, Redis com TTL) — um ADR
  por decisão; confirmação pela API, apagar na decisão e `SELECT ... FOR UPDATE` ficam no plano, no código e nas
  regras (decisão do usuário, ajuste após a aprovação)

## Definição de pronto

- [ ] `env -u OPENAI_API_KEY -u JWT_SECRET ./gradlew build` passa (Spotless, Checkstyle, `*Test`, `*IT`, JaCoCo)
- [ ] comando de registro → `200` com `speech` e `transactionProposal`, proposta gravada, nenhuma transação criada
- [ ] consulta → `200` só com `speech`, nada gravado
- [ ] segunda chamada idêntica da tool → uma proposta; diferente → 422, nada gravado
- [ ] falha na síntese de voz → 503 e nenhuma proposta gravada
- [ ] confirmar → `201` + `Location` + transação com `createdAt` da confirmação, proposta apagada; confirmar ou
      cancelar de novo → 404
- [ ] cancelar → `204`, proposta apagada, nenhuma transação
- [ ] proposta de outro usuário ou ID malformado → 404 `transaction-proposal-not-found`
- [ ] nenhuma ocorrência de `persist-transaction` em `src/` e `docs/` (exceto plan files antigos)
- [ ] `docs/openapi.yaml`, README, `docs/exemplos/voz.http`, ADR 0005, `docs/arquitetura.md`, regras, `docs/tarefas.md`
      e `docs/escopo.md` atualizados; checkbox da T12 marcado no último commit

## Para futuros agentes

Execute com `/executar-tarefa`. Durante a execução:

- marque `- [x]` no mesmo commit que conclui o item;
- antes do último commit de uma fase, rode o plano de verificação, escreva o resumo da fase com o resultado e
  mude o status para `Concluída` — tudo nesse mesmo commit;
- na última fase, no mesmo commit, confira a definição de pronto e escreva o resumo final (e o plano de deploy,
  se houver);
- mudança no plano só com aprovação do usuário, registrada em Decisões com o motivo.

Contexto para uma sessão nova:

- Branch `feat/confirmacao-em-duas-etapas`; título do PR `feat: adiciona confirmação em duas etapas`. Commits
  `feat:`/`test:`/`refactor:` (código e testes) e `docs:` (documentação e regras).
- Estado antes da tarefa: a tool `persist-transaction` (`infrastructure/ai/tool/TransactionTools`) chama o
  `PersistTransactionUseCase` e devolve o `TransactionOutput` ao modelo; `AssistantGateway.interpret(UserId, String)`
  devolve só o texto; `ProcessVoiceCommandUseCase` encadeia transcrição → interpretação → voz e devolve
  `VoiceCommandOutput(byte[] speech)`; o controller responde `audio/mpeg` com `Content-Disposition`.
- Nomes previstos (ajustáveis às convenções do `CLAUDE.md`):
  - `domain/`: `TransactionProposal` (construtor de criação valida como o `Transaction`; reidratação não valida;
    `toTransaction(Instant)`), `TransactionProposalId` (record, `parse` como o `TransactionId`),
    `TransactionProposalRepository` (`save`, `findByIdAndUserIdForUpdate`, `delete`),
    `TransactionProposalNotFoundException`
  - `application/`: `ProposeTransactionInput` (descrição, valor, categoria), `output/AssistantAnswer(String text,
    Optional<ProposeTransactionInput> proposal)` como retorno do `AssistantGateway.interpret`,
    `output/TransactionProposalOutput`, `VoiceCommandOutput(byte[] speech, Optional<TransactionProposalOutput>)`,
    `ConfirmTransactionProposalUseCase` (`@Transactional`, devolve `TransactionOutput`) e
    `CancelTransactionProposalUseCase` (`@Transactional`)
  - `infrastructure/persistence/`: `TransactionProposalEntity`, `TransactionProposalEntityRepository` (método de
    busca com `@Lock(LockModeType.PESSIMISTIC_WRITE)`), `JpaTransactionProposalRepository`
  - `infrastructure/ai/tool/`: `ProposeTransactionToolArguments` (renomeia `PersistTransactionToolArguments`), coletor
    do comando (ex.: `TransactionProposalCollector`, chave no `toolContext` ao lado de `TransactionTools.USER_ID`),
    resposta ao modelo (record) e a exceção da segunda proposta diferente (ex.:
    `MultipleTransactionProposalsException`)
  - `infrastructure/http/`: `TransactionProposalController` (`/api/v1/transaction-proposals`),
    `response/VoiceCommandResponse`, `response/SpeechResponse`, `response/TransactionProposalResponse`
- O use case de voz não é `@Transactional` (chama a IA); a gravação da proposta é um `save` do repositório, atômico
  por si. O `ProcessVoiceCommandUseCase` passa a depender de `TransactionProposalRepository` e `Clock`.
- A exceção da segunda proposta precisa entrar no `rethrowExceptions` do `ToolExecutionConfiguration` (senão vira
  500) e no `@ExceptionHandler` do 422 `invalid-command` do `ApiExceptionHandler`, sem `invalidFields` no log.
- Jackson serializa `byte[]` como base64; `transactionProposal` ausente é omitido (`@JsonInclude(NON_NULL)`).
- `Location` da confirmação aponta para `/api/v1/transactions/{tid}`, não para o caminho atual: montar a partir do
  context path (`ServletUriComponentsBuilder.fromCurrentContextPath()`).
- `@Lock` em método derivado do Spring Data exige transação ativa: a do use case.
- Testes: `MySqlCleaner` trunca as tabelas lidas do `information_schema`, então a tabela nova entra sozinha. O
  `OpenAiWireMockStub.givenToolCall` gera uma `tool_call` por resposta (`chat-tool-call.json`); o teste da segunda
  chamada precisa de `AiStub.givenToolCalls(String tool, String... argumentsJson)` (várias `tool_calls` na mesma
  resposta), neutro no `AiStub`, com o protocolo só no stub e no payload em `src/test/resources/ai/openai/`.
- `VoiceCommandIT.WhenToolFailsUnexpectedly` hoje faz o `TransactionRepository.save` falhar; com a tool sem
  gravação, passa a falhar a tool de consulta (`findAllByUserIdAndCategory`) para continuar cobrindo o 500.
- No `*IT` com `@AutoConfigureAiStub`, chamada à IA sem stub cai no WireMock e vira 500.
- Comentários em inglês, só o porquê; documentação em pt-BR; Spotless confere `.properties` e `.md`.

## Fase 1: Proposta persistida, confirmação e cancelamento

Status: Concluída  <!-- Não iniciada | Em andamento | Concluída -->

- [x] [TDD] Domínio `TransactionProposal`, `TransactionProposalId` e `TransactionProposalNotFoundException`:
      `TransactionProposalTest` cobre as invariantes do construtor de criação (como `TransactionTest`) e
      `toTransaction(Instant)` com os dados da proposta e o instante recebido; `TransactionProposalIdTest` cobre `parse`
- [x] [TDD] `ConfirmTransactionProposalUseCase`: `ConfirmTransactionProposalUseCaseTest` → proposta do usuário →
      grava a transação com `Instant.now(clock)`, apaga a proposta e devolve o `TransactionOutput`; proposta não
      encontrada → `TransactionProposalNotFoundException`, nada gravado
- [x] [TDD] `CancelTransactionProposalUseCase`: `CancelTransactionProposalUseCaseTest` → apaga a proposta; não
      encontrada → `TransactionProposalNotFoundException`
- [x] [TDD] Persistência (`TransactionProposalEntity`, `TransactionProposalEntityRepository` com
      `@Lock(PESSIMISTIC_WRITE)`, `JpaTransactionProposalRepository`) e `TransactionProposalController`:
      `TransactionProposalEndpointsIT` (propostas criadas pelo repositório) → `confirm` → `201`, `Location`
      `/api/v1/transactions/{tid}`, corpo da transação com `createdAt` da confirmação, transação gravada e proposta
      apagada; `confirm` de novo → 404; `cancel` → `204`, proposta apagada e nenhuma transação; `cancel` de novo → 404
- [x] [TDD] 404 `transaction-proposal-not-found` (`ProblemType`, `messages.properties`, `ApiExceptionHandler`):
      `TransactionProposalEndpointsIT` → proposta de outro usuário e ID malformado → 404 com o mesmo corpo do
      inexistente; sem token → 401; `ErrorResponseIT` → log `Request rejected` com `problemType`
- [x] [TDD] Logs `Transaction proposal confirmed` (`transactionProposalId`, `transactionId`) e `Transaction proposal
      canceled` (`transactionProposalId`) com `traceId` (`TransactionProposalEndpointsIT`)
- [x] [verificação] `docs/openapi.yaml`: `POST /api/v1/transaction-proposals/{id}/confirm` (201, `Location`, 404,
      401, 500) e `/cancel` (204, 404, 401, 500), com `bearerAuth` e descrições em pt-BR

### Plano de verificação

- `env -u OPENAI_API_KEY -u JWT_SECRET ./gradlew build` → `BUILD SUCCESSFUL`
- `./gradlew test --tests 'dio.budgeting.application.*TransactionProposal*' --tests 'dio.budgeting.domain.TransactionProposal*'`
  → testes passam
- `./gradlew integrationTest --tests dio.budgeting.infrastructure.http.TransactionProposalEndpointsIT --tests dio.budgeting.infrastructure.http.ErrorResponseIT`
  → testes passam
- `grep -n "transaction-proposals/{id}/confirm\|transaction-proposals/{id}/cancel" docs/openapi.yaml` → 2 caminhos

### Resumo da fase

- Feito: domínio `TransactionProposal` (com `toTransaction(Instant)`), `TransactionProposalId`,
  `TransactionProposalNotFoundException` e a porta `TransactionProposalRepository`; `ConfirmTransactionProposalUseCase` e
  `CancelTransactionProposalUseCase` (ambos `@Transactional`, com busca bloqueante); adaptador JPA da proposta
  (`TransactionProposalEntity`, `@Lock(PESSIMISTIC_WRITE)` no Spring Data); `TransactionProposalController` com
  `POST /{id}/confirm` (201 + `Location` para `/api/v1/transactions/{id}`) e `/cancel` (204); 404
  `transaction-proposal-not-found` no `ApiExceptionHandler`; logs `confirmed` e `canceled`; contrato no
  `docs/openapi.yaml`.
- Decisões (registradas em Decisões): os use cases recebem o ID tipado e o `parse` com o 404 fica no controller
  (`GetTransactionUseCase` ajustado igual, em commit `refactor:` próprio); o adaptador JPA e o 404 no handler foram
  antecipados para os itens 2 e 4, porque sem eles o contexto Spring não sobe ou o "de novo → 404" daria 500. O
  item 5 cobre só os testes do corpo do 404, do 401 e do log, que passaram de primeira.
- Verificação: `build` (Spotless, Checkstyle, `*Test`, `*IT`, JaCoCo), testes unitários da proposta,
  `TransactionProposalEndpointsIT` e `ErrorResponseIT` passam; o `grep` do `openapi.yaml` acha os 2 caminhos. O
  YAML do contrato não foi validado por parser (sem `pyyaml` no ambiente).
- Para uma sessão nova: a fase 2 troca a tool `persist-transaction` por `propose-transaction` e grava a proposta pelo
  `TransactionProposalRepository` (`save`) no `ProcessVoiceCommandUseCase`; `Location` e o 404 já existem. A tabela
  nasce por `ddl-auto=update`.

## Fase 2: Comando de voz gera a proposta

Status: Não iniciada  <!-- Não iniciada | Em andamento | Concluída -->

- [ ] [TDD] `AiStub.givenToolCalls(String tool, String... argumentsJson)` no `OpenAiWireMockStub` (várias
      `tool_calls` na mesma resposta do chat), usado pelos testes abaixo
- [ ] [TDD] `ProcessVoiceCommandUseCase` com `AssistantAnswer` e gravação por último:
      `ProcessVoiceCommandUseCaseTest` → com proposta, grava a `TransactionProposal` (com `Instant.now(clock)`)
      depois da síntese (`InOrder`) e a devolve no output; sem proposta, não grava; síntese falha → nada gravado
      (comentário do porquê da ordem no use case)
- [ ] [TDD] Tool `propose-transaction` com o coletor no `toolContext` e a resposta mínima ao modelo; resposta HTTP
      `200` JSON (`VoiceCommandResponse`): `VoiceCommandIT` → registro → `speech.contentType = audio/mpeg`,
      `speech.content` = base64 da fala do stub, `transactionProposal` com os dados e o `id` da proposta gravada para
      o usuário do token, nenhuma transação criada; consulta (sem tool de escrita) → só `speech`, nada gravado
- [ ] [TDD] Idempotência dentro do comando: `VoiceCommandIT` → duas chamadas idênticas → `200` e uma proposta; duas
      diferentes → 422 `invalid-command` e nenhuma proposta (exceção no `rethrowExceptions` e no handler do 422)
- [ ] [TDD] Falhas: `VoiceCommandIT` → 503 na síntese de voz e 422 de argumentos inválidos sem proposta gravada;
      `WhenToolFailsUnexpectedly` passa a falhar a tool de consulta e continua 500 genérico
- [ ] [TDD] Log `Transaction proposal created` (`transactionProposalId`) no lugar de `Transaction persisted` no fluxo
      de voz; conteúdo continua fora do log (`VoiceCommandIT`)
- [ ] [verificação] Prompt `prompts/system-message.st`: um gasto por comando; dizer o que entendeu e pedir a
      confirmação; nunca dizer que registrou
- [ ] [verificação] `docs/openapi.yaml` (resposta `200` JSON do `/transactions/ai`, schemas `VoiceCommand`, `Speech`
      e `TransactionProposal`, 422 com mais de uma transação no comando), README (tabela da API e `curl` do fluxo de
      voz) e `docs/exemplos/voz.http` (enviar `recording-1.m4a`, confirmar e cancelar pelo `id` da resposta, 404)

### Plano de verificação

- `env -u OPENAI_API_KEY -u JWT_SECRET ./gradlew build` → `BUILD SUCCESSFUL`
- `./gradlew test --tests dio.budgeting.application.ProcessVoiceCommandUseCaseTest` → testes passam
- `./gradlew integrationTest --tests dio.budgeting.infrastructure.http.VoiceCommandIT` → testes passam
- `grep -rn "persist-transaction" src docs --exclude-dir=tarefas` → nenhuma linha
- `grep -n "audio/mpeg" docs/openapi.yaml` → só como `contentType` da fala, não como tipo da resposta do
  `/transactions/ai`

### Resumo da fase

_(preencher ao concluir a fase)_

## Fase 3: ADR, diagramas, regras e tarefas

Status: Não iniciada  <!-- Não iniciada | Em andamento | Concluída -->

- [ ] [verificação] `docs/adr/0005-proposta-de-transacao-em-entidade-propria.md` (template MADR, `status: aceito`):
      contexto (FN-03, RES-08, a IA nunca grava); opções: `status` na `Transaction`, tabela própria no MySQL, Redis
      com TTL; decisão: tabela própria no MySQL; consequências: 404 único depois da decisão, histórico da decisão como
      evento na T20/T22, expiração na T13
- [ ] [verificação] `docs/arquitetura.md`: sequência do comando de voz atualizada (tool de proposta → voz → gravação
      → resposta com fala e proposta) e nova sequência da decisão (confirmar e cancelar, com bloqueio); camada de
      domínio cita `TransactionProposal`
- [ ] [verificação] Regras: `api-conventions.md` (ações de negócio fora do CRUD como `POST /{id}/<verbo>`, `201` +
      `Location` do recurso criado; resposta do comando de voz em JSON com a fala em base64);
      `ai-integration.md` (tool `propose-transaction`, coletor no `toolContext`, uma proposta por comando com
      idempotência, gravação por último); `error-handling.md` (linha do `transaction-proposal-not-found` e da segunda
      proposta diferente no 422); `architecture.md` (porta `TransactionProposalRepository` com busca bloqueante)
- [ ] [verificação] `docs/tarefas.md`: descrição da T12 e item DOC-04 (sequência da confirmação no lugar de estados
      da proposta); notas na T13 (expiração apaga a proposta; teste de confirmação concorrente; o bloqueio já existe),
      T16 (ordem voz → gravação ao responder em texto), T20 e T22 (decisão registrada como evento, porque a proposta é
      apagada); `docs/escopo.md`: API-01 (proposta como recurso das ações; comando de voz no endpoint atual) e DOC-04

### Plano de verificação

- `env -u OPENAI_API_KEY -u JWT_SECRET ./gradlew build` → `BUILD SUCCESSFUL`
- `head -4 docs/adr/0005-proposta-de-transacao-em-entidade-propria.md` → `status: aceito` e a data
- `grep -c "sequenceDiagram" docs/arquitetura.md` → 2
- `grep -n "0005" docs/tarefas.md` → referência na T12
- `grep -n "propose-transaction" .claude/rules/ai-integration.md` → presente
- `grep -n "transaction-proposal-not-found" .claude/rules/error-handling.md` → presente

### Resumo da fase

_(preencher ao concluir a fase)_

## Resumo final

_(preencher ao concluir todas as fases)_
