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
  do JSON enviada pelo modelo. A descrição da proposta tem até 100 caracteres (`DESCRIPTION_MAX_LENGTH`: ~5-6 s de
  fala); o endpoint manual aceita 255.
- O usuário chega à tool pelo `ToolContext` (parâmetro do método, excluído do schema enviado ao modelo), montado no
  `toolContext(...)` do `ChatClient` com o `UserId` autenticado; a tool nunca pede o usuário ao modelo e opera só
  para ele.
- O modelo só extrai a intenção: as tools usam `@Tool(returnDirect = true)` e são `void`, então o resultado nunca
  volta ao modelo e há uma única chamada ao chat por comando. O texto que o modelo devolve é descartado.
- As tools não gravam: `propose-transaction` anota uma proposta e `summarize-transactions-by-category` chama
  `SummarizeTransactionsByCategoryUseCase` (contagem e soma no banco, por usuário e categoria) e anota o resumo. A
  anotação vai para um `InterpretationCollector`, criado por comando e passado pelo `toolContext` ao lado do `UserId`.
  O `AssistantGateway` devolve `collector.interpretation()`: `Proposal`, `Summary` ou `NotUnderstood` (o padrão, quando
  nenhuma tool é chamada).
- Coletor estrito: uma única anotação por comando. A segunda chamada de tool, igual ou diferente, de qualquer tool →
  `MultipleToolCallsException` → 422 `invalid-command`, nada gravado. `parallel-tool-calls=false` e
  `max-completion-tokens=500` no `application.properties` limitam o chat.
- A resposta falada é montada pela aplicação, nunca pelo modelo: `SpokenAnswers` (`application/`, métodos estáticos)
  traz as frases fixas, o valor em "R$ 1.234,56", o plural e o nome falado da categoria por `switch` exaustivo sem
  `default`; o `ProcessVoiceCommandUseCase` a monta a partir do `AssistantOutput` e a sintetiza. Sem tool, a frase é a
  de "não entendido".
- A transcrição chega ao modelo por `prompts/user-message.st`, entre `<transcricao>` e `</transcricao>`, montada no
  `SpringAiAssistantGateway` com um `PromptTemplate` (bean do `AssistantConfiguration`) depois de remover `<` e `>`.
  O prompt do sistema restringe o escopo e trata a transcrição como dado, nunca como instrução.
- A proposta é gravada por último, depois da síntese de voz (`ProcessVoiceCommandUseCase`): nada é registrado antes
  de a resposta que o anuncia estar pronta, e uma falha de voz não deixa proposta órfã. A síntese nunca fica dentro
  de uma transação de banco.
- Argumentos inválidos da tool (inclusive categoria inexistente) → 422 `invalid-command` sem resposta do modelo e sem
  voz (ver `error-handling.md`). Falhas de tool nunca voltam ao modelo como texto: o
  `ToolExecutionExceptionProcessor` relança, e qualquer outra falha vira 500.
- `MethodToolCallback` em `ERROR` no `application.properties`: o `WARN` de conversão traz o valor recusado no stack
  trace.

## Consumo

- O consumo (segundos de áudio, tokens, caracteres) é capturado pela observation do Spring AI
  (`AiUsageObservationHandler`), não pelas portas: cobre o 422 `invalid-command`, cuja resposta do chat não chega ao
  gateway. A gravação é assíncrona e de melhor esforço, em `ai_usage_entries`.
- A transcrição usa `response-format=verbose_json`: é o único formato em que o `whisper-1` devolve a duração do áudio.

## Regras de produto

- A IA nunca grava: a tool de escrita só anota uma proposta; a transação só nasce quando o usuário a confirma por
  chamada explícita à API (`POST /transaction-proposals/{id}/confirm`), sem IA.
- Respostas faladas padronizadas: frases fixas montadas pela aplicação, formato único de valores em R$, sem dados
  que identifiquem o usuário.

## Testes

- Nenhum teste chama a OpenAI real nem exige API key.
- Testes unitários usam mocks das portas de IA; testes de integração simulam a IA com `@AutoConfigureAiStub` e a
  interface neutra `AiStub` (`support/ai/`), sem citar o provedor no `*IT`.
- O protocolo do provedor fica só na implementação do stub (hoje WireMock) e em `src/test/resources/ai/<provedor>/`;
  trocar de provedor não reescreve os testes.
- Falhas da IA (timeout, erro, conexão derrubada) são simuladas com `AiStub.givenFailure(AiStage, AiFailure)`, por
  etapa (`TRANSCRIPTION`, `ASSISTANT`, `SPEECH`), implementado no stub do WireMock; o `VoiceCommandIT` cobre cada
  etapa.
- O stub tem uma única resposta de chat por comando, porque `returnDirect` encerra a chamada; o teste confere a frase
  falada com `AiStub.speechText()`, a mensagem enviada ao assistente com `assistantCommand()` e o número de chamadas
  ao chat com `assistantCallCount()`.

## Não fazer

- Montar o `ChatClient` ou orquestrar etapas no controller.
- Tipo do SDK do provedor (`com.openai.*`) fora do aspecto de tradução; tratar exceção do provedor no adaptador ou
  no advice.
- Retry sobre o laço de tools (`interpret` inteiro) enquanto houver tool de escrita não idempotente: refaria a
  conversa e gravaria em dobro.
- Prompt fixo em código Java.
- Deixar o modelo escrever a resposta falada, ou devolver a ele o resultado de uma tool.
- Logging do Spring AI em `DEBUG` versionado.
- Ligar as opções de observação do Spring AI que logam conteúdo (`log-prompt`, `log-completion`, `log-transcript`,
  `log-text`, `include-content`); transcrição, prompt e resposta nunca vão para o log.
