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
        dom["Transaction, Money, Category, TransactionRepository"]
    end
    Cliente --- infrastructure
    IA --- infrastructure
    DB --- infrastructure
    infrastructure -->|depende de| application
    application -->|depende de| domain
```

## Fluxo de voz

O cliente envia o áudio de um comando. A aplicação o transcreve, pede ao provedor de IA que interprete o texto e,
quando o modelo pede uma tool, executa-a (registra ou consulta uma transação) e devolve o resultado ao modelo. A
resposta em texto é sintetizada em voz e volta ao cliente como áudio.

```mermaid
sequenceDiagram
    participant Cliente
    participant App as Aplicação
    participant IA as Provedor de IA
    Cliente->>App: áudio
    App->>IA: transcrever áudio
    IA-->>App: texto
    App->>IA: interpretar texto
    IA-->>App: pede uma tool
    App->>App: executa a tool (registra ou consulta)
    App->>IA: resultado da tool
    IA-->>App: resposta em texto
    App->>IA: sintetizar resposta
    IA-->>App: áudio
    App-->>Cliente: resposta em áudio
```
