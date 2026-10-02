# Calendário de Convivência Familiar

Aplicação web de uso pessoal para um responsável organizar a convivência de uma criança: calendário, planejado e realizado, uma observação por dia, histórico e, nas fases seguintes, documentos e relatório.

Fonte de verdade do produto: `Especificacao_Tecnica_Calendario_Convivencia_v4.md` (versão 4.1). Backlog: `BACKLOG.md`.

## Stack

Java 21 · Spring Boot · PostgreSQL · Flyway · JWT · JUnit · Testcontainers

## Já implementado (API)

- Auth: login, refresh, logout; reset de senha só por script local
- Família: cadastrar criança e responsáveis (`guardian_id`)
- Plano de convivência e regra recorrente (`SCH-001`)
- Calendário mensal calculado em `America/Sao_Paulo` (`CAL-001` / `CAL-002`)
- Exceção pontual por criança/data com overlay no mês e auditoria write-only (`CAL-003`)

Prefixo: `/api/v1`

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

- Onde guardar documentos em produção: decidir antes da fase de documentos. No uso local, pasta da própria máquina.
- Backup e prazo de retenção: decidir antes de hospedar fora da máquina.
- Papéis de quem vê e quem edita: só quando existir acesso compartilhado.

## Próximo

Fase 3 — realização e diário (`DAY-002`, `DAY-001`, `CAL-004`).
