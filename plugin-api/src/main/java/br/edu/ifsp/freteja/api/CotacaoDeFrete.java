package br.edu.ifsp.freteja.api;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

public record CotacaoDeFrete(
        String transportadoraId,
        String transportadora,
        String servico,
        Situacao situacao,
        BigDecimal valor,
        Integer prazoDias,
        String detalhe,
        Instant calculadaEm) implements Serializable {

    public static CotacaoDeFrete cotada(String id, String nome, String servico,
                                        double valor, int prazoDias, String regraAplicada) {
        return new CotacaoDeFrete(id, nome, servico, Situacao.COTADA,
                BigDecimal.valueOf(valor).setScale(2, RoundingMode.HALF_UP), prazoDias, regraAplicada, Instant.now());
    }

    public static CotacaoDeFrete naoAtende(String id, String nome, String motivo) {
        return new CotacaoDeFrete(id, nome, null, Situacao.NAO_ATENDE, null, null, motivo, Instant.now());
    }

    public static CotacaoDeFrete indisponivel(String id, String nome, String motivo) {
        return new CotacaoDeFrete(id, nome, null, Situacao.INDISPONIVEL, null, null, motivo, Instant.now());
    }

    public boolean indisponivel() {
        return situacao == Situacao.INDISPONIVEL;
    }
}
