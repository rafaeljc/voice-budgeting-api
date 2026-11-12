---
status: aceito
data: 2026-11-12
---

# Guardar a trava de idempotência do comando de voz no Caffeine local

## Contexto e problema

Um comando de voz reenviado com a mesma chave de idempotência, dentro de uma janela de poucos minutos, não pode ser
processado de novo, e isso precisa ser decidido antes de qualquer chamada à IA. A aplicação roda hoje em uma única
instância, e a marca da trava só precisa durar até o fim da janela. Onde guardar essa marca?

## Opções consideradas

- Caffeine local: nenhum serviço novo, acesso em memória, e tamanho e janela só em propriedades. Mas a marca vive na
  memória de cada instância e se perde no reinício.
- MySQL: durável e compartilhado entre instâncias. Mas é escrita e limpeza de uma tabela, no banco transacional, para
  uma marca que vale poucos minutos.
- MongoDB: já existe no projeto, durável e compartilhado, com expiração por índice TTL. Mas o TTL expira em
  intervalos, não no instante exato, e a marca vira escrita em disco.
- Redis: compartilhado entre instâncias e com expiração nativa no instante exato. Mas é um serviço a mais para
  operar, com rede, credenciais e falhas próprias, sem necessidade com uma instância.

## Decisão

Opção escolhida: "Caffeine local", porque é a mais simples e adequada ao momento do projeto (uma instância, janela
curta) e não exige serviço novo.

### Consequências

- Bom, porque não há serviço novo para operar, e a janela e o tamanho se ajustam só em propriedades.
- Bom, porque a marca é consultada em memória, sem ida à rede nem ao disco antes de chamar a IA.
- Ruim, porque a marca é da instância: em várias instâncias, o mesmo reenvio pode cair em outra e passar.
- Ruim, porque a marca se perde no reinício, e o limite por quantidade pode descartá-la antes do fim da janela.
