# Importação

A API já extrai o arquivo e só grava na aprovação. Esta fatia abre essa revisão.

- Enviar PDF ou DOCX da criança. Nada entra no calendário nesse envio
- A tela mostra o texto extraído e cada item reconhecido
- Linha solta fica só no texto. Não vira item
- Cada item é aprovado ou rejeitado. A conclusão manda uma decisão por item
- Sem conflito, aprovar não manda resolução
- Com conflito, aprovar exige manter, substituir ou mesclar. Mesclar só aparece na observação
- Rejeitar não manda resolução
- Segunda conclusão não é oferecida quando o status já é revisado
