---
status: aceito
data: 2026-10-28
---

# Correlacionar requisições com o trace ID do OpenTelemetry, logar em JSON ECS e manter o conteúdo fora dos logs

## Contexto e problema

A aplicação não tinha logs próprios, e a configuração versionada escrevia prompts, transcrições e argumentos de
tools no log (`org.springframework.ai=DEBUG`) e o SQL em `System.out` (OBS-01, SEG-06). Como localizar uma
requisição nos logs sem expor texto livre com dados pessoais e financeiros do usuário?

## Opções consideradas

- SDK do X-Ray (instrumentação específica da AWS)
- ID de correlação próprio (`correlationId` gerado pela aplicação)
- OpenTelemetry com o `traceId` como ID de correlação (Micrometer Tracing)
- Cabeçalho de resposta `X-Request-Id`, `x-fapi-interaction-id` (Open Finance) ou `traceresponse`
- Access log na aplicação, ou só métricas e spans

## Decisão

Opção escolhida: "OpenTelemetry com o `traceId` como ID de correlação", devolvido em `X-Request-Id`, porque é a forma
idiomática do Spring Boot 4, neutra de fornecedor, e evita dois IDs paralelos. Os SDKs clássicos do X-Ray estão em
manutenção desde 25/02/2026 e a AWS recomenda OpenTelemetry (ADOT).

- **Correlação:** `spring-boot-starter-opentelemetry`; `traceId` e `spanId` no MDC. A entrada é só pelo `traceparent`
  (W3C); um `X-Request-Id` do cliente é ignorado. A saída é o `X-Request-Id` com o `traceId`, convenção de mercado
  (OpenAI, Stripe). O `x-fapi-interaction-id`, gerado pelo cliente e útil quando a resposta não chega, foi
  preterido e fica como FUT-15.
- **Formato:** JSON ECS (`logging.structured.format.console=ecs`) em todos os ambientes, inclusive `bootRun` e
  testes, para o `*IT` verificar a configuração real. Leitura local com `jq`.
- **Exportação:** OTLP neutro de fornecedor, desligada enquanto não houver endpoint
  (`MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT`); a exportação OTLP de métricas também fica desligada
  até a T22. Em produção: aplicação → OTLP → ADOT Collector ou CloudWatch Agent → X-Ray, sem código específico.
- **Conteúdo fora dos logs:** transcrição, prompt, resposta do modelo, texto sintetizado, argumentos de tools,
  áudio e dados de negócio (descrição, valor, categoria) nunca são logados; o texto livre pode revelar dado pessoal
  sensível (LGPD, art. 5º, II, por exemplo saúde numa compra em farmácia), o tratamento deve se limitar ao mínimo
  necessário (art. 6º, III) e mascarar texto livre não é confiável. Os logs levam só IDs e metadados técnicos, e o
  conteúdo vai para a auditoria (T20). As opções de observação do Spring AI que escrevem conteúdo ficam em `false`.
- **Sem access log na aplicação:** a métrica `http.server.requests` diz quantas requisições falharam e onde, e os
  spans dizem quais. O access log do Tomcat fica fora do Logback e sem `traceId`, e o
  `CommonsRequestLoggingFilter` emite texto livre com opção de corpo e cabeçalhos.
- **Nível por exceção** (aplicado na T05): inesperada (5xx) em `ERROR` com stack trace; falha de dependência externa
  em `WARN` no adaptador; 401 e 429 em `WARN`; demais 4xx em `INFO`, uma linha sem stack trace, para atender o
  suporte por `X-Request-Id`.

### Consequências

- Bom, porque uma requisição é localizada nos logs, nos traces e na reclamação do usuário pelo mesmo ID.
- Bom, porque trocar o backend de traces (X-Ray, Grafana, Datadog) é configuração, não código.
- Bom, porque nenhum dado pessoal ou financeiro chega aos logs por construção, em vez de depender de mascaramento.
- Ruim, porque o JSON ECS é pouco legível sem `jq` no desenvolvimento local.
- Ruim, porque a investigação de um erro não mostra o conteúdo do comando de voz até existir a auditoria (T20).
- Ruim, porque o `traceId` nasce no servidor: se a resposta não chega, o cliente não tem o ID (FUT-15).
- Ruim, porque, até a T05, um 500 é logado pelo Tomcat sem `traceId`.
