# Limitações conhecidas do FreteJá

Uma página com as bordas da arquitetura: o que sabemos que não está coberto, o que reduz o problema
hoje e qual seria o próximo passo. Cada item aponta o ADR em que a decisão está registrada.

| Limitação | O que existe hoje | Próximo passo |
|---|---|---|
| **Isolamento só lógico** (ADR-001). Plugins rodam no processo do núcleo: um plugin pode esgotar a memória, mudar propriedades da JVM ou ocupar CPU. | Class loader por plugin e contrato mínimo; tempo limite e circuit breaker para falhas e lentidão. | Rodar cada plugin em outro processo (ou contêiner), com limite de memória e CPU. |
| **Tempo limite é da resposta, não da execução** (ADR-002). `cancel(true)` é cooperativo: um laço sem fim continua rodando. | Resposta em até 2 s; plugins em threads de plataforma, para não travar as threads virtuais do núcleo; o circuit breaker limita as threads presas (3, depois 1 a cada 20 s). | Mesmo do item acima: só outro processo pode ser encerrado à força. |
| **Confiança na pasta de plugins** (ADR-001). Quem escreve na pasta controla o núcleo; a autoria do JAR não é verificada. | Operador confiável (projeto acadêmico). | Aceitar só JARs com SHA-256 numa lista aprovada (o hash já é calculado) ou assinados com `jarsigner`. |
| **API sem autenticação e sem limite de requisições.** | Nada; uso local. | Autenticação da loja (token) e limite por cliente no gateway. |
| **Cache stampede** (ADR-003). Pedidos simultâneos com a mesma chave fria consultam todos a transportadora. | Medido: 60 consultas no primeiro segundo do teste de carga, depois nenhuma. | Juntar pedidos iguais em andamento (request coalescing). |
| **Metaspace sem monitoramento** (ADR-001). | Verificado fora do Spring: 100 e 300 recargas, nenhum class loader antigo vivo depois do GC. | `-XX:MaxMetaspaceSize`, métrica de classes carregadas e alerta. |
| **Descoberta por verificação periódica** (ADR-001). Plugin novo entra em até ~4 s, não na hora. | Escolha consciente (funciona em volume do Docker e pasta de rede); cópia atômica no `instalar.sh`. Medido: 1,5 s a 3,9 s em 30 instalações, numa máquina. | `WatchService` como atalho onde os eventos funcionam, mantendo a verificação periódica. |
| **ArchUnit verifica pacotes, não intenção.** Não pega chamada por reflexão nem um `ServiceLoader` novo fora do pacote `plugin`; não há regra de camadas dentro do núcleo. | 4 regras rodando no build; testes funcionais (`ComparadorDeFreteTest`) como complemento. | Regra de camadas `web → cotacao → execucao → plugin` e regra para `ServiceLoader`. |
| **O painel é didático, não observabilidade.** O monitor de tráfego fica em memória, por instância, sem histórico, alertas ou rastreamento. | Painel com tráfego por segundo, `/api/circuitos` e o Actuator (`health`, `info`, `caches`). | Micrometer + Prometheus + Grafana para métricas e alertas, OpenTelemetry para rastrear uma cotação do controller ao plugin. |
| **Números do teste de carga.** Cinco pacotes que se repetem dão acerto de cache alto de propósito (checkout). | A instância sem cache mostra o pior caso (0% de acerto). | Repetir com destinos variados (cauda longa) e em mais de uma máquina. |
| **"Não atende" em cache por até 5 min** se a cobertura mudar só na API da transportadora (ADR-003). | Mudança de regra no plugin gera versão nova e vale na hora. | Validade menor só para `NAO_ATENDE`. |
