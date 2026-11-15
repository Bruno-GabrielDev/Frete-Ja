package br.edu.ifsp.freteja.nucleo.execucao;

public record EstadoDoCircuito(
        String plugin,
        String estado,
        float taxaDeFalha,
        int chamadasComFalha,
        int chamadasComSucesso,
        long chamadasRecusadas) {
}
