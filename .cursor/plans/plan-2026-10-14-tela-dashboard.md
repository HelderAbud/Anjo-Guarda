# Spec — Tela do dashboard

## Problem

Depois do login a tela para em "Sessão iniciada". A seção 16 pede o dashboard: resumo do mês, próximos dias e últimas observações.

## Scope

- Depois do login, a tela lê a API que já existe e mostra esses três blocos.
- `GET /api/v1/children`, `GET /api/v1/children/{id}/calendar?year=&month=` e `GET /api/v1/notes?childId=&from=&to=`, com `Authorization: Bearer`.
- Hoje é a data civil de `America/Sao_Paulo`.
- Resumo: conta os dias do mês atual por status. Dia sem `guardianId` não entra na conta.
- Próximos dias: até 7 dias a partir de hoje, inclusive, que tenham responsável. Se o mês atual não completar 7, lê o mês seguinte.
- Observações: de hoje menos 90 dias até hoje. Mostra até 5, da data mais recente para a mais antiga.
- Mais de uma criança: seletor, começando pela primeira da lista.
- Nenhuma criança: aviso, sem formulário de cadastro.
- 401 nessas leituras volta ao login. Sair continua no logout.

## Out of scope

- Grade do calendário, detalhe do dia, cadastro de criança, React Query além destas leituras, Tailwind, fases 7 e 8.

## Acceptance criteria

- Com criança, plano e observações, os três blocos aparecem com os números e os nomes acima.
- Dia sem responsável não aumenta o resumo nem entra nos próximos dias.
- Sem criança, a tela não chama o calendário.
- 401 limpa a sessão e mostra o formulário de entrar.

## Decisions

- Nenhum endpoint novo. Os limites 7, 5 e 90 são só da tela.
- Status e categoria aparecem como a API grava.
- O nome do responsável vem de `guardians` da criança.

## Risks

- O access token dura 15 minutos. Esta fatia só renova a tela voltando ao login no 401.

## Verification

- Vitest na tela, com a data de hoje injetada.
- `npm test` e `npm run build`.
- No navegador: login ausente continua no formulário; o dashboard com API desligada mostra erro sem sair do login já feito só se a leitura falhar por rede.
