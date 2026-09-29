**ESPECIFICAÇÃO TÉCNICA**

Projeto: Calendário de Convivência Familiar

*Documento-base para planejamento, arquitetura, desenvolvimento, testes
e evolução do sistema*

Versão 4.1 --- lacunas de modelo fechadas antes de codar: vínculo
guardian/usuário, responsável por ID, refresh token persistido, reset
local de senha, rastro da importação, versão do relatório e unicidade
explícita. Uma observação por criança por dia permanece.

1\. Visão do produto

Sistema web responsivo, de uso pessoal (com evolução planejada para uso
compartilhado entre os dois responsáveis), para organizar a convivência
familiar por meio de um calendário interativo, registros diários,
distinção entre planejado e realizado, histórico consolidado,
armazenamento de documentos e informações, importação de PDF/DOCX e
geração de relatórios mensais. O produto deve priorizar simplicidade,
histórico, segurança, rastreabilidade e excelente uso em celular.

> *Nota: Escopo de uso definido: hoje é uso individual, com autenticação
> simples para um único usuário. O acesso dos dois responsáveis ao
> aplicativo é uma implementação futura --- a estrutura de dados já
> prevê essa evolução (seção 8), mas a autenticação do MVP não precisa
> ser construída já pronta para multiusuário. O produto continua focado
> nesse núcleo --- não é tratado aqui como plataforma multi-tenant para
> terceiros (ver seção 30).*

2\. Objetivos

-   Visualizar de forma rápida com quem a criança estará em cada dia.

-   Cadastrar uma regra recorrente de convivência e suas exceções.

-   Diferenciar o que foi planejado do que efetivamente aconteceu em
    cada dia.

-   Registrar observações vinculadas a cada data.

-   Consultar um histórico/linha do tempo consolidado das alterações
    relevantes.

-   Guardar documentos e informações importantes em um único local.

-   Importar informações de documentos (PDF e DOCX) sem alterar dados
    automaticamente, sempre com confirmação manual.

-   Gerar relatório mensal cronológico, de finalidade organizacional,
    com os registros do período.

-   Permitir evolução futura para acesso compartilhado entre
    responsáveis e recursos de IA.

3\. Princípios do produto

Princípios que orientam decisões de produto e evitam retrabalho quando o
sistema crescer:

-   Não tomar partido entre responsáveis nos registros e relatórios.

-   Diferenciar dado registrado (fato) de interpretação (observação
    pessoal ou, futuramente, IA).

-   Preservar histórico de qualquer alteração relevante --- nada crítico
    é sobrescrito silenciosamente.

-   Nunca alterar dado importado automaticamente sem confirmação manual
    explícita.

-   Priorizar privacidade e segurança dos dados da criança em qualquer
    decisão técnica.

-   Ser simples o suficiente para uso diário --- funcionalidade nova só
    entra quando o núcleo estiver estável.

-   Funcionar muito bem no celular, não só no desktop.

-   Não depender de IA para nenhuma funcionalidade essencial do MVP.

4\. Escopo do MVP

  --------------------------------------------------------------------------
  **Módulo**          **MVP**                               **Prioridade**
  ------------------- ------------------------------------- ----------------
  Autenticação        Login, logout, JWT + refresh token,   Alta
                      para um único usuário (acesso         
                      multiusuário é fase futura)           

  Perfil familiar     Família, criança e responsáveis       Alta

  Calendário          Visão mensal seg-dom, calculada sob   Alta
                      demanda, e detalhes por dia           

  Plano de            Regra recorrente + exceções           Alta
  convivência         (responsável único por dia)           

  Planejado vs.       Status por dia e registro do que      Alta
  Realizado           efetivamente ocorreu                  

  Observações         Registro diário com categorias        Alta

  Histórico / Linha   Consolidação cronológica de           Alta
  do tempo            alterações, observações e documentos  

  Documentos          Upload, categorias e metadados        Média

  Relatórios          Resumo mensal organizacional e        Alta
                      exportação PDF                        

  Auditoria           Registro de alterações críticas       Alta

  Importação PDF/DOCX Pipeline de revisão, ambos os         Média/2ª fase
                      formatos desde o início               

  IA                  Resumo inteligente                    3ª fase
  --------------------------------------------------------------------------

