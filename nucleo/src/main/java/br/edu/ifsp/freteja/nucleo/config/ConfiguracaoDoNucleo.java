package br.edu.ifsp.freteja.nucleo.config;

import br.edu.ifsp.freteja.nucleo.cotacao.InvalidadorDeCache;
import br.edu.ifsp.freteja.nucleo.execucao.ExecutorIsolado;
import br.edu.ifsp.freteja.nucleo.plugin.CarregadorDePlugins;
import br.edu.ifsp.freteja.nucleo.plugin.RegistroDePlugins;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

@Configuration
public class ConfiguracaoDoNucleo {

    @Bean
    public RegistroDePlugins registroDePlugins() {
        return new RegistroDePlugins();
    }

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry(
            @Value("${circuito.janela-de-chamadas:4}") int janela,
            @Value("${circuito.minimo-de-chamadas:3}") int minimo,
            @Value("${circuito.taxa-de-falha-percentual:50}") float taxaDeFalha,
            @Value("${circuito.tempo-aberto-segundos:20}") long tempoAberto) {

        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(janela)
                .minimumNumberOfCalls(minimo)
                .failureRateThreshold(taxaDeFalha)
                .waitDurationInOpenState(Duration.ofSeconds(tempoAberto))
                .permittedNumberOfCallsInHalfOpenState(1)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();
        return CircuitBreakerRegistry.of(config);
    }

    @Bean(destroyMethod = "close")
    public ExecutorIsolado executorIsolado(
            CircuitBreakerRegistry circuitos,
            @Value("${plugins.tempo-limite-ms:2000}") long tempoLimiteMs) {
        return new ExecutorIsolado(circuitos, Duration.ofMillis(tempoLimiteMs));
    }

    @Bean
    public InvalidadorDeCache invalidadorDeCache(CacheManager cacheManager) {
        return new InvalidadorDeCache(cacheManager);
    }

    @Bean(destroyMethod = "close")
    public CarregadorDePlugins carregadorDePlugins(
            @Value("${plugins.diretorio:./plugins-ativos}") String diretorio,
            RegistroDePlugins registro,
            ExecutorIsolado executor,
            InvalidadorDeCache invalidador) {
        return new CarregadorDePlugins(Path.of(diretorio), registro, List.of(executor, invalidador));
    }
}
