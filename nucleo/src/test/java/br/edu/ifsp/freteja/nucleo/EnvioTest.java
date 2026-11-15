package br.edu.ifsp.freteja.nucleo;

import br.edu.ifsp.freteja.api.Distancia;
import br.edu.ifsp.freteja.api.Envio;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class EnvioTest {

    @Test
    void normalizaCepComOuSemTraco() {
        Envio envio = new Envio("13560-000", "01310100", 1, 10, 10, 10);

        assertThat(envio.cepOrigem()).isEqualTo("13560000");
        assertThat(envio.cepDestino()).isEqualTo("01310100");
        assertThat(envio.ufOrigem()).isEqualTo("SP");
    }

    @Test
    void calculaDistanciaPelosCeps() {
        assertThat(new Envio("13560-000", "13566-590", 1, 10, 10, 10).distancia()).isEqualTo(Distancia.LOCAL);
        assertThat(new Envio("13560-000", "01310-100", 1, 10, 10, 10).distancia()).isEqualTo(Distancia.MESMO_ESTADO);
        assertThat(new Envio("13560-000", "20040-020", 1, 10, 10, 10).distancia()).isEqualTo(Distancia.OUTRO_ESTADO);
    }

    @Test
    void pesoTaxadoEhOMaiorEntreRealECubico() {
        Envio caixaGrandeELeve = new Envio("13560-000", "01310-100", 1, 40, 30, 30);

        assertThat(caixaGrandeELeve.pesoCubicoKg(6000)).isCloseTo(6.0, within(0.001));
        assertThat(caixaGrandeELeve.pesoTaxadoKg(6000)).isCloseTo(6.0, within(0.001));
    }

    @Test
    void enviosIguaisTemAMesmaChave() {
        Envio a = new Envio("13560-000", "01310-100", 2.5, 20, 30, 15);
        Envio b = new Envio("13560000", "01310100", 2.5, 20, 30, 15);

        assertThat(a.chave()).isEqualTo(b.chave());
    }

    @Test
    void recusaDadosInvalidos() {
        assertThatThrownBy(() -> new Envio("1356", "01310-100", 1, 10, 10, 10))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("CEP de origem");
        assertThatThrownBy(() -> new Envio("13560-000", "01310-100", 0, 10, 10, 10))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("peso");
        assertThatThrownBy(() -> new Envio("13560-000", "01310-100", 1, 0, 10, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
