---
paths:
  - "src/main/java/**"
---

# Arquitetura

Detalha a seção "Arquitetura" do `CLAUDE.md`.

## Camadas

| Camada           | Contém                                                         | Pode depender de                         |
|------------------|----------------------------------------------------------------|------------------------------------------|
| `domain`         | entidades, value objects, IDs tipados, enums, portas de repositório | JDK                                  |
| `application`    | use cases, `input/`, `output/`, portas de gateway              | `domain`                                 |
| `infrastructure` | controllers, DTOs HTTP, JPA, IA, segurança, `@Configuration`   | `application`, `domain`, frameworks      |

- `domain` não importa Spring, JPA (`jakarta.persistence.*`), Jackson nem Spring AI.
- `application` não importa Spring Web, JPA nem Spring AI. Não importa nada de `infrastructure`.
- Integrações externas são acessadas por portas (interfaces) definidas em `application`, implementadas em
  `infrastructure`.

## Portas e adaptadores

```
domain/TransactionRepository                                   # porta (interface)
infrastructure/persistence/repository/JpaTransactionRepository # adaptador, implementa a porta
infrastructure/persistence/repository/TransactionEntityRepository # Spring Data (CrudRepository)
infrastructure/persistence/entity/TransactionEntity            # entidade JPA
```

Serviços externos seguem o mesmo padrão, com o sufixo `Gateway` (par do `Repository`): a porta fica na raiz de
`application/`, sem subpacote, como o `TransactionRepository` na raiz de `domain/`; o adaptador, em
`infrastructure/`, leva o nome da tecnologia. Uma porta por etapa, para que cada dependência tenha timeout, retry,
circuit breaker e degradação próprios.

```
application/SpeechToTextGateway                                # porta (interface)
infrastructure/ai/SpringAiSpeechToTextGateway                  # adaptador, implementa a porta
```

Adaptadores de entrada (o mundo externo aciona a aplicação) ficam na mesma camada, cada um com seus DTOs:

```
infrastructure/http/TransactionController                      # adaptador HTTP; DTOs em http/request|response
infrastructure/ai/tool/TransactionTools                        # adaptador de tools de IA; DTOs em ai/tool/argument
```

O adaptador de tools é irmão do controller: o modelo escolhe os argumentos, como um cliente HTTP escolhe o corpo.
`@Tool` e `@ToolParam` descrevem o canal (nome, prompt, schema) e ficam só nele; o use case não os conhece.

Os tipos das portas são neutros: `Audio`, `byte[]` e `String`, nunca `MultipartFile`, `Resource` nem tipos do
Spring AI. A falha do provedor sai da porta como `AiProviderUnavailableException` (transitória) ou
`AiProviderFailedException` (definitiva), em `application/`, sem tipo do provedor.

Entidades JPA nunca saem de `infrastructure`: o adaptador converte com `entity.toDomain()` e `XEntity.from(domain)`.

## Modelagem

- IDs tipados: `record <X>Id(UUID ...)`; construtor sem argumentos gera `UUID.randomUUID()`; construtor compacto
  rejeita `null`.
- Entidades de domínio: classe com dois construtores — criação (gera o ID) e reidratação (todos os campos).
- Value objects: `record` com invariantes no construtor compacto (`IllegalArgumentException`).
- Use cases: uma classe por caso de uso, um único método público `execute(...)`, recebe `Input` e devolve `Output`.
  O usuário autenticado é o primeiro parâmetro (`execute(UserId userId, ...)`): contexto da requisição, fora do
  `Input`.
- Dono dos dados: toda transação tem `userId`. A porta nomeia o usuário em toda leitura
  (`findByIdAndUserId(TransactionId, UserId)`, `findAllByUserId(UserId, TransactionFilter, TransactionSort, PageQuery)`,
  `findAllByUserIdAndCategory(UserId, Category)`); `UserId` é `record` sobre `UUID`, com `parse` como o `TransactionId`.
- A proposta de transação é uma entidade própria (`TransactionProposal`, sem status: se existe, está pendente), com
  a porta `TransactionProposalRepository`. A busca da decisão é bloqueante
  (`findByIdAndUserIdForUpdate`, `SELECT ... FOR UPDATE`) e exige a transação do use case; a decisão apaga a
  proposta.
- Exceções de negócio (`TransactionNotFoundException`, `TransactionProposalNotFoundException`) ficam em `domain/`,
  são unchecked e não importam Spring.
- Consultas paginadas usam tipos próprios do `domain` na assinatura da porta: `PageQuery(number, size)`,
  `PageResult<T>` (com `totalPages()` e `map`), `TransactionSort` (campos e direções fechados) e
  `TransactionFilter`, uma lista de `TransactionCriterion` (`sealed interface`, hoje `CategoryIs`). `Pageable` e
  `Page` do Spring Data nunca aparecem em `domain` nem em `application`: o adaptador JPA os traduz (`PageRequest`,
  `Sort` com desempate por `id`, `Specification` por critério).
- Critério de filtro novo é um `record` em `TransactionCriterion` e um `case` no `switch` do adaptador; o
  compilador acusa o `case` que faltar, e nenhum chamador muda.

## Validação

- Todo dado externo é validado na fronteira em que entra — Bean Validation no `Request` do controller ou nos
  argumentos da tool (`@Validated` + `@Valid` no adaptador). O use case recebe dado válido: o ID do caminho é
  convertido no controller (`<X>Id.parse`), e o use case recebe o ID tipado, nunca o texto.
- As invariantes do domínio (construtor de criação e value objects, `IllegalArgumentException`) são a rede de
  proteção: uma entrada nova que esqueça a validação falha alto (500) em vez de gravar dado inválido. O construtor
  de reidratação não valida.

## Transações de banco (`@Transactional`)

- A fronteira é o use case: `@Transactional` no `execute` dos use cases de escrita, `@Transactional(readOnly = true)`
  nos de consulta. Controller, adaptadores e domínio não abrem transação.
- Escritas que precisam ser atômicas ficam no mesmo use case, em uma única transação.
- Nenhuma chamada externa (HTTP, IA, outro banco) dentro de transação: seguraria uma conexão do pool durante a
  chamada.
- Um use case não chama outro use case transacional; a composição fica fora da transação.
- Exceções de negócio são unchecked: o rollback padrão se aplica, sem `rollbackFor` / `noRollbackFor`.
- `@Transactional` só funciona em chamada vinda de outro bean (proxy); chamada interna na mesma classe ignora a
  anotação.

## Não fazer

- Use case importando client, controller ou repositório JPA de `infrastructure`.
- Anotações do Spring AI (`@Tool`, `@ToolParam`) em `domain` ou `application`.
- Lógica de negócio em controller ou adaptador.
- Injeção por campo; `System.out` / `System.err`.