5\. Stack tecnológica

  -----------------------------------------------------------------------
  **Camada**          **Tecnologia**
  ------------------- ---------------------------------------------------
  Backend             Java 21 + Spring Boot 3

  API                 Spring Web / REST, versionada (/api/v1)

  Persistência        Spring Data JPA + PostgreSQL

  Migrações           Flyway

  Segurança           Spring Security + JWT (access token curto) +
                      refresh token, single-user no MVP

  Documentação        springdoc OpenAPI / Swagger

  Frontend            React + TypeScript

  Estado/cache no     React Query (ou equivalente) para dados do
  frontend            calendário

  Estilo/UI           Tailwind CSS ou biblioteca de componentes

  Validação           Bean Validation no backend + validação no frontend

  Testes backend      JUnit 5 + Mockito + testes de integração + testes
                      baseados em propriedades para o motor de regras

  Testes frontend     Vitest/Jest + React Testing Library

  Infra local         Docker + Docker Compose

  Deploy              Render/Railway/AWS ou equivalente

  Arquivos            Storage de objetos; evitar guardar arquivos grandes
                      no PostgreSQL

  Observabilidade     Actuator + logs estruturados; métricas futuramente
  -----------------------------------------------------------------------

6\. Arquitetura

Arquitetura modular em camadas, mantendo regras de negócio fora dos
controllers. Fluxo recomendado: Controller → Use Case/Application
Service → Domain/Service → Repository → PostgreSQL.

-   Controller: recebe HTTP, valida entrada superficial e devolve DTOs.

-   Application/Use Case: orquestra cada caso de uso.

-   Domain/Service: contém regras de negócio, incluindo o motor de
    geração do calendário.

-   Repository: acesso aos dados.

-   DTOs: nunca expor entidades JPA diretamente pela API.

-   Mapper: conversão entre entidade e DTO.

-   Exception Handler global: respostas HTTP padronizadas.

-   Auditoria: registrar operações críticas sem misturar auditoria com
    regra de negócio.

7\. Módulos do backend

-   auth --- autenticação simples de um usuário (JWT + refresh);
    estrutura para múltiplos usuários fica para fase futura.

-   family --- família, criança e responsáveis.

-   access --- vínculo do usuário à família; hoje suporta um usuário,
    preparado no dado para expansão futura.

-   calendar --- geração e consulta do calendário (motor de cálculo sob
    demanda), incluindo planejado vs. realizado.

-   schedule --- regras recorrentes e exceções.

-   notes --- observações diárias.

-   timeline --- consolidação da linha do tempo/histórico a partir de
    audit_logs, notes, exceptions, confirmations e documents.

-   documents --- documentos e arquivos.

-   imports --- processamento e revisão de PDF/DOCX.

-   reports --- geração de relatórios.

-   audit --- histórico e rastreabilidade.

-   common --- tratamento de erros, segurança, paginação, utilitários e
    configurações.

8\. Modelo de dados

Decisão de arquitetura: calendar_days NÃO é uma tabela gerada e
persistida a cada mudança de regra. O calendário do mês é calculado sob
demanda a partir de schedule_rules + calendar_exceptions. Só é
persistido o que foi efetivamente editado por um usuário: uma exceção
pontual, uma observação, uma confirmação de realização, ou horários
específicos de um dia. Isso garante o requisito de determinismo (mesma
entrada = mesmo calendário) sem duplicar dado.

  -------------------------------------------------------------------------------------------
  **Entidade**             **Campos principais**          **Responsabilidade**
  ------------------------ ------------------------------ -----------------------------------
  users                    id, name, email,               Usuários do sistema (login)
                           password_hash, status,         
                           created_at                     

  families                 id, name, created_at           Agrupamento familiar (permite
                                                          múltiplos filhos no futuro)

  children                 id, family_id, name,           Criança
                           birth_date, active, created_at 

  guardians                id, child_id, name,            Responsável pela criança (dado de
                           relationship, user_id          parentesco). user_id é FK
                           (nullable)                     nullable para users: no MVP fica
                                                          vazio; na fase 8 liga o login ao
                                                          responsável certo

  refresh_tokens           id, user_id, token_hash,       Refresh com rotação e revogação.
                           expires_at, revoked_at,        Persiste só o hash, nunca o token
                           created_at                     em claro. Logout e reuso invalidam
                                                          pela linha

  family_access            id, family_id, user_id, role,  Vínculo de usuário à família. No
                           invited_by, accepted_at        MVP, uma única linha ativa; a
                                                          estrutura já suporta mais de um
                                                          usuário no futuro

  custody_plans            id, child_id, name,            Plano de convivência
                           start_date, end_date, active   

  schedule_rules           id, plan_id, rule_type,        Regra recorrente (fonte de verdade
                           configuration_json,            do calendário)
                           start_date, end_date           

  calendar_exceptions      id, child_id, date, reason,    Sobrescreve o planejamento da regra
                           original_guardian_id,          para uma data específica. Guarda
                           new_guardian_id, created_by    guardian_id, não nome livre

  calendar_confirmations   id, child_id, date, status,    Registra o que efetivamente
                           realized_guardian_id,          aconteceu num dia
                           realized_start_time,           (planejado/realizado/alterado/não
                           realized_end_time, note,       realizado). Responsável realizado
                           created_by, created_at         é guardian_id

  daily_notes              id, child_id, date, category,  Anotações vinculadas a uma data
                           text, created_by, created_at,  (exclusão lógica via deleted_at)
                           updated_at, deleted_at         

  documents                id, child_id, category,        Arquivos
                           filename, storage_key,         
                           mime_type, size, created_by    

  imports                  id, child_id, filename,        Processos de importação (PDF ou
                           source_type, status,           DOCX)
                           extracted_text, created_at     

  import_items             id, import_id, type,           Itens identificados; \'resolution\'
                           proposed_data, confidence,     registra o conflito. Ao aprovar,
                           approved, resolution,          resulting_entity_type +
                           resulting_entity_type,         resulting_entity_id apontam a
                           resulting_entity_id            exceção, confirmação ou observação
                                                          criada ou mantida. Rejeitado: ambos
                                                          nulos

  monthly_reports          id, child_id, year, month,     Relatórios. Cada geração insere
                           version, generated_at,         nova linha (version = máxima + 1
                           content_snapshot               no mês). Não sobrescreve.
                                                          content_snapshot é JSON dos dados
                                                          usados, não HTML nem PDF. O PDF é
                                                          exportado a partir dessa versão

  audit_logs               id, user_id, action,           Auditoria
                           entity_type, entity_id,        
                           metadata, created_at           
  -------------------------------------------------------------------------------------------

