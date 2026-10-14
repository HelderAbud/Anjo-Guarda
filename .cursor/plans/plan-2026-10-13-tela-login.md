# Tela 1 — Login

Contrato da spec, seção 16. Aprovado ao seguir depois das fases 0 a 6.

- React + TypeScript, Vite, testes com Vitest e Testing Library
- Campos e-mail e senha, botão Entrar. Sem "esqueci minha senha"
- E-mail inválido ou senha vazia não chama a API
- `POST /api/v1/auth/login`. 401 mostra "Credenciais inválidas"
- Sucesso guarda access e refresh na sessão da aba e mostra Sair
- Sair chama `POST /api/v1/auth/logout` e volta ao formulário
- O navegador fala com a API pelo proxy `/api` → `localhost:8086`
- Calendário, React Query e Tailwind ficam para a próxima tela
