# T11 — Validação do áudio recebido

Rejeitar antes de qualquer chamada à IA o áudio vazio, acima de 1 MB ou cujos bytes não tenham a assinatura de
M4A/MP4, MP3 ou WAV (FN-05, em versão básica). Documentar em ADR que é um *sanity check*, não a validação ideal, com
as opções mais robustas para produção.

## Escopo

**Dentro:**
- Tamanho máximo explícito: `spring.servlet.multipart.max-file-size=1MB` e `spring.servlet.multipart.max-request-size=1MB`
  no `application.properties`, com comentário do porquê (continua 413 `about:blank`)
- Arquivo vazio (0 bytes) → 400 `validation-error`, `errors: [{parameter: "file", detail: "O arquivo está vazio."}]`
- Formato pela assinatura dos bytes, ignorando `Content-Type` e extensão do cliente → 400 `validation-error`,
  `errors: [{parameter: "file", detail: "Envie o áudio em M4A, MP3 ou WAV."}]`:
  - M4A/MP4: `ftyp` nos bytes 4–7, qualquer *brand* (os áudios de teste são `mp42`)
  - MP3: `ID3` nos bytes 0–2, ou *frame sync* (`0xFF` e `(b1 & 0xE0) == 0xE0`) com *layer* ≠ 00
    (`(b1 & 0x06) != 0`), para não aceitar AAC ADTS (`FF F1`/`FF F9`)
  - WAV: `RIFF` nos bytes 0–3 e `WAVE` nos bytes 8–11
  - arquivo curto demais para a assinatura de um formato não casa com esse formato
- `application/input/AudioFormat` (enum `M4A`, `MP3`, `WAV`, com `extension()` e
  `static Optional<AudioFormat> detect(byte[])`), no padrão do `UserId.parse`
- `ProcessVoiceCommandInput(byte[] audio, AudioFormat format)` e `Audio(byte[] content, AudioFormat format)`: saem
  `filename` e `contentType` do cliente
- `infrastructure/http/request/VoiceCommandRequest(MultipartFile file)` com `@NotNull(message = "{problem.required-part}")`
  e `@AudioFile` (constraint própria; `AudioFileValidator` usa `AudioFormat.detect` e escolhe a mensagem: vazio ou
  formato); `toInput()` monta o `ProcessVoiceCommandInput` com o formato detectado
- `TransactionController.transcribe` recebe `@Valid VoiceCommandRequest` no lugar de `@RequestParam("file")`
- `SpringAiSpeechToTextGateway` envia o nome `audio.<extensão>` do formato detectado
- Log `Voice command received`: `audioFormat` (detectado) no lugar de `audioContentType`; `audioSizeBytes` continua
- `AiStub.transcribedAudioFilename()` (neutro), implementado no `OpenAiWireMockStub` pela parte `file` da última
  requisição de transcrição
- `docs/openapi.yaml` (parte `file` e resposta 400) e README (tabela da API)
- ADR `docs/adr/0004-validacao-basica-do-audio.md`; FUT-17 em `docs/escopo.md`; descrição da T11 em
  `docs/tarefas.md`; regras `api-conventions.md`, `ai-integration.md`, `error-handling.md`

**Fora:**
- duração mínima e máxima, áudio inaudível ou silêncio, transcrição vazia antes do chat
- Apache Tika, código próprio de leitura do contêiner, ffmpeg (ficam como opções no ADR e no FUT-17)
- rate limiter, tipo de erro novo, 415
- recusa do provedor a áudio corrompido com assinatura válida (continua 500 genérico)
- `docs/arquitetura.md` (diagrama de sequência não muda)
- deploy

## Decisões

- Validação básica (*sanity check*): não vazio, tamanho e assinatura dos bytes — validação estrutural, duração e
  silêncio são complexos demais para o escopo; o limite fica documentado em ADR com as opções robustas (decisão do
  usuário)
- Formatos M4A/MP4, MP3 e WAV — gravações de iOS, Android, Safari e ferramentas simples (decisão do usuário)
- Só os bytes decidem o formato; `Content-Type` e extensão do cliente são ignorados em todo o fluxo (decisão do
  usuário)
- 1 MB por arquivo e por requisição, explícito — um comando dura poucos segundos (~40 s em M4A de 206 kbps); a
  requisição só tem o arquivo (decisão do usuário)
