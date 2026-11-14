package br.edu.ifsp.freteja.nucleo.execucao;

import br.edu.ifsp.freteja.api.CotacaoDeFrete;
import br.edu.ifsp.freteja.api.Envio;
import br.edu.ifsp.freteja.api.Transportadora;
import br.edu.ifsp.freteja.nucleo.plugin.PluginCarregado;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

public class ExecutorIsolado implements AutoCloseable {

    private final Duration tempoLimite;
    private final ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor();

    public ExecutorIsolado(Duration tempoLimite) {
        this.tempoLimite = tempoLimite;
    }

    public CotacaoDeFrete cotar(PluginCarregado plugin, Envio envio) {
        return executar(plugin, transportadora -> transportadora.cotar(envio));
    }

    private <T> T executar(PluginCarregado plugin, Function<Transportadora, T> chamada) {
        try {
            return comTempoLimite(plugin, chamada);
        } catch (FalhaDePluginException e) {
            throw new PluginIndisponivelException(e.getMessage());
        }
    }

    private <T> T comTempoLimite(PluginCarregado plugin, Function<Transportadora, T> chamada) {
        Future<T> futuro = threads.submit(() -> {
            Thread atual = Thread.currentThread();
            ClassLoader anterior = atual.getContextClassLoader();
            atual.setContextClassLoader(plugin.transportadora().getClass().getClassLoader());
            try {
                T resultado = chamada.apply(plugin.transportadora());
                if (resultado == null) {
                    throw new IllegalStateException("devolveu cotação vazia");
                }
                return resultado;
            } finally {
                atual.setContextClassLoader(anterior);
            }
        });
        try {
            return futuro.get(tempoLimite.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            futuro.cancel(true);
            throw new FalhaDePluginException(plugin.id(), "excedeu o tempo limite de " + tempoLimite.toMillis() + " ms");
        } catch (ExecutionException e) {
            throw new FalhaDePluginException(plugin.id(), "lançou " + e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FalhaDePluginException(plugin.id(), "foi interrompido");
        }
    }

    @Override
    public void close() {
        threads.shutdownNow();
    }
}
