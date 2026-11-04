---
paths:
  - "src/main/resources/prompts/**"
  - "src/main/java/**/infrastructure/ai/**"
---

# Integração com IA

## Etapas

Transcrição → interpretação com tools → síntese de voz.
Cada etapa é uma chamada isolada, com timeout e retry, circuit breaker e degradação, atrás de uma porta própria
(`SpeechToTextGateway`, `AssistantGateway`, `TextToSpeechGateway`) orquestrada por `ProcessVoiceCommandUseCase`.
Os adaptadores `SpringAi*Gateway` ficam em `infrastructure/ai/`.
O áudio chega com o `AudioFormat` detectado pelos bytes; o gateway de transcrição envia `audio.<extensão>` desse
formato (a OpenAI deduz o formato pela extensão), nunca o nome do cliente.
O timeout e o retry de cada etapa ficam no SDK, por requisição HTTP, com chaves por etapa no `application.properties`
(`spring.ai.openai.audio.transcription.*`, `chat.*`, `audio.speech.*`), nunca pela chave global `spring.ai.openai.*`.

## Prompts e tools

- Prompts em `src/main/resources/prompts/*.st`, lidos em UTF-8.
- Prompts, descrições de tools e de parâmetros em pt-BR.
- Nome de tool em kebab-case (`propose-transaction`).
- `@ToolParam` informa unidade e formato do valor (ex.: valor em reais com 2 casas decimais).
- Opções dos modelos (modelo, temperatura, voz, idioma) em `application.properties`.
- O `ChatClient` é um `@Bean` do `AssistantConfiguration` (prompt do sistema e `defaultTools`); o adaptador
  `SpringAiAssistantGateway` só o recebe pronto. Um segundo assistente seria outro `@Bean` com `@Qualifier`.
- A falha do provedor é traduzida por um aspecto (`OpenAiExceptionTranslationAspect`, `@Around` nas portas
  `*Gateway`), a única classe que conhece os tipos do SDK; os adaptadores não tratam exceção do provedor. Trocar de
  provedor é trocar o aspecto. Exceções de argumento de tool passam intactas.
- As tools ficam em `infrastructure/ai/tool/` (adaptador de entrada); os argumentos são um `record` em
  `ai/tool/argument/` (`<Verbo><Substantivo>ToolArguments`) com Bean Validation e `@ToolParam`, validado no
  adaptador (`@Validated` + `@Valid`) e convertido com `arguments.toInput()`. O nome do parâmetro do método é a chave
  do JSON enviada pelo modelo. O que a tool devolve ao modelo é um `record` em `ai/tool/result/`
  (`<Verbo><Substantivo>ToolResult`), com só o necessário para a resposta falada.
- O usuário chega à tool pelo `ToolContext` (parâmetro do método, excluído do schema enviado ao modelo), montado no
  `toolContext(...)` do `ChatClient` com o `UserId` autenticado; a tool nunca pede o usuário ao modelo e opera só
  para ele.
- A tool de escrita (`propose-transaction`) não grava: anota a proposta num `TransactionProposalCollector`, criado
  por comando e passado pelo `toolContext` ao lado do `UserId`, e devolve ao modelo só descrição, valor, categoria
  e a mensagem "Proposta de transação anotada com sucesso.", sem ID. O resultado relata o que a tool fez, sem
  instruir o modelo: o comportamento depois da proposta fica no prompt. O `AssistantGateway` devolve o texto e a
  proposta anotada (`AssistantOutput`).
- Uma proposta por comando, com idempotência dentro dele: a segunda chamada com os mesmos argumentos (depois da
  normalização do domínio: `strip` da descrição, valor com 2 casas) é a mesma proposta; com argumentos diferentes
  → 422 `invalid-command`, nada gravado.
- A proposta é gravada por último, depois da síntese de voz (`ProcessVoiceCommandUseCase`): nada é registrado antes
  de a resposta que o anuncia estar pronta, e uma falha de voz não deixa proposta órfã. A síntese nunca fica dentro
  de uma transação de banco.
- Argumentos inválidos da tool (inclusive categoria inexistente) → 422 `invalid-command` sem resposta do modelo e sem
  voz (ver `error-handling.md`). Falhas de tool nunca voltam ao modelo como texto: o
  `ToolExecutionExceptionProcessor` relança, e qualquer outra falha vira 500.
- `MethodToolCallback` em `ERROR` no `application.properties`: o `WARN` de conversão traz o valor recusado no stack
  trace.

## Regras de produto

- A IA nunca grava: a tool de escrita só anota uma proposta; a transação só nasce quando o usuário a confirma por
  chamada explícita à API (`POST /transaction-proposals/{id}/confirm`), sem IA.
- Respostas faladas padronizadas: frases fixas, formato único de valores em R$, sem dados que identifiquem
  o usuário.

## Testes

- Nenhum teste chama a OpenAI real nem exige API key.
- Testes unitários usam mocks das portas de IA; testes de integração simulam a IA com `@AutoConfigureAiStub` e a
  interface neutra `AiStub` (`support/ai/`), sem citar o provedor no `*IT`.
- O protocolo do provedor fica só na implementação do stub (hoje WireMock) e em `src/test/resources/ai/<provedor>/`;
  trocar de provedor não reescreve os testes.
- Falhas da IA (timeout, erro, conexão derrubada) são simuladas com `AiStub.givenFailure(AiStage, AiFailure)`, por
  etapa (`TRANSCRIPTION`, `ASSISTANT`, `SPEECH`), implementado no stub do WireMock; o `VoiceCommandIT` cobre cada
  etapa.

## Não fazer

- Montar o `ChatClient` ou orquestrar etapas no controller.
- Tipo do SDK do provedor (`com.openai.*`) fora do aspecto de tradução; tratar exceção do provedor no adaptador ou
  no advice.
- Retry sobre o laço de tools (`interpret` inteiro) enquanto houver tool de escrita não idempotente: refaria a
  conversa e gravaria em dobro.
- Prompt fixo em código Java.
- Logging do Spring AI em `DEBUG` versionado.
- Ligar as opções de observação do Spring AI que logam conteúdo (`log-prompt`, `log-completion`, `log-transcript`,
  `log-text`, `include-content`); transcrição, prompt e resposta nunca vão para o log.
