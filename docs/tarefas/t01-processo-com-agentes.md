# T01 — Processo de desenvolvimento com agentes

Definir como os agentes trabalham no projeto: instruções, planejamento e execução por plan file, convenções de
branch, commit e PR, e modelo de ADR (DOC-07, DOC-03).

## Escopo

**Dentro:**
- instruções do projeto (`CLAUDE.md`) e regras por tema (`.claude/rules/`)
- escopo e plano de tarefas (`docs/escopo.md`, `docs/tarefas.md`)
- skills `/planejar-tarefa`, `/executar-tarefa` e `/pr`
- modelos de ADR (MADR minimal) e de pull request
- validação de commits (`.githooks/commit-msg`) e bloqueio de push/merge pelo agente (`.claude/hooks/guard_git.py`)
- squash merge padronizado (`scripts/merge.py`)
- `.gitignore` com segredos, arquivos locais do agente, Python, logs e SO

**Fora:**
- CI e verificação de vulnerabilidades (T02)
- remoção das credenciais do `compose.yml` (T02, SEG-08)
- alterações no código da aplicação

## Decisões

- Artefatos já existiam sem commit quando a execução começou; a execução os commitou item a item, sem recriá-los
- Falha do `./gradlew build` por falta do container/datasource nos testes não é corrigida na T01; Testcontainers
  entra em tarefa posterior
- Plan file escrito depois do trabalho, sem entrevista — os artefatos precedem o fluxo que eles definem; o PR
  segue o processo mesmo assim
- Plan file como fonte da verdade, executado em sessão nova — a execução não depende do contexto do planejamento
- Conventional Commits sem escopo e só com título; o squash usa o título do PR e os bullets de "Mudanças" — o
  histórico da `main` fica com um commit por tarefa, legível
- `git push` e merge exclusivos do usuário, garantidos por hook e não só por instrução — o agente pode ignorar
  uma instrução, mas não um hook
- Criar ou editar PR exige aprovação (permissão `ask` + hook) — o PR é público
- Regras por tema em `.claude/rules/`, carregadas por caminho — o `CLAUDE.md` fica curto e cada regra só entra no
  contexto quando é relevante
- `.claude/settings.local.json` e `CLAUDE.local.md` fora do versionamento — configuração pessoal

## Definição de pronto

- [ ] Todos os artefatos do escopo versionados na branch
- [ ] `commit-msg` rejeita mensagem fora do padrão e aceita os commits da branch
- [ ] `guard_git.py` bloqueia `git push`, `git merge`, `gh pr merge` e `--no-verify`, e pede aprovação para
  `gh pr create`
- [ ] `./gradlew build` passa
- [ ] Nenhum arquivo pessoal ou de segredo versionado

## Para futuros agentes

Execute com `/executar-tarefa`. Durante a execução:

- marque `- [x]` no mesmo commit que conclui o item;
- antes do último commit de uma fase, rode o plano de verificação, escreva o resumo da fase com o resultado e
  mude o status para `Concluída` — tudo nesse mesmo commit;
- na última fase, no mesmo commit, confira a definição de pronto e escreva o resumo final (e o plano de deploy,
  se houver);
- mudança no plano só com aprovação do usuário, registrada em Decisões com o motivo.

## Fase 1: Instruções e plano do projeto

Status: Concluída  <!-- Não iniciada | Em andamento | Concluída -->

- [x] [verificação] Ampliar o `.gitignore` (segredos, arquivos locais do agente, Python, logs, SO)
- [x] [verificação] Adicionar `docs/escopo.md` e `docs/tarefas.md`
- [x] [verificação] Adicionar `CLAUDE.md` e `.claude/rules/`

### Plano de verificação

- `git check-ignore .claude/settings.local.json .env CLAUDE.local.md` → as três entradas impressas
- `git status --short` → sem arquivos de segredo ou pessoais pendentes
- `./gradlew build` → `BUILD SUCCESSFUL`

### Resumo da fase

Entregues `.gitignore` ampliado, `docs/escopo.md`, `docs/tarefas.md`, `CLAUDE.md` e `.claude/rules/`. Os artefatos
já existiam sem commit (ver Decisões); cada item foi commitado separadamente.

Verificação: `git check-ignore` imprimiu as três entradas e `git status --short` não mostrou segredo nem arquivo
pessoal. `./gradlew build` falhou de início (`Failed to determine a suitable driver class`): `./gradlew test` não sobe
o `compose.yml` (`spring-boot-docker-compose` está em `developmentOnly`) e não há `spring.datasource.*` no teste. Com
o container no ar (`docker compose up -d --wait`) e `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` apontando para
`localhost:3307`, o build passou (`BUILD SUCCESSFUL`). Sem `OPENAI_API_KEY`, os `*IT` de OpenAI não foram
exercitados.

Para uma sessão nova: para rodar `./gradlew build`, suba o MySQL antes e informe o datasource por variável de
ambiente. O build não foi alterado; Testcontainers entrará em uma tarefa posterior, por decisão do usuário.

## Fase 2: Fluxo de tarefas

Status: Em andamento  <!-- Não iniciada | Em andamento | Concluída -->

- [x] [verificação] Adicionar `docs/adr/template.md` e `.github/pull_request_template.md`
- [ ] [verificação] Adicionar as skills `planejar-tarefa` (com `template.md`), `executar-tarefa` e `pr`

### Plano de verificação

- `ls .claude/skills/*/SKILL.md` → três arquivos
- `grep -c '^## ' .github/pull_request_template.md` → `4` (Descrição, Mudanças, Testes, Informações adicionais)

### Resumo da fase

_(preencher ao concluir a fase)_

## Fase 3: Proteções do git

Status: Não iniciada  <!-- Não iniciada | Em andamento | Concluída -->

- [ ] [verificação] Adicionar `.githooks/commit-msg`, `.claude/hooks/guard_git.py` e `.claude/settings.json`
- [ ] [verificação] Adicionar `scripts/merge.py`

### Plano de verificação

Os arquivos temporários (`m`) ficam fora do repositório.

- `printf 'Feat: X.\n' > m && python3 .githooks/commit-msg m` → exit 1 com o erro de formato
- `printf 'docs: ok\n' > m && python3 .githooks/commit-msg m` → exit 0
- `echo '{"tool_input":{"command":"git push"}}' | python3 .claude/hooks/guard_git.py` → exit 2,
  `Blocked: git push is user-only.`
- `echo '{"tool_input":{"command":"gh pr create"}}' | python3 .claude/hooks/guard_git.py` → JSON com
  `"permissionDecision": "ask"`
- `python3 scripts/merge.py --help` → exit 0 com o uso
- `git log --format=%s main..HEAD` → só títulos no padrão, sem corpo

### Resumo da fase

_(preencher ao concluir a fase)_

## Resumo final

_(preencher ao concluir todas as fases)_
