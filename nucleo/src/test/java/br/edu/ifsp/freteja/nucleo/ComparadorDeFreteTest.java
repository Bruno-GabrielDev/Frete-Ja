package br.edu.ifsp.freteja.nucleo;

import br.edu.ifsp.freteja.api.CotacaoDeFrete;
import br.edu.ifsp.freteja.api.Envio;
import br.edu.ifsp.freteja.api.Situacao;
import br.edu.ifsp.freteja.api.Transportadora;
import br.edu.ifsp.freteja.nucleo.cotacao.ComparadorDeFrete;
import br.edu.ifsp.freteja.nucleo.cotacao.CotadorDeTransportadora;
import br.edu.ifsp.freteja.nucleo.execucao.ExecutorIsolado;
import br.edu.ifsp.freteja.nucleo.plugin.PluginCarregado;
import br.edu.ifsp.freteja.nucleo.plugin.RegistroDePlugins;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ComparadorDeFreteTest {

    private final Envio envio = new Envio("13560-000", "01310-100", 2.0, 10, 10, 10);

    private RegistroDePlugins registro;
    private ExecutorIsolado executor;
    private ComparadorDeFrete comparador;

    @BeforeEach
    void preparar() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(4)
                .minimumNumberOfCalls(3)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .build();
        registro = new RegistroDePlugins();
        executor = new ExecutorIsolado(CircuitBreakerRegistry.of(config), Duration.ofMillis(200));
        comparador = new ComparadorDeFrete(registro, new CotadorDeTransportadora(executor));
    }

    @AfterEach
    void encerrar() {
        executor.close();
    }

    private void instalar(Transportadora transportadora) {
        registro.registrar(new PluginCarregado(transportadora, transportadora.id() + ".jar", "teste", Instant.now()));
    }

    @Test
    void ordenaPorMenorPrecoDepoisNaoAtendeDepoisIndisponivel() {
        instalar(new Fixa("cara", 40));
        instalar(new Fixa("barata", 20));
        instalar(new NaoAtende());
        instalar(new Travada());

        List<CotacaoDeFrete> cotacoes = comparador.comparar(envio);

        assertThat(cotacoes).extracting(CotacaoDeFrete::transportadoraId)
                .containsExactly("barata", "cara", "nao-atende", "travada");
        assertThat(cotacoes).extracting(CotacaoDeFrete::situacao)
                .containsExactly(Situacao.COTADA, Situacao.COTADA, Situacao.NAO_ATENDE, Situacao.INDISPONIVEL);
    }

    @Test
    void transportadoraTravadaViraIndisponivelPorTempoLimite() {
        instalar(new Travada());

        CotacaoDeFrete cotacao = comparador.comparar(envio).get(0);

        assertThat(cotacao.situacao()).isEqualTo(Situacao.INDISPONIVEL);
        assertThat(cotacao.detalhe()).contains("tempo limite");
    }

    @Test
    void consultaEmParaleloDemoraOTempoDaMaisLentaENaoASoma() {
        instalar(new Fixa("a", 10, 150));
        instalar(new Fixa("b", 10, 150));
        instalar(new Fixa("c", 10, 150));

        long inicio = System.nanoTime();
        comparador.comparar(envio);
        long duracaoMs = (System.nanoTime() - inicio) / 1_000_000;

        assertThat(duracaoMs).isLessThan(400);
    }

    @Test
    void circuitoAbreDepoisDeFalhasRepetidasEParaDeConsultar() {
        instalar(new Travada());
        for (int i = 0; i < 3; i++) {
            comparador.comparar(envio);
        }

        long inicio = System.nanoTime();
        CotacaoDeFrete cotacao = comparador.comparar(envio).get(0);
        long duracaoMs = (System.nanoTime() - inicio) / 1_000_000;

        assertThat(executor.estadoDe("travada")).isEqualTo("OPEN");
        assertThat(cotacao.detalhe()).contains("circuito aberto");
        assertThat(duracaoMs).as("com o circuito aberto a transportadora nem é consultada").isLessThan(100);
    }

    @Test
    void transportadoraComDefeitoNaoAfetaAsOutras() {
        instalar(new Fixa("funciona", 25));
        instalar(new Travada());
        for (int i = 0; i < 4; i++) {
            comparador.comparar(envio);
        }

        CotacaoDeFrete melhor = comparador.comparar(envio).get(0);

        assertThat(melhor.transportadoraId()).isEqualTo("funciona");
        assertThat(melhor.situacao()).isEqualTo(Situacao.COTADA);
        assertThat(executor.estadoDe("funciona")).isEqualTo("CLOSED");
    }

    @Test
    void recarregarOPluginZeraOCircuito() {
        instalar(new Travada());
        for (int i = 0; i < 4; i++) {
            comparador.comparar(envio);
        }
        assertThat(executor.estadoDe("travada")).isEqualTo("OPEN");

        executor.pluginCarregado("travada");

        assertThat(executor.estadoDe("travada")).isEqualTo("CLOSED");
    }

    @Test
    void semTransportadorasDevolveListaVazia() {
        assertThat(comparador.comparar(envio)).isEmpty();
    }

    static class Fixa implements Transportadora {
        private final String id;
        private final double valor;
        private final long demoraMs;

        Fixa(String id, double valor) { this(id, valor, 0); }
        Fixa(String id, double valor, long demoraMs) { this.id = id; this.valor = valor; this.demoraMs = demoraMs; }

        public String id() { return id; }
        public String nome() { return id; }
        public CotacaoDeFrete cotar(Envio e) {
            dormir(demoraMs);
            return CotacaoDeFrete.cotada(id, id, "padrão", valor, 3, "fixa");
        }
    }

    static class NaoAtende implements Transportadora {
        public String id() { return "nao-atende"; }
        public String nome() { return "Não atende"; }
        public CotacaoDeFrete cotar(Envio e) { return CotacaoDeFrete.naoAtende(id(), nome(), "fora da área"); }
    }

    static class Travada implements Transportadora {
        public String id() { return "travada"; }
        public String nome() { return "Travada"; }
        public CotacaoDeFrete cotar(Envio e) {
            dormir(5_000);
            return CotacaoDeFrete.cotada(id(), nome(), "padrão", 1, 1, "nunca a tempo");
        }
    }

    static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
