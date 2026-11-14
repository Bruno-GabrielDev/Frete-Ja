package br.edu.ifsp.freteja.nucleo.cotacao;

import br.edu.ifsp.freteja.api.CotacaoDeFrete;
import br.edu.ifsp.freteja.api.Envio;
import br.edu.ifsp.freteja.nucleo.plugin.PluginCarregado;
import br.edu.ifsp.freteja.nucleo.plugin.RegistroDePlugins;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
public class ComparadorDeFrete {

    private static final Comparator<CotacaoDeFrete> ORDEM =
            Comparator.comparing(CotacaoDeFrete::situacao)
                    .thenComparing(CotacaoDeFrete::valor, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(CotacaoDeFrete::prazoDias, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(CotacaoDeFrete::transportadoraId);

    private final RegistroDePlugins registro;
    private final CotadorDeTransportadora cotador;

    public ComparadorDeFrete(RegistroDePlugins registro, CotadorDeTransportadora cotador) {
        this.registro = registro;
        this.cotador = cotador;
    }

    public List<CotacaoDeFrete> comparar(Envio envio) {
        List<PluginCarregado> transportadoras = registro.listar();
        List<CotacaoDeFrete> cotacoes = new ArrayList<>();

        try (ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<CotacaoDeFrete>> futuros = transportadoras.stream()
                    .map(p -> threads.submit(() -> cotador.cotar(p, envio)))
                    .toList();
            for (int i = 0; i < futuros.size(); i++) {
                cotacoes.add(aguardar(futuros.get(i), transportadoras.get(i)));
            }
        }

        cotacoes.sort(ORDEM);
        return cotacoes;
    }

    private static CotacaoDeFrete aguardar(Future<CotacaoDeFrete> futuro, PluginCarregado plugin) {
        try {
            return futuro.get();
        } catch (ExecutionException e) {
            return CotacaoDeFrete.indisponivel(plugin.id(), plugin.nome(), "erro inesperado: " + e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CotacaoDeFrete.indisponivel(plugin.id(), plugin.nome(), "consulta interrompida");
        }
    }
}