- Vazio e formato como 400 `validation-error` na parte `file`, não 415 com tipo próprio — reaproveita o tipo, o
  handler e o contrato; a validação é Bean Validation no `Request`, como pede `api-conventions.md` (decisão do
  usuário)
- `AudioFormat` substitui nome e tipo do cliente; o gateway envia `audio.<extensão>`, coerente com o conteúdo (a
  OpenAI deduz o formato pela extensão); o log registra o formato detectado (decisão do usuário)
- `AudioFormat` em `application/input/`, junto de `Audio` e `ProcessVoiceCommandInput`, que o carregam (decisão do
  usuário); `detect` no próprio enum, como `UserId.parse`, e a fronteira HTTP decide que o vazio é erro
- ADR 0004 + FUT-17 para a limitação e as alternativas (decisão do usuário)
- Duas fases; tipo `feat` (muda o contrato do endpoint de voz)

## Definição de pronto

- [ ] `env -u OPENAI_API_KEY -u JWT_SECRET ./gradlew build` passa (Spotless, Checkstyle, `*Test`, `*IT`, JaCoCo)
- [ ] arquivo vazio e arquivo que não é M4A/MP4, MP3 ou WAV respondem 400 `validation-error` com
      `errors[0].parameter = file`, sem chamada à IA
- [ ] áudio válido com nome e `Content-Type` enganosos é aceito, e a transcrição recebe `audio.<extensão>` do formato
      detectado
- [ ] nenhum uso de `getOriginalFilename`/`getContentType` do arquivo em `src/main`
- [ ] limites de 1 MB explícitos no `application.properties`
- [ ] `docs/openapi.yaml`, README, ADR 0004, FUT-17, descrição da T11 e regras atualizados; checkbox da T11 marcado
      em `docs/tarefas.md` no último commit

## Para futuros agentes

Execute com `/executar-tarefa`. Durante a execução:

- marque `- [x]` no mesmo commit que conclui o item;
- antes do último commit de uma fase, rode o plano de verificação, escreva o resumo da fase com o resultado e
  mude o status para `Concluída` — tudo nesse mesmo commit;
- na última fase, no mesmo commit, confira a definição de pronto e escreva o resumo final (e o plano de deploy,
  se houver);
- mudança no plano só com aprovação do usuário, registrada em Decisões com o motivo.

Contexto para uma sessão nova:

- Branch `feat/validacao-do-audio`; título do PR `feat: valida o áudio recebido antes da ia`. Commits `feat:`/`test:`
  (código e testes) e `docs:` (documentação e regras).
- Estado antes da tarefa: `TransactionController.transcribe` recebe `@RequestParam("file") MultipartFile` e repassa
  `getOriginalFilename()` e `getContentType()` em `ProcessVoiceCommandInput` → `Audio`; o
  `SpringAiSpeechToTextGateway` envia o nome original porque a OpenAI deduz o formato pela extensão. Único limite: o
  padrão do Spring (1 MB por arquivo, 10 MB por requisição), coberto pelo `UploadLimitIT` (servidor real; o MockMvc
  não aplica o limite).
- Parte `file` ausente hoje gera `MissingServletRequestPartException` → 400 com "Campo obrigatório ausente."
  (`ErrorResponseIT.should_returnValidationProblemWithParameter_when_audioFileIsMissing`). Com o record, a ausência
  vira `@NotNull(message = "{problem.required-part}")` → `MethodArgumentNotValidException`; o `ApiExceptionHandler`
  já monta `{parameter, detail}` quando o parâmetro não tem `@RequestBody` (`InvalidField.inRequest`). O corpo do
  teste existente não muda. O handler de `MissingServletRequestPartException` continua (mapeamento genérico).
- Mensagens de Bean Validation vêm de `messages.properties` por chave (`{transaction.amount.invalid}`); criar
  `voice-command.file.empty` e `voice-command.file.unsupported-format`. O `AudioFileValidator` devolve `true` para
  `null` (o `@NotNull` cuida) e usa `buildConstraintViolationWithTemplate` para escolher a mensagem.
- Risco: binding por construtor do `MultipartFile` num record (`@ModelAttribute` implícito), inclusive arquivo de 0
  bytes. É o primeiro ciclo TDD da fase 1; se não funcionar, pare e proponha alternativa ao usuário.
