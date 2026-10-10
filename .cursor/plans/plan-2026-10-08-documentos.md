# DOC-001 — guardar documentos

Contrato aprovado em 2026-10-08.

- `POST /api/v1/documents` (multipart: `childId`, `category`, `file`)
- `GET /api/v1/documents?childId=` lista metadados
- `GET /api/v1/documents/{id}/download` devolve os bytes com o MIME guardado
- Colunas: `id`, `child_id`, `category`, `filename`, `storage_key`, `mime_type`, `size`, `created_by`
- O nome original fica só no metadado. O caminho no disco é uma chave gerada.
- Pasta local `documentos/` (já no `.gitignore`). Produção continua em aberto.
- Categoria: texto obrigatório, até 40 caracteres, sem lista fechada.
- Até 10 MB. MIME: `application/pdf`, `image/jpeg`, `image/png`, `image/webp`.
- Outro responsável ou id inexistente: 404.
- Auditoria do upload. Sem exclusão, sem linha do tempo, sem data de validade.
