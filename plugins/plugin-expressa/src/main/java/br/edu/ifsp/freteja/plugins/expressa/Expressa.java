package br.edu.ifsp.freteja.plugins.expressa;

import br.edu.ifsp.freteja.api.CotacaoDeFrete;
import br.edu.ifsp.freteja.api.Envio;
import br.edu.ifsp.freteja.api.Transportadora;

import java.util.Locale;

public class Expressa implements Transportadora {

    private static final int FATOR_CUBICO = 6000;
    private static final double LIMITE_KG = 50;

    @Override
    public String id() {
        return "expressa";
    }

    @Override
    public String nome() {
        return "Expressa Brasil";
    }

    @Override
    public CotacaoDeFrete cotar(Envio envio) {
        consultarApiDaTransportadora();
        double peso = envio.pesoTaxadoKg(FATOR_CUBICO);
        if (peso > LIMITE_KG) {
            return CotacaoDeFrete.naoAtende(id(), nome(), String.format(Locale.of("pt", "BR"), "aceita até 50 kg (peso taxado: %.2f kg)", peso));
        }
        double base;
        double porKg;
        int prazo;
        switch (envio.distancia()) {
            case LOCAL -> { base = 24.90; porKg = 3.50; prazo = 1; }
            case MESMO_ESTADO -> { base = 32.90; porKg = 4.50; prazo = 1; }
            default -> { base = 45.90; porKg = 6.90; prazo = 2; }
        }
        double valor = base + Math.max(0, peso - 1) * porKg;
        String regra = String.format(Locale.of("pt", "BR"),
                "Expresso 24h: base R$ %.2f (%s) + R$ %.2f/kg acima de 1 kg; peso taxado %.2f kg",
                base, envio.distancia().descricao(), porKg, peso);
        return CotacaoDeFrete.cotada(id(), nome(), "Expresso 24h", valor, prazo, regra);
    }

    private static void consultarApiDaTransportadora() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("consulta interrompida", e);
        }
    }
}
