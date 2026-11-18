package br.edu.ifsp.freteja.nucleo.plugin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CarregadorDePluginsTest {

    @TempDir
    Path pasta;

    @Test
    void mesmoConteudoDaMesmaVersaoMesmoComOutroNomeOuData() throws IOException {
        Path original = Files.write(pasta.resolve("plugin-loggi.jar"), new byte[]{1, 2, 3});
        Path copia = Files.copy(original, pasta.resolve("outra-replica.jar"));
        Files.setLastModifiedTime(copia, FileTime.from(Instant.EPOCH));

        assertThat(CarregadorDePlugins.versaoDe(original)).hasSize(12);
        assertThat(CarregadorDePlugins.versaoDe(copia)).isEqualTo(CarregadorDePlugins.versaoDe(original));
    }

    @Test
    void conteudoDiferenteDaOutraVersao() throws IOException {
        Path antiga = Files.write(pasta.resolve("plugin-expressa.jar"), new byte[]{1, 2, 3});
        String versaoAntiga = CarregadorDePlugins.versaoDe(antiga);

        Files.write(antiga, new byte[]{1, 2, 4});

        assertThat(CarregadorDePlugins.versaoDe(antiga)).isNotEqualTo(versaoAntiga);
    }
}
