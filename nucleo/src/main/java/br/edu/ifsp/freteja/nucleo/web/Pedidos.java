package br.edu.ifsp.freteja.nucleo.web;

import br.edu.ifsp.freteja.api.CotacaoDeFrete;
import br.edu.ifsp.freteja.api.Envio;
import br.edu.ifsp.freteja.api.Situacao;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class Pedidos {

    private Pedidos() {
    }

    public record PedidoDeCotacao(String cepOrigem, String cepDestino, Double pesoKg,
                                  Integer alturaCm, Integer larguraCm, Integer comprimentoCm) {

        public Envio envio() {
            if (pesoKg == null || alturaCm == null || larguraCm == null || comprimentoCm == null) {
                throw new IllegalArgumentException("cepOrigem, cepDestino, pesoKg, alturaCm, larguraCm e comprimentoCm são obrigatórios");
            }
            return new Envio(cepOrigem, cepDestino, pesoKg, alturaCm, larguraCm, comprimentoCm);
        }
    }

    public record ItemDaComparacao(String transportadoraId, String transportadora, String servico,
                                   Situacao situacao, BigDecimal valor, Integer prazoDias,
                                   String detalhe, String origem) {

        static ItemDaComparacao de(CotacaoDeFrete c, Instant inicio) {
            String origem = c.indisponivel() ? "FALHA" : "PLUGIN";
            return new ItemDaComparacao(c.transportadoraId(), c.transportadora(), c.servico(),
                    c.situacao(), c.valor(), c.prazoDias(), c.detalhe(), origem);
        }
    }

    public record RespostaDaComparacao(String ufOrigem, String ufDestino, String distancia,
                                       List<ItemDaComparacao> cotacoes, long tempoMs) {
    }

    public record TransportadoraAtiva(String id, String nome, String arquivo, Instant carregadoEm) {
    }
}
