---
status: aceito
data: 2026-11-03
---

# Validar o áudio recebido pela assinatura dos bytes, como *sanity check*, e deixar a validação robusta para depois

## Contexto e problema

O endpoint de voz envia o áudio recebido à transcrição sem conferir nada além do limite de tamanho (FN-05). Um
arquivo vazio, de outro tipo ou claramente fora do que a API aceita chega até o provedor, gasta uma chamada e vira um
erro genérico. Sem cliente próprio, a API não controla o que é enviado. Que validação barata, feita antes de qualquer
chamada à IA, protege o custo e o abuso sem trazer a complexidade de inspecionar o conteúdo do áudio?

Esta decisão cobre o formato, o tamanho e o arquivo vazio. Duração, silêncio e integridade ficam fora (FUT-17).

## Opções consideradas

- Confiar no declarado pelo cliente (`Content-Type` e extensão): não custa nada, mas não protege de nada, porque o
  cliente escreve o que quiser.
- Assinatura dos bytes (*magic numbers*): lê os primeiros bytes e confirma que são de M4A/MP4, MP3 ou WAV. Barato,
  sem dependência, ignora o declarado. Não garante que o arquivo é um áudio válido nem informa a duração.
- Estrutura do contêiner com duração declarada: Apache Tika 4.x ou código próprio de leitura do contêiner (Java Sound
  para WAV). Rejeita arquivo truncado e dá a duração escrita no cabeçalho, mas uma dependência ou um parser próprio
  para manter, e a duração declarada vem do próprio arquivo.
- Decodificação com ffmpeg: duração real, arquivo corrompido, detecção de silêncio (VAD) e conversão para um formato
  canônico. É a validação completa, ao custo de um processo externo, de CPU por requisição e de mais uma peça a operar.
- Transcrição vazia antes do chat: detecta o áudio inaudível, mas só depois de pagar a transcrição.

## Decisão

Opção escolhida: "assinatura dos bytes", porque é a única que bloqueia o óbvio (vazio, outro tipo de arquivo, formato
que o provedor não trata) sem dependência nem custo por requisição, e só os bytes decidem o formato. O limite de
1 MB por arquivo e por requisição fica explícito. É um *sanity check*, não a validação ideal; as opções mais robustas
ficam registradas como FUT-17.

### Consequências

- Bom, porque o arquivo vazio e o que não é M4A/MP4, MP3 ou WAV respondem 400 sem chamar a IA.
- Bom, porque o gateway envia `audio.<extensão>` coerente com o conteúdo, e `Content-Type` e extensão do cliente são
  ignorados em todo o fluxo.
- Ruim, porque a duração não é verificada: o tamanho não limita os segundos (1 MB a 8 kbps ≈ 16 min), e a
  transcrição é cobrada por minuto de áudio.
- Ruim, porque o áudio inaudível ou silencioso passa e é processado.
- Ruim, porque a assinatura não garante um áudio válido: um arquivo corrompido com assinatura correta chega ao
  provedor e, se ele o recusar, a API responde 500 genérico. A documentação da OpenAI não diz se o arquivo recusado
  é cobrado, então a economia de custo não é garantida para esse caso.
- Ruim, porque a duração declarada no cabeçalho, mesmo com Tika ou parser próprio, não protege contra um cliente
  mal-intencionado; só a decodificação protege.
