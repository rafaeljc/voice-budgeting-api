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
- `@ToolParam` informa unidade e formato do valor (ex.: valor em reais com 2 casas decimais).
- Opções dos modelos (modelo, temperatura, voz, idioma) em `application.properties`.

## Regras de produto

- A IA nunca grava: tools de escrita só criam propostas; a gravação depende da confirmação do usuário.
- Respostas faladas padronizadas: frases fixas, formato único de valores em R$, sem dados que identifiquem
  o usuário.

## Testes

- Nenhum teste chama a OpenAI real nem exige API key.
- Testes unitários usam mocks das portas de IA; testes de integração simulam a IA com `@AutoConfigureAiStub` e a
  interface neutra `AiStub` (`support/ai/`), sem citar o provedor no `*IT`.
- O protocolo do provedor fica só na implementação do stub (hoje WireMock) e em `src/test/resources/ai/<provedor>/`;
  trocar de provedor não reescreve os testes.
- Falhas da IA (timeout, erro, conexão derrubada) são testadas com stubs do WireMock, estendendo o `AiStub`.

## Não fazer

- Montar o `ChatClient` ou orquestrar etapas no controller.
- Prompt fixo em código Java.
- Logging do Spring AI em `DEBUG` versionado.
- Ligar as opções de observação do Spring AI que logam conteúdo (`log-prompt`, `log-completion`, `log-transcript`,
  `log-text`, `include-content`); transcrição, prompt e resposta nunca vão para o log.
