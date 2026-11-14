package br.edu.ifsp.freteja.plugins.expressa;

import br.edu.ifsp.freteja.api.CotacaoDeFrete;
import br.edu.ifsp.freteja.api.Envio;
import br.edu.ifsp.freteja.api.Transportadora;

public class ExpressaInstavel implements Transportadora {

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
        try {
            Thread.sleep(5_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("consulta interrompida", e);
        }
        return CotacaoDeFrete.cotada(id(), nome(), "Expresso 24h", 1, 1, "nunca chega aqui a tempo");
    }
}
