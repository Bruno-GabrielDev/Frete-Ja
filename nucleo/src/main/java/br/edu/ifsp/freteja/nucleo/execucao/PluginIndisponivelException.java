package br.edu.ifsp.freteja.nucleo.execucao;

public class PluginIndisponivelException extends RuntimeException {

    private final boolean circuitoAberto;

    PluginIndisponivelException(String mensagem, boolean circuitoAberto) {
        super(mensagem);
        this.circuitoAberto = circuitoAberto;
    }

    public boolean circuitoAberto() {
        return circuitoAberto;
    }
}
