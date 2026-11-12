package br.edu.ifsp.freteja.api;

import java.io.Serializable;
import java.util.Locale;

public record Envio(String cepOrigem, String cepDestino, double pesoKg,
                    int alturaCm, int larguraCm, int comprimentoCm) implements Serializable {

    public Envio {
        cepOrigem = normalizarCep(cepOrigem, "origem");
        cepDestino = normalizarCep(cepDestino, "destino");
        if (!(pesoKg > 0 && pesoKg <= 1000)) {
            throw new IllegalArgumentException("peso deve estar entre 0 e 1000 kg");
        }
        if (alturaCm <= 0 || larguraCm <= 0 || comprimentoCm <= 0) {
            throw new IllegalArgumentException("altura, largura e comprimento devem ser maiores que zero");
        }
        FaixasDeCep.uf(cepOrigem);
        FaixasDeCep.uf(cepDestino);
    }

    public String ufOrigem() {
        return FaixasDeCep.uf(cepOrigem);
    }

    public String ufDestino() {
        return FaixasDeCep.uf(cepDestino);
    }

    public Distancia distancia() {
        if (cepOrigem.substring(0, 3).equals(cepDestino.substring(0, 3))) {
            return Distancia.LOCAL;
        }
        return ufOrigem().equals(ufDestino()) ? Distancia.MESMO_ESTADO : Distancia.OUTRO_ESTADO;
    }

    public double pesoCubicoKg(int fator) {
        return (double) alturaCm * larguraCm * comprimentoCm / fator;
    }

    public double pesoTaxadoKg(int fator) {
        return Math.max(pesoKg, pesoCubicoKg(fator));
    }

    public String chave() {
        return String.format(Locale.ROOT, "%s>%s:%.3fkg:%dx%dx%d",
                cepOrigem, cepDestino, pesoKg, alturaCm, larguraCm, comprimentoCm);
    }

    private static String normalizarCep(String cep, String campo) {
        String digitos = cep == null ? "" : cep.replaceAll("\\D", "");
        if (digitos.length() != 8) {
            throw new IllegalArgumentException("CEP de " + campo + " inválido: " + cep);
        }
        return digitos;
    }
}
