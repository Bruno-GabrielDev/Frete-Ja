# ADR-002 — Circuit breaker por transportadora

**Situação:** Aceita

## Contexto

Este ADR completa a escolha do estilo (ADR-001): o Microkernel tem baixa tolerância a falhas, porque
os plugins rodam no mesmo processo do núcleo, e este elemento adicional cobre essa fraqueza.

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
- um **tempo limite de resposta** de 2 s por consulta, executada em outra thread: estourar o tempo
  conta como falha. O código do plugin roda em **threads de plataforma**, e não virtuais (ver
  Consequências);
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
- **O tempo limite é da resposta, não da execução.** Quem chama recebe a resposta em até 2 s, mas o
  `cancel(true)` só interrompe a thread, e o Java não tem como parar à força um código que não verifica
  a interrupção. Um plugin preso em cálculo (um laço sem fim) continua rodando e ocupando CPU. O circuit
  breaker limita o acúmulo: até 3 threads presas antes de o circuito abrir e mais 1 a cada teste do
  meio-aberto (20 s). Remover o plugin não para essas threads. Parar de verdade exigiria rodar cada
  plugin em outro processo, fora do escopo deste projeto.
- Por isso o código dos plugins roda em threads de plataforma. Uma thread virtual presa em cálculo não
  devolve a thread do sistema em que está montada; com poucas presas, nenhuma outra thread virtual do
  núcleo roda, nem as do comparador. Reproduzimos isso: numa máquina com 2 núcleos de CPU, 2 plugins
  presos impediram qualquer outra thread virtual de rodar; com threads de plataforma, a cotação seguinte
  respondeu em dezenas de milissegundos. O custo é uma thread do sistema por consulta em andamento.

## Conformidade

- ArchUnit (`ArquiteturaTest`): nenhuma classe fora do pacote `execucao` chama `cotar()` de uma
  transportadora — não existe caminho que contorne o circuit breaker.
- `ComparadorDeFreteTest`: transportadora travada vira `INDISPONIVEL` pelo tempo limite; o circuito
  abre após falhas repetidas e para de consultá-la; as outras continuam cotando; recarregar o plugin
  zera o circuito.

## Notas

- **Autor:** Filipe
- **Data de aprovação:** 02/11/2026, com o aceite da proposta do projeto pelo professor
- **Última modificação:** 18/11/2026 — o tempo limite passou a ser descrito como tempo limite de resposta, e o código dos plugins passou a rodar em threads de plataforma
- **ADRs relacionadas:** Depende do ADR-001.