- Assinaturas para testes: M4A `00 00 00 18 66 74 79 70 6D 70 34 32`; MP3 `49 44 33 …` e `FF FB …`; ADTS `FF F1 …`
  (não é MP3); WAV `52 49 46 46 xx xx xx xx 57 41 56 45`; PDF `25 50 44 46 2D`.
- No `*IT` com `@AutoConfigureAiStub`, uma chamada à IA sem stub cai no WireMock e vira 500; um 400 prova que a IA
  não foi chamada.
- `AiStub` é neutro: o método novo fala em nome do arquivo de áudio transcrito, não em multipart ou WireMock; só o
  `OpenAiWireMockStub` conhece a requisição (`server.findAll(postRequestedFor(urlEqualTo("/v1/audio/transcriptions")))`,
  parte `file`, `Content-Disposition`).
- Comentários em inglês, só o porquê; documentação em pt-BR; Spotless confere `.properties` e `.md`.

## Fase 1: Validação, formato detectado e contrato

Status: Concluída  <!-- Não iniciada | Em andamento | Concluída -->

- [x] [TDD] `VoiceCommandRequest(MultipartFile file)` com `@NotNull` no controller (`@Valid`), mantendo o 400 da
      parte ausente (`ErrorResponseIT`) e o fluxo feliz (`VoiceCommandIT`)
- [x] [TDD] `application/input/AudioFormat` com `extension()` e `detect(byte[])`: `AudioFormatTest` cobre M4A, MP3
      (ID3 e *frame sync*), WAV, vazio, curto demais, PDF/texto e ADTS
- [x] [TDD] `@AudioFile` + `AudioFileValidator` em `infrastructure/http/request`: `VoiceCommandIT` → vazio → 400 "O
      arquivo está vazio."; não-áudio → 400 "Envie o áudio em M4A, MP3 ou WAV."; ambos com `errors[0].parameter = file`
      e sem chamada à IA
- [x] [TDD] `ProcessVoiceCommandInput`/`Audio` com `AudioFormat` no lugar de nome e tipo; `toInput()` com o formato
      detectado; gateway envia `audio.<extensão>`: `VoiceCommandIT` envia a gravação M4A como `gravacao.bin` /
      `application/octet-stream` → 200 e `aiStub.transcribedAudioFilename()` = `audio.m4a`
      (`ProcessVoiceCommandUseCaseTest` ajustado)
- [x] [TDD] Log `Voice command received` com `audioFormat` (`M4A`) no lugar de `audioContentType`
      (`VoiceCommandIT.should_logFlowEventsWithTraceId_when_commandIsProcessed`)
- [x] [verificação] `application.properties`: `spring.servlet.multipart.max-file-size=1MB` e
      `max-request-size=1MB`, com comentário; comentário do `UploadLimitIT` passa a citar o limite configurado
- [x] [verificação] `docs/openapi.yaml`: parte `file` (M4A, MP3 ou WAV, até 1 MB, formato identificado pelo
      conteúdo) e 400 com parte ausente, vazia ou fora do formato (exemplos); README, tabela da API

### Plano de verificação

- `env -u OPENAI_API_KEY -u JWT_SECRET ./gradlew build` → `BUILD SUCCESSFUL`
- `./gradlew test --tests dio.budgeting.application.input.AudioFormatTest` → testes passam
- `./gradlew integrationTest --tests dio.budgeting.infrastructure.http.VoiceCommandIT --tests dio.budgeting.infrastructure.http.ErrorResponseIT --tests dio.budgeting.infrastructure.http.UploadLimitIT`
  → testes passam
- `grep -rnE "getOriginalFilename|getContentType" src/main` → nenhuma linha
- `grep -nE "^spring\.servlet\.multipart\.max-(file|request)-size=1MB" src/main/resources/application.properties`
  → 2 linhas
- `grep -n "M4A, MP3 ou WAV" docs/openapi.yaml` → presente na parte `file` e no exemplo do 400

### Resumo da fase

