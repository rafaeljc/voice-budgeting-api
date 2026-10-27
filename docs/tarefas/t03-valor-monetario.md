# T03 — Correção do valor monetário

Corrigir a conversão entre centavos e reais, que hoje exibe o valor gravado em centavos como se fosse reais (FN-01),
e adotar uma representação exata e consistente de dinheiro em toda a API, no padrão do Open Finance Brasil: string
decimal com 2 casas mais o código da moeda (API-02).

## Escopo

**Dentro:**
- value object `Money` em `domain`, que passa a ser o tipo de `Transaction.amount`
- coluna `amount` como `DECIMAL(18,2)`, com reais
- `PersistTransactionInput` e `TransactionOutput` em reais (`BigDecimal`), com a moeda na saída
- contrato HTTP no padrão Open Finance: `"amount": "80.00"` (string) e `"currency": "BRL"`, na entrada e na saída
- validação de formato do `amount` e da `currency` com Bean Validation (400)
- tools de IA em reais, sem conta feita pelo modelo
- testes: `MoneyTest`, `PersistTransactionUseCaseTest`, `TransactionEndpointsIT` (com o teste de regressão da FN-01
  e os casos de 400) e `VoiceCommandIT`
- ADR `docs/adr/0001-representacao-de-valores-monetarios.md`
- `CLAUDE.md`, `.claude/rules/api-conventions.md` e `.claude/rules/ai-integration.md` coerentes com a nova
  representação
- marcações da T27 (checkbox em `docs/tarefas.md` e último critério de pronto em
  `docs/tarefas/t27-dependencias-vulneraveis.md`) em um commit próprio

**Fora:**
- regras de negócio do valor: positivo e teto (T06); o `Money` não restringe sinal, e o `"0.00"` passa no padrão
- validação de `description` e `category` (T06)
- corpo do 400 em Problem Details e identificação dos campos inválidos (T05); aqui vale o corpo padrão do Spring
- separação entre DTO de tool e `Input`/`Output` e saída de `@Tool`/`@ToolParam` de `application` (T09)
- formatação "R$ 80,00" nas respostas faladas (T14)
- documentação OpenAPI e exemplos de requisição (T07)
- tratamento próprio de erro na tool quando o modelo manda mais de 2 casas (vale o comportamento padrão do Spring
  AI, que devolve o erro ao modelo; respostas claras na T14)
- migrações versionadas de schema (Flyway) e migração dos dados locais
- outras moedas além de `BRL`

## Decisões

- Contrato no padrão do Open Finance Brasil (Payments API 4.0.0): `amount` string com padrão `^\d{1,16}\.\d{2}$`
  (exatamente 2 casas, separador ponto) e `currency` ISO 4217 lado a lado, na entrada e na saída — alinhado ao
  mercado brasileiro e exato; número JSON com casas decimais foi descartado porque clientes o leem como `double`, e
  centavos inteiros (`amountInCents`) foram considerados e preteridos pelo usuário em favor do padrão de mercado
- Campos lado a lado (`amount` + `currency`), não objeto aninhado — formato da API de pagamentos do Open Finance,
  sem a verbosidade do aninhamento da API de contas
- `currency` obrigatória na entrada e só aceita `BRL` — única moeda do produto; o domínio não tem campo de moeda,
  e a constante `Money.CURRENCY = "BRL"` é a fonte única do código
- Domínio com `record Money(BigDecimal value)`: o construtor compacto é estrito e não ajusta o valor — exige valor
  não nulo, escala exatamente 2 (`80.00`; `80`, `80.5` e `80.005` → `IllegalArgumentException`) e no máximo 16
  dígitos inteiros (limite de representação do contrato e do `DECIMAL(18,2)`, não teto de negócio); sem restrição
  de sinal. A fábrica `Money.of(BigDecimal)` normaliza a escala para 2 sem arredondar
  (`setScale(2, RoundingMode.UNNECESSARY)`: `80` → `80.00`, `80.000` → `80.00`, `80.005` → exceção) e delega ao
  construtor — usada onde a escala não é garantida (tool de IA). Escolhido `BigDecimal` em vez de centavos em
  `long`, pelo usuário; construtor estrito + fábrica explícita (decisão do usuário) mantém a invariante visível e
  o `equals` independente da escala de entrada, sem efeito colateral escondido no construtor
- Banco com `DECIMAL(18,2)` na coluna `amount` (16 + 2 dígitos, igual ao contrato)
- Banco local recriado (`docker compose down -v`) em vez de migração — o `ddl-auto=update` não altera o tipo de
  uma coluna existente, não há produção nem dados reais, e Flyway não entra agora (decisão do usuário)
- Bean Validation na borda HTTP (`spring-boot-starter-validation`, `@NotNull` + `@Pattern`, `@Valid`) — regra de
  `api-conventions.md` para validação de formato; formato inválido responde 400 em vez do 500 que a conversão
  daria
