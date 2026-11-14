package br.edu.ifsp.freteja.nucleo.cotacao;

import br.edu.ifsp.freteja.api.CotacaoDeFrete;
import br.edu.ifsp.freteja.api.Envio;
import br.edu.ifsp.freteja.nucleo.execucao.ExecutorIsolado;
import br.edu.ifsp.freteja.nucleo.execucao.PluginIndisponivelException;
import br.edu.ifsp.freteja.nucleo.plugin.PluginCarregado;
import org.springframework.stereotype.Service;

@Service
public class CotadorDeTransportadora {

    private final ExecutorIsolado executor;

    public CotadorDeTransportadora(ExecutorIsolado executor) {
        this.executor = executor;
    }

    public CotacaoDeFrete cotar(PluginCarregado plugin, Envio envio) {
        try {
            return executor.cotar(plugin, envio);
        } catch (PluginIndisponivelException e) {
            return CotacaoDeFrete.indisponivel(plugin.id(), plugin.nome(), e.getMessage());
        }
    }
}
