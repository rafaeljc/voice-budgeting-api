---
paths:
  - "src/test/**"
---

# Testes

<!-- Rascunho. Base de testes e CI definidos na T02. -->

## Tipos

| Sufixo  | Escopo                         | Contexto Spring | Dependências externas                     |
|---------|--------------------------------|-----------------|-------------------------------------------|
| `*Test` | domain, application            | não             | nenhuma (Mockito para portas)             |
| `*IT`   | adaptadores, HTTP, fluxo        | sim             | Testcontainers MySQL (`@ServiceConnection`), WireMock no lugar da OpenAI |

- <!-- A definir na T02: separação entre *Test e *IT no Gradle e o que roda no CI. -->

## Escrita

- JUnit 5 + AssertJ (não usar `assertEquals`).
- Nome: `should_<resultado>_when_<condição>`.
- Estrutura `// given // when // then`; um comportamento por teste.
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
