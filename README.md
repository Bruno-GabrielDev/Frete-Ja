# FreteJá — Microkernel com descoberta dinâmica de plugins

Trabalho prático de Arquitetura de Software (IFSP São Carlos — Prof. Dr. Lucas Oliveira).

**Equipe:** Bruno G. A. Silva, Sabrina, Felipe

Comparador de frete para lojas virtuais: a loja informa CEP de origem, CEP de destino, peso e
dimensões do pacote, e o sistema devolve as cotações de várias transportadoras (valor e prazo),
ordenadas pelo menor preço. **Cada transportadora tem regras próprias, entra e sai com frequência e
depende de um serviço externo que pode cair** — por isso cada transportadora é um plugin.

| | |
|---|---|
| **Estilo arquitetural** | Microkernel com descoberta dinâmica de plugins |
| **Elemento adicional 1** | Circuit Breaking (Resilience4j), um circuito por transportadora |
| **Elemento adicional 2** | Caching (Redis) das cotações, por transportadora + envio |
| **Stack** | Java 21, Spring Boot 3.3, Resilience4j 2.2, Redis 7, Docker |

> As regras e os valores das transportadoras são **fictícios** e a "consulta à API" é simulada com
> uma espera de 300 a 600 ms. Não há integração real com nenhuma transportadora.

![Painel](docs/painel.png)

---

## Como a arquitetura aparece no código

```
freteja/
├── plugin-api/          CONTRATO — única coisa que núcleo e plugins compartilham
│   ├── Transportadora        interface: id(), nome(), cotar(Envio)
│   ├── Envio                 CEPs, peso, dimensões; distância, peso cúbico, chave de cache
│   └── CotacaoDeFrete        COTADA / NAO_ATENDE / INDISPONIVEL, valor, prazo
│
├── nucleo/              NÚCLEO — fluxo comum, não sabe calcular frete
│   ├── plugin/               descoberta dinâmica (Java puro, sem Spring)
│   │   ├── CarregadorDePlugins   observa a pasta, carrega/troca/remove JARs em execução
│   │   └── RegistroDePlugins     "quais transportadoras estão disponíveis agora?"
│   ├── execucao/
│   │   └── ExecutorIsolado       ÚNICO ponto que chama plugin: circuit breaker + tempo limite
│   ├── cotacao/
│   │   ├── ComparadorDeFrete     consulta todas em paralelo e ordena
│   │   ├── CotadorDeTransportadora  cotação de uma transportadora + @Cacheable
│   │   └── InvalidadorDeCache    limpa o cache quando um plugin muda
│   ├── config/               montagem (Spring) e verificação periódica da pasta
│   └── web/                  API REST + painel em /index.html
│
└── plugins/             PLUGINS — cada um é um JAR independente
    ├── plugin-correios         PAC, até 30 kg
    ├── plugin-jadlog           mais barata para pacotes pesados, até 100 kg
    ├── plugin-loggi            a mais barata, mas só dentro do mesmo estado
    ├── plugin-expressa-instavel   API "fora do ar": trava de propósito (demonstra o circuit breaker)
    └── plugin-expressa         versão corrigida (demonstra troca de versão em execução)
```

**Como uma comparação acontece:**
1. O painel (ou a loja) envia `POST /api/cotacoes` com o envio.
2. O `ComparadorDeFrete` pega no `RegistroDePlugins` as transportadoras carregadas e dispara **uma
   thread virtual por transportadora**: a resposta demora o tempo da mais lenta, não a soma.
3. Para cada uma, o `CotadorDeTransportadora` procura no cache (`id + envio`). Se não houver, chama
   o `ExecutorIsolado`, que passa pelo **circuit breaker** daquela transportadora e executa `cotar()`
   com **tempo limite de 2 s**.
4. Falha, demora ou circuito aberto viram `INDISPONIVEL` só para aquela transportadora; as outras
   aparecem normalmente.
5. O resultado volta ordenado: cotadas (menor preço, depois menor prazo), depois "não atende", depois
   indisponíveis. Cada linha informa a origem: `PLUGIN`, `CACHE` ou `FALHA`.

**Como um plugin é descoberto:** o núcleo verifica `plugins-ativos/` a cada 3 s. Para cada JAR novo,
cria um `URLClassLoader` só para ele e usa `ServiceLoader` para achar as classes que implementam
`Transportadora` (declaradas em `META-INF/services/`). JAR apagado → plugin removido. JAR substituído →
versão nova carregada, circuito zerado e cache limpo. Nada disso exige reiniciar.

**Restrições do estilo, verificadas no build** (`ArquiteturaTest`, ArchUnit):
1. o núcleo nunca depende de uma transportadora concreta;
2. o contrato não depende do núcleo;
3. nenhuma classe fora de `execucao` chama `cotar()` de uma transportadora — toda chamada passa pelo circuit breaker;
4. o mecanismo de descoberta de plugins não depende do Spring.

---

## Como rodar

Pré-requisitos: **JDK 21** e **Maven 3.9+**. Docker é opcional.

### 1. Compilar

```bash
./scripts/build.sh
```

Compila, roda os testes, coloca todos os plugins em `plugins-disponiveis/` e ativa **Correios** e
**Jadlog** em `plugins-ativos/`.

