# Renovar a sessão

Quando um pedido autenticado recebe 401, a tela chama o refresh uma vez, guarda a sessão nova e repete o pedido. Se o refresh falha, a sessão sai. O login com senha errada não dispara refresh. A sessão continua no sessionStorage.
