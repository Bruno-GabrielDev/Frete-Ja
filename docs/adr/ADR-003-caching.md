# ADR-003 — Cache de cotações em Redis

**Status:** Aceito · **Data:** 02/11/2026

## Contexto

Cada cotação depende de uma consulta à transportadora (simulada com 300 a 600 ms). O padrão de uso é
repetitivo: no checkout de uma loja, o mesmo envio (mesma origem, destino e pacote) é cotado várias
vezes — o cliente volta ao carrinho, muda a forma de pagamento, recarrega a página.

Consultar todas as transportadoras de novo a cada repetição aumenta o tempo de resposta e o volume
de chamadas às APIs externas, que costumam ter limite de uso.

## Decisão

Guardar as cotações em **cache Redis**, via `@Cacheable` do Spring, **por transportadora**:

- **chave:** id da transportadora + envio (CEP de origem, CEP de destino, peso e dimensões);
- **validade:** 5 minutos, curta o bastante para que mudanças de tabela de frete não fiquem
  desatualizadas;
- respostas `INDISPONIVEL` **não entram no cache** — assim que a transportadora voltar, a próxima
  cotação é calculada de verdade. Respostas `NAO_ATENDE` entram, porque dependem só das regras;
- cache por transportadora (e não da comparação inteira): se uma transportadora está fora do ar, as
  outras continuam sendo servidas do cache;
- quando um plugin é carregado, trocado ou removido, o cache é **limpo**, para não servir valores
  calculados por regras que não existem mais;
- se o Redis ficar indisponível, o erro de cache é registrado em log e a comparação segue sem cache.

Foi escolhido Redis, e não cache em memória, para que o cache sobreviva a reinícios do núcleo e possa
ser compartilhado caso o núcleo seja replicado.

## Consequências

**Positivas**
- Envios repetidos respondem em poucos milissegundos, sem consultar as transportadoras.
- Menos chamadas às APIs externas das transportadoras.

**Negativas**
- Um componente a mais para operar (Redis).
- Durante a validade, uma cotação pode não refletir uma mudança de tabela da transportadora.
- Os objetos guardados precisam ser serializáveis, o que vira uma exigência do contrato
  (`CotacaoDeFrete` e `Envio implements Serializable`).

## Conformidade

- Cada linha da resposta de `/api/cotacoes` informa a `origem` (`PLUGIN`, `CACHE` ou `FALHA`),
  tornando o uso do cache observável.
- `/actuator/health` mostra a disponibilidade do Redis.

## Notas

Autor: Bruno .
