# Calendário de Convivência Familiar

Aplicação web de uso pessoal para um responsável organizar a convivência de uma criança: calendário calculado na leitura, planejado e realizado, uma observação por dia, histórico, documentos e relatório.

Fonte de verdade do produto: `Especificacao_Tecnica_Calendario_Convivencia_v4.md` (versão 4.1). Backlog: `BACKLOG.md`.

## Stack

Java 21 · Spring Boot · PostgreSQL · Flyway · JWT · JUnit · Testcontainers
Tela: React · TypeScript · Vite · Vitest

## Já implementado (API)

Fases 0 a 6, prefixo `/api/v1`: auth, família, plano, calendário mensal, exceção, confirmação, observação, busca, timeline, auditoria, relatório JSON e PDF, documentos e importação com revisão.

## Tela

`frontend/`: login, painel, grade do calendário, detalhe do dia, plano, linha do tempo, documentos, importação, relatórios, auditoria e configurações. O plano ativo é só leitura. Sem anexo no dia, sem exceção no plano, sem documento na linha do tempo e sem apagar documento. A importação só grava depois da revisão. O PDF do relatório sai da versão aberta. A auditoria lista o usuário autenticado, sem metadata. Configurações não edita perfil, não troca senha e não convida. O Vite encaminha `/api` para `localhost:8086`.

## Decisões fechadas

- Uso pessoal de um responsável. Acesso do segundo responsável é fase futura.
- Calendário calculado na leitura (regra + exceções). O dia só é gravado quando há exceção, confirmação ou observação.
- Um responsável por dia, sempre por `guardian_id`.
- Uma observação ativa por criança por dia.
- Arquitetura em camadas; API em `/api/v1`, sem expor entidade do banco.
- Refresh token persistido só como hash, com expiração e revogação.
- Reset de senha por script local. Sem e-mail e sem endpoint público.
- Importação aprovada aponta a entidade criada ou mantida.
- Relatório mensal: nova versão a cada geração; snapshot em JSON; PDF derivado dessa versão.
- Pronto da primeira versão: critérios da seção 25 da spec, na ordem das fases 0 a 6. Cada fase só começa com aprovação.

## Ainda em aberto

- Papéis de quem vê e quem edita: só quando existir acesso compartilhado.
- Onde a API, o Postgres e a tela sobem fora desta máquina.

## Como rodar

Pré-requisitos: Java 21, Maven, Docker e Node.js. O segredo do JWT não vai no Git.

### API

```powershell
docker compose up -d
$env:JWT_SECRET = "somente-local-troque-este-segredo-jwt"
mvn spring-boot:run
```

O Postgres local escuta só em `127.0.0.1:5436`, banco `anjo_guarda`, usuário e senha `anjo`. Esses valores são só desta máquina. Os dados ficam no volume `anjo-postgres-data`; `docker compose down -v` apaga esse volume. A API sobe em http://localhost:8086. Saúde: http://localhost:8086/actuator/health.

`JWT_SECRET` precisa ter pelo menos 32 caracteres. O modelo está em `.env.example`.

Reset de senha, sem endpoint público:

```powershell
.\scripts\reset-senha.ps1 -Email pessoa@example.com
```

O script pede a senha, ou usa `ANJO_NEW_PASSWORD` se essa variável já existir. A senha não entra no comando do Maven.

### Backup local

```powershell
.\scripts\backup-local.ps1
```

Cria `backups/aaaaMMdd-HHmmss` com o dump do banco e, se existirem, as pastas `documentos` e `imports-originais`. A pasta fica fora do Git. O script não apaga cópias antigas e não envia nada para fora. O prazo fechado é diário às 02:00, com 30 diários e 12 mensais; este script ainda não aplica esse prazo. Para recolocar essa cópia no Postgres do compose, com ele no ar:

```powershell
docker compose cp .\backups\<pasta>\anjo_guarda.sql postgres:/tmp/anjo_guarda.sql
docker compose exec -T postgres psql -U anjo -d anjo_guarda -f /tmp/anjo_guarda.sql
docker compose exec -T postgres rm -f /tmp/anjo_guarda.sql
```

O dump apaga e recria os objetos do banco. Não há restauração automática.

### Backup remoto

```powershell
.\scripts\backup-s3.ps1
```

Envia o dump e, se existirem, `documentos` e `imports-originais` para `backups/daily/aaaaMMdd-HHmmss` no bucket. A primeira cópia de cada mês também fica em `backups/monthly/aaaaMM`. O script guarda 30 diárias e 12 mensais, e só apaga pastas com esse nome, dentro desses prefixos. A hora é America/Sao_Paulo. Ele não cria a tarefa das 02:00. Para agendar nesta máquina:

```powershell
schtasks /Create /TN "Anjo-Guarda backup" /SC DAILY /ST 02:00 /TR "powershell -NoProfile -File \"C:\caminho\scripts\backup-s3.ps1\""
```

As variáveis `S3_ENDPOINT`, `S3_BUCKET`, `S3_REGION`, `S3_ACCESS_KEY` e `S3_SECRET_KEY` vêm do ambiente ou de um `.env` local, que fica fora do Git. O endpoint precisa ser `https`. Também precisa do Docker e da AWS CLI. Se o envio falhar, a pasta temporária fica na máquina e o script mostra o caminho.

### Testes da API

`mvn test` sobe um PostgreSQL pelo Testcontainers. O Docker precisa estar acessível ao processo do Maven.

### Tela

```powershell
cd frontend
npm install
npm test
npm run dev
```

http://localhost:5173 encaminha `/api` para `localhost:8086`.

## Próximo

Nomear onde a API sobe.
