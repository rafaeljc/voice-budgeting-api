---
name: planejar-tarefa
description: Planeja uma tarefa antes de implementar — explora o código, entrevista o usuário até não restar ambiguidade e versiona um plan file que /executar-tarefa executa em uma sessão nova. Use para trabalho com várias etapas ou que dure mais de uma sessão.
argument-hint: "<referência da tarefa ou descrição>"
---

# Planejar tarefa

O plan file é a fonte da verdade da tarefa: especificação, decisões, progresso e verificação. Uma sessão nova
precisa conseguir executá-lo sem nenhum contexto desta conversa.

## 1. Convenções do projeto

Leia o `CLAUDE.md` e identifique:

- diretório e nome dos plan files;
- padrão de branch;
- padrão de mensagem de commit;
- idioma da documentação;
- lista de tarefas, se houver.

O que não estiver definido, pergunte ao usuário antes de seguir.

## 2. Entender a tarefa

- Entrada ($ARGUMENTS): referência a uma tarefa da lista do projeto → leia a descrição e os itens de escopo;
  texto livre → use-o como ponto de partida. Sem argumento, pergunte qual é a tarefa.
- Explore o código e os documentos relevantes antes de perguntar qualquer coisa. Não pergunte o que o código
  responde.

## 3. Entrevista

- Uma pergunta por vez, em texto, sem `AskUserQuestion`. Quando houver escolhas, apresente as opções e
  recomende uma quando houver motivo.
- Confirme as suposições tiradas do código.
- Cubra: limites do escopo (dentro e fora), dependências, restrições, critérios de pronto, dados, ambientes,
  casos de falha e deploy.
- Se uma resposta abrir uma nova dúvida, pergunte em seguida. Pergunta não feita agora vira bug na implementação.
- Termine com um resumo completo do escopo e só siga quando o usuário confirmar que nada falta.

## 4. Escrever o plano

Use [template.md](template.md), com títulos e conteúdo no idioma de documentação do projeto.

- **Fases**: cada uma é um incremento coerente, termina com build e testes passando, tem verificação que o agente
  roda sozinho e cabe em uma revisão do usuário — se não couber, divida. Tarefa pequena pode ter uma fase só.
- **Itens**: tarefas concretas ("adicionar retry em `PaymentClient.charge`"), nunca temas ("melhorar pagamentos").
  Cada item indica o modo de execução:
  - `[TDD]` — tem comportamento testável;
  - `[verificação]` — sem comportamento testável (configuração, documentação, CI, build, prompts); validado
    pelo plano de verificação da fase.
- **Verificação**: comandos com o resultado esperado; nunca "testar manualmente".
- Resumos das fases, resumo final e plano de deploy ficam como placeholders. Sem deploy, remova a seção.

## 5. Aprovar e versionar

1. Mostre o plano completo ao usuário e aguarde a aprovação explícita. Qualquer ajuste é mostrado de novo.
2. Aprovado: crie a branch no padrão do projeto, a partir da branch principal, e commite somente o plan file.
3. Encerre informando o caminho do plan file e que a execução começa em uma sessão nova com
   `/executar-tarefa <caminho>`. Não comece a implementação.
