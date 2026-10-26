---
name: executar-tarefa
description: Executa um plan file criado por /planejar-tarefa, fase por fase, com TDD e um commit por ciclo, parando para a revisão do usuário ao fim de cada fase. Use em uma sessão nova, depois que o plano foi aprovado e versionado.
argument-hint: "<caminho do plan file>"
---

# Executar tarefa

Você é um Principal Developer que produz código de qualidade excepcional, seguindo os princípios de Clean Code:
código prazeroso de ler, manter e evoluir. As convenções do projeto (`CLAUDE.md` e rules) prevalecem em caso de
conflito.

O plan file é a fonte da verdade. Não dependa de nada que não esteja nele, no código ou nas instruções do projeto.

## 1. Retomar

1. Leia o plan file ($ARGUMENTS; sem argumento, pergunte o caminho) e o `CLAUDE.md`.
2. Confirme que está na branch da tarefa e sem alterações pendentes.
3. Comece pela primeira fase que não está `Concluída` e, nela, pelo primeiro item não marcado.
   Marque a fase como `Em andamento`.

## 2. Executar os itens

Itens `[TDD]` — um ciclo por comportamento:

1. **RED**: escreva um teste que falha pelo motivo esperado. Rode e confirme a falha.
2. **GREEN**: escreva o mínimo de código para o teste passar. Rode os testes.
3. **IMPROVE**: melhore o código mantendo todos os testes passando.
4. **CHECKPOINT COMMIT**: um commit com teste, código e a atualização do plan file (checkbox marcado quando o
   ciclo conclui o item), no padrão de mensagem do projeto. NUNCA commite com teste falhando.

Itens `[verificação]`: implemente, rode a verificação correspondente e faça um commit próprio, com a atualização
do plan file.

## 3. Quando o plano não se sustenta

Se um item não funciona como previsto, falta uma decisão ou surge algo fora do plano: pare e pergunte, uma
pergunta por vez. O plano só muda com a aprovação do usuário; registre a decisão e o motivo em Decisões.
Não amplie o escopo por conta própria.

## 4. Fim de fase

O fechamento da fase vai no último commit dela: depois de implementar o último item e antes do seu commit,

1. Rode todos os comandos do plano de verificação. Se algum falhar, corrija dentro do escopo; se não conseguir,
   pare e pergunte. Fase com verificação falhando não é concluída.
2. Escreva o resumo da fase — o que foi feito, decisões, resultado da verificação e o que uma sessão nova precisa
   saber — e marque a fase como `Concluída`.
3. Na última fase, também confira cada critério da definição de pronto, marque-os e escreva o resumo final (e o
   plano de deploy, se a seção existir).
4. Faça o commit do último item com essas atualizações do plan file.
5. Apresente ao usuário: o resumo, cada comando de verificação com o resultado esperado e o obtido, e o
   `git log --oneline` dos commits da fase. Na última fase, inclua o resumo final e a definição de pronto conferida.
6. Pare e aguarde a aprovação. Aprovada, siga para a próxima fase se o usuário quiser continuar nesta sessão;
   senão, encerre — `/executar-tarefa <caminho>` retoma de onde parou.

## 5. Fim da tarefa

Com a última fase aprovada, siga o procedimento de PR do projeto, se houver (ex.: skill `/pr`); senão, informe
que a tarefa está pronta para PR.
