# T26 — Conclusão do README

Concluir o README como porta de entrada para o avaliador: visão geral, contexto de negócio com fontes, melhorias
implementadas, tecnologias, roteiro do fluxo principal, aprendizados e melhorias futuras (DOC-01, DOC-02, DOC-08). O
roteiro é comprovado de ponta a ponta com IA real.

## Escopo

**Dentro:**
- README reorganizado na ordem: título e badge, visão geral, contexto de negócio, melhorias implementadas,
  tecnologias, pré-requisitos/configuração/execução, como testar o fluxo principal, API, operação, testes,
  processo de desenvolvimento, aprendizados, melhorias futuras
- `docs/operacao.md` (novo) com as seções "Logs e rastreamento", "Métricas" e "Health checks e encerramento"
  movidas do README sem mudar o conteúdo
- `docs/escopo.md`: revisão só dos itens FUT desatualizados pelo que foi entregue
- `.env.example`: commit do bloco Azure OpenAI já presente no working tree da `main`, e uma linha no README
  apontando a alternativa
- `JpaTransactionRepositoryIT`: remoção do overload `transaction(UserId, String, Category)`, sem uso (warning da IDE)
- `CLAUDE.md` (Estrutura): linha para `docs/operacao.md`
- roteiro executado contra o Azure OpenAI do `.env`, com áudios de entrada, falas e respostas salvos no scratchpad

**Fora:**
- `docs/arquitetura.md` e diagramas — o usuário quer os diagramas simples, só com a ideia geral
- ADR novo
- plan files antigos (`docs/tarefas/*.md`) que citam seções do README — são histórico
- `docs/openapi.yaml` e `docs/exemplos/*.http`
- cancelamento de proposta e consulta por voz no roteiro
- aviso de "sem vínculo oficial" com o Itaú (o texto só não pode sugerir vínculo)
- seções do `escopo.md` além da tabela de melhorias futuras

## Decisões

- Seções operacionais detalhadas vão para `docs/operacao.md` — o README fica legível como porta de entrada; executar
  e testar continuam no README, o que mantém o critério "um avaliador executa seguindo apenas o README"
- Contexto de negócio reescrito no README a partir do que foi entregue, com as fontes na própria seção; o
  `escopo.md` fica como registro do planejamento original
- Sem aviso de vínculo oficial — pedido do usuário
- Melhorias implementadas: uma tabela por área do escopo (Fluxo e regras de negócio, API, Resiliência, Segurança e
  rastreabilidade, Custo e desempenho, Observabilidade, Qualidade); colunas melhoria (linguagem de produto), o que
  garante (uma frase), ADR (link, quando houver); sem ID do escopo nem link do PR
- Aprendizados: rascunho do agente a partir dos resumos dos plan files; o usuário revisa na Fase 2
- Melhorias futuras: lista enxuta agrupada por tema (IA e áudio, identidade e segurança, escala e operação,
  qualidade e avaliação), uma linha por item com o motivo resumido, apontando para a tabela do `escopo.md`
- Roteiro só com registro: nenhum áudio de exemplo (`recording-1` a `recording-6`) é consulta; usa o `recording-1`
  e uma nota diz que os demais também são comandos de registro
- O bloco `curl` da seção API se funde ao roteiro, sem duplicar comandos
- `.env.example` e a remoção do código morto entram na T26, cada um em commit próprio — mudanças triviais, sem PR
  próprio
- Verificação com IA real (Azure OpenAI do `.env`, recursos disponíveis); artefatos no scratchpad da sessão de
  execução

## Definição de pronto

- [ ] README com as 12 seções na ordem do escopo, sem as seções operacionais detalhadas
- [ ] `docs/operacao.md` com logs, métricas e health checks, conteúdo idêntico ao que saiu do README
- [ ] Contexto de negócio com cada necessidade ligada a uma melhoria entregue e todas as fontes citadas
- [ ] Todos os links internos (arquivos e âncoras) de `README.md` e `docs/operacao.md` resolvem
- [ ] Todos os links externos das fontes respondem HTTP 200 (ou o problema registrado no resumo da fase)
- [ ] Roteiro do README executado ao pé da letra com Azure OpenAI: proposta gerada, fala MP3 válida, transação
      confirmada (201) e presente na listagem; artefatos no scratchpad, caminho no resumo da fase
- [ ] `./gradlew build` passa e o warning do `JpaTransactionRepositoryIT` some
- [ ] Aprendizados revisados pelo usuário
- [ ] `docs/tarefas.md` com a T26 marcada

## Para futuros agentes

Execute com `/executar-tarefa`. Durante a execução:

