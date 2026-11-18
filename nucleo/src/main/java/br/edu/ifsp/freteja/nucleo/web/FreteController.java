package br.edu.ifsp.freteja.nucleo.web;

import br.edu.ifsp.freteja.api.Envio;
import br.edu.ifsp.freteja.nucleo.cotacao.ComparadorDeFrete;
import br.edu.ifsp.freteja.nucleo.execucao.EstadoDoCircuito;
import br.edu.ifsp.freteja.nucleo.execucao.ExecutorIsolado;
import br.edu.ifsp.freteja.nucleo.plugin.RegistroDePlugins;
import br.edu.ifsp.freteja.nucleo.web.Pedidos.ItemDaComparacao;
import br.edu.ifsp.freteja.nucleo.web.Pedidos.PedidoDeCotacao;
import br.edu.ifsp.freteja.nucleo.web.Pedidos.RespostaDaComparacao;
import br.edu.ifsp.freteja.nucleo.web.Pedidos.TransportadoraAtiva;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api")
public class FreteController {

    private final ComparadorDeFrete comparador;
    private final RegistroDePlugins registro;
    private final ExecutorIsolado executor;
    private final MonitorDeTrafego monitor;

    public FreteController(ComparadorDeFrete comparador, RegistroDePlugins registro, ExecutorIsolado executor,
                           MonitorDeTrafego monitor) {
        this.comparador = comparador;
        this.registro = registro;
        this.executor = executor;
        this.monitor = monitor;
    }

    @GetMapping("/transportadoras")
    public List<TransportadoraAtiva> transportadoras() {
        return registro.listar().stream()
                .map(p -> new TransportadoraAtiva(p.id(), p.nome(), p.arquivo(), p.versao(), p.carregadoEm(), executor.estadoDe(p.id())))
                .toList();
    }

    @PostMapping("/cotacoes")
    public RespostaDaComparacao cotar(@RequestBody PedidoDeCotacao pedido) {
        Envio envio = pedido.envio();
        Instant inicio = Instant.now();
        long t0 = System.nanoTime();
        List<ItemDaComparacao> itens = comparador.comparar(envio).stream()
                .map(c -> ItemDaComparacao.de(c, inicio))
                .toList();
        long tempoMs = (System.nanoTime() - t0) / 1_000_000;
        monitor.registrar(tempoMs, itens);
        return new RespostaDaComparacao(envio.ufOrigem(), envio.ufDestino(), envio.distancia().descricao(), itens, tempoMs);
    }

    @GetMapping("/trafego")
    public MonitorDeTrafego.Trafego trafego(@RequestParam(defaultValue = "30") int segundos) {
        return monitor.ultimos(segundos);
    }

    @GetMapping("/circuitos")
    public List<EstadoDoCircuito> circuitos() {
        return executor.estados();
    }
}