> *Nota: calendar_confirmations é opcional por dia: se não existir
> registro, o dia é tratado como \'planejado\' (o que a regra + exceções
> definem). Só existe uma linha quando o usuário confirma explicitamente
> o que aconteceu. Isso mantém o princípio de calendar_days não
> materializado (seção 8/introdução) mesmo com o novo conceito de
> realização.*

9\. Regras de negócio do calendário

-   O calendário deve sempre trabalhar com uma data de referência no
    fuso America/Sao_Paulo (ver seção 13).

-   A semana deve ser exibida de segunda a domingo.

-   Cada data deve possuir um estado de convivência definido pela regra
    ativa ou explicitamente marcado como não definido.

-   Uma regra recorrente gera o planejamento; uma exceção pode
    sobrescrever o planejamento de uma data específica, mas nunca apaga
    a regra recorrente.

-   Cada dia tem um único responsável no MVP. Múltiplos períodos de
    responsável no mesmo dia (ex: manhã/tarde) ficam como evolução
    futura --- o modelo não deve ser desenhado de forma que bloqueie
    essa extensão depois (ver seção 30).

-   Mudanças críticas (exceções, confirmações, observações, documentos,
    aprovação de importação) devem gerar auditoria.

-   Excluir uma observação deve ser exclusão lógica (deleted_at), nunca
    exclusão física.

10\. Motor de regras de convivência

-   Suportar inicialmente padrões simples: dias alternados, fins de
    semana, semanas alternadas e configuração personalizada.

-   A regra deve ter data inicial e opcionalmente data final.

-   A configuração da regra deve ser persistida de forma estruturada
    (configuration_json com schema validado na aplicação). Todo
    responsável citado no JSON é guardian_id (UUID). Nome livre não
    entra no cálculo. Padrões iniciais (dias alternados, fins de semana,
    semanas alternadas, personalizada) referenciam os guardian_id
    envolvidos, por exemplo weekday ou papel da regra → guardian_id.

-   O motor deve conseguir gerar o planejamento para um mês específico
    sem persistir esse resultado --- apenas calculá-lo (ver seção 8).

-   Exceções devem ser aplicadas depois da regra-base, na camada de
    leitura.

-   O resultado final do mês deve ser determinístico: mesma entrada
    (regra + exceções) = mesmo calendário.

-   O motor de regras não conhece \'realização\' --- essa é uma camada
    separada (calendar_confirmations) aplicada por cima do resultado
    calculado.

11\. Planejado vs. Realizado

