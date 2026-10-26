---
paths:
  - "src/main/resources/prompts/**"
  - "src/main/java/**/infrastructure/ai/**"
---

# Integração com IA

## Etapas

Transcrição → interpretação com tools → síntese de voz.
Cada etapa é uma chamada isolada, com timeout e retry, circuit breaker e degradação.

## Prompts e tools

- Prompts em `src/main/resources/prompts/*.st`, lidos em UTF-8.
- Prompts, descrições de tools e de parâmetros em pt-BR.
- Nome de tool em kebab-case (`persist-transaction`).
- `@ToolParam` informa unidade e formato do valor (ex.: centavos).
- Opções dos modelos (modelo, temperatura, voz, idioma) em `application.properties`.

## Regras de produto

- A IA nunca grava: tools de escrita só criam propostas; a gravação depende da confirmação do usuário.
- Respostas faladas padronizadas: frases fixas, formato único de valores em R$, sem dados que identifiquem
  o usuário.

## Testes

- Nenhum teste chama a OpenAI real nem exige API key.
- Testes unitários usam mocks das portas de IA; testes de integração simulam a OpenAI com WireMock.
- Falhas da OpenAI (timeout, erro, conexão derrubada) são testadas com stubs do WireMock.

## Não fazer

- Montar o `ChatClient` ou orquestrar etapas no controller.
- Prompt fixo em código Java.
- Logging do Spring AI em `DEBUG` versionado.
