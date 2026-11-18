# ADR-002 — Circuit breaker por transportadora

**Status:** Aceito · **Data:** 02/11/2026

## Contexto

Cada transportadora calcula o frete consultando um serviço externo, que pode ficar lento ou fora do
ar. Pelo ADR-001, o código de cada transportadora roda dentro do processo do núcleo, e a comparação
consulta todas elas a cada pedido.

Sem proteção, uma transportadora travada atrasa **todas** as comparações (a resposta espera a mais
lenta) e, sob carga, prende threads do núcleo. Continuar consultando um serviço que já se sabe fora
do ar só piora o tempo de resposta.

## Decisão

Toda consulta a uma transportadora passa por um único componente, o `ExecutorIsolado`, que aplica:

- um **circuit breaker por transportadora** (Resilience4j), nomeado pelo id dela. Com 3 chamadas
  avaliadas e 50% de falhas, o circuito **abre** e o núcleo deixa de consultar aquela transportadora
  por 20 s; depois passa a **meio-aberto** e testa uma chamada antes de fechar de novo;
- um **tempo limite** de 2 s por consulta, executada em outra thread: estourar o tempo conta como falha;
- um **fallback**: a transportadora com falha aparece na comparação como `INDISPONIVEL`, e as demais
  são exibidas normalmente, em vez de a comparação inteira falhar.

Ao carregar uma nova versão do plugin, o circuito daquela transportadora é zerado.

## Consequências

**Positivas**
- Uma transportadora fora do ar não impede a cotação com as outras.
- Com o circuito aberto, a comparação volta a responder rápido em vez de esperar o tempo limite a
  cada pedido.
- O estado de cada circuito fica visível (`/api/circuitos` e painel), o que facilita identificar a
  transportadora com problema.

**Negativas**
- Mais um mecanismo para configurar: limites mal calibrados abrem o circuito cedo ou tarde demais.
- Enquanto o circuito está aberto, a transportadora fica fora da comparação mesmo que já tenha voltado
  (até o teste do estado meio-aberto).
- O tempo limite interrompe a thread, mas não garante que um plugin mal escrito pare de consumir CPU.
  Isolamento total exigiria rodar plugins em processo separado, fora do escopo deste projeto.

## Conformidade

- ArchUnit (`ArquiteturaTest`): nenhuma classe fora do pacote `execucao` chama `cotar()` de uma
  transportadora — não existe caminho que contorne o circuit breaker.
- `ComparadorDeFreteTest`: transportadora travada vira `INDISPONIVEL` pelo tempo limite; o circuito
  abre após falhas repetidas e para de consultá-la; as outras continuam cotando; recarregar o plugin
  zera o circuito.

## Notas

Depende do ADR-001. Autor: Filipe.
