---
paths:
  - "src/main/java/**/infrastructure/security/**"
---

# Segurança

Detalhes e uso em `docs/autenticacao.md`. Rate limiting fica para a T19.

## Identidade

- Spring Security como *resource server*: a API só valida tokens, não os emite nem guarda usuários.
- JWT HS256 de desenvolvimento, assinado com `budgeting.security.jwt.secret` (`JWT_SECRET`, mínimo de 32 bytes, a
  aplicação não sobe sem ele). O único bean próprio é o `JwtDecoder`, porque o Boot não tem propriedade para segredo
  HMAC.
- O token carrega só `sub` (UUID do usuário) e `exp`, ambos obrigatórios; sem `iss`, `aud`, papéis nem 403. Qualquer
  JWT bem assinado, dentro da validade e com `sub` UUID identifica um usuário.
- Sem senha nem `PasswordEncoder`: não há cadastro, login, *refresh* nem revogação.
- Negar por padrão: só `/actuator/health` é público; sessão `STATELESS`, sem CSRF, *form login* e HTTP Basic.
- O 401 sai pelo `ApiExceptionHandler` (Problem Details `unauthenticated`, `WWW-Authenticate: Bearer`), um só corpo
  para qualquer token inválido; o `WARN` `Authentication failed` nunca traz o token.
- Em produção, um provedor de identidade emite os tokens e a API os valida pela chave pública do emissor
  (`issuer-uri` / `jwk-set-uri`); domínio e use cases não mudam.
- O usuário autenticado chega ao controller por `@AuthenticationPrincipal Jwt` e é convertido em `UserId`; o domínio
  nunca vê classes do Spring Security.

## Isolamento

- Todo dado pertence a um usuário (`userId` na entidade); leituras e escritas filtram pelo usuário autenticado.
- O usuário é parâmetro obrigatório da porta (`findByIdAndUserId`, `findAllByUserId`, `summarizeByUserIdAndCategory`)
  e primeiro parâmetro do `execute` dos use cases: o compilador impede esquecer o isolamento. Ele não faz parte do
  `Input` nem do `TransactionFilter`.
- Recurso de outro usuário responde 404, com o mesmo corpo do inexistente.
- As tools da IA recebem o usuário pelo `ToolContext`, nunca pelos argumentos que o modelo escolhe.

## Não fazer

- Credenciais, chaves ou tokens em arquivos versionados; `JWT_SECRET` real fora do `.env`.
- Classe de usuário ou de token do Spring Security em `domain` ou `application`.
- Logar o token ou o `Authorization`.
- Rota nova sem autenticação por padrão; `permitAll` além do `/actuator/health`.
- `userId` como argumento da tool escolhido pelo modelo, ou como critério opcional de filtro.
