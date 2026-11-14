package br.edu.ifsp.freteja.nucleo.cotacao;

import br.edu.ifsp.freteja.api.CotacaoDeFrete;
import br.edu.ifsp.freteja.api.Envio;
import br.edu.ifsp.freteja.nucleo.plugin.PluginCarregado;
import org.springframework.stereotype.Service;

@Service
public class CotadorDeTransportadora {

    public CotacaoDeFrete cotar(PluginCarregado plugin, Envio envio) {
        try {
            return plugin.transportadora().cotar(envio);
        } catch (RuntimeException e) {
            return CotacaoDeFrete.indisponivel(plugin.id(), plugin.nome(), "erro na transportadora: " + e.getMessage());
        }
    }
}