- Resposta com `toPlainString()` — nunca notação científica (`8E+1`)
- IA em reais: `@ToolParam` de `amount` como `BigDecimal` "valor em reais, até 2 casas decimais, separador ponto
  (ex.: 80.00)", e a tool de consulta devolve `amount` em reais + `currency` — o modelo repete o valor ouvido em
  vez de multiplicar por 100 (fonte de erros de 100×); a conversão exata fica no `Money`
- `PersistTransactionInput.amount` é `BigDecimal` (não `Money`) porque o record também define o schema da tool, e
  `Money` viraria um objeto aninhado para o modelo; o use case cria o `Money` com `Money.of`. A separação fica para a T09
- ADR 0001 registra a representação (contrato, domínio, banco e IA) — decisão não óbvia, que afeta o contrato
  público e será consultada pela T06 (teto), T07 (OpenAPI) e T14 (formatação em R$)
- Uma fase só — tarefa pequena (esforço P), e domínio, persistência, aplicação e HTTP mudam juntos
- Marcações da T27 em um commit próprio nesta branch, logo depois do plan file — a `main` é protegida e as
  marcações ficaram fora do PR #3

## Definição de pronto

- [ ] `./gradlew build` passa (Spotless, Checkstyle, `*Test`, `*IT` e JaCoCo), sem `OPENAI_API_KEY`
- [ ] Nenhum valor monetário em `double`/`float` em `src/main/java`
- [ ] `POST /transactions` com `{"amount": "80.00", "currency": "BRL", ...}` responde 201 com `"amount": "80.00"`
      e `"currency": "BRL"`, e a listagem por categoria devolve o mesmo valor (não `8000.0`)
- [ ] Formato inválido de `amount` ou `currency` (ou campo ausente) responde 400, sem chegar ao use case
- [ ] Pela voz, a tool recebe o valor em reais e a transação é gravada com o mesmo valor
- [ ] ADR 0001 aceito; `CLAUDE.md` e as regras de API e de IA sem menção a centavos como unidade
- [ ] Marcações da T27 commitadas; checkbox da T03 marcado em `docs/tarefas.md` no último commit

## Para futuros agentes

Execute com `/executar-tarefa`. Durante a execução:

- marque `- [x]` no mesmo commit que conclui o item;
- antes do último commit de uma fase, rode o plano de verificação, escreva o resumo da fase com o resultado e
  mude o status para `Concluída` — tudo nesse mesmo commit;
- na última fase, no mesmo commit, confira a definição de pronto e escreva o resumo final (e o plano de deploy,
  se houver);
- mudança no plano só com aprovação do usuário, registrada em Decisões com o motivo.

Contexto para uma sessão nova:

- Branch `fix/valor-monetario`; título do PR `fix!: corrige o valor monetário no padrão open finance`. Commits:
  `fix!:` no que muda o contrato HTTP ou da tool, `fix:`/`refactor:`/`test:` nos demais, `docs:` para documentação.
- O bug: `TransactionOutput.from` faz `BigDecimal.valueOf(amount).setScale(2).doubleValue()` sobre o valor em
  centavos, sem dividir por 100; `TransactionResponse.amount` e o retorno da tool `list-transactions-by-category`
  saem 100× maiores. Nenhum teste atual verifica o valor na resposta.
- Arquivos afetados: `domain/Transaction.java`, `domain/Money.java` (novo),
  `application/input/PersistTransactionInput.java`, `application/output/TransactionOutput.java`,
  `application/PersistTransactionUseCase.java`, `infrastructure/http/request/TransactionRequest.java`,
  `infrastructure/http/response/TransactionResponse.java`, `infrastructure/http/TransactionController.java`
  (`@Valid`), `infrastructure/persistence/entity/TransactionEntity.java`, `build.gradle`.
- Padrão Open Finance conferido na especificação oficial:
  https://raw.githubusercontent.com/OpenBanking-Brasil/openapi/main/swagger-apis/payments/4.0.0.yml
  (`amount`: string, `^((\d{1,16}\.\d{2}))$`, 4–19 caracteres; `currency`: `^([A-Z]{3})$`, só `BRL`).
- `@Pattern` em Java: `"^\\d{1,16}\\.\\d{2}$"` e `"^BRL$"` (pode referenciar uma constante em tempo de compilação).
- `TransactionEntity`: `@Column(precision = 18, scale = 2) BigDecimal amount`; `from`/`toDomain` convertem com
  `Money`. O schema dos `*IT` é criado do zero a cada execução (Testcontainers), então não depende do banco local.
- O `VoiceCommandIT` envia os argumentos da tool como JSON (`givenToolCall`); com o valor em reais, o argumento
  passa a ser `"amount": 80.00` (número JSON, convertido exatamente para `BigDecimal` pelo Jackson).
- Commits intermediários precisam compilar: se a ordem dos itens deixar uma camada para depois, use uma adaptação
  temporária que o item seguinte remove.