Todo dia tem, por padrão, apenas o planejamento (regra + exceções). O
usuário pode opcionalmente confirmar o que de fato aconteceu, criando
uma calendar_confirmation.

  -----------------------------------------------------------------------
  **Status**      **Significado**
  --------------- -------------------------------------------------------
  Planejado       Nenhuma confirmação registrada; vale o que a
                  regra/exceção definem

  Realizado       Confirmado que aconteceu exatamente como planejado

  Alterado        Aconteceu diferente do planejado (responsável e/ou
                  horário diferentes)

  Não realizado   O planejado não ocorreu
  -----------------------------------------------------------------------

-   A confirmação é sempre uma ação manual do usuário --- nunca inferida
    automaticamente.

-   Quando o status é \'Alterado\', o sistema guarda o
    responsável/horário realizado junto do que estava planejado, para
    permitir comparação depois.

-   Confirmações entram na auditoria e na linha do tempo (seção 12).

12\. Histórico / Linha do tempo

Tela de leitura que consolida, em ordem cronológica, tudo que aconteceu
em torno de uma data ou período: alteração de responsável (exceção),
confirmação de realização, observação criada/editada, documento anexado
e eventos de auditoria relevantes.

-   Não introduz tabela nova além de calendar_confirmations --- é uma
    consulta agregada sobre exceptions, confirmations, notes, documents
    e audit_logs.

-   Filtrável por criança, período e tipo de evento.

-   Serve como base direta para o relatório mensal (seção 20) e para o
    pipeline de importação revisar conflitos (seção 19).

13\. Fuso horário e tratamento de datas

-   Fuso horário fixo do sistema: America/Sao_Paulo.

-   Datas de convivência são armazenadas e comparadas como DATE (sem
    hora), horários específicos ficam em campos separados
    (start_time/end_time).

-   O Brasil não observa horário de verão atualmente, mas o motor de
    regras não deve assumir isso permanentemente --- usar biblioteca de
    data/hora com suporte a fuso (java.time) em vez de cálculos manuais
    de offset.

-   Consultas de calendário mensal devem considerar corretamente viradas
    de mês/ano e anos bissextos --- cobertos por teste (seção 24).

14\. API REST --- endpoints iniciais

Todos os endpoints abaixo são prefixados por /api/v1.

  --------------------------------------------------------------------------------------------
  **Método**   **Endpoint**                                **Objetivo**
  ------------ ------------------------------------------- -----------------------------------
  POST         /auth/login                                 Autenticar e emitir access +
                                                           refresh token

  POST         /auth/refresh                               Renovar access token

  POST         /auth/logout                                Invalidar refresh token

  GET          /me                                         Usuário autenticado

  GET          /children                                   Listar crianças acessíveis ao
                                                           usuário

  POST         /children                                   Cadastrar criança

  GET          /children/{id}                              Detalhes da criança

  GET          /children/{id}/calendar?year=2026&month=9   Calendário mensal calculado
                                                           (regra + exceções + confirmações)

  POST         /calendar/exceptions                        Criar exceção pontual

  GET          /calendar/days/{date}                       Detalhes de uma data (planejado +
                                                           realizado, se houver)

  POST         /calendar/confirmations                     Confirmar o que aconteceu em uma
                                                           data
                                                           (planejado/realizado/alterado/não
                                                           realizado)

  GET          /children/{id}/timeline?from=&to=           Consultar linha do tempo
                                                           consolidada

  POST         /notes                                      Criar observação

  GET          /notes?childId=&from=&to=                   Consultar observações

  PATCH        /notes/{id}                                 Editar observação

  DELETE       /notes/{id}                                 Excluir/anular observação (lógico)

  POST         /documents                                  Enviar documento

  GET          /documents                                  Listar documentos

  GET          /documents/{id}/download                    Baixar/visualizar documento

  POST         /imports                                    Iniciar importação (PDF ou DOCX)

  GET          /imports/{id}                               Consultar resultado, incluindo
                                                           conflitos detectados

  POST         /imports/{id}/approve                       Confirmar itens importados
                                                           (resolvendo conflitos indicados)

  GET          /reports/monthly?year=2026&month=9          Gerar/consultar relatório

  GET          /reports/monthly/{id}/pdf                   Exportar PDF
  --------------------------------------------------------------------------------------------

15\. Padrão de resposta da API

Sucesso: HTTP 2xx com DTO. Erros: objeto padronizado contendo timestamp,
status, code, message, path e, quando aplicável, validationErrors. Não
retornar stack trace ao cliente.

Exemplo conceitual de erro:

