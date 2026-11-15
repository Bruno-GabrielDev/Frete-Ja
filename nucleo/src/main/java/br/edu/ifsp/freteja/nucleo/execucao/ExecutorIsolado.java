package br.edu.ifsp.freteja.nucleo.execucao;

import br.edu.ifsp.freteja.api.CotacaoDeFrete;
import br.edu.ifsp.freteja.api.Envio;
import br.edu.ifsp.freteja.api.Transportadora;
import br.edu.ifsp.freteja.nucleo.plugin.OuvinteDePlugins;
import br.edu.ifsp.freteja.nucleo.plugin.PluginCarregado;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

public class ExecutorIsolado implements OuvinteDePlugins, AutoCloseable {

    private final CircuitBreakerRegistry circuitos;
    private final Duration tempoLimite;
    private final ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor();

    public ExecutorIsolado(CircuitBreakerRegistry circuitos, Duration tempoLimite) {
        this.circuitos = circuitos;
        this.tempoLimite = tempoLimite;
    }

    public CotacaoDeFrete cotar(PluginCarregado plugin, Envio envio) {
        return executar(plugin, transportadora -> transportadora.cotar(envio));
    }

    private <T> T executar(PluginCarregado plugin, Function<Transportadora, T> chamada) {
        CircuitBreaker circuito = circuitos.circuitBreaker(plugin.id());
        try {
            return circuito.executeSupplier(() -> comTempoLimite(plugin, chamada));
        } catch (CallNotPermittedException e) {
            throw new PluginIndisponivelException(
                    "circuito aberto para '" + plugin.id() + "': chamadas suspensas temporariamente", true);
        } catch (FalhaDePluginException e) {
            throw new PluginIndisponivelException(e.getMessage(), false);
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

    public List<EstadoDoCircuito> estados() {
        return circuitos.getAllCircuitBreakers().stream()
                .map(cb -> new EstadoDoCircuito(
                        cb.getName(),
                        cb.getState().name(),
                        cb.getMetrics().getFailureRate(),
                        cb.getMetrics().getNumberOfFailedCalls(),
                        cb.getMetrics().getNumberOfSuccessfulCalls(),
                        cb.getMetrics().getNumberOfNotPermittedCalls()))
                .sorted(Comparator.comparing(EstadoDoCircuito::plugin))
                .toList();
    }

    public String estadoDe(String pluginId) {
        return circuitos.getAllCircuitBreakers().stream()
                .filter(cb -> cb.getName().equals(pluginId))
                .map(cb -> cb.getState().name())
                .findFirst()
                .orElse("CLOSED");
    }

    @Override
    public void pluginCarregado(String id) {
        zerar(id);
    }

    @Override
    public void pluginRemovido(String id) {
        zerar(id);
    }

    private void zerar(String id) {
        circuitos.getAllCircuitBreakers().stream()
                .filter(cb -> cb.getName().equals(id))
                .forEach(CircuitBreaker::reset);
    }

    @Override
    public void close() {
        threads.shutdownNow();
    }
}
