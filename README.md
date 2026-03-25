# CardSync Security Baseline — Parte 1 (Base do Projeto)

Este ZIP contém a base inicial do projeto Spring Boot (Maven) alinhada ao baseline oficial de segurança do CardSync.

Inclui:
- Estrutura de pacotes (api, bff, web, core, domain, infrastructure)
- `pom.xml` com starters preferenciais
- `application.yml` + perfis `dev` e `prod`
- Flyway (pasta de migrations)
- Thymeleaf templates mínimos (login/sucesso/erro)
- Placeholders de controllers (REST e pages) e configs (a serem completadas nas próximas partes)

## Requisitos
- Java 21
- Maven 3.9+

## Rodar (DEV)
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

> Implementações completas de Security (SAS/BFF/Resource Server, JWT custom claims, session/CSRF, lockout progressivo, etc.) entram nas próximas partes.


## Deploy no Railway (produção)

Arquivos adicionados/ajustados para facilitar o deploy:
- `Dockerfile`
- `.dockerignore`
- `.env.railway.example`
- `application.yml` e `application-prod.yml` preparados para variáveis de ambiente

### Variáveis mínimas
Copie os valores de `.env.railway.example` para o serviço do backend no Railway e ajuste:
- `SPRING_PROFILES_ACTIVE=prod`
- `CARDSYNC_ISSUER`
- `CARDSYNC_PUBLIC_BASE_URL`
- `CARDSYNC_SPA_BASE_URL`
- `CARDSYNC_ALLOWED_ORIGIN_1`
- `CARDSYNC_BFF_CLIENT_SECRET`
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

### Observações
- O projeto não usa mais credenciais SMTP fixas em arquivo.
- Em produção, o backend deve rodar com domínio público HTTPS.
- No Railway, o `PORT` é fornecido automaticamente.
- Se usar MySQL do próprio Railway, copie as credenciais do serviço de banco para as variáveis `SPRING_DATASOURCE_*`.
