package br.edu.ifsp.freteja.plugins.jadlog;

import br.edu.ifsp.freteja.api.CotacaoDeFrete;
import br.edu.ifsp.freteja.api.Envio;
import br.edu.ifsp.freteja.api.Transportadora;

import java.util.Locale;

public class Jadlog implements Transportadora {

    private static final int FATOR_CUBICO = 6000;
    private static final double LIMITE_KG = 100;

    @Override
    public String id() {
        return "jadlog";
    }

    @Override
    public String nome() {
        return "Jadlog";
    }

    @Override
    public CotacaoDeFrete cotar(Envio envio) {
        consultarApiDaTransportadora();
        double peso = envio.pesoTaxadoKg(FATOR_CUBICO);
        if (peso > LIMITE_KG) {
            return CotacaoDeFrete.naoAtende(id(), nome(), String.format(Locale.of("pt", "BR"), ".Package aceita até 100 kg (peso taxado: %.2f kg)", peso));
        }
        double base;
        double porKg;
        int prazo;
        switch (envio.distancia()) {
            case LOCAL -> { base = 17.50; porKg = 2.10; prazo = 2; }
            case MESMO_ESTADO -> { base = 23.00; porKg = 2.70; prazo = 4; }
            default -> { base = 31.00; porKg = 3.80; prazo = 7; }
        }
        double valor = base + Math.max(0, peso - 1) * porKg;
        String regra = String.format(Locale.of("pt", "BR"),
                ".Package: base R$ %.2f (%s) + R$ %.2f/kg acima de 1 kg; peso taxado %.2f kg",
                base, envio.distancia().descricao(), porKg, peso);
        return CotacaoDeFrete.cotada(id(), nome(), ".Package", valor, prazo, regra);
    }

    private static void consultarApiDaTransportadora() {
        try {
            Thread.sleep(400);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("consulta interrompida", e);
        }
    }
}
