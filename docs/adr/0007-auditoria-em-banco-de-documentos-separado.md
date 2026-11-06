---
status: aceito
data: 2026-11-06
---

# Guardar a auditoria em um MongoDB separado do banco transacional, e não no MySQL ou em um Cassandra

## Contexto e problema

O sistema precisa registrar, para cada fato essencial do fluxo de voz e da decisão sobre a proposta (SEG-04), quem fez,
quando e o que foi dito e proposto: o texto transcrito e a proposta variam por tipo de fato, e os registros de
auditoria só se acumulam. A gravação não pode atrasar nem derrubar a requisição, e o dado de auditoria tem ciclo de
vida diferente do dado transacional (por exemplo, quanto tempo guardar). Em que banco guardar a auditoria?

## Opções consideradas

- MySQL, no banco transacional: nenhuma tecnologia nova, e o mesmo driver, o mesmo backup e o mesmo monitoramento. Mas
  cada tipo de fato tem campos diferentes, o que leva a uma tabela larga com colunas quase sempre nulas ou a uma
  tabela por tipo, e a evolução do esquema exige migração. Os registros de auditoria, que só se acumulam, disputam
  espaço, conexões e backup com o dado transacional, e a retenção não pode ser aplicada sem tocar nele.
- MongoDB: documentos com esquema flexível por tipo de fato, só de acréscimo, sem migração quando um fato ganha um
  campo. Spring Data, `spring-boot-docker-compose`, Testcontainers e health indicator vêm prontos. A retenção pode ser
  aplicada depois, no banco de auditoria, sem tocar no MySQL. É uma tecnologia a mais para operar.
- Cassandra: escrita em grande volume e distribuída. Mas a aplicação não tem esse volume nem lê os registros por
  consultas que justifiquem o modelo orientado a consulta, e o custo de operar um cluster é muito maior que o ganho.

## Decisão

Opção escolhida: "MongoDB", porque guarda cada tipo de fato como um documento com os campos que ele tem, sem migração,
e tira os registros, que só se acumulam, do banco transacional. Os quatro fatos (proposta criada, confirmada,
cancelada e comando não entendido) viram um registro de auditoria cada, gravado em `audit_entries` de forma
assíncrona, depois do commit.

### Consequências

- Bom, porque cada tipo de fato guarda só os campos que tem, e um fato novo ou um campo novo não exige migração.
- Bom, porque os registros de auditoria não disputam espaço, conexões nem backup com o dado transacional.
- Bom, porque a retenção pode ser aplicada depois no banco de auditoria, sem tocar no MySQL.
- Ruim, porque é uma tecnologia a mais para operar: banco, credenciais, backup e monitoramento.
- Ruim, porque não há transação comum entre o MySQL e o MongoDB: a decisão pode ser confirmada no MySQL e a gravação
  da auditoria falhar, ou a aplicação cair entre o commit e a gravação. A auditoria é de melhor esforço até que a T21
  a reforce.
- Ruim, porque o esquema é implícito: cada tipo de fato tem os seus campos, e quem lê a coleção precisa conhecê-los.
