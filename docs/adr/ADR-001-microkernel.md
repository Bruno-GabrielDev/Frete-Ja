# ADR-001 — Microkernel com descoberta dinâmica de plugins

**Status:** Aceito · **Data:** 02/11/2026

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

Adotar **microkernel com descoberta dinâmica de plugins**:

- o **núcleo** concentra o fluxo comum (consultar, consolidar, ordenar) e conhece apenas o contrato
  `Transportadora` (módulo `plugin-api`);
- cada transportadora é um **plugin** em um JAR separado, que implementa o contrato;
- o núcleo verifica uma pasta a cada 3 s e, para cada JAR, cria um `URLClassLoader` próprio e
  descobre as implementações via `ServiceLoader`. JAR novo é carregado, JAR substituído é recarregado,
  JAR apagado é removido — sem reiniciar o núcleo;
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
- O contrato vira uma fronteira rígida: mudar `Transportadora`, `Envio` ou `CotacaoDeFrete` quebra
  todos os plugins existentes.
- Gerenciar class loaders exige cuidado (fechar o loader ao remover, evitar que o `ServiceLoader`
  enxergue classes do loader pai).

## Conformidade

Testes ArchUnit em `ArquiteturaTest`:
- o núcleo não depende de nenhuma transportadora concreta;
- o contrato não depende do núcleo;
- o mecanismo de descoberta (`nucleo.plugin`) não depende do Spring.

## Notas

Autores: Bruno G. A. Silva, Sabrina, Felipe.
