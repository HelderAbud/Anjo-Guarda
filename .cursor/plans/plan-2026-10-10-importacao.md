# IMP-001 — importar PDF ou DOCX

Contrato aprovado em 2026-10-10.

- `POST /api/v1/imports` (multipart: `childId`, `file`)
- `GET /api/v1/imports/{id}` devolve nome, `sourceType` (`PDF` ou `DOCX`), status `EXTRAIDO`, texto extraído e data
- PDF com texto: extrai. PDF sem texto: texto vazio. Sem OCR
- DOCX: parágrafos e tabelas como texto
- Até 10 MB. Nome original só no metadado. Chave gerada em `imports-originais/`
- Coluna `storage_key` além da tabela da spec, porque o nome não pode ser o caminho
- Outro responsável ou id inexistente: 404. Tipo fora de PDF/DOCX: 400
- Nenhuma exceção, confirmação ou observação. Auditoria da aprovação fica no IMP-002
