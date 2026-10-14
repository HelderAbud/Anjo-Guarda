# Detalhe do dia

A grade já abre o dia em leitura. Esta fatia grava com a API existente.

- Responsável planejado vem do dia calculado
- Confirmação: REALIZADO, ALTERADO ou NAO_REALIZADO. PLANEJADO não se grava
- REALIZADO só quando há responsável planejado, sem horário
- ALTERADO pede um responsável da criança. Horário é opcional. O mesmo responsável sem horário não é enviado
- NAO_REALIZADO não envia responsável nem horário
- Observação usa a nota do dia: categoria e texto. Se já existe, atualiza. Apagar é o delete lógico
- Sem anexo
- O mês não devolve o horário realizado. O campo abre vazio
