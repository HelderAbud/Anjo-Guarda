# Backup local

A política de prazo e periodicidade continua em aberto. Esta fatia só cria a cópia manual na máquina.

- O script gera uma pasta `backups/aaaaMMdd-HHmmss` fora do Git
- Entra o dump do banco e, se existirem, as pastas `documentos` e `imports-originais`
- Não apaga cópias antigas e não envia nada para fora
- Não há restauração automática
