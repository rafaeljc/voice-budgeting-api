---
status: aceito
data: 2026-10-27
---

# Representar valores monetários como string decimal com moeda, em reais e sem arredondamento implícito

## Contexto e problema

O valor era gravado em centavos (`long`) e exibido como se fosse reais, 100× maior (FN-01), e a API expunha o
valor como `double` (API-02). Como representar dinheiro de forma exata e consistente no contrato HTTP, no domínio,
no banco e nas tools de IA?

## Opções consideradas

- Centavos inteiros (`amountInCents`, `long`)
- String decimal com 2 casas mais o código da moeda, no padrão do Open Finance Brasil
- Número JSON decimal (`80.00`)

## Decisão

Opção escolhida: "string decimal com 2 casas mais o código da moeda", porque é o padrão do mercado brasileiro
(Open Finance, Payments API 4.0.0), é exata e evita a leitura como `double` que os clientes fazem de um número JSON.

- **Contrato HTTP:** `amount` string com padrão `^\d{1,16}\.\d{2}$` e `currency` ISO 4217 lado a lado, na entrada e
  na saída; só `BRL`. A resposta usa `toPlainString()`, nunca notação científica.
- **Domínio:** `record Money(BigDecimal value)`. O construtor é estrito: exige valor não nulo, escala exatamente 2
  e no máximo 16 dígitos inteiros, e não ajusta o valor. A fábrica `Money.of` normaliza a escala para 2 sem
  arredondar (`80` → `80.00`; `80.005` é rejeitado) e é usada onde a escala não é garantida, como na tool de IA.
- **Banco:** coluna `amount` como `DECIMAL(18,2)`, em reais.
- **IA:** a tool recebe `amount` em reais (`BigDecimal`) e devolve reais mais `currency`. O modelo repete o valor
  ouvido, sem multiplicar por 100.

### Consequências

- Bom, porque o valor é exato de ponta a ponta e igual ao padrão que clientes bancários brasileiros já conhecem.
- Bom, porque o `Money` rejeita valores que exigiriam arredondamento, em vez de perder centavos em silêncio.
- Bom, porque a IA não faz conta: o valor ouvido é o valor gravado, sem fator de 100×.
- Ruim, porque o cliente precisa enviar e ler string em vez de número.
- Ruim, porque a regra de formato aparece na borda HTTP (regex) e no `Money`, e as duas precisam ficar coerentes.
- Ruim, porque o banco existente com `BIGINT` em centavos precisa ser recriado (`docker compose down -v`), já que o
  `ddl-auto=update` não altera o tipo da coluna.
- Ruim, porque o `Money` não restringe sinal nem impõe teto de negócio; essas regras ficam para a T06.