- Regras: `.claude/rules/testing.md` (o que testar; nada de testar mapeamento sem lógica), `architecture.md`
  (value object em `record` com invariantes no construtor compacto), `api-conventions.md`, `ai-integration.md`.

## Fase 1: Money de ponta a ponta

Status: Em andamento  <!-- Não iniciada | Em andamento | Concluída -->

- [x] [verificação] Commitar as marcações da T27 já presentes no working tree (`docs/tarefas.md`, checkbox da
      T27; `docs/tarefas/t27-dependencias-vulneraveis.md`, último critério da definição de pronto) — commit
      `docs: marca a tarefa t27 como concluída`
- [x] [TDD] `domain/Money` (`record Money(BigDecimal value)`, constante `CURRENCY = "BRL"`) com `MoneyTest`:
      construtor estrito (aceita `80.00`; rejeita `null`, escala diferente de 2 e mais de 16 dígitos inteiros com
      `IllegalArgumentException`; aceita 16 dígitos inteiros); fábrica `Money.of` normaliza para escala 2
      (`80` → `80.00`, `80.5` → `80.50`, `80.000` → `80.00`), rejeita `80.005` e devolve valores iguais para
      escalas de entrada diferentes
- [ ] [TDD] `Transaction.amount` como `Money`; `PersistTransactionInput.amount` como `BigDecimal` em reais
      (`@ToolParam` "Valor do gasto em reais, com até 2 casas decimais e ponto como separador (ex.: 80.00)");
      `PersistTransactionUseCase` cria o `Money` com `Money.of`; `TransactionOutput` com `BigDecimal amount` e `String currency`
      (sem `double`); `TransactionEntity.amount` como `BigDecimal` `DECIMAL(18,2)` — `PersistTransactionUseCaseTest`
      verifica que a entrada `80` em reais é gravada como `Money` `80.00`
- [ ] [TDD] Contrato HTTP: `TransactionRequest` e `TransactionResponse` com `amount` (string) e `currency`;
      `toPlainString()` na resposta — `TransactionEndpointsIT`: criação responde 201 com `"amount": "80.00"` e
      `"currency": "BRL"`; listagem por categoria devolve `"80.00"` (regressão da FN-01)
- [ ] [TDD] Validação de formato: `spring-boot-starter-validation` no `build.gradle`; `@NotNull` + `@Pattern` em
      `amount` e `currency`, `@Valid` no `TransactionController` — `TransactionEndpointsIT` parametrizado responde
      400 para `"80,00"`, `"80.5"`, `"80"`, `"abc"`, `"-10.00"`, 17 dígitos inteiros, `currency` `"USD"`, `amount`
      ausente e `currency` ausente, sem gravar nada
- [ ] [TDD] Voz em reais: `VoiceCommandIT` com a tool `persist-transaction` recebendo `"amount": 80.00` grava a
      transação com `Money` `80.00`
- [ ] [verificação] ADR `docs/adr/0001-representacao-de-valores-monetarios.md` (a partir de `docs/adr/template.md`,
      `status: aceito`): contexto (FN-01, API-02), opções (centavos inteiros, string decimal Open Finance, número
      JSON decimal), decisão e consequências — contrato, `Money(BigDecimal)` com escala 2 e sem arredondamento
      implícito, `DECIMAL(18,2)`, IA em reais
- [ ] [verificação] `CLAUDE.md` (Estrutura: `domain/` com `Money`); `.claude/rules/api-conventions.md`
      (Representação: valor monetário como string `^\d{1,16}\.\d{2}$` mais `currency`, com link para o ADR 0001);
      `.claude/rules/ai-integration.md` (exemplo do `@ToolParam` sem "centavos": valor em reais com 2 casas)
- [ ] [verificação] Marcar a T03 em `docs/tarefas.md` (último commit)

### Plano de verificação

- `./gradlew build` → `BUILD SUCCESSFUL`, sem `OPENAI_API_KEY`
- `./gradlew test --tests "dio.budgeting.domain.MoneyTest"` → passa
- `./gradlew integrationTest --tests "dio.budgeting.infrastructure.http.TransactionEndpointsIT"` → passa, com os
  casos de 400
- `./gradlew integrationTest --tests "dio.budgeting.infrastructure.http.VoiceCommandIT"` → passa
- `grep -rnE '\b(double|float|Double|Float)\b' src/main/java` → nenhuma linha
- `grep -rni 'centavo' src/main .claude/rules CLAUDE.md` → nenhuma linha

### Resumo da fase

_(preencher ao concluir a fase)_

## Resumo final

_(preencher ao concluir todas as fases)_

## Plano de deploy

_(preencher ao concluir todas as fases)_ — sem ambiente de produção. Passo local antes do próximo `bootRun`:
`docker compose down -v` (apaga o volume `transaction_data`), porque o `ddl-auto=update` não muda a coluna
`amount` de `BIGINT` (centavos) para `DECIMAL(18,2)` (reais).
