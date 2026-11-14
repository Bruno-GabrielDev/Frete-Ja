package br.edu.ifsp.freteja.nucleo.execucao;

class FalhaDePluginException extends RuntimeException {

    FalhaDePluginException(String pluginId, String motivo) {
        super("plugin '" + pluginId + "' " + motivo);
    }
}
