# Cardsync

API de negócio + BFF do CardSync (conciliação, contratos, arquivos, transações, dashboards).

Extraído do antigo monólito "Cardsync Auth" (que fazia ao mesmo tempo o papel de
Authorization Server, BFF e API de negócio). O login, senha, lockout e a gestão de
usuários/grupos/permissões agora vivem no **NimbusAuth** — o Authorization Server
central de todas as aplicações Nimbus. O Cardsync ficou só com:

- BFF (`/bff/**`): sessão, cookies, CSRF, proxy — `oauth2Login` contra o NimbusAuth externo.
- Resource Server (`/api/**`): valida o JWT emitido pelo NimbusAuth (JWKS remoto via
  `cardsync.security.issuer`).
- Todo o domínio de negócio.

Para exibir "criado por"/"alterado por" nas respostas da API (já que o `UserEntity` não
existe mais localmente), o Cardsync mantém um cache local somente-leitura
(`cs_user_directory`, ver `UserDirectoryService`) sincronizado sob demanda com o
NimbusAuth (`GET /internal/users`).

## Requisitos
- Java 21
- Maven 3.9+

## Rodar (DEV)
Requer o NimbusAuth rodando (padrão `http://localhost:9090`) para login/JWKS funcionarem.

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

## Deploy no Railway (produção)

Arquivos adicionados/ajustados para facilitar o deploy:
- `Dockerfile`
- `.dockerignore`
- `.env.railway.example`
- `application.yml` e `application-prod.yml` preparados para variáveis de ambiente

### Variáveis mínimas
- `SPRING_PROFILES_ACTIVE=prod`
- `CARDSYNC_ISSUER` (URL pública do NimbusAuth)
- `CARDSYNC_SPA_BASE_URL`
- `CARDSYNC_ALLOWED_ORIGIN`
- `CARDSYNC_BFF_CLIENT_SECRET`
- `NIMBUS_AUTH_BASE_URL`, `NIMBUS_INTERNAL_API_SECRET` (mesmo valor configurado no NimbusAuth)
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

### Observações
- O projeto não usa mais credenciais SMTP fixas em arquivo.
- Em produção, o backend deve rodar com domínio público HTTPS.
- No Railway, o `PORT` é fornecido automaticamente.
- Se usar MySQL do próprio Railway, copie as credenciais do serviço de banco para as
  variáveis `SPRING_DATASOURCE_*` — em um banco/schema separado do NimbusAuth.
