# Escopo — Assistente de Orçamento por Voz com Padrão de Produção

> Documento de escopo do desafio final do bootcamp "Itaú - Java com Inteligência Artificial" (DIO).
>
> Base: código fornecido pelo desafio, no primeiro commit deste repositório (API de orçamento que processa comandos de voz para registrar e consultar transações financeiras).

## Objetivo

Elevar a funcionalidade **existente** — registrar e consultar gastos por comando de voz — a um padrão de produção para uma aplicação financeira: **segura, confiável, resiliente, observável e com custo controlado**. **Nenhuma funcionalidade nova** é criada; todo item deste escopo existe para fortalecer o fluxo atual.

## Princípios

1. **Profundidade antes de amplitude** — uma funcionalidade com qualidade de produção vale mais que várias frágeis.
2. **A IA propõe, o cliente decide** — nenhuma escrita acontece sem confirmação explícita.
3. **Falhar com elegância** — a falha de uma dependência não derruba a experiência do cliente.
4. **Medir em vez de confiar** — consumo de IA, latência e resiliência são comprovados com evidências.
5. **Decisões guiadas pelo negócio** — cada escolha técnica é justificada por uma dor real, com fontes públicas.

## Contexto de negócio

Este projeto nasce de um bootcamp patrocinado pelo Itaú Unibanco, e o escopo foi definido a partir de informações públicas do banco — para que as decisões técnicas respondam a necessidades reais do negócio, e não apenas implementem tecnologias (fontes ao final):

- **O ia.i valida a intenção antes de executar**: em julho de 2026, o Itaú lançou o ia.i, assistente conversacional que entende voz, analisa gastos e realiza Pix por comando de voz — e toda transação só é concluída após confirmação explícita do cliente. Este projeto adota o mesmo princípio: a IA propõe, o cliente confirma ou cancela (FN-03).
- **Fraude com IA é uma preocupação declarada**: o Itaú alerta seus clientes sobre golpes com deepfakes e clonagem de voz. Para um sistema que aceita comandos por áudio, isso reforça a necessidade de identidade, isolamento de dados, auditoria e confirmação explícita (SEG-01, SEG-02, SEG-04).
- **Valores que viram requisitos**: "Só é bom pra gente se for bom para o cliente", "Ética é inegociável" e a centralidade no cliente, da cultura do banco, orientam decisões como nunca gravar sem confirmação, respostas claras e proteção de dados pessoais nos logs (FN-08, SEG-06).
- **Estratégia "AI First" com IA responsável**: o banco trata a IA generativa como um novo paradigma de atendimento, ampliou em 84% as iniciativas de IA generativa em 2025 e investe em IA responsável. Isso motiva medir o consumo da IA, conter seu custo e protegê-la contra uso indevido (CUS-01, SEG-03, FN-07).
- **O cliente quer um banco consultivo e confiável**: segundo a pesquisa "Consciência e Prosperidade", do Itaú, 67% dos brasileiros esperam que seus bancos sejam mais consultivos e 53% desejam apoio para tomar decisões mais inteligentes. Um assistente só é consultivo se for confiável: valores corretos, respostas claras e disponibilidade mesmo diante de falhas (FN-01, API-02, RES-05).

## Legenda

**Prioridade** — o quanto o item é necessário para cumprir o objetivo:

| Prioridade | Significado |
|---|---|
| **Crítico** | Sem ele, a funcionalidade não é correta/segura ou o desafio não é cumprido. |
| **Alto** | Esperado de uma aplicação em produção; sua ausência seria apontada em uma revisão técnica. |
| **Médio** | Melhoria significativa; a ausência é tolerável no contexto do projeto. |
| **Baixo** | Refinamento; agrega pouco no contexto atual. |

**Esforço relativo** — estimativa de especificação + implementação (por agentes) + revisão + validação:

| Esforço | Significado |
|---|---|
| **P** | Pequeno — mudança localizada, validação simples. |
| **M** | Médio — envolve várias camadas ou exige testes específicos. |
| **G** | Grande — altera o fluxo principal ou introduz nova infraestrutura. |

## 1. Fluxo e regras de negócio

