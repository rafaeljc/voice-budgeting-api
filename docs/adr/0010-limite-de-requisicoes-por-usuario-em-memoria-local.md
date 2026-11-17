---
status: aceito
data: 2026-11-17
---

# Limitar as requisições por usuário em memória local, atrás de uma interface neutra

## Contexto e problema

Cada usuário autenticado precisa de um limite de requisições (SEG-03), com um limite próprio para as rotas de IA, que
custam e demoram mais, e a recusa do excesso com 429 e `Retry-After`. A aplicação roda hoje em uma única instância.
Onde guardar a contagem e como aplicá-la, deixando espaço para mais instâncias?

## Opções consideradas

- `@RateLimiter` do Resilience4j anotado: simples, mas foi feito para proteger um recurso com limite fixo, não um
  limite por cliente, e cada rota nova precisaria lembrar da anotação.
- Interceptor com o Resilience4j e o Caffeine atrás de uma interface neutra: cobre toda rota autenticada por
  construção, a contagem fica em memória e trocar o armazenamento é escrever um adaptador.
- Redis já agora: o limite vale entre instâncias, mas é um serviço a mais para operar, sem necessidade com uma
  instância, e o Resilience4j não tem backend Redis.
- Limite por IP na aplicação: exige confiar no `X-Forwarded-For`, que só o gateway, o ingress ou o WAF sabem
  validar.

## Decisão

Opção escolhida: "interceptor com o Resilience4j e o Caffeine atrás de uma interface neutra", porque cobre toda rota
autenticada, inclusive as futuras, sem serviço novo, e deixa a migração para o Redis em um adaptador.

### Consequências

- Bom, porque as rotas novas já nascem limitadas, e as de IA ganham o limite próprio só com a `@AiRateLimit`.
- Bom, porque as propriedades `budgeting.rate-limit.*` não dependem do Resilience4j: migrar para o Redis é um
  adaptador novo que lê as mesmas chaves.
- Bom, porque os limitadores ficam fora do `RateLimiterRegistry`: a memória é limitada pelo cache e não há série de
  métrica por usuário.
- Ruim, porque o limite é por instância: com N instâncias, o usuário tem N vezes o valor.
- Ruim, porque a contagem se perde no reinício, e um limitador descartado pelo teto do cache recomeça cheio.
- Ruim, porque a espera do `Retry-After` vem de um cast para a classe interna do Resilience4j.
