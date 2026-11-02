# Autenticação

Toda rota da API, exceto `GET /actuator/health`, exige um token JWT no cabeçalho `Authorization`. Cada usuário vê e
altera só as próprias transações: a transação de outro usuário responde 404, como se não existisse.

## Gerar um token (desenvolvimento)

O script `scripts/generate_token.py` usa só a biblioteca padrão do Python e imprime apenas o token:

```bash
set -a && source .env && set +a          # carrega JWT_SECRET
scripts/generate_token.py                # usuário aleatório, validade de 24h
scripts/generate_token.py --user-id 0b7e2f0a-1c3d-4e5f-8a9b-0c1d2e3f4a5b --expires-in 30m
```

| Argumento / variável | Descrição                                                                              |
|----------------------|----------------------------------------------------------------------------------------|
| `JWT_SECRET`         | segredo HS256, com no mínimo 32 bytes; o mesmo com que a API executa (obrigatório)     |
| `--user-id`          | UUID do usuário (claim `sub`); sem ele, um UUID aleatório novo a cada execução         |
| `--expires-in`       | validade: número seguido de `s`, `m`, `h` ou `d` (padrão `24h`)                        |

Sem `--user-id`, cada token pertence a um usuário diferente. Para ver de novo as transações criadas, reutilize o
mesmo `--user-id`.

## Enviar o token

```bash
TOKEN=$(scripts/generate_token.py)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/transactions
```

## O que a API valida

- assinatura HS256 com o segredo `JWT_SECRET`;
- `exp` presente e no futuro;
- `sub` presente e em UUID (a forma canônica de 36 caracteres): é o identificador do usuário.

O token carrega só `sub` e `exp`. A API não guarda lista de usuários: qualquer token bem assinado, dentro da
validade e com `sub` UUID identifica um usuário.

## Resposta 401

Token ausente, malformado, com assinatura errada, expirado, sem `exp` ou com `sub` que não é UUID recebem a mesma
resposta, `401` com `WWW-Authenticate: Bearer` e Problem Details:

```json
{
  "type": "urn:problem-type:budgeting:unauthenticated",
  "title": "Não autenticado",
  "status": 401,
  "detail": "Envie um token válido no cabeçalho Authorization.",
  "instance": "/api/v1/transactions"
}
```

## Produção

O script e o segredo compartilhado servem só ao desenvolvimento: quem tem o segredo emite token de qualquer usuário.
Em produção, um provedor de identidade (Keycloak ou outro) emite os tokens, e a API os valida pela chave pública do
emissor, configurando `spring.security.oauth2.resourceserver.jwt.issuer-uri` ou `jwk-set-uri` no lugar do segredo.
O domínio e os use cases não mudam: eles já recebem apenas o `UserId`.
