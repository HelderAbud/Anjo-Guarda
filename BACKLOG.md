# Backlog inicial

Fonte: spec 4.1, seções 25, 29 e 35. Cada fase só começa com aprovação. Fases 7 e 8 não entram na primeira versão.

## Fase 0 — Planejamento

Feito no alinhamento: modelo, arquitetura, lacunas 4.1, README e este backlog.

Ainda sem protótipo de tela. Só entra se você pedir.

## Fase 1 — Fundação

| ID | História | Aceite | Status |
|---|---|---|---|
| AUTH-001 | Entrar com e-mail e senha | Login emite access token curto e grava o refresh só como hash | Feito |
| AUTH-002 | Sair e renovar a sessão | Logout revoga o refresh; reuso de token já rotacionado revoga a cadeia | Feito |
| AUTH-003 | Recuperar o acesso sem e-mail | Script local redefine o hash da senha; não existe endpoint público de reset | Feito |
| FAM-001 | Cadastrar a criança e os responsáveis | Responsável guarda `guardian_id`; `user_id` do responsável fica vazio neste uso pessoal | Feito |

## Fase 2 — Calendário

| ID | História | Aceite | Status |
|---|---|---|---|
| SCH-001 | Configurar a regra recorrente | `configuration_json` cita só `guardian_id`; o mês não é gravado como tabela | Feito |
| CAL-001 | Ver o mês inteiro | Grade de segunda a domingo, com botão Hoje e mês anterior/próximo | Feito (API mês) |
| CAL-002 | Saber com quem a criança fica em cada data | O dia mostra o responsável calculado pela regra | Feito |
| CAL-003 | Registrar uma exceção | Uma exceção por criança e data; troca o dia sem apagar a regra; auditoria | Feito |

## Fase 3 — Realização e diário

| ID | História | Aceite | Status |
|---|---|---|---|
| DAY-002 | Confirmar o que aconteceu | Status realizado, alterado ou não realizado; sem confirmação o dia continua planejado; uma confirmação por criança e data | Feito |
| DAY-001 | Escrever a observação do dia | Uma observação ativa por criança e data; apagar é lógico | Feito |
| CAL-004 | Pesquisar observações | Filtro por criança, período, texto e categoria | Feito |

## Fase 4 — Linha do tempo e relatórios

| ID | História | Aceite |
|---|---|---|
| HIS-001 | Ver a linha do tempo de um período | Lista cronológica de exceções, confirmações, observações e auditoria |
| AUD-001 | Consultar alterações críticas | Login, exceção, confirmação, observação, documento, importação aprovada e relatório ficam auditados |
| REP-001 | Gerar o resumo mensal | Nova geração cria nova versão; snapshot em JSON; a versão anterior não é sobrescrita |
| REP-002 | Exportar o mês em PDF | O PDF sai do JSON da versão escolhida |

## Fase 5 — Documentos

| ID | História | Aceite |
|---|---|---|
| DOC-001 | Guardar documentos | Upload com categoria e metadados; o nome original não vira caminho no disco |

Storage de produção continua em aberto. Até lá, pasta local.

## Fase 6 — Importação

| ID | História | Aceite |
|---|---|---|
| IMP-001 | Importar PDF ou DOCX | O original é guardado; nada é gravado no calendário sem confirmação |
| IMP-002 | Resolver conflitos antes de gravar | Cada item é aprovado ou rejeitado; aprovado aponta a entidade criada ou mantida |

## Fora da primeira versão

| ID | História | Fase |
|---|---|---|
| AI-001 | Resumo mensal assistido por IA | 7 |
| SHR-001 | Segundo responsável entra no mesmo calendário | 8, usando `guardians.user_id` e `family_access` |
