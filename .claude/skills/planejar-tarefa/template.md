# <Título da tarefa>

<Objetivo em 1–2 frases.>

## Escopo

**Dentro:**
- <item>

**Fora:**
- <item>

## Decisões

- <decisão> — <motivo>

## Definição de pronto

- [ ] <critério verificável>

## Para futuros agentes

Execute com `/executar-tarefa`. Durante a execução:

- marque `- [x]` no mesmo commit que conclui o item;
- antes do último commit de uma fase, rode o plano de verificação, escreva o resumo da fase com o resultado e
  mude o status para `Concluída` — tudo nesse mesmo commit;
- na última fase, no mesmo commit, confira a definição de pronto e escreva o resumo final (e o plano de deploy,
  se houver);
- mudança no plano só com aprovação do usuário, registrada em Decisões com o motivo.

## Fase 1: <título>

Status: Não iniciada  <!-- Não iniciada | Em andamento | Concluída -->

- [ ] [TDD] <item concreto>
- [ ] [verificação] <item concreto>

### Plano de verificação

- `<comando>` → <resultado esperado>

### Resumo da fase

_(preencher ao concluir a fase)_

## Resumo final

_(preencher ao concluir todas as fases)_

## Plano de deploy

_(opcional — preencher ao concluir todas as fases; remover se a tarefa não tiver deploy)_