*{\"timestamp\":\"\...\",\"status\":400,\"code\":\"VALIDATION_ERROR\",\"message\":\"Dados
inválidos\",\"path\":\"/api/v1/notes\"}*

16\. Frontend --- telas

  -----------------------------------------------------------------------
  **Tela**           **Principais componentes**
  ------------------ ----------------------------------------------------
  Login              Email, senha e validação. Sem
                     \"esqueci minha senha\" por e-mail no MVP

  Dashboard          Resumo do mês, próximos dias, últimas observações

  Calendário         Cabeçalho, navegação mensal, grid seg-dom, legenda

  Detalhes do dia    Responsável planejado, confirmação de realizado,
                     horários, observações, categoria e anexos

  Plano de           Regra, período, visualização e exceções
  convivência        

  Histórico / Linha  Lista cronológica filtrável de alterações,
  do tempo           confirmações, observações e documentos

  Documentos         Categorias, upload, busca, visualização

  Importação         Upload → processamento → itens encontrados →
                     conflitos → revisão → confirmação

  Relatórios         Resumo, cronologia, totais e exportação

  Auditoria          Histórico de alterações para usuário autorizado

  Configurações      Perfil, preferências e segurança
                     (convites/multiusuário: fase futura)
  -----------------------------------------------------------------------

17\. UX do calendário

-   Desktop: grid mensal tradicional.

-   Celular: calendário adaptado para toque e leitura rápida.

-   Cores/ícones devem possuir legenda e não depender somente de cor
    para transmitir informação.

-   Um indicador visual simples deve distinguir dias apenas planejados
    de dias já confirmados (realizado/alterado/não realizado).

-   Clique/toque em uma data abre o detalhe.

-   Botão \'Hoje\'.

-   Navegação mês anterior/próximo.

-   Indicador discreto quando existe observação, exceção ou anexo.

-   Resumo do mês no topo: quantidade de dias por responsável e
    quantidade de registros.

18\. Registro diário

-   Data --- automática.

-   Dia da semana --- calculado.

-   Responsável planejado --- derivado do calendário calculado, editável
    via exceção conforme permissão.

-   Status de realização --- opcional: planejado (padrão), realizado,
    alterado ou não realizado.

-   Horário inicial/final planejado e, se aplicável, realizado ---
    opcionais.

-   Categoria --- rotina, escola, atividade, saúde, viagem, troca de
    horário ou outro.

-   Observação --- texto livre.

-   Anexos --- fase posterior.

-   Criado por, criado em, atualizado em --- automáticos.

19\. Documentos e importação

Pipeline: upload → armazenamento do original → extração de texto →
interpretação → propostas estruturadas → detecção de conflito com dado
existente → tela de revisão → confirmação → gravação no domínio.

-   PDF textual: extrair texto; PDF escaneado: OCR em fase posterior.

-   DOCX: extrair parágrafos e tabelas.

-   Ambos os formatos (PDF e DOCX) fazem parte do MVP de importação, não
    apenas um deles.

-   Identificar datas, horários, nomes, responsáveis e padrões.

-   Cada item extraído deve possuir confiança e origem
    (import_items.confidence).

-   Se um item proposto conflitar com uma exceção, confirmação ou
    observação já registrada na mesma data, o sistema deve sinalizar o
    conflito explicitamente na tela de revisão --- a aprovação exige que
    o usuário escolha manter o dado existente, substituir, ou mesclar.

-   Usuário deve aprovar ou rejeitar cada alteração relevante, item a
    item.

-   Item aprovado que cria ou mantém uma exceção, confirmação ou
    observação grava resulting_entity_type e resulting_entity_id. Item
    rejeitado deixa os dois nulos. Um item aponta no máximo uma entidade.

-   Manter o documento original para auditoria/referência.

20\. Relatório mensal

Finalidade organizacional/pessoal --- o relatório não precisa ter
formato ou rigor documental/jurídico, mas mantém rastreabilidade
(snapshot + versão) para consistência ao longo do tempo.

-   Título do mês e criança.

-   Resumo quantitativo de convivência, incluindo dias planejados vs.
    realizados/alterados/não realizados.

-   Calendário visual do mês.

-   Cronologia diária em ordem crescente, baseada na linha do tempo
    (seção 12): data + dia da semana + responsável + status + horários +
    observações.

-   Seção de exceções/alterações.

-   Seção de documentos ou anexos relevantes, se habilitada.

-   Data de geração e versão do relatório.

-   Exportação em PDF; futuramente DOCX. O PDF sai do content_snapshot
    da versão escolhida e não substitui esse JSON.

Importante: regenerar o mesmo mês cria nova linha em monthly_reports,
com version incrementada para aquela criança/ano/mês. A linha antiga não
é atualizada. content_snapshot é JSON com os dados brutos usados
(criança, período, dias com guardian_id, status, horários, observação e
exceção). Não é HTML renderizado nem o binário do PDF.

21\. IA --- fase futura

-   Resumo mensal das anotações.

-   Resumo cronológico.

-   Classificação assistida de observações.

-   Extração de regras de documentos.

-   Busca semântica no histórico.

-   Perguntas sobre registros existentes.

-   A IA não deve inventar fatos.

-   A IA deve distinguir dado registrado de interpretação (princípio da
    seção 3).

-   Conteúdo gerado por IA deve ser identificável e revisável antes de
    entrar em relatório.

22\. Segurança e privacidade

-   HTTPS obrigatório em produção.

-   Senhas armazenadas somente com hash forte (bcrypt/argon2).

-   Autenticação via JWT de curta duração + refresh token com rotação,
    para um único usuário no MVP; o dado (family_access) já comporta
    mais de um usuário quando o acesso compartilhado for implementado.
    O refresh é persistido em refresh_tokens (hash, expires_at,
    revoked_at). Logout revoga. Reuso de token já rotacionado revoga a
    cadeia.

-   Sem recuperação de senha por e-mail no MVP. Esquecer a senha não
    pode prender o único usuário: o reset é um script local, na máquina
    de quem administra o sistema, que grava um novo password_hash. Não
    há endpoint público de reset.

-   Autorização por recurso: usuário só acessa dados da família à qual
    tem family_access ativo.

-   Validação de ownership da criança em toda operação.

-   Limite de tamanho e MIME type para uploads.

-   Nome do arquivo do usuário não deve ser usado diretamente como
    caminho no storage.

-   Logs não devem conter senha, token ou dados desnecessários.

-   Proteção contra IDOR, upload malicioso, SQL injection, XSS e CSRF
    conforme arquitetura escolhida.

-   Backup periódico do banco e estratégia de recuperação.

-   Dados pessoais de menor de idade: tratamento em conformidade com a
    LGPD --- definir base legal, finalidade e política de
    retenção/exclusão antes de qualquer versão compartilhada ou
    hospedada fora do ambiente local.

23\. Auditoria

  -----------------------------------------------------------------------
  **Ação**                                    **Auditar?**
  ------------------------------------------- ---------------------------
  Login bem-sucedido/falho                    Sim, com cautela

  Criação/edição de observação                Sim

  Criação de exceção                          Sim

  Confirmação de realização                   Sim
  (planejado/realizado/alterado/não           
  realizado)                                  

  Upload/exclusão de documento                Sim

  Importação aprovada (incluindo resolução de Sim
  conflitos)                                  

  Geração de relatório                        Sim

  Convite/alteração de acesso a família       Sim
  (quando multiusuário existir)               

  Consulta simples ao calendário ou à linha   Opcional
  do tempo                                    
  -----------------------------------------------------------------------

24\. Testes

-   Unitários para o motor de regras de convivência.

-   Testes baseados em propriedades para geração do calendário mensal
    (cobrindo viradas de mês/ano, anos bissextos e sobreposição de
    regras).

-   Testes de exceções, confirmações e conflitos de importação.

-   Testes de autorização/ownership.

-   Testes de validação de DTOs.

-   Testes de integração com PostgreSQL.

-   Testes dos principais endpoints.

-   Testes de importação com documentos reais anonimizados (PDF e DOCX).

-   Testes de geração de PDF.

-   Testes frontend para calendário, detalhe do dia, linha do tempo e
    importação.

-   Testes de regressão para cada regra de convivência.

25\. Critérios de aceite do MVP

-   Usuário consegue entrar no sistema.

-   Usuário consegue cadastrar a criança e responsáveis.

-   Usuário consegue configurar uma regra de convivência.

-   Sistema calcula corretamente um mês completo de segunda a domingo.

-   Usuário consegue alterar uma data por exceção.

-   Usuário consegue confirmar o que aconteceu em um dia
    (realizado/alterado/não realizado).

-   Usuário consegue abrir qualquer data e registrar observação.

-   Usuário consegue visualizar a linha do tempo de um período.

-   Observações e confirmações permanecem armazenadas após sair e entrar
    novamente.

-   Usuário consegue pesquisar/filtrar registros.

-   Sistema gera relatório mensal cronológico.

-   Relatório pode ser exportado em PDF.

-   Alterações críticas ficam auditadas.

-   Importação processa PDF e DOCX e sinaliza conflitos antes da
    aprovação.

26\. Estrutura de projeto sugerida

Backend

-   src/main/java/\.../auth

-   src/main/java/\.../access

-   src/main/java/\.../family

-   src/main/java/\.../calendar

-   src/main/java/\.../schedule

-   src/main/java/\.../notes

-   src/main/java/\.../timeline

-   src/main/java/\.../documents

-   src/main/java/\.../imports

-   src/main/java/\.../reports

-   src/main/java/\.../audit

-   src/main/java/\.../common

Frontend

-   src/features/auth

-   src/features/calendar

-   src/features/notes

-   src/features/timeline

-   src/features/documents

-   src/features/imports

-   src/features/reports

-   src/components

-   src/services

-   src/hooks

-   src/types

27\. Banco de dados --- princípios

-   UUID como identificador.

-   created_at e updated_at nas entidades relevantes; deleted_at onde há
    exclusão lógica.

-   FKs e índices para child_id, date, plan_id e created_by.

-   Unicidade explícita:
    calendar_exceptions (child_id, date) única;
    calendar_confirmations (child_id, date) única;
    daily_notes uma linha ativa por (child_id, date) — índice único
    parcial onde deleted_at é nulo, porque a exclusão é lógica e a
    decisão de produto é uma observação por criança por dia;
    monthly_reports (child_id, year, month, version) única;
    guardians.user_id, quando preenchido, único por criança.

-   Flyway para todas as mudanças de schema.

-   Nunca editar manualmente schema de produção fora das migrações.

-   Índice composto para consultas mensais por criança e data.

28\. Deploy e ambientes

  -----------------------------------------------------------------------
  **Ambiente**        **Objetivo**
  ------------------- ---------------------------------------------------
  local               Docker Compose + PostgreSQL

  test                Testes automatizados/integração

  staging             Validação antes de produção

  production          Uso real, HTTPS, backups e observabilidade
  -----------------------------------------------------------------------

-   Secrets somente em variáveis de ambiente/secret manager.

-   Banco separado por ambiente.

-   Migration automática controlada.

-   Health check do backend.

-   Backup e teste periódico de restauração.

-   Logs centralizados na plataforma escolhida.

29\. Roadmap

  -----------------------------------------------------------------------
  **Fase**            **Entrega**
  ------------------- ---------------------------------------------------
  0 --- Planejamento  Requisitos, arquitetura, modelo de dados e
                      protótipo

  1 --- Fundação      Backend, banco, segurança (auth+refresh,
                      single-user) e estrutura frontend

  2 --- Calendário    Motor de convivência (cálculo sob demanda), grid
                      mensal, exceções

  3 --- Realização e  Confirmações (planejado/realizado), observações,
  diário              categorias, busca

  4 --- Linha do      Histórico consolidado, resumo mensal e PDF
  tempo e relatórios  

  5 --- Documentos    Upload, armazenamento e organização

  6 --- Importação    PDF + DOCX, detecção de conflito, revisão antes da
                      confirmação

  7 --- IA            Resumos, extração e busca inteligente

  8 --- Acesso        Convites, ativação de mais de um usuário em
  compartilhado       family_access, permissões e colaboração
  -----------------------------------------------------------------------

30\. Ideias futuras registradas (não comprometidas no MVP)

Sugestões avaliadas e consideradas válidas para uma eventual evolução do
produto, mas fora do escopo atual --- registradas aqui para não se
perderem, sem virar trabalho pendente do MVP:

-   Múltiplos períodos de responsável no mesmo dia (ex: manhã/tarde) ---
    ver nota na seção 9.

-   Conceito de \'evento\' (escola, médico, viagem, aniversário) além da
    observação livre e da confirmação de realização.

-   Notificações (lembrete de troca de responsável, compromisso do dia,
    vencimento de documento).

-   Documentos com categorias estruturadas (acordos, escolares, médicos,
    viagens) e data de validade, habilitando notificação de vencimento.

-   Modo de exportação de dados por período e tipo de registro
    (PDF/ZIP/JSON/CSV).

-   Visão de produto como plataforma para múltiplas famílias, com
    personas e identidade de marca --- só faz sentido revisitar se
    houver decisão explícita de disponibilizar o sistema para terceiros.

31\. Decisões tomadas

Decisões já definidas para este projeto (registradas para não serem
reabertas sem necessidade):

-   Cálculo do calendário: sob demanda, sem materializar calendar_days.

-   Uso do sistema: pessoal por enquanto; acesso dos dois responsáveis
    ao aplicativo é objetivo futuro, não implementado no MVP.

-   Responsável por dia: único no MVP; múltiplos períodos por dia fica
    reaberto como evolução futura, sem fechar o modelo para isso.

-   Importação: PDF e DOCX desde o início.

-   Relatório mensal: finalidade organizacional, sem exigência
    documental/jurídica.

-   Autenticação: JWT + refresh token persistido (hash, expiração,
    revogação), simples para um único usuário no MVP. Reset de senha é
    script local, sem e-mail e sem endpoint público. family_access já
    comporta mais de um usuário na fase futura. guardians.user_id
    nullable liga, desde já, o login futuro ao responsável.

-   Responsável no cálculo, na exceção e na confirmação: sempre
    guardian_id, nunca nome livre.

-   Importação aprovada aponta a entidade resultante
    (resulting_entity_type + resulting_entity_id).

-   Relatório: nova versão a cada geração; snapshot em JSON; PDF derivado
    dessa versão.

-   Uma observação ativa por criança por dia.

-   Planejado vs. Realizado: entra no MVP via calendar_confirmations,
    não fica apenas como evolução futura.

-   Escopo da especificação: ajuste enxuto focado no MVP --- visão de
    plataforma, personas e marca ficam registradas como ideias futuras
    (seção 30), não desenvolvidas agora.

Decisões ainda em aberto

-   Onde os arquivos (documentos e originais de importação) serão
    armazenados em produção.

-   Política concreta de backup e retenção (periodicidade, prazo).

-   Papéis/permissões exatos em family_access quando o acesso
    compartilhado for ativado (ex: visualizador vs. editor).

32\. Ordem recomendada de implementação

-   Criar repositório e documentação.

-   Criar arquitetura base do backend.

-   Criar PostgreSQL + Docker.

-   Criar Flyway e tabelas iniciais.

-   Implementar autenticação single-user (JWT + refresh).

-   Implementar família/criança/responsáveis.

-   Implementar motor de regras (cálculo sob demanda).

-   Implementar exceções.

-   Implementar confirmações de realização (planejado/realizado).

-   Implementar observações.

-   Implementar linha do tempo.

-   Implementar auditoria.

-   Implementar relatório mensal.

-   Implementar exportação PDF.

-   Implementar documentos.

-   Implementar importação (PDF e DOCX) com detecção de conflito.

-   Adicionar IA somente depois que o domínio estiver estável.

-   Implementar acesso compartilhado (fase futura) por último, sobre a
    base já modelada em family_access.

33\. Princípio de desenvolvimento

O projeto deve ser desenvolvido incrementalmente. Cada módulo deve ser
implementado, testado e validado antes de avançar para o próximo. Evitar
mudanças estruturais grandes sem necessidade. Toda regra de negócio
importante deve possuir teste automatizado. O objetivo é chegar a um
sistema simples, confiável, auditável e fácil de evoluir.

34\. Checklist inicial

-   Aprovar modelo de dados (seção 8).

-   Aprovar arquitetura (seção 6).

-   Criar repositório.

-   Criar README.

-   Criar backlog.

-   Criar banco e Flyway.

-   Implementar primeiro caso de uso (login).

35\. Backlog inicial

  -----------------------------------------------------------------------------
  **ID**      **História**                                     **Prioridade**
  ----------- ------------------------------------------------ ----------------
  AUTH-001    Como usuário, quero fazer login.                 Alta

  FAM-001     Como usuário, quero cadastrar a criança e os     Alta
              responsáveis.                                    

  CAL-001     Como usuário, quero visualizar o mês completo.   Alta

  CAL-002     Como usuário, quero saber com quem minha filha   Alta
              ficará em cada data.                             

  CAL-003     Como usuário, quero registrar uma exceção.       Alta

  DAY-001     Como usuário, quero escrever uma observação em   Alta
              cada dia.                                        

  DAY-002     Como usuário, quero confirmar o que realmente    Alta
              aconteceu em um dia.                             

  HIS-001     Como usuário, quero visualizar a linha do tempo  Alta
              de um período.                                   

  CAL-004     Como usuário, quero pesquisar observações.       Alta

  REP-001     Como usuário, quero gerar o resumo mensal.       Alta

  DOC-001     Como usuário, quero guardar documentos.          Média

  IMP-001     Como usuário, quero importar PDF ou DOCX.        Média

  IMP-002     Como usuário, quero revisar e resolver conflitos Alta
              antes de importar.                               

  AI-001      Como usuário, quero um resumo mensal assistido   Baixa/3ª fase
              por IA.                                          
  -----------------------------------------------------------------------------
