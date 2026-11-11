# Coleta e itens

## Business rules

- Duplicata = mesmo `(source, external_id)`; o primeiro item é preservado.
- Entrada de fonte até 4.096 caracteres; até 1.000 itens por execução.
- Falha de persistência desfaz o lote inteiro.
- Item removido pelo admin exige motivo, gera registro de auditoria e não é reingerido em coletas futuras.

## Related

- `docs/sdd/coletas-assincronas-com-historico/spec.md`, `docs/sdd/remocao-de-itens-com-auditoria/spec.md`, `docs/sdd/bloqueio-de-reingestao/spec.md`
