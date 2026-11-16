package br.edu.ifsp.freteja.nucleo.web;

import br.edu.ifsp.freteja.api.Situacao;
import br.edu.ifsp.freteja.nucleo.web.Pedidos.ItemDaComparacao;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MonitorDeTrafegoTest {

    static class Relogio extends Clock {
        long segundo = 1_000;
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return Instant.ofEpochSecond(segundo); }
    }

    private static ItemDaComparacao item(String origem) {
        return new ItemDaComparacao("x", "X", "s", Situacao.COTADA, null, null, "", origem);
    }

    @Test
    void contaRequisicoesConsultasCacheEFalhasPorSegundo() {
        Relogio relogio = new Relogio();
        MonitorDeTrafego monitor = new MonitorDeTrafego("teste", relogio);

        monitor.registrar(600, List.of(item("PLUGIN"), item("PLUGIN")));
        monitor.registrar(2, List.of(item("CACHE"), item("FALHA")));
        relogio.segundo++;

        MonitorDeTrafego.Ponto ultimo = monitor.ultimos(5).pontos().get(4);
        assertThat(ultimo.requisicoes()).isEqualTo(2L);
        assertThat(ultimo.consultasAsTransportadoras()).isEqualTo(2L);
        assertThat(ultimo.respostasDoCache()).isEqualTo(1L);
        assertThat(ultimo.falhas()).isEqualTo(1L);
        assertThat(ultimo.tempoMedioMs()).isEqualTo(301L);
    }

    @Test
    void segundosSemTrafegoAparecemZeradosEOSegundoAtualFicaDeFora() {
        Relogio relogio = new Relogio();
        MonitorDeTrafego monitor = new MonitorDeTrafego("teste", relogio);

        monitor.registrar(10, List.of(item("PLUGIN")));

        List<MonitorDeTrafego.Ponto> pontos = monitor.ultimos(3).pontos();
        assertThat(pontos).extracting(MonitorDeTrafego.Ponto::requisicoes).containsExactly(0L, 0L, 0L);

        relogio.segundo += 2;
        pontos = monitor.ultimos(3).pontos();
        assertThat(pontos).extracting(MonitorDeTrafego.Ponto::requisicoes).containsExactly(0L, 1L, 0L);
    }

    @Test
    void umSegundoAntigoNaoSeMisturaComOAtual() {
        Relogio relogio = new Relogio();
        MonitorDeTrafego monitor = new MonitorDeTrafego("teste", relogio);

        monitor.registrar(10, List.of(item("PLUGIN")));
        relogio.segundo += 60;
        monitor.registrar(10, List.of(item("CACHE")));
        relogio.segundo++;

        MonitorDeTrafego.Ponto ultimo = monitor.ultimos(1).pontos().get(0);
        assertThat(ultimo.requisicoes()).isEqualTo(1L);
        assertThat(ultimo.consultasAsTransportadoras()).isEqualTo(0L);
        assertThat(ultimo.respostasDoCache()).isEqualTo(1L);
    }
}
