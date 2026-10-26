---
paths:
  - "src/main/java/**/infrastructure/security/**"
---

# Segurança

<!-- Rascunho. Definido na T08 (identidade) e na T19 (rate limiting). -->

## Identidade

- Endpoints protegidos por token; usuários de demonstração pré-configurados.
- <!-- A definir na T08: tipo de token, emissão, expiração, origem das credenciais de demonstração. -->
- Senhas com `PasswordEncoder` (BCrypt).
- O usuário autenticado chega ao controller por `@AuthenticationPrincipal` e é convertido para um tipo do
  domínio; o domínio nunca vê classes do Spring Security.

## Isolamento

- Todo dado pertence a um usuário; leituras e escritas filtram pelo usuário autenticado.
- Recurso de outro usuário responde 404, como se não existisse.

## Não fazer

- Credenciais, chaves ou tokens em arquivos versionados.
- Classe de usuário do Spring Security em `domain` ou `application`.