| ID | Item | O que é | Benefícios | Prioridade | Esforço |
|---|---|---|---|---|---|
| FN-01 | Correção do valor monetário | Corrigir a conversão entre centavos e reais: hoje o valor armazenado em centavos é exibido como se fosse reais. | Valores corretos para o cliente; elimina um erro grave em aplicação financeira. | **Crítico** | P |
| FN-02 | Data e hora da transação | Registrar quando cada transação ocorreu (hoje não há nenhuma informação temporal). | Registro financeiro completo; base para auditoria, ordenação e filtros por período. | Alto | P |
| FN-03 | Confirmação em duas etapas | A IA não grava diretamente: ela gera uma **proposta** que o cliente apenas **confirma** ou **cancela** (sem correção — em caso de erro, o cliente envia um novo comando). Consultas continuam em etapa única. | Erros de transcrição/interpretação não chegam ao banco de dados; cliente no controle; alinhado ao padrão adotado pelo setor. | **Crítico** | G |
| FN-04 | Expiração de propostas | Propostas não confirmadas expiram após um tempo definido. | Evita confirmação de propostas antigas fora de contexto; mantém o estado limpo. | Alto | P |
| FN-05 | Validação do áudio recebido | Rejeitar arquivos fora do formato, tamanho ou duração aceitos, e áudios vazios/inaudíveis. | Falha rápida com mensagem clara; evita custo de IA com entradas inválidas; protege contra abuso. | Alto | P |
| FN-06 | Validação de regras de negócio | Validar os dados antes de propor/gravar (valor positivo, teto razoável, descrição presente, categoria válida). | Dados íntegros independentemente do que a IA extrair; defesa contra alucinações do modelo. | **Crítico** | P |
| FN-07 | Proteções da IA (guardrails) | Restringir o assistente ao escopo financeiro e resistir a *prompt injection* falada (ex.: "ignore as instruções e..."). | Comportamento previsível; reduz risco de uso indevido e de ações não pretendidas. | Alto | M |
| FN-08 | Respostas mais claras | Respostas padronizadas (frases fixas e formato único de valores em R$), em linguagem simples, repetindo o que foi entendido e sem dados que identifiquem o usuário. | Cliente entende exatamente o que será feito antes de confirmar; melhor experiência. | Médio | P |
| FN-09 | Orquestração de IA fora do controller | Mover o fluxo transcrição → interpretação → resposta → voz do controller para a camada de aplicação. | Responsabilidades corretas; testabilidade; habilita resiliência, auditoria e custo de forma centralizada (pré-requisito para vários itens). | Alto | M |

## 2. API

> Os endpoints manuais existentes (cadastro e consulta) recebem o mesmo tratamento do fluxo por voz: validação, autenticação, idempotência e observabilidade. A quebra do contrato atual é aceita.

| ID | Item | O que é | Benefícios | Prioridade | Esforço |
|---|---|---|---|---|---|
| API-01 | Adequação às boas práticas REST | Recursos modelados corretamente (comando de voz e proposta como recursos próprios), filtros como parâmetros de consulta, status HTTP corretos, cabeçalho `Location` em criações, tipo de mídia de áudio correto. | API previsível e alinhada a convenções de mercado; mais fácil de consumir e evoluir. | Alto | M |
| API-02 | Valores monetários sem ponto flutuante | Representar dinheiro de forma exata e com unidade consistente entre entrada e saída. | Elimina erros de arredondamento — requisito básico de aplicações financeiras. | **Crítico** | P |
| API-03 | Paginação e ordenação | Limitar e ordenar resultados de consultas. | Desempenho previsível conforme o volume cresce; respostas menores. | Médio | P |
| API-04 | Contrato de erro padronizado | Todos os erros (incluindo falhas da IA) no formato *Problem Details* (RFC 9457). | Clientes tratam erros de forma uniforme; mensagens úteis sem expor detalhes internos. | Alto | P |
| API-05 | Versionamento da API | Identificar a versão do contrato. | Permite evoluir a API sem quebrar clientes existentes. | Médio | P |

## 3. Resiliência

