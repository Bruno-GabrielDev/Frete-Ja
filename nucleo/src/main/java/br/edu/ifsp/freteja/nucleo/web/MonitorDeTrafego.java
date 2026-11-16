package br.edu.ifsp.freteja.nucleo.web;

import br.edu.ifsp.freteja.nucleo.web.Pedidos.ItemDaComparacao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

@Component
public class MonitorDeTrafego {

    private static final int JANELA = 60;

    private final String instancia;
    private final Clock relogio;
    private final long[] segundo = new long[JANELA];
    private final long[] requisicoes = new long[JANELA];
    private final long[] consultas = new long[JANELA];
    private final long[] doCache = new long[JANELA];
    private final long[] falhas = new long[JANELA];
    private final long[] somaTempoMs = new long[JANELA];

    @Autowired
    public MonitorDeTrafego(@Value("${freteja.instancia:completa}") String instancia) {
        this(instancia, Clock.systemUTC());
    }

    MonitorDeTrafego(String instancia, Clock relogio) {
        this.instancia = instancia;
        this.relogio = relogio;
    }

    public synchronized void registrar(long tempoMs, List<ItemDaComparacao> itens) {
        long agora = relogio.instant().getEpochSecond();
        int i = posicao(agora);
        if (segundo[i] != agora) {
            segundo[i] = agora;
            requisicoes[i] = consultas[i] = doCache[i] = falhas[i] = somaTempoMs[i] = 0;
        }
        requisicoes[i]++;
        somaTempoMs[i] += tempoMs;
        for (ItemDaComparacao item : itens) {
            switch (item.origem()) {
                case "PLUGIN" -> consultas[i]++;
                case "CACHE" -> doCache[i]++;
                default -> falhas[i]++;
            }
        }
    }

    public synchronized Trafego ultimos(int segundos) {
        int n = Math.max(1, Math.min(segundos, JANELA - 1));
        long agora = relogio.instant().getEpochSecond();
        List<Ponto> pontos = new ArrayList<>(n);
        for (long s = agora - n; s < agora; s++) {
            int i = posicao(s);
            if (segundo[i] == s) {
                long media = requisicoes[i] == 0 ? 0 : somaTempoMs[i] / requisicoes[i];
                pontos.add(new Ponto(s, requisicoes[i], consultas[i], doCache[i], falhas[i], media));
            } else {
                pontos.add(new Ponto(s, 0, 0, 0, 0, 0));
            }
        }
        return new Trafego(instancia, pontos);
    }

    private static int posicao(long epochSecond) {
        return (int) (epochSecond % JANELA);
    }

    public record Ponto(long segundo, long requisicoes, long consultasAsTransportadoras,
                        long respostasDoCache, long falhas, long tempoMedioMs) {
    }

    public record Trafego(String instancia, List<Ponto> pontos) {
    }
}
