# Plano de convivência

A API já grava um plano ativo por criança. Esta fatia só abre essa tela.

- Sem plano (404): formulário com nome, início, fim opcional e regra
- Regras: dias alternados, fins de semana, semanas alternadas e personalizada
- Cada papel da regra escolhe um responsável da criança, por id
- Fim anterior ao início não é enviado
- Com plano ativo: mostra nome, período, regra e responsáveis. Sem segundo envio
- Não há como substituir o plano ativo nesta API
- Exceção continua só no calendário. Esta tela não cria exceção