| ID | Item | O que é | Benefícios | Prioridade | Esforço |
|---|---|---|---|---|---|
| RES-01 | Timeouts | Tempo máximo para toda chamada externa (transcrição, chat, voz, bancos de dados). | Nenhuma requisição fica presa indefinidamente; recursos liberados; latência previsível. | **Crítico** | P |
| RES-02 | Retries com backoff exponencial e jitter | Repetir chamadas que falharam por erros **transitórios**, com intervalos crescentes e aleatorizados. | Recupera falhas momentâneas sem intervenção; o jitter evita picos sincronizados sobre a dependência. | Alto | P |
| RES-03 | Circuit breaker | Interromper temporariamente chamadas a uma dependência de IA que está falhando, por dependência. | Falha rápida em vez de esperar timeouts; dá tempo para a dependência se recuperar; evita falhas em cascata. | Alto | M |
| RES-04 | Bulkhead | Limitar chamadas simultâneas a cada dependência de IA. | Lentidão do provedor de IA não esgota as threads da aplicação; endpoints manuais continuam responsivos. | Médio | P |
| RES-05 | Degradação controlada | Quando uma etapa falha, entregar o melhor resultado possível: resposta em texto se a geração de voz falhar; mensagem clara se a IA estiver indisponível; mensagens comuns (erros, indisponibilidade) geradas uma única vez e servidas como áudio estático; endpoints manuais continuam funcionando. | O cliente nunca fica sem resposta; a falha de um componente não derruba toda a experiência. | Alto | M |
| RES-06 | Idempotência no envio de áudio | Reenvio do mesmo comando (ex.: retry do cliente após falha de rede) não gera proposta duplicada nem nova cobrança de IA. | Elimina duplicidades; reduz custo; permite retry seguro pelo cliente. | Alto | M |
| RES-07 | Idempotência na confirmação | Confirmar a mesma proposta mais de uma vez não cria transações duplicadas. | Garante que um gasto é registrado exatamente uma vez — requisito básico em finanças. | **Crítico** | P |
| RES-08 | Estado persistido | Propostas e seus estados ficam armazenados, não apenas em memória. | Reinícios e falhas da aplicação não perdem propostas pendentes. | Alto | P |
| RES-09 | Health checks | Indicadores de vida (*liveness*) e prontidão (*readiness*) incluindo as dependências. | Permite que orquestradores e monitoramento detectem e reajam a falhas automaticamente. | Alto | P |
| RES-10 | Encerramento gracioso | Concluir requisições em andamento antes de desligar. | Reinícios e atualizações não interrompem operações no meio. | Médio | P |

## 4. Segurança e rastreabilidade

| ID | Item | O que é | Benefícios | Prioridade | Esforço |
|---|---|---|---|---|---|
| SEG-01 | Identidade mínima | Endpoints protegidos por token, com usuários de demonstração pré-configurados (sem cadastro/login próprios — em produção, a identidade viria de um provedor corporativo). | Toda operação tem um autor identificado; fecha a brecha de qualquer pessoa confirmar propostas alheias. | **Crítico** | M |
| SEG-02 | Isolamento de dados por usuário | Cada usuário vê, propõe e confirma apenas os próprios dados. | Privacidade e integridade dos dados de cada cliente. | **Crítico** | P |
| SEG-03 | Rate limiting por usuário | Limite de requisições por usuário, principalmente no endpoint de voz. | Protege contra abuso e ataques de exaustão; contém custo de IA; distribui capacidade de forma justa. | Alto | M |
| SEG-04 | Auditoria em banco NoSQL | Registro de eventos em um banco NoSQL separado do transacional: quem, quando, canal/dispositivo, metadados do áudio, transcrição, proposta gerada e decisão (confirmada, cancelada, expirada). | Rastreabilidade completa das decisões da IA e do cliente; esquema flexível para metadados variáveis; modelo *append-only*; retenção independente; não sobrecarrega o banco transacional. | Alto | G |
| SEG-05 | Garantia de entrega da auditoria | Nenhum evento de auditoria pode ser perdido, e uma falha do banco de auditoria não pode interromper o fluxo do cliente. | Auditoria confiável sem acoplar a disponibilidade do fluxo principal à do banco de auditoria. | Alto | M |
| SEG-06 | Dados sensíveis fora dos logs | Impedir que logs registrem conteúdo sensível (prompts completos, transcrições, tokens, dados pessoais). | Conformidade com a LGPD; reduz o impacto de vazamento de logs. | Alto | P |
| SEG-07 | Análise de vulnerabilidades das dependências | Verificação automática de bibliotecas com vulnerabilidades conhecidas. | Detecta riscos de segurança antes de chegarem à produção. | Médio | P |
| SEG-08 | Gestão de segredos | Nenhuma credencial fixa no código ou na configuração versionada; segredos fornecidos pelo ambiente. | Evita vazamento de credenciais pelo repositório; prática obrigatória em produção. | Médio | P |