O endpoint de voz recebe `VoiceCommandRequest(@NotNull @AudioFile MultipartFile file)`. `AudioFormat.detect` decide
o formato só pelos bytes (M4A `ftyp`, MP3 `ID3`/*frame sync* com *layer* ≠ 00, WAV `RIFF`+`WAVE`) e o
`AudioFileValidator` responde 400 `validation-error` na parte `file` para vazio ("O arquivo está vazio.") e para
formato fora de M4A/MP3/WAV ("Envie o áudio em M4A, MP3 ou WAV."), antes de qualquer chamada à IA. `Audio` e
`ProcessVoiceCommandInput` carregam `AudioFormat` no lugar de nome e tipo do cliente; o gateway envia
`audio.<extensão>` e o log `Voice command received` registra `audioFormat`. Limites de 1 MB por arquivo e por
requisição explícitos no `application.properties`. `docs/openapi.yaml` e README atualizados.

Decisões: o binding por construtor do `MultipartFile` em record funcionou, inclusive para a parte ausente, então o
plano não mudou. O primeiro ciclo não teve RED (comportamento preservado, coberto por `ErrorResponseIT` e
`VoiceCommandIT`).

Verificação: `env -u OPENAI_API_KEY -u JWT_SECRET ./gradlew build` → `BUILD SUCCESSFUL`; `AudioFormatTest`,
`VoiceCommandIT`, `ErrorResponseIT` e `UploadLimitIT` passam; `grep getOriginalFilename|getContentType src/main` →
nenhuma linha; 2 linhas de limite de 1 MB; "M4A, MP3 ou WAV" presente no `file` e no exemplo do 400.

Para uma sessão nova: falta a fase 2 (ADR 0004, FUT-17, descrição da T11 e regras). No ADR, não afirmar que a
validação "evita custo" de forma absoluta: a documentação da OpenAI não diz se um arquivo recusado pelo provedor é
cobrado (`whisper-1` custa US$ 0,006 por minuto de áudio), então registrar a incerteza.

## Fase 2: ADR, escopo, tarefas e regras

Status: Em andamento  <!-- Não iniciada | Em andamento | Concluída -->

- [x] [verificação] `docs/adr/0004-validacao-basica-do-audio.md` (template MADR, `status: aceito`): contexto
      (FN-05, custo e abuso, sem cliente próprio); opções consideradas com o que protegem e o que custam — só o
      declarado pelo cliente; assinatura dos bytes; estrutura do contêiner com duração declarada (Apache Tika 4.x ou
      código próprio, Java Sound para WAV); decodificação com ffmpeg (duração real, arquivo corrompido,
      silêncio/VAD, conversão para formato canônico); transcrição vazia antes do chat; decisão: assinatura dos
      bytes; consequências: duração não verificada (tamanho não limita segundos: 1 MB a 8 kbps ≈ 16 min), inaudível
      não detectado, assinatura não garante áudio válido (recusa do provedor → 500), duração declarada no cabeçalho
      não protege contra cliente mal-intencionado
- [ ] [verificação] `docs/escopo.md`: FUT-17 "Validação robusta do áudio" (duração, silêncio, integridade), com o
      porquê de ficar para depois e referência ao ADR 0004
- [ ] [verificação] `docs/tarefas.md`: descrição da T11 ajustada (validação básica: vazio, tamanho e formato pelos
      bytes; duração e inaudível em FUT-17; ADR 0004); item DOC-03 (ADRs) na T11 e na rastreabilidade
- [ ] [verificação] Regras: `api-conventions.md` (áudio: M4A, MP3 ou WAV até 1 MB, formato pelos bytes, nunca pelo
      `Content-Type` ou extensão); `ai-integration.md` (o gateway envia `audio.<extensão>` do `AudioFormat`);
      `error-handling.md` (o 400 `validation-error` também cobre a parte `file` vazia ou fora do formato)

### Plano de verificação

- `env -u OPENAI_API_KEY -u JWT_SECRET ./gradlew build` → `BUILD SUCCESSFUL`
- `head -4 docs/adr/0004-validacao-basica-do-audio.md` → front matter com `status: aceito` e a data
- `grep -n "FUT-17" docs/escopo.md` → linha na tabela de melhorias futuras
- `grep -n "0004" docs/tarefas.md docs/escopo.md` → referências ao ADR na T11 e no FUT-17
- `grep -n "DOC-03" docs/tarefas.md` → presente na T11 e na rastreabilidade (`T11`)
- `grep -n "AudioFormat\|audio\.<extensão>" .claude/rules/ai-integration.md` → presente

### Resumo da fase

_(preencher ao concluir a fase)_

## Resumo final

_(preencher ao concluir todas as fases)_