- marque `- [x]` no mesmo commit que conclui o item;
- antes do último commit de uma fase, rode o plano de verificação, escreva o resumo da fase com o resultado e
  mude o status para `Concluída` — tudo nesse mesmo commit;
- na última fase, no mesmo commit, confira a definição de pronto e escreva o resumo final (e o plano de deploy,
  se houver);
- mudança no plano só com aprovação do usuário, registrada em Decisões com o motivo.

Contexto para uma sessão nova:

- Branch `docs/conclusao-do-readme`; commits `docs: ...` (exceto a remoção do código morto, `test: ...`); título do
  PR `docs: conclui o readme`.
- O bloco Azure do `.env.example` foi criado na `main` sem commit; ao criar a branch ele veio junto no working tree.
  Se não estiver no working tree, pare e pergunte ao usuário.
- Fontes de conteúdo:
  - contexto de negócio original e fontes: `docs/escopo.md` (seções "Contexto de negócio" e "Fontes");
  - o que foi entregue: seções "Resumo final" de `docs/tarefas/t*.md` e `docs/tarefas.md`; T16 e T24 saíram do escopo
    (commit `3a8eea4`), por isso não há plan file delas;
  - ADRs 0001–0010 em `docs/adr/` (todos aceitos);
  - tecnologias: seção Stack do `CLAUDE.md`;
  - requisitos do desafio (DOC-01): o que o projeto faz, como executar, melhorias implementadas, tecnologias, como
    testar o fluxo principal e aprendizados.
- Melhorias futuras: FUT-01 a FUT-21 em `docs/escopo.md`. Exemplo de item desatualizado: FUT-16 (o coletor local
  `grafana/otel-lgtm` já existe). Revise cada item contra os resumos dos plan files.
- Resposta do comando de voz: `speech.content` (MP3 em base64) e `transactionProposal` (com `id`); ver
  `docs/openapi.yaml`.
- Azure OpenAI: o deployment do Whisper tem limite baixo (~3 requisições por minuto); o roteiro faz uma transcrição
  só. Carregar o `.env` com `set -a && source .env && set +a` antes do `bootRun` e do `generate_token.py`. Nunca
  imprimir nem registrar o conteúdo do `.env`.
- Docker precisa estar ativo (MySQL, MongoDB e LGTM sobem pelo `spring-boot-docker-compose`).

## Fase 1: Arrumação

Status: Concluída  <!-- Não iniciada | Em andamento | Concluída -->

- [x] [verificação] Commitar o bloco Azure OpenAI do `.env.example` (`docs: documenta o azure openai como alternativa
      no env de exemplo`)
- [x] [verificação] Remover `transaction(UserId, String, Category)` de
      `src/test/java/dio/budgeting/infrastructure/persistence/JpaTransactionRepositoryIT.java` (`test: remove método
      auxiliar não usado do teste do repositório`)
- [x] [verificação] Criar `docs/operacao.md` com "Logs e rastreamento", "Métricas" e "Health checks e encerramento"
      movidas do README, sem mudar o conteúdo (só ajustar níveis de título e links relativos); no README, trocar as
      três seções por uma seção "Operação" de um parágrafo que aponta para o arquivo
- [x] [verificação] `CLAUDE.md` (Estrutura): adicionar `docs/operacao.md` ao lado de `docs/openapi.yaml`

### Plano de verificação

- `./gradlew build` → `BUILD SUCCESSFUL`
- `grep -n "transaction(UserId userId, String description, Category category)" src/test/java/dio/budgeting/infrastructure/persistence/JpaTransactionRepositoryIT.java` → nenhuma linha
- `git diff main -- README.md docs/operacao.md` conferido: as linhas removidas do README reaparecem no
  `docs/operacao.md` (exceto títulos e links ajustados)
- `git status --short` → working tree limpo

### Resumo da fase

- Bloco Azure OpenAI do `.env.example` commitado como estava no working tree.
- Removido o overload `transaction(UserId, String, Category)` do `JpaTransactionRepositoryIT`; o IT segue com 2
  testes passando.
- `docs/operacao.md` criado com um título `# Operação`, um parágrafo de abertura (link para o README) e as seções
  "Logs e rastreamento", "Métricas" e "Health checks e encerramento" copiadas sem mudança: os níveis `##` já serviam e
  as seções não tinham links relativos. No README, as três viraram a seção "Operação" (um parágrafo com o link). Nenhum
  documento fora de `docs/tarefas/` apontava para âncoras dessas seções.
- `CLAUDE.md` (Estrutura): linha `docs/operacao.md`.
- Verificação: `./gradlew build` → `BUILD SUCCESSFUL`; o `grep` do overload → nenhuma linha; o trecho removido do
  README (linhas 81–174 do `HEAD` anterior) é idêntico ao corpo do `docs/operacao.md` (`diff` sem saída); working
  tree limpo após o commit.
