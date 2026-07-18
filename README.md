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
- `CARDSYNC_BFF_CLIENT_SECRET` (mesmo valor configurado no client `cardsync-bff` do NimbusAuth)
- `CARDSYNC_COOKIE_DOMAIN` (obrigatório, sem default — domínio do próprio CardsyncServer)
- `NIMBUS_AUTH_BASE_URL`, `NIMBUS_INTERNAL_API_SECRET` (mesmo valor configurado no NimbusAuth)
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

### Observações
- O projeto não usa mais credenciais SMTP fixas em arquivo.
- Em produção, o backend deve rodar com domínio público HTTPS.
- No Railway, o `PORT` é fornecido automaticamente — a aplicação já lê `server.port: ${PORT:9091}`.
- Banco é **PostgreSQL** (não MySQL) — se usar o Postgres do próprio Railway, copie as
  credenciais do serviço de banco para as variáveis `SPRING_DATASOURCE_*` (ou referencie
  direto com `${{Postgres.PGHOST}}` etc.) — em um banco/schema separado do NimbusAuth.
- **Domínio customizado no Railway**: ao configurar o Custom Domain nas Networking Settings,
  confirme que o "Target Port" está em **8080** (a porta que o Dockerfile expõe/o Tomcat usa
  de fato) — deixar no padrão errado (ex.: 80) resulta em 502 "Application failed to respond"
  mesmo com o container rodando normalmente.

### Armazenamento dos arquivos importados (EEFI/CNAB/ERP)

O processamento de arquivos (ERP, adquirente Rede, bancos Itaú/Santander/Bradesco) espera
uma árvore de pastas `input/processed/error/duplicate/invalid_file` num disco local — ver
`file-processing.base-path` em `application.yml`. Como o filesystem de um container Railway
é efêmero (some a cada deploy/restart, e não é compartilhado entre réplicas), é necessário
um **Volume** para persistir esses arquivos:

1. No painel do serviço no Railway, vá em **Settings → Volumes → New Volume**.
2. Defina um **Mount Path**, por exemplo `/data/cardsync`.
3. Configure a variável de ambiente `FILE_PROCESSING_BASE_PATH` com o mesmo caminho
   (`/data/cardsync`) — é o que `application.yml` usa como `file-processing.base-path`.
   Sem essa variável, o valor cai no default local (`C:/cardsync/files`), que não existe
   dentro do container Linux.
4. As subpastas (`ERP/input`, `bank/ITAU/input`, `acquirer/REDE/input`, etc., e seus
   irmãos `processed`/`error`/`duplicate`/`invalid_file`) são criadas automaticamente pela
   própria aplicação na inicialização — não precisa criar nada manualmente dentro do Volume.
5. Os arquivos chegam nessas pastas pela tela **Processamento → Upload de Arquivos** no
   CardSyncWeb (`POST /bff/v1/file-processing/upload`), que grava direto na pasta `input`
   do sistema escolhido — o mesmo lugar que hoje recebe arquivos copiados manualmente.
   Depois do upload, o scheduler automático (a cada 30 min, configurável) ou o botão
   "Processar agora" da própria tela processam o arquivo normalmente.

Limitação conhecida: um Volume do Railway é local a uma única instância/região — não é um
problema hoje (o backend roda como uma única réplica), mas se no futuro for necessário
escalar horizontalmente, essa parte precisará migrar para object storage (S3/R2/etc.).