## 5. Custo e desempenho

| ID | Item | O que é | Benefícios | Prioridade | Esforço |
|---|---|---|---|---|---|
| CUS-01 | Medição de consumo por usuário | Registrar o consumo de IA por requisição e por usuário (tokens, segundos de áudio transcritos, caracteres sintetizados). | Dados de consumo por usuário, que o provedor de IA não possui, disponíveis para análises futuras. | Alto | M |
| CUS-02 | Cache de consultas | Nos endpoints convencionais (sem IA), resultados de consultas reaproveitados e invalidados quando uma transação é registrada (cadastro manual ou confirmação de proposta). | Menos carga no banco e respostas mais rápidas em consultas repetidas. | Baixo | P |
| CUS-03 | Cache da síntese de voz | Reaproveitar o áudio já gerado para um mesmo texto de resposta, usando como chave o texto completo e padronizado, com TTL. O áudio enviado pelo usuário nunca é usado como chave. | Menos custo e latência de TTS em respostas repetidas, sem risco de entregar a um usuário um conteúdo diferente do que ele deveria ouvir. | Médio | M |

## 6. Observabilidade

| ID | Item | O que é | Benefícios | Prioridade | Esforço |
|---|---|---|---|---|---|
| OBS-01 | Logs estruturados com ID de correlação | Logs em formato estruturado, com um identificador que acompanha cada requisição de ponta a ponta. | Diagnóstico rápido de problemas; logs pesquisáveis por ferramentas de análise. | Alto | P |
| OBS-02 | Métricas técnicas | Latência por etapa (transcrição, interpretação, voz), taxa de erros, estado dos circuit breakers, uso de recursos. | Identifica gargalos e degradações antes que o cliente perceba. | Alto | M |
| OBS-03 | Métricas de negócio | Taxas de propostas confirmadas, canceladas e expiradas. | Mede a qualidade percebida da IA: muitos cancelamentos indicam que a IA está entendendo errado. | Médio | P |

## 7. Qualidade

| ID | Item | O que é | Benefícios | Prioridade | Esforço |
|---|---|---|---|---|---|
| QA-01 | Testes unitários | Testes do domínio, das validações e dos casos de uso. | Feedback rápido; protege regras de negócio contra regressões. | **Crítico** | M |
| QA-02 | Testes de integração sem OpenAI | Testes do fluxo completo com a IA simulada e banco de dados real em container. | Testes determinísticos, rápidos e gratuitos; rodam no CI sem credenciais. | **Crítico** | M |
| QA-03 | Testes de falha | Simular timeout, erros e lentidão das dependências e comprovar retry, circuit breaker e degradação. | Prova que a resiliência funciona de fato, em vez de apenas estar configurada. | Alto | M |
| QA-05 | Integração contínua (CI) | Build, testes e análises automáticos a cada alteração. | Qualidade verificada continuamente; evidência pública de que o projeto funciona. | Alto | P |

## 8. Documentação e apresentação

> Toda documentação é criada junto com o item que a justifica e atualizada pelos itens que alteram o que ela descreve.

