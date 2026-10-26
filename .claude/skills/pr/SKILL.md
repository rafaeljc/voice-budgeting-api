---
name: pr
description: Abre o pull request da tarefa atual seguindo o template e o padrão de título do projeto. Use sempre que for abrir um PR.
argument-hint: "[T<nn>]"
---

# Abrir pull request

1. Confirme que a branch atual não é a `main`, segue o padrão `<tipo>/<descrição>` e não tem alterações sem commit.
2. Rode localmente os mesmos comandos dos workflows de `.github/workflows/`. Se algum falhar, corrija antes de
   continuar — não abra PR com check quebrado.
3. Identifique a tarefa ($ARGUMENTS ou o plan file adicionado na branch: `git diff --name-only main...HEAD -- docs/tarefas/`).
4. Monte o título no formato de commit: `<tipo>[!]: <descrição>` — mesmo tipo da branch, sem escopo, minúsculo,
   sem ponto final, até 72 caracteres. Use `!` se houver quebra de contrato.
5. Preencha o corpo com as seções de `.github/pull_request_template.md`, sem os comentários `<!-- -->`:
   - **Descrição**: o que o PR faz e o link para o plan file da tarefa.
   - **Mudanças**: só bullets curtos com as mudanças relevantes, sem explicações — esta lista vira o corpo
     do commit de squash.
   - **Testes**: como as mudanças foram verificadas (testes automatizados e passos de validação manual).
   - **Informações adicionais**: quebras de contrato, decisões (ADRs) e o que mais o revisor precisa saber.
6. Mostre ao usuário o título e o corpo exatamente como serão enviados e aguarde a aprovação explícita.
   Qualquer ajuste é mostrado de novo. Sem aprovação, não crie o PR.
7. Peça ao usuário para enviar a branch (`git push` é exclusivo do usuário) e aguarde a confirmação.
8. Crie o PR com `gh pr create --title "<título>" --body-file <arquivo>` e informe o link.
9. Acompanhe os checks com `gh pr checks <número> --watch`. Todos devem passar; se algum falhar, corrija, peça
   ao usuário um novo push e acompanhe de novo. Alteração no título ou no corpo segue o passo 6.
