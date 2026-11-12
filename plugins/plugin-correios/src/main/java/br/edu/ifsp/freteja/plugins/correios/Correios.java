package br.edu.ifsp.freteja.plugins.correios;

import br.edu.ifsp.freteja.api.CotacaoDeFrete;
import br.edu.ifsp.freteja.api.Envio;
import br.edu.ifsp.freteja.api.Transportadora;

import java.util.Locale;

public class Correios implements Transportadora {

    private static final int FATOR_CUBICO = 6000;
    private static final double LIMITE_KG = 30;

    @Override
    public String id() {
        return "correios";
    }

    @Override
    public String nome() {
        return "Correios";
    }

    @Override
    public CotacaoDeFrete cotar(Envio envio) {
        consultarApiDaTransportadora();
        double peso = envio.pesoTaxadoKg(FATOR_CUBICO);
        if (peso > LIMITE_KG) {
            return CotacaoDeFrete.naoAtende(id(), nome(), String.format(Locale.of("pt", "BR"), "PAC aceita até 30 kg (peso taxado: %.2f kg)", peso));
        }
        double base;
        double porKg;
        int prazo;
        switch (envio.distancia()) {
            case LOCAL -> { base = 14.90; porKg = 3.20; prazo = 3; }
            case MESMO_ESTADO -> { base = 19.90; porKg = 4.10; prazo = 5; }
            default -> { base = 29.90; porKg = 6.30; prazo = 8; }
        }
        double valor = base + Math.max(0, peso - 1) * porKg;
        String regra = String.format(Locale.of("pt", "BR"),
                "PAC: base R$ %.2f (%s) + R$ %.2f/kg acima de 1 kg; peso taxado %.2f kg",
                base, envio.distancia().descricao(), porKg, peso);
        return CotacaoDeFrete.cotada(id(), nome(), "PAC", valor, prazo, regra);
    }

    private static void consultarApiDaTransportadora() {
        try {
            Thread.sleep(600);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("consulta interrompida", e);
        }
    }
}