| ID | Item | O que é | Benefícios | Prioridade | Esforço |
|---|---|---|---|---|---|
| DOC-01 | README completo | O que o projeto faz, como executar, melhorias implementadas, tecnologias, como testar o fluxo principal e aprendizados (requisitos do desafio). | Cumpre o desafio; permite que qualquer avaliador rode e entenda o projeto. | **Crítico** | M |
| DOC-02 | Contexto de negócio | Seção que conecta as decisões técnicas às necessidades e aos valores declarados publicamente pelo Itaú, citando as fontes (sem identidade visual e sem sugerir vínculo oficial). | Demonstra visão de negócio, não apenas técnica. | Alto | P |
| DOC-03 | ADRs | Registros das decisões arquiteturais: contexto, alternativas consideradas, decisão e consequências. | Mostra o raciocínio por trás das escolhas e os trade-offs avaliados. | Alto | M |
| DOC-04 | Diagramas | Arquitetura, sequência do fluxo de voz e estados da proposta. | Compreensão rápida do sistema por quem não leu o código. | Alto | P |
| DOC-05 | Documentação OpenAPI | Especificação navegável dos endpoints. | Contrato da API explorável e testável sem ler o código. | Médio | P |
| DOC-06 | Exemplos de requisição e áudio | Coleção de requisições prontas e áudios de exemplo. | Avaliadores testam o fluxo em minutos. | Médio | P |
| DOC-07 | Processo de desenvolvimento com agentes | Plano de cada entrega, com critérios de aceite e verificação, instruções para os agentes, revisão e validação explícitas e histórico de commits que conta a evolução. | Demonstra desenvolvimento assistido por IA com disciplina e responsabilidade humana sobre as decisões. | Alto | M |
| DOC-08 | Melhorias futuras documentadas | Lista do que ficou fora do escopo e por quê. | Mostra consciência dos limites da solução e visão de evolução. | Médio | P |

---

## Fora do escopo

### Funcionalidades novas (excluídas por princípio)

Novos tipos de consulta, recursos consultivos/analíticos, novas categorias, correção de propostas (o cliente cancela e reenvia o comando) e qualquer outra capacidade que não exista no projeto base.

### Melhorias futuras

| ID | Item | O que é | Por que fica para depois |
|---|---|---|---|
| FUT-01 | Fallback multi-provedor de IA | Usar outro provedor/modelo quando o principal estiver indisponível. | A degradação controlada já cobre a indisponibilidade; o multi-provedor adiciona configuração, testes e uma nova fonte de variação na acurácia. |
| FUT-02 | Tools via servidor MCP | Expor os casos de uso como ferramentas em um servidor MCP, desacoplando a IA da aplicação. | Mudança arquitetural que não aumenta a confiabilidade do fluxo atual. |
| FUT-03 | Microsserviço de áudio | Transcrição e síntese de voz em um serviço isolado. | Mudança arquitetural; adiciona uma dependência de rede sem ganho para o fluxo atual. |
| FUT-04 | Antifraude externo | Consultar um serviço antifraude antes de confirmar transações. | Integração nova; mais relevante para transferências do que para registro de gastos. |
| FUT-05 | Verificação de voz contra deepfake | Detectar áudio sintético ou voz clonada. | Exige tecnologia especializada, fora do alcance do projeto; o risco é mitigado pela autenticação e pela confirmação explícita. |
| FUT-06 | Cadastro e login reais | Gestão completa de identidade. | Em produção, a identidade viria de um provedor corporativo, não desta API. |
| FUT-07 | Preparação para deploy na nuvem | Ajustar a aplicação para execução em ambiente de nuvem. | O foco do projeto é o valor da funcionalidade, não a infraestrutura de implantação. |
| FUT-08 | Migrations versionadas | Evolução do esquema do banco por scripts versionados, substituindo a geração automática de tabelas. | A prioridade agora é evoluir a funcionalidade; para desenvolver e testar, a geração automática de tabelas é suficiente. O controle rigoroso do esquema passa a ser necessário quando houver dados reais a preservar. |
| FUT-09 | Cota de consumo por usuário | Limite de consumo de IA por usuário, recusando requisições ao atingi-lo. | O rate limiting já contém abusos e o consumo; os dados da medição por usuário permitem calibrar uma cota no futuro. |
| FUT-10 | Alertas e limites de gasto no provedor | Alertas de consumo e teto de orçamento configurados no provedor de IA ou no serviço de nuvem. | São configurações do ambiente de produção, não da aplicação. |
| FUT-11 | Teste de carga | Teste de carga sobre os endpoints, medindo o desempenho e comprovando rate limiting e bulkhead sob carga. | O foco atual é evoluir a funcionalidade; o comportamento sob falha já é comprovado pelos testes de falha. |
| FUT-12 | Demonstração em vídeo/GIF | Gravação do fluxo funcionando de ponta a ponta. | O README e os exemplos de requisição e áudio permitem executar e avaliar o fluxo. |
| FUT-13 | Dashboard local | Painel que exibe métricas técnicas, de negócio e de consumo de IA. | O foco atual é evoluir a funcionalidade; as métricas já ficam expostas e podem ser consultadas sem um painel. |
| FUT-14 | Avaliação da acurácia da IA | Conjunto de comandos de voz/texto com resultados esperados, medindo a acurácia da extração (valor, descrição, categoria, intenção). | O foco atual é evoluir a funcionalidade; a confirmação em duas etapas impede que erros de interpretação cheguem ao banco, e a taxa de cancelamentos (OBS-03) já indica a qualidade percebida da IA. |