### 2a. Rodar com Docker (núcleo + Redis)

```bash
docker compose up --build
```

> Docker Desktop no Linux: se aparecer `failed to connect to the docker API at unix:///var/run/docker.sock`,
> abra o Docker Desktop e rode `docker context use desktop-linux`.

### 2b. Rodar sem Docker (cache em memória, sem Redis)

```bash
java -jar nucleo/target/nucleo.jar --spring.profiles.active=local
```

Abra **http://localhost:8080**.

---

## Roteiro da demonstração (~5 min)

Painel aberto no navegador e um terminal ao lado. Valores com o envio padrão do painel
(São Carlos → São Paulo, 2,5 kg, 20 × 30 × 15 cm).

**1. Estado inicial.** O painel mostra *Correios* e *Jadlog*. Clique em **Comparar fretes** →
Correios R$ 26,05 (menor preço) e Jadlog R$ 27,05, origem `PLUGIN`, ~600 ms.

**2. Cache.** Clique de novo → as duas com origem `CACHE`, poucos ms.

**3. Descoberta dinâmica.** Com o sistema rodando:
```bash
./scripts/instalar.sh loggi
```
Em poucos segundos *Loggi* aparece na lista (linha destacada). Compare de novo → Loggi R$ 19,80 vira
o **menor preço**. Nenhum restart. Troque o destino para o Rio (`20040-020`) → Loggi responde
**NÃO ATENDE** (só entrega dentro do estado) e Jadlog passa a ser a mais barata.

**4. Circuit breaker.** Volte o destino para `01310-100` e instale a transportadora com defeito:
```bash
./scripts/instalar.sh expressa-instavel
```
Compare três vezes: cada comparação leva ~2 s (tempo limite) e a Expressa aparece **INDISPONÍVEL**,
enquanto as outras continuam cotando. Na terceira, o circuito da `expressa` fica **ABERTO**. A
quarta comparação volta em poucos ms: o núcleo nem consulta a Expressa. *Uma transportadora fora do
ar não derruba a comparação.*

**5. Correção com o sistema no ar.**
```bash
./scripts/remover.sh expressa-instavel
./scripts/instalar.sh expressa
```
A versão corrigida é carregada, o circuito é zerado e a Expressa volta cotando (R$ 39,65, 1 dia útil).

Pela linha de comando, sem o painel: `./scripts/cotar.sh 13560-000 01310-100 2.5 20 30 15`.

---

## API

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/api/transportadoras` | transportadoras disponíveis = plugins carregados, com estado do circuito |
| `POST` | `/api/cotacoes` | `{"cepOrigem","cepDestino","pesoKg","alturaCm","larguraCm","comprimentoCm"}` → UFs, distância, cotações ordenadas e `tempoMs` |
| `GET` | `/api/circuitos` | estado, falhas, sucessos e chamadas recusadas de cada circuit breaker |
| `GET` | `/actuator/health` | saúde do núcleo e do Redis |

Cada cotação traz `situacao` (`COTADA`, `NAO_ATENDE`, `INDISPONIVEL`) e `origem` (`PLUGIN`, `CACHE`, `FALHA`).
CEPs aceitam `13560-000` ou `13560000`.

---

## Como criar uma transportadora nova

1. Crie um módulo Maven com dependência `plugin-api` em escopo `provided`.
2. Implemente `Transportadora`. Se não fizer o envio, devolva `CotacaoDeFrete.naoAtende(...)` em vez de lançar exceção.
3. Declare a classe em `src/main/resources/META-INF/services/br.edu.ifsp.freteja.api.Transportadora`.
4. `mvn package` e copie o JAR para `plugins-ativos/`.

O núcleo não é recompilado nem reiniciado. Use `plugins/plugin-loggi` como modelo.

---

## Configuração (`nucleo/src/main/resources/application.yml`)

| Propriedade | Padrão | Efeito |
|---|---|---|
| `plugins.diretorio` | `./plugins-ativos` | pasta observada (env `PLUGINS_DIR`) |
| `plugins.intervalo-ms` | `3000` | intervalo de verificação da pasta |
| `plugins.tempo-limite-ms` | `2000` | acima disso a consulta conta como falha |
| `circuito.minimo-de-chamadas` | `3` | chamadas antes de o circuito poder abrir |
| `circuito.taxa-de-falha-percentual` | `50` | taxa de falha que abre o circuito |
| `circuito.tempo-aberto-segundos` | `20` | tempo aberto antes de testar a transportadora de novo |
| `spring.cache.redis.time-to-live` | `5m` | validade de uma cotação em cache |

## Testes

```bash
mvn test
```

- `ArquiteturaTest` — as quatro restrições do estilo (ArchUnit).
- `ComparadorDeFreteTest` — ordenação, consulta em paralelo, transportadora travada vira indisponível,
  circuito abre e para de consultar, as outras continuam cotando, recarregar o plugin zera o circuito.
- `EnvioTest` — CEP, distância, peso cúbico e chave de cache.

---

## Documentação

- `docs/adr/` — os três ADRs (microkernel, circuit breaking, caching)
- `docs/c4/` — modelo C4 (contexto, containers e componentes) em PlantUML, com as imagens já geradas
