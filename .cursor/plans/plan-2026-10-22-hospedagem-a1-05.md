# A1-05 — Hospedagem dos arquivos

Implementado no ramo `feat/hospedagem-a1-05`. Commit só quando pedir PR.

## Decisões fechadas

- Fora desta máquina, documentos e originais de importação vão para storage S3-compatível. Vale Supabase Storage, Cloudflare R2 ou Amazon S3. O cliente é um só, com endpoint configurável. O plano não escolhe o fornecedor.
- Local continua o caminho quando o storage remoto não está configurado.
- Chave nova de documento: `documentos/{ano}/{mes}/{id}.{ext}`
- Chave nova de importação: `imports-originais/{ano}/{mes}/{id}.{ext}`
- `{ano}` e `{mes}` vêm de America/Sao_Paulo no momento da gravação. `{mes}` tem dois dígitos. `{id}` é o UUID do registro.
- `{ext}` é a extensão real: documento `pdf`, `jpg`, `png` ou `webp`; importação `pdf` ou `docx`. Não usar `.xml`. O original continua PDF ou DOCX, como na seção 19.
- A chave não leva o nome original do arquivo.
- Backup diário às 02:00, com 30 cópias diárias e 12 mensais. Isso fica escrito no contrato. O script que envia e apaga pelo prazo é fatia seguinte. `scripts/backup-local.ps1` permanece: não apaga cópia antiga e não envia nada para fora.

## O que esta fatia entrega

- A spec, o README e o backlog deixam de tratar armazenamento e prazo de backup como abertos. O papel de quem vê e quem edita continua aberto, na fase 8.
- Gravação nova usa a chave com ano e mês.
- Chave antiga, sem barra, continua sendo lida e apagada. Não há migração dos arquivos já gravados.
- Com as variáveis S3 ausentes, a API grava nas pastas `documentos/` e `imports-originais/`.
- Com `ANJO_STORAGE=s3` e endpoint, bucket, região e chaves presentes, a API grava e lê no bucket. As mesmas pastas lógicas viram prefixo da chave.
- Se `ANJO_STORAGE=s3` e faltar variável, a API não sobe. Falha de rede no bucket não cai para a pasta local: o arquivo não pode ficar partido entre os dois lugares.

## Fora

- Onde a API, o Postgres e a tela vão rodar.
- Agendar 02:00, apagar backup com mais de 30 dias, guardar o mensal, ou criar `scripts/backup-s3.ps1`.
- OCR, IA, segundo responsável, convite, OpenAPI, CI, licença.

## Arquivos

- `Especificacao_Tecnica_Calendario_Convivencia_v4.md` — seção "Decisões ainda em aberto"
- `README.md` — "Ainda em aberto" e "Próximo"
- `BACKLOG.md` — item A1-05
- `DocumentService` e `ImportService` — montar a chave nova
- `DocumentFiles` e `ImportFiles` — criar a pasta do mês; continuar recusando chave que sai da raiz
- Porta de storage com duas implementações: pasta local e S3. Os serviços não conhecem o fornecedor
- `application.yml` e `.env.example` — nomes das variáveis, sem segredo real
- Testes de unidade da chave e do cliente de storage com double. Sem bucket real e sem Testcontainers nesta fatia

## Variáveis

Nomes, vazios no exemplo:

- `ANJO_STORAGE` — `local` por omissão
- `S3_ENDPOINT`
- `S3_BUCKET`
- `S3_REGION`
- `S3_ACCESS_KEY`
- `S3_SECRET_KEY`

## Aceite

- Documento e importação novos gravam chave `ano/mes/id.ext` e o teste vê o byte no destino local
- A resposta da API continua sem `storageKey`
- Arquivo já gravado com chave antiga ainda abre
- Sem variável S3, o teste não abre conexão de rede
- `backup-local.ps1` não ganha retenção nem upload

## Verificação

- Testes de unidade no Windows, com `MAVEN_OPTS` do trust store do sistema
- `mvn test` completo continua no Ubuntu, por causa do Testcontainers
- Nenhum segredo no diff

## Próximo, depois desta fatia

Nomear onde a API sobe. Só então o backup das 02:00 vira `scripts/backup-s3.ps1`, com 30 diários e 12 mensais.
