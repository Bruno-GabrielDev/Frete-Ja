package br.edu.ifsp.freteja.nucleo.plugin;

import br.edu.ifsp.freteja.api.Transportadora;

import java.time.Instant;

public record PluginCarregado(Transportadora transportadora, String arquivo, Instant carregadoEm) {

    public String id() {
        return transportadora.id();
    }

    public String nome() {
        return transportadora.nome();
    }
}
