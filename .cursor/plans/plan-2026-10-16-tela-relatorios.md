# Relatórios

A API já gera o resumo mensal e o PDF sai do snapshot da versão. Esta fatia abre essa leitura.

- Mês da criança, padrão o mês de hoje em America/Sao_Paulo
- A lista mostra as versões daquele mês, da mais antiga à mais nova
- Gerar cria uma versão nova e mostra o snapshot devolvido
- Abrir uma versão busca o snapshot dela, sem gerar outra
- O resumo mostra criança, versão, data de geração, contagens e os dias do snapshot
- O PDF baixado é o da versão aberta
- Trocar de criança ou de mês limpa o resumo aberto
- Sem seção de documentos: o snapshot não traz anexos
- Sem alterar a fonte do PDF
