package br.edu.ifsp.freteja.api;

public interface Transportadora {

    String id();

    String nome();

    CotacaoDeFrete cotar(Envio envio);
}
