---
paths:
  - "src/test/**"
---

# Testes

## Tipos

| Sufixo  | Escopo                         | Contexto Spring | Dependências externas                     |
|---------|--------------------------------|-----------------|-------------------------------------------|
| `*Test` | domain, application            | não             | nenhuma (Mockito para portas)             |
| `*IT`   | adaptadores, HTTP, fluxo        | sim             | Testcontainers MySQL e MongoDB (`@ServiceConnection`), WireMock no lugar da OpenAI |

- `./gradlew test` roda só `*Test` (sem Docker); `./gradlew integrationTest` roda só `*IT` (exige Docker). Os dois
  usam o mesmo source set `src/test/java`, e `./gradlew build` roda tudo, mais Spotless, Checkstyle e JaCoCo.
- O CI (`ci.yml`) roda `./gradlew build` sem `OPENAI_API_KEY`.

## Infraestrutura de integração

- `@IntegrationTest` (`support/`): contexto Spring, perfil `test`, injeção por construtor, MySQL e MongoDB. Todo `*IT` usa.
- MySQL: container `mysql:9.6` único por JVM (`support/mysql/`). O banco é zerado antes de cada teste
  (`TRUNCATE` das tabelas lidas de `information_schema`); não depender de rollback.
- MongoDB: container `mongo:9.0` único por JVM (`support/mongo/`). Os documentos de todas as coleções são apagados antes
  de cada teste (`MongoCleaner`).
- Autenticação: todo `*IT` HTTP envia `Authorization: Bearer` com um token real, assinado por
  `support/security/AccessTokens` (`NimbusJwtEncoder` e o segredo do perfil `test`), não o `jwt()` do
  `spring-security-test`. `AccessTokens.authorization()` usa o usuário padrão; `authorization(userId)` e
  `validClaims`/`sign` cobrem outro usuário e tokens inválidos.
- Auditoria: a gravação é assíncrona, então o `*IT` espera com Awaitility (`await().untilAsserted`), nunca com `sleep`.
  Cada teste usa um usuário novo (`UserId` aleatório) e filtra os registros por ele, porque uma gravação de um teste
  anterior pode chegar depois da limpeza. A ausência de registro é conferida depois de um registro seguinte do mesmo
  usuário ter chegado. Os documentos são lidos crus (`MongoTemplate`, `Document`) para conferir o tipo BSON. A falha do
  MongoDB é coberta só pelo `DomainEventAuditListenerTest`.
- Faxina das propostas vencidas: o job fica desligado no perfil `test`
  (`budgeting.transaction-proposals.expiration-sweep-enabled=false`), para não apagar propostas no meio de outro teste.
  O laço dos lotes é testado pelo `TransactionProposalExpirationJobTest` e o apagar em lote, incluindo a linha
  trancada por outra transação, pelo `JpaTransactionProposalRepositoryIT`; o log do job não tem teste.
- Concorrência: o `TransactionProposalConcurrencyIT` usa `RANDOM_PORT` e `RestClient`, não MockMvc, porque as chamadas
  precisam se sobrepor no banco; ele falha sem o `@Lock(PESSIMISTIC_WRITE)`.
- IA: o perfil `test` aponta a OpenAI para uma URL inválida e desliga o retry do SDK em cada etapa, então um `*IT` que
  aciona a IA sem stub falha na hora.
- `@AutoConfigureAiStub` (opt-in) injeta o bean `AiStub`, que simula a IA: `givenTranscription`, `givenToolCall`,
  `givenToolCalls(AiToolCall...)`, `givenAnswer`, `givenSpeech` e `givenFailure(AiStage, AiFailure)` (falha por etapa).
  O stub tem uma única resposta de chat por comando, e `speechText()`, `assistantCommand()` e `assistantCallCount()`
  leem o que foi enviado à síntese e ao assistente. O stub é zerado antes de cada teste. Nenhum `*IT` cita o provedor.
- Só `support/ai/OpenAiWireMockStub` e os payloads em `src/test/resources/ai/<provedor>/` conhecem o protocolo do
  provedor.
- Config de teste em `support/` usa `@TestConfiguration`; `@Configuration` seria carregada pelo component scan em
  todos os testes.

## Escrita

- JUnit 5 + AssertJ (não usar `assertEquals`).
- Nome: `should_<resultado>_when_<condição>`.
- Estrutura `// given // when // then`; um comportamento por teste.
- Teste unitário: `@ExtendWith(MockitoExtension.class)`, `@Mock` nas portas e `@InjectMocks` na classe testada.
- Áudios de exemplo em `src/test/resources/audio/`.

## O que testar

- Só testes que protegem um comportamento: se o teste for apagado, algum bug passaria despercebido.
- Caminho feliz, casos de borda definidos pela regra ou pelo contrato e falhas que o código trata explicitamente.

## O que não testar

- Falhas que o código não trata.
- Getters, records e DTOs sem lógica.
- Mapeamento sem lógica (`from`, `toInput`, `toDomain`).
- Configuração (`@Configuration`, properties).
- Comportamento do Spring, do Hibernate ou do Jackson.
- A mesma regra em mais de uma camada: o `*IT` verifica a integração, não repete as regras do domínio.

## Não fazer

- `@SpringBootTest` em teste unitário.
- `System.out.println` em teste.
- Mock de classe do framework; mocke portas da aplicação.
- Teste que chama a OpenAI real ou exige API key.
