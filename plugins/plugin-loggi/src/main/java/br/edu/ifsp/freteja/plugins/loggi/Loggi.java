package br.edu.ifsp.freteja.plugins.loggi;

import br.edu.ifsp.freteja.api.CotacaoDeFrete;
import br.edu.ifsp.freteja.api.Distancia;
import br.edu.ifsp.freteja.api.Envio;
import br.edu.ifsp.freteja.api.Transportadora;

import java.util.Locale;

public class Loggi implements Transportadora {

    private static final int FATOR_CUBICO = 6000;
    private static final double LIMITE_KG = 20;

    @Override
    public String id() {
        return "loggi";
    }

    @Override
    public String nome() {
        return "Loggi";
    }

    @Override
    public CotacaoDeFrete cotar(Envio envio) {
        consultarApiDaTransportadora();
        if (envio.distancia() == Distancia.OUTRO_ESTADO) {
            return CotacaoDeFrete.naoAtende(id(), nome(), "atende apenas entregas dentro do mesmo estado");
        }
        double peso = envio.pesoTaxadoKg(FATOR_CUBICO);
        if (peso > LIMITE_KG) {
            return CotacaoDeFrete.naoAtende(id(), nome(), String.format(Locale.of("pt", "BR"), "aceita até 20 kg (peso taxado: %.2f kg)", peso));
        }
        boolean local = envio.distancia() == Distancia.LOCAL;
        double base = local ? 9.90 : 15.90;
        double porKg = local ? 1.80 : 2.60;
        int prazo = local ? 1 : 2;
        double valor = base + Math.max(0, peso - 1) * porKg;
        String regra = String.format(Locale.of("pt", "BR"),
                "Loggi Express: base R$ %.2f (%s) + R$ %.2f/kg acima de 1 kg; peso taxado %.2f kg",
                base, envio.distancia().descricao(), porKg, peso);
        return CotacaoDeFrete.cotada(id(), nome(), "Loggi Express", valor, prazo, regra);
    }

    private static void consultarApiDaTransportadora() {
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("consulta interrompida", e);
        }
    }
}
