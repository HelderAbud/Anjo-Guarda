# Backup remoto

O servidor da API continua sem nome. Esta fatia só envia a cópia e aplica o prazo.

- `scripts/backup-s3.ps1` manda o dump e as pastas para o bucket, em `backups/daily` e, uma vez por mês, `backups/monthly`
- Guarda 30 diárias e 12 mensais. Só apaga nome no formato da data, dentro desses prefixos
- Não cria a tarefa das 02:00 e não mexe no que `backup-local.ps1` guarda nesta máquina
- Não foi executado contra o banco
