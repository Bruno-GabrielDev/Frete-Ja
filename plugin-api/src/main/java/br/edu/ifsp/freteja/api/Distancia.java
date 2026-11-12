package br.edu.ifsp.freteja.api;

public enum Distancia {
    LOCAL("local"),
    MESMO_ESTADO("mesmo estado"),
    OUTRO_ESTADO("outro estado");

    private final String descricao;

    Distancia(String descricao) {
        this.descricao = descricao;
    }

    public String descricao() {
        return descricao;
    }
}
