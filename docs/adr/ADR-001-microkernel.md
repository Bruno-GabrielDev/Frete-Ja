# ADR-001 — Microkernel com descoberta dinâmica de plugins

**Situação:** Aceita

## Contexto

O FreteJá compara o frete de um envio em várias transportadoras. Cada transportadora (Correios,
Jadlog, Loggi, transportadoras regionais...) tem regras próprias: peso cúbico, limite de peso,
regiões atendidas, faixas de preço e prazo. A loja começa e deixa de trabalhar com transportadoras
com frequência.

O fluxo em volta dessas regras, por outro lado, é sempre o mesmo: receber o envio, perguntar a cada
transportadora disponível, consolidar e ordenar as respostas.

Se as regras ficassem dentro do sistema principal, cada transportadora nova exigiria alterar, testar
e reimplantar o sistema inteiro, e transportadoras descontinuadas deixariam código morto acumulado.

### Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Camadas | As regras virariam condicionais por transportadora na camada de negócio, alterada a cada transportadora nova. |
| Pipe-and-Filters | As transportadoras são alternativas consultadas lado a lado, não etapas sucessivas que transformam o mesmo dado. |
| Microsserviços | Um serviço por transportadora multiplicaria o custo operacional para regras pequenas e sem estado. |

## Decisão

Adotar **microkernel com descoberta dinâmica de plugins**.

Usamos "microkernel" no sentido de arquitetura de software de Richards e Ford (*Fundamentals of
Software Architecture*, que chama o estilo também de arquitetura de plug-ins) e da descrição deste
trabalho: um núcleo e plugins **no mesmo processo**, ligados por um contrato. Não é o microkernel de
sistema operacional (Mach, L4), que põe cada módulo em um processo com isolamento forte. Aqui o
**isolamento é lógico**: cada plugin tem o próprio class loader e só conhece o contrato, mas divide
memória, CPU e propriedades da JVM com o núcleo.

- o **núcleo** concentra o fluxo comum (consultar, consolidar, ordenar) e conhece apenas o contrato
  `Transportadora` (módulo `plugin-api`);
- cada transportadora é um **plugin** em um JAR separado, que implementa o contrato;
- o núcleo verifica uma pasta a cada 3 s e, para cada JAR, cria um `URLClassLoader` próprio e
  descobre as implementações via `ServiceLoader`. JAR novo é carregado, JAR substituído é recarregado,
  JAR apagado é removido — sem reiniciar o núcleo;
- a verificação é **periódica**, e não por eventos do sistema de arquivos (`WatchService`): eventos nem
  sempre chegam em pastas de rede (NFS) e em pastas compartilhadas com o Docker no Windows e no macOS,
  e no macOS a própria JDK implementa o `WatchService` verificando periodicamente. Um JAR modificado há
  menos de 1 s é deixado para o ciclo seguinte (pode estar sendo copiado), e o `instalar.sh` copia com
  outro nome e renomeia, o que é atômico. Por construção, um plugin entra em até ~4 s (3 s + 1 s);
  medido em 30 instalações na mesma máquina: de 1,5 s a 3,9 s, mediana de 2,8 s;
- o class loader de cada plugin delega primeiro ao do núcleo (padrão do Java). Isso é necessário:
  núcleo e plugin precisam enxergar a **mesma** interface `Transportadora`. Por isso o carregador
  aceita só implementações definidas pelo próprio JAR (o `ServiceLoader` também enxerga o loader pai);
- cada plugin carregado ganha uma **versão**, o começo do SHA-256 do JAR, que o cache usa na chave
  (ADR-003);
- o núcleo consulta todas as transportadoras **em paralelo** (uma thread virtual por transportadora),
  então a comparação demora o tempo da mais lenta, não a soma.

## Consequências

**Positivas**
- Adicionar ou remover uma transportadora não altera nem reimplanta o núcleo.
- Plugins são pequenos, independentes e testáveis isoladamente.
- A lista de transportadoras disponíveis é sempre o conjunto de plugins carregados, sem cadastro duplicado.

**Negativas**
- Código de terceiros roda dentro do processo do núcleo, e cada plugin depende de um serviço externo
  que pode falhar ou travar (tratado no ADR-002).
- Como o isolamento é só lógico, um plugin com defeito grave ou malicioso pode derrubar ou afetar o
  núcleo: esgotar a memória, mudar propriedades da JVM (`System.setProperty`) ou ocupar CPU sem parar.
- **Modelo de confiança:** quem consegue escrever na pasta de plugins controla o núcleo. Assumimos um
  operador confiável e não verificamos a autoria do JAR. O próximo passo seria aceitar só JARs cujo
  SHA-256 (que já calculamos para a versão) esteja numa lista aprovada, ou JARs assinados (`jarsigner`).
- Uma biblioteca empacotada no plugin que também exista no núcleo é resolvida pela versão do núcleo
  (efeito da delegação ao loader pai).
- Coleta dos class loaders antigos: verificamos fora do Spring (100 e 300 recargas seguidas, nenhum
  class loader antigo vivo depois do GC), mas no sistema completo não monitoramos o Metaspace. Em
  produção: limitar com `-XX:MaxMetaspaceSize` e alertar.
- Dois JARs com o mesmo id: vale o último carregado, com aviso no log. Um JAR que falha ao carregar
  fica de fora até o arquivo mudar (o erro vai para o log uma vez, não a cada 3 s).
- O contrato vira uma fronteira rígida: mudar `Transportadora`, `Envio` ou `CotacaoDeFrete` quebra
  todos os plugins existentes.
- Gerenciar class loaders exige cuidado (fechar o loader ao remover, evitar que o `ServiceLoader`
  enxergue classes do loader pai).

## Conformidade

Testes ArchUnit em `ArquiteturaTest`:
- o núcleo não depende de nenhuma transportadora concreta;
- o contrato não depende do núcleo;
- o mecanismo de descoberta (`nucleo.plugin`) não depende do Spring.

`CarregadorDePluginsTest`: a versão do plugin depende só do conteúdo do JAR. O ArchUnit verifica
dependências entre pacotes, não chamadas por reflexão; os testes funcionais (`ComparadorDeFreteTest`)
são o complemento.

## Notas

- **Autor:** Sabrina
- **Data de aprovação:** 02/11/2026, com o aceite da proposta do projeto pelo professor
- **Última modificação:** 18/11/2026 — limites do isolamento, modelo de confiança e descoberta por verificação periódica
- **ADRs relacionadas:** ADR-002 (circuit breaker) e ADR-003 (cache) completam esta decisão.
