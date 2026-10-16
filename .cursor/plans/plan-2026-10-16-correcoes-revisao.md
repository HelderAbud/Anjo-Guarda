# Correções da revisão

Só entra o que o código confirma. O resto fica de fora.

- Login de usuário ausente ou inativo também calcula bcrypt
- E-mail do login só perde espaços. O índice é sensível a maiúsculas
- Violação de unicidade (SQL 23505) responde 409. Outro erro de integridade segue 500
- O reset lê ANJO_NEW_PASSWORD. A senha não vai no argumento do Maven
- PDF com mais de 100 páginas é recusado. PDF sem texto continua vazio
- DOCX limita a entrada descompactada a 20 MB
