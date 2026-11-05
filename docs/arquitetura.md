# Arquitetura

## Camadas

A aplicação é organizada em três camadas, e as dependências apontam sempre para dentro: `infrastructure` depende de
`application`, que depende de `domain`. As integrações externas (cliente HTTP, provedor de IA, banco de dados) entram
e saem por adaptadores em `infrastructure`; `application` as enxerga só por portas (interfaces).

```mermaid
flowchart TB
    Cliente([Cliente])
    IA([Provedor de IA])
    DB[(Banco de dados)]
    subgraph infrastructure
        entrada["entrada: Controller HTTP, Tools de IA"]
        saida["saída: Adaptadores de IA, Adaptador JPA"]
    end
    subgraph application
        app["Use cases, Portas de IA (gateways)"]
    end
    subgraph domain
        dom["Transaction, TransactionProposal, Money, Category, TransactionRepository, TransactionProposalRepository"]
    end
    Cliente --- infrastructure
    IA --- infrastructure
    DB --- infrastructure
    infrastructure -->|depende de| application
    application -->|depende de| domain
```

## Fluxo de voz

O cliente envia o áudio de um comando. A aplicação o transcreve e pede ao provedor de IA que interprete o texto. Quando
o modelo pede uma tool, ela é executada: uma consulta devolve o resultado ao modelo; um registro **não grava**, só anota
a proposta do modelo, e o modelo recebe apenas a confirmação de que ela foi anotada. A resposta em texto é sintetizada
em voz e, só depois de a voz estar pronta, a proposta é gravada. O cliente recebe a fala e a proposta (`id`,
descrição, valor, categoria e `expiresAt`). Uma consulta, ou um conteúdo fora do escopo, recebe só a fala.

A proposta é gravada por último de propósito: nada é registrado antes de a resposta que o anuncia estar pronta, e uma
falha na síntese de voz não deixa uma proposta órfã. A chamada à IA nunca fica dentro de uma transação de banco.

```mermaid
sequenceDiagram
    participant Cliente
    participant App as Aplicação
    participant IA as Provedor de IA
    participant DB as Banco de dados
    Cliente->>App: áudio
    App->>IA: transcrever áudio
    IA-->>App: texto
    App->>IA: interpretar texto
    IA-->>App: pede uma tool
    App->>App: executa a tool (anota a proposta, não grava)
    App->>IA: resultado da tool
    IA-->>App: resposta em texto
    App->>IA: sintetizar resposta
    IA-->>App: áudio
    App->>DB: grava a proposta
    App-->>Cliente: fala e proposta
```

## Decisão sobre a proposta

Confirmar ou cancelar é uma chamada explícita à API, sem IA: a decisão é determinística e não repete os erros de
transcrição e interpretação que a confirmação existe para barrar. As duas operações rodam em uma transação de banco
que começa buscando a proposta com bloqueio (`SELECT ... FOR UPDATE`) e termina apagando-a. Uma segunda decisão
simultânea espera o bloqueio, não encontra a proposta e responde 404, então nenhuma transação é criada em dobro. Uma
proposta inexistente, malformada, de outro usuário, já confirmada, já cancelada ou vencida responde o mesmo 404.

A proposta vale por 5 minutos (configurável): `expiresAt` é gravado na criação, e a checagem (`isPendingAt`) acontece
depois da busca bloqueante, no instante exato, sem depender da faxina. Uma proposta vencida não é confirmada nem
cancelada, e a linha fica para a faxina.

```mermaid
sequenceDiagram
    participant Cliente
    participant App as Aplicação
    participant DB as Banco de dados
    Cliente->>App: confirmar a proposta
    App->>DB: busca a proposta (com bloqueio)
    App->>DB: cria a transação
    App->>DB: apaga a proposta
    App-->>Cliente: 201 e a transação
    Cliente->>App: cancelar a proposta
    App->>DB: busca a proposta (com bloqueio)
    App->>DB: apaga a proposta
    App-->>Cliente: 204
```

## Faxina das propostas vencidas

Uma rotina agendada (`TransactionProposalExpirationJob`) apaga as propostas vencidas. Ela usa `fixedDelay`: o intervalo
(1 minuto por padrão) conta a partir do fim da execução anterior, então uma execução lenta nunca se sobrepõe à seguinte
na mesma instância. Cada execução lê o relógio uma vez e apaga em lotes (1000 por padrão), cada lote em sua própria
transação, com o mesmo instante, até um lote vir incompleto. A consulta do lote tranca as linhas com `SKIP LOCKED`: uma
proposta que uma confirmação ou um cancelamento está usando é pulada e fica para a próxima execução. A faxina é só
limpeza; a expiração vale sem ela.
