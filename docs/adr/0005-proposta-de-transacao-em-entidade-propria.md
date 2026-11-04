---
status: aceito
data: 2026-11-04
---

# Guardar a proposta de transação em uma entidade própria no MySQL, e não como estado da transação ou em um cache com expiração

## Contexto e problema

A IA nunca grava uma transação: um comando de voz de registro gera uma **proposta**, e o usuário a confirma ou a
cancela por chamada explícita à API (FN-03). A proposta precisa sobreviver a um reinício da aplicação (RES-08), e
confirmar precisa criar a transação e consumir a proposta de forma atômica. Onde guardar a proposta enquanto ela
espera a decisão?

Esta decisão cobre onde a proposta vive e o que acontece com ela depois da decisão. Expiração (T13) e histórico da
decisão (T20) ficam fora.

## Opções consideradas

- Campo `status` na `Transaction` (`PROPOSTA`, `CONFIRMADA`, `CANCELADA`): uma só tabela e uma só entidade, e a
  confirmação vira uma mudança de estado. Mas a tabela de transações passaria a guardar gastos que nunca existiram, e
  toda leitura (listagem, consulta por categoria, totais) teria de filtrar o estado; esquecer o filtro mostra uma
  proposta como gasto real.
- Tabela própria no MySQL: a proposta é uma entidade de domínio (`TransactionProposal`) com a sua tabela, e a tabela
  de transações guarda só gastos reais. Confirmar é uma transação local do banco: cria a `Transaction` e apaga a
  proposta.
- Redis com TTL: a expiração vem de graça e a leitura é rápida. Mas é uma segunda tecnologia para operar, e confirmar
  deixa de ser atômico, porque criar a transação no MySQL e apagar a proposta no Redis são dois sistemas sem
  transação em comum; uma falha entre os dois cria uma transação duplicada ou perde a decisão. Para garantir a
  atomicidade, a aplicação teria de implementar o controle por conta própria (trava distribuída, reserva da proposta
  antes de criar a transação, compensação ou reprocessamento da falha parcial), código que o banco já entrega pronto
  com a transação local e o `SELECT ... FOR UPDATE`.

## Decisão

Opção escolhida: "tabela própria no MySQL", porque mantém a tabela de transações só com gastos reais, dispensa
qualquer filtro de estado nas leituras e faz da confirmação uma única transação local e atômica. A proposta não tem
status: se existe, está pendente. Confirmar e cancelar buscam a proposta com bloqueio (`SELECT ... FOR UPDATE`) e a
apagam, então uma segunda decisão simultânea espera e encontra 404.

### Consequências

- Bom, porque a tabela e as consultas de transações não mudam, e nenhuma leitura precisa saber que propostas existem.
- Bom, porque confirmar não duplica a transação: o bloqueio serializa as decisões e a segunda não encontra a
  proposta.
- Bom, porque a atomicidade vem do próprio banco; não há controle de consistência entre sistemas para escrever,
  testar e manter.
- Bom, porque nenhuma tecnologia nova entra na operação.
- Ruim, porque, depois da decisão, a proposta some: confirmada, cancelada e inexistente respondem o mesmo 404
  `transaction-proposal-not-found`. O cliente não distingue "já decidida" de "nunca existiu".
- Ruim, porque o histórico da decisão não fica no banco transacional. Ele passa a ser registrado como evento na
  auditoria (T20) e nas métricas (T22).
- Ruim, porque a expiração não vem de graça como no Redis: a T13 precisa de uma rotina que apague as propostas
  vencidas.
