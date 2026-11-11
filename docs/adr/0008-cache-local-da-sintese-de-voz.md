---
status: aceito
data: 2026-11-11
---

# Guardar o áudio sintetizado em cache local da instância, pela abstração de cache do Spring

## Contexto e problema

Toda resposta falada passa pela síntese de voz, que é uma chamada paga e lenta ao provedor. Muitas respostas se
repetem: as frases fixas do `SpokenAnswers` e as frases de proposta de quem repete o mesmo gasto. Reaproveitar o áudio
de um texto já sintetizado reduz custo e latência, mas o cache precisa ter tamanho e tempo de vida limitados, e a
aplicação roda hoje em uma única instância. Onde guardar o áudio, e como deixar a troca por um cache distribuído barata?

## Opções consideradas

- Caffeine local, pela abstração de cache do Spring: nenhum serviço novo, acesso em memória, tamanho e TTL só em
  propriedades. Cada instância tem o seu cache.
- Redis: cache compartilhado entre instâncias e que sobrevive ao reinício. Mas é um serviço a mais para operar, com
  rede, credenciais e falhas próprias, sem necessidade com uma instância e frases curtas.
- MongoDB com índice TTL: já existe no projeto, mas é uma leitura de rede e de disco para guardar áudio que cabe em
  memória, e o índice TTL expira em intervalos, não no instante exato.

## Decisão

Opção escolhida: "Caffeine local, pela abstração de cache do Spring", porque é a mais simples e adequada ao momento do
projeto (uma instância, frases curtas), não exige serviço novo, e a troca para um cache distribuído muda só dependência
e propriedades. O `@Cacheable(cacheNames = "speech", sync = true)` fica no adaptador `SpringAiTextToSpeechGateway`
(cache-aside): a chave é o texto completo vindo do `SpokenAnswers` e o valor é o `byte[]` do áudio. A porta, o use case
e o `SpokenAnswers` não mudam. Configuração em `application.properties`:
`spring.cache.caffeine.spec=maximumSize=500,expireAfterWrite=1h,recordStats`.

O `sync = true` protege contra o *stampede*: no Caffeine o cálculo é atômico por chave, então chamadas simultâneas com
a mesma frase fazem uma única síntese e as outras esperam o resultado. Uma síntese que falha não é gravada, e quem
esperava tenta de novo. Chaves diferentes só se bloqueiam se caírem no mesmo compartimento interno do mapa, o que é
raro e só causa espera. O `sync` aceita um único cache e não aceita `unless`.

### Consequências

- Bom, porque a síntese repetida não chama o provedor: menos custo e menos latência, e o consumo medido pela T23 passa
  a refletir só as sínteses reais.
- Bom, porque não há serviço novo, e tamanho e TTL se ajustam só em propriedades.
- Bom, porque o W-TinyLFU do Caffeine protege as frases quentes das frases de proposta, que quase não se repetem; por
  isso todo texto é guardado.
- Bom, porque o `recordStats` faz o Actuator publicar `cache.gets{cache=speech,result=hit|miss}`, `cache.size` e
  `cache.evictions`.
- Ruim, porque o limite é por quantidade de entradas e não por bytes: um limite por bytes exigiria um `weigher`, e
  portanto um bean do Caffeine em Java. Com dezenas de KB por áudio, o pior caso fica na casa de dezenas de MB.
- Ruim, porque em várias instâncias cada uma aquece o próprio cache, com duplicação de sínteses e hit ratio menor.
- Ruim, porque o cache se perde no reinício.
- Ruim, porque a frase de proposta, com a descrição e o valor ditados, fica em memória por até uma hora.
