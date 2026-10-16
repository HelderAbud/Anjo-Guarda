# Auditoria

A API já lista os registros do usuário autenticado. Esta fatia abre essa leitura.

- Período padrão: o mês de hoje em America/Sao_Paulo
- A lista preserva a ordem da API
- Cada linha mostra data, ação e tipo de entidade
- Período invertido não consulta a API e avisa "Período inválido"
- Sem filtro de criança: a consulta não tem criança
- Sem metadata: a resposta não traz esse campo
