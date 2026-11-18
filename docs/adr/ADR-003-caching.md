# ADR-003 — Cache de cotações em Redis, com chave versionada pelo plugin

**Status:** Aceito · **Data:** 02/11/2026 · **Revisado em:** 18/11/2026 (a chave passou a levar a
versão do JAR, no lugar da limpeza do cache a cada troca de plugin)

## Contexto

Cada cotação depende de uma consulta à transportadora (simulada com 300 a 600 ms). O padrão de uso é
repetitivo: no checkout de uma loja, o mesmo envio (mesma origem, destino e pacote) é cotado várias
vezes — o cliente volta ao carrinho, muda a forma de pagamento, recarrega a página.

Consultar todas as transportadoras de novo a cada repetição aumenta o tempo de resposta e o volume
de chamadas às APIs externas, que costumam ter limite de uso.

Como as regras de cada transportadora ficam no JAR do plugin (ADR-001), um plugin trocado pode
calcular valores diferentes para o mesmo envio. O cache não pode servir a resposta da regra antiga.

## Decisão

Guardar as cotações em **cache Redis**, via `@Cacheable` do Spring, **por transportadora**:

- **chave:** id da transportadora + **versão do JAR** + envio (CEPs, peso e dimensões), por exemplo
  `loggi:f53f4e52580c:13560000>01310100:2.500kg:20x30x15`;
- a **versão** é o começo do SHA-256 do conteúdo do JAR, calculada pelo `CarregadorDePlugins` ao
  carregar o plugin. Depende só dos bytes do arquivo: o mesmo JAR tem a mesma versão em qualquer
  réplica e depois de reiniciar; um JAR diferente tem outra versão;
- **nenhuma limpeza ativa:** trocar o JAR de uma transportadora gera chaves novas só para ela. As
  entradas da versão antiga deixam de ser lidas e expiram sozinhas pela validade;
- **validade:** 5 minutos, curta o bastante para que mudanças de tabela de frete feitas do lado da
  transportadora não fiquem muito tempo desatualizadas;
- respostas `INDISPONIVEL` **não entram no cache**: assim que a transportadora voltar, a próxima
  cotação é calculada de verdade;
- respostas `NAO_ATENDE` **entram**: no FreteJá a cobertura é regra do plugin, então uma mudança de
  cobertura chega como um JAR novo, com versão nova, e o "não atende" antigo deixa de ser lido na hora;
- cache por transportadora (e não da comparação inteira): se uma transportadora está fora do ar ou é
  trocada, as outras continuam sendo servidas do cache;
- se o Redis ficar indisponível, o erro de cache é registrado em log e a comparação segue sem cache.

Foi escolhido Redis, e não cache em memória, para que o cache sobreviva a reinícios do núcleo e seja
compartilhado caso o núcleo seja replicado. A versão na chave é o que torna isso verdade: reiniciar o
núcleo ou subir outra réplica recarrega os mesmos JARs, com as mesmas versões, e encontra as mesmas
chaves.

### Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Limpar o cache inteiro quando um plugin entra, sai ou é trocado (versão anterior desta decisão) | Todo reinício recarrega os JARs e zerava o cache; com réplicas, cada uma apagava o cache das outras. Na prática o cache não sobrevivia a reinício nem era compartilhado. |
| Limpar só as chaves da transportadora trocada (`SCAN` + `DEL` de `cotacoes::loggi:*`) | Funciona, mas é uma operação ativa no Redis, depende do formato da chave e ainda tem corrida entre réplicas que carregam o JAR em momentos diferentes. |
| Cache em memória no núcleo | Não sobrevive a reinício e cada réplica teria o seu. |

## Consequências

**Positivas**
- Envios repetidos respondem em poucos milissegundos, sem consultar as transportadoras.
- Menos chamadas às APIs externas das transportadoras.
- Reiniciar o núcleo ou adicionar réplicas não perde o cache; trocar um plugin só afeta as cotações
  daquela transportadora.

**Negativas**
- Um componente a mais para operar (Redis).
- Durante a validade, uma cotação pode não refletir uma mudança de tabela feita do lado da
  transportadora sem JAR novo. O mesmo vale para cobertura: se ela mudar só na API da transportadora,
  um `NAO_ATENDE` pode durar até 5 min para um CEP que passou a ser atendido. Aceitamos porque mudança
  de cobertura é rara e combinada com a transportadora, enquanto cotação repetida acontece a cada
  checkout. Se virar problema, dá para dar uma validade menor só ao `NAO_ATENDE` (o Spring Data Redis
  permite validade por entrada).
- As entradas de uma versão antiga ocupam memória até expirar (no máximo 5 min).
- Recompilar um plugin sem mudar a regra gera outro JAR e, portanto, outra versão: aquela
  transportadora perde o cache uma vez. É o lado seguro do erro.
- Os objetos guardados precisam ser serializáveis, o que vira uma exigência do contrato
  (`CotacaoDeFrete` e `Envio implements Serializable`).

## Conformidade

- Cada linha da resposta de `/api/cotacoes` informa a `origem` (`PLUGIN`, `CACHE` ou `FALHA`),
  tornando o uso do cache observável.
- `/api/transportadoras` e o painel mostram a versão de cada plugin carregado.
- `CarregadorDePluginsTest`: o mesmo conteúdo dá a mesma versão, mesmo com outro nome ou data; um
  conteúdo diferente dá outra versão.
- `/actuator/health` mostra a disponibilidade do Redis.

## Notas

Autor: Bruno .