- Para a Fase 2: o README ainda tem a ordem antiga (Pré-requisitos, Configuração, Execução, API, Operação, Testes);
  a seção "Operação" já existe e entra na posição do escopo.

## Fase 2: Conteúdo novo do README

Status: Em andamento  <!-- Não iniciada | Em andamento | Concluída -->

- [x] [verificação] Visão geral no topo: o que a API faz, o fluxo áudio → transcrição → IA (tool calling) → proposta
      → confirmação → fala, e o princípio "a IA propõe, o cliente decide"; links para escopo, tarefas e arquitetura
- [x] [verificação] Seção "Contexto de negócio": cada necessidade pública do Itaú (ia.i com confirmação explícita,
      fraude com IA/deepfake, valores da cultura, AI First com IA responsável, banco consultivo e confiável)
      ligada ao que a API faz hoje; lista "Fontes" com todas as fontes do `escopo.md`; sem aviso de vínculo e sem
      sugerir vínculo
- [x] [verificação] Seção "Melhorias implementadas": uma tabela por área (ver Decisões), cobrindo todos os itens
      entregues das T03–T25 e T27, com links para os ADRs 0001–0010
- [x] [verificação] Seção "Tecnologias": tabela tecnologia → para que serve, a partir da Stack do `CLAUDE.md`
- [x] [verificação] Configuração: linha sobre a alternativa Azure OpenAI no `.env.example`
- [x] [verificação] Seção "Como testar o fluxo principal": passos numerados com a API no ar — gerar token; enviar
      `recording-1.m4a` e guardar a resposta; extrair a fala (`jq -r .speech.content | base64 -d > fala.mp3`) e
      ouvir; confirmar pelo `transactionProposal.id` (201 + `Location`); listar as transações e ver a nova; nota de
      que `recording-2` a `recording-6` também são comandos de registro. O bloco `curl` da seção API é absorvido aqui
- [x] [verificação] Seção "Processo de desenvolvimento": parágrafo curto (um plan file por tarefa em
      `docs/tarefas/`, planejar e executar com agentes, um PR por tarefa, ADRs em `docs/adr/`, Conventional Commits)
- [x] [verificação] Seção "Aprendizados": rascunho a partir dos resumos dos plan files; mostrar ao usuário e
      aplicar a revisão dele antes de concluir a fase
- [ ] [verificação] Seção "Melhorias futuras": lista agrupada por tema (ver Decisões), apontando para a tabela do
      `escopo.md`
- [ ] [verificação] `docs/escopo.md`: atualizar os itens FUT desatualizados pelo que foi entregue (ex.: FUT-16)

### Plano de verificação

- `./gradlew build` → `BUILD SUCCESSFUL`
- `grep -n '^## ' README.md` → as 12 seções na ordem do escopo
- script de links (scratchpad, não versionado) sobre `README.md` e `docs/operacao.md`: todo link relativo aponta
  para arquivo existente e toda âncora (`#...`) para um título existente → nenhuma falha
- `curl -sIL -o /dev/null -w '%{http_code} %{url_effective}\n' <url>` para cada fonte externa → `200` (falha
  registrada no resumo da fase com o motivo)
- usuário aprovou o texto dos aprendizados

### Resumo da fase

_(preencher ao concluir a fase)_

## Fase 3: Roteiro de ponta a ponta com IA real

Status: Não iniciada  <!-- Não iniciada | Em andamento | Concluída -->

- [ ] [verificação] Executar o roteiro do README ao pé da letra contra o Azure OpenAI do `.env` (`bootRun` em
      segundo plano), salvando no scratchpad da sessão em `roteiro-t26/`: cópia do áudio de entrada
      (`recording-1.m4a`), resposta JSON do comando de voz, `fala.mp3`, resposta da confirmação (status e
      `Location`) e resposta da listagem
- [ ] [verificação] Corrigir no README qualquer comando ou saída que o roteiro revelar errado
- [ ] [verificação] Marcar a T26 em `docs/tarefas.md`

### Plano de verificação

- comando de voz → `200`, com `transactionProposal.id` e `speech.content` não vazios
- `file <scratchpad>/roteiro-t26/fala.mp3` → áudio MPEG
- confirmação → `201` com `Location`
- listagem → contém a transação confirmada
- `./gradlew build` → `BUILD SUCCESSFUL`
- caminho completo de `roteiro-t26/` registrado no resumo da fase, para o usuário ouvir os áudios

### Resumo da fase

_(preencher ao concluir a fase)_

## Resumo final

_(preencher ao concluir todas as fases)_
