package br.edu.ifsp.freteja.nucleo.config;

import br.edu.ifsp.freteja.nucleo.plugin.CarregadorDePlugins;
import br.edu.ifsp.freteja.nucleo.plugin.RegistroDePlugins;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.util.List;

@Configuration
public class ConfiguracaoDoNucleo {

    @Bean
    public RegistroDePlugins registroDePlugins() {
        return new RegistroDePlugins();
    }

    @Bean(destroyMethod = "close")
    public CarregadorDePlugins carregadorDePlugins(
            @Value("${plugins.diretorio:./plugins-ativos}") String diretorio,
            RegistroDePlugins registro) {
        return new CarregadorDePlugins(Path.of(diretorio), registro, List.of());
    }
}
