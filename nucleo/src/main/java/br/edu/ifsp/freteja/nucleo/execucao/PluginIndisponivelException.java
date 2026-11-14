package br.edu.ifsp.freteja.nucleo.execucao;

public class PluginIndisponivelException extends RuntimeException {

    PluginIndisponivelException(String mensagem) {
        super(mensagem);
    }
}
