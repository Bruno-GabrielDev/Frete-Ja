package br.edu.ifsp.freteja.nucleo.config;

import br.edu.ifsp.freteja.nucleo.plugin.CarregadorDePlugins;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ObservadorDePastaDePlugins {

    private static final Logger LOG = LoggerFactory.getLogger(ObservadorDePastaDePlugins.class);

    private final CarregadorDePlugins carregador;

    public ObservadorDePastaDePlugins(CarregadorDePlugins carregador) {
        this.carregador = carregador;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void aoIniciar() {
        LOG.info("Observando a pasta de plugins: {}", carregador.diretorio());
    }

    @Scheduled(initialDelay = 0, fixedDelayString = "${plugins.intervalo-ms:3000}")
    public void verificarPasta() {
        carregador.sincronizar();
    }
}
