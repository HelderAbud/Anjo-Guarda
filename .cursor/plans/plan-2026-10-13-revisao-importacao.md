# IMP-002 — resolver conflitos antes de gravar

Contrato aprovado em 2026-10-13.

Linhas marcadas viram itens com confiança 1. O resto fica só no texto extraído.

- `OBSERVACAO <data> <categoria> <texto>`
- `CONFIRMACAO <data> REALIZADO` ou `NAO_REALIZADO`
- `CONFIRMACAO <data> ALTERADO <guardianId>` com horários opcionais
- `EXCECAO <data> <guardianId> <motivo>`

`GET /api/v1/imports/{id}` inclui os itens. Conflito = já existe o mesmo tipo na data.

`POST /api/v1/imports/{id}/approve` decide cada item uma vez. Transação única.

- Sem conflito e aprovado: cria a entidade e aponta os dois campos.
- Com conflito: `MANTER`, `SUBSTITUIR` ou `MESCLAR`. Mesclar só observação (texto novo na linha de baixo, categoria atual).
- Rejeitado: ponteiros nulos.
- Segunda aprovação: 409. Status `REVISADO`.
- Auditoria `IMPORT_APPROVED`. As regras já existentes de confirmação, exceção e observação continuam valendo.
