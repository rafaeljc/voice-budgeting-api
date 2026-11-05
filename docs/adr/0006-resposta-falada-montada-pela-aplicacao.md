---
status: aceito
data: 2026-11-05
---

# Montar a resposta falada na aplicação, a partir da intenção que o modelo extrai, e não deixar o modelo escrevê-la

## Contexto e problema

Antes da T14, o modelo escrevia a resposta falada. A tool devolvia o resultado ao modelo (a proposta anotada, ou a
lista de transações da categoria consultada) e ele redigia a frase em uma segunda chamada ao chat, seguindo uma
instrução livre do prompt ("diga em uma frase o que entendeu"). A aplicação sintetizava o texto como vinha, qualquer
que fosse o comando, inclusive um fora do escopo ou uma tentativa de injeção de prompt. O escopo pede respostas
padronizadas, com frases fixas e formato único de valores em R$ (FN-08), e um assistente restrito ao escopo
financeiro, que resista a injeção de prompt falada (FN-07). Quem escreve a frase que o usuário ouve: o modelo ou a
aplicação?

Esta decisão cobre quem redige a resposta falada e o que volta ao modelo. O texto exato das frases, o delimitador da
transcrição e as opções do chat ficam no plano da T14, no código e em `.claude/rules/ai-integration.md`.

## Opções consideradas

- O modelo escreve a resposta (como estava): o texto vem do modelo e a aplicação o sintetiza sem alteração; o modelo
  recebe o resultado das tools, incluindo as transações do usuário; um comando que chama tool usa duas requisições ao
  chat.
- O modelo escreve a resposta, guiado por frases-modelo no prompt: a frase continua sendo gerada pelo modelo, então
  seguir o formato depende de ele obedecer ao prompt; o resultado das tools continua indo ao modelo e continuam as duas
  requisições ao chat.
- O modelo só extrai a intenção pelas tools e a aplicação monta a frase: o modelo escolhe a tool e os argumentos (o
  gasto proposto ou a categoria consultada), a tool anota a interpretação e `returnDirect` encerra a chamada, então o
  resultado da tool nunca volta ao modelo. A frase vem de código Java, com os dados da proposta ou do banco. Sem
  chamada de tool, a resposta é a frase de "não entendido"; o texto que o modelo devolver é descartado.

## Decisão

Opção escolhida: "o modelo só extrai a intenção pelas tools e a aplicação monta a frase", porque é a única que torna o
texto de saída previsível por construção: a frase vem do código, e não da obediência do modelo ao prompt, então a mesma
interpretação resulta sempre na mesma frase. É também a única em que o texto do modelo nunca chega à voz nem o
resultado das tools chega ao modelo.

### Consequências

- Bom, porque o texto de saída é previsível: a mesma interpretação gera sempre a mesma frase, no mesmo formato de
  valor. Isso permite testar a frase exata e usá-la como chave do cache de voz (T25).
- Bom, porque o resultado das tools (a contagem e a soma do banco, as transações) não é enviado ao modelo.
- Bom, porque, quando o modelo não chama nenhuma tool (fora do escopo, uma injeção que não resulta em tool), o
  usuário ouve só a frase de "não entendido", e nada é gravado.
- Bom, porque há uma única chamada ao chat por comando em vez de duas, e o pior caso de tempo de um comando cai de
  128 s para 96 s.
- Ruim, porque a resposta é menos flexível: mudar uma frase é mudar código.
- Ruim, porque toda recusa tem a mesma frase; o modelo não explica por que não entendeu.
- Ruim, porque o comportamento depende de `returnDirect` e do coletor estrito, que aceita uma interpretação por
  comando: uma segunda chamada de tool no mesmo comando é recusada com 422.
- Ruim, porque a decisão não impede que uma injeção leve o modelo a chamar uma tool; o que a contém é a delimitação
  da transcrição, o prompt restrito e o fato de a IA só criar propostas, que o usuário confirma.