---

## Critério de pronto

- O fluxo por voz funciona de ponta a ponta com áudio real.
- Cada atributo de qualidade possui **evidência**: teste automatizado, métrica ou demonstração.
- O CI está verde.
- Um avaliador consegue executar o projeto seguindo apenas o README.

## Prazo

5 a 10 dias úteis, com 4 horas por dia — um **norte** para manter o foco, não um limite rígido.

## Fontes

- [Itaú Unibanco lança ia.i, nova experiência conversacional com IA generativa (27/07/2026)](https://www.itau.com.br/media/dam/m/6faa1448dd30b9a0/original/27-07-2026-Itau-Unibanco-lanc-a-ia-i-nova-experie-ncia-conversacional-que-levara-a-IA-generativa-a-todos-os-produtos-e-servic-os-do-banco.pdf)
- [Let's Money — ia.i, o assistente de IA do Itaú, e a confirmação explícita de Pix](https://www.letsmoney.com.br/noticias/itau-ia-i-assistente-app/)
- [Cartões e Viagens — Itaú coloca IA no app que entende voz, analisa gastos e faz Pix](https://cartoeseviagens.com.br/artigos/itau-ia-app-voz-analisa-gastos-faz-pix)
- [Itaú avança 35% em velocidade de implantações tecnológicas e amplia uso de IA generativa (09/02/2026)](https://www.itau.com.br/media/dam/m/11c5a26e8fc3cd99/original/09-02-2026-itau-avanca-35-por-cento-em-velocidade-de-implantacoes-tecnologicas-em-2025-e-amplia-uso-de-ia-generativa-na-experiencia-dos-clientes.pdf)
- [Itaú — Cultura Itubers e nosso novo conjunto de valores](https://www.itau.com.br/relacoes-com-investidores/noticias/cultura-itubers-e-nosso-novo-conjunto-de-valores/)
- [Forbes — CEO do Itaú prioriza cultura centrada no cliente](https://forbes.com.br/forbes-money/2023/12/ceo-do-itau-prioriza-cultura-centrada-no-cliente-em-cenario-ultracompetitivo/)
- [Itaú alerta para golpes com deepfakes e reforça orientações de segurança digital](https://www.itau.com.br/relacoes-com-investidores/noticias/itau-unibanco-alerta-para-golpes-com-deepfakes-e-reforca-orientacoes-de-seguranca-digital/)
- [Itaú lança Feito.Itaú e pesquisa "Consciência e Prosperidade" (09/02/2026)](https://www.itau.com.br/media/dam/m/1c23db1493e188a/original/09-02-2026-itau-unibanco-lanca-feito-itau-hub-de-conteudo-proprietario-que-amplia-o-acesso-ao-conhecimento-financeiro-e-reforca-o-papel-do-banco-como-parceiro-estrategico-na-vida-das-pessoas-e-empresas.pdf)
- [Seu Crédito Digital — Itaú lança programa de alívio financeiro](https://seucreditodigital.com.br/itau-lanca-programa-alivio-financeiro/)
