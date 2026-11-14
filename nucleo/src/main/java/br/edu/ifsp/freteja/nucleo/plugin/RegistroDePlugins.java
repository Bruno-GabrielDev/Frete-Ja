package br.edu.ifsp.freteja.nucleo.plugin;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class RegistroDePlugins {

    private final Map<String, PluginCarregado> plugins = new ConcurrentHashMap<>();

    public void registrar(PluginCarregado plugin) {
        plugins.put(normalizar(plugin.id()), plugin);
    }

    public void remover(String id) {
        plugins.remove(normalizar(id));
    }

    public void remover(String id, String arquivo) {
        plugins.computeIfPresent(normalizar(id), (chave, atual) -> atual.arquivo().equals(arquivo) ? null : atual);
    }

    public Optional<PluginCarregado> buscar(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(plugins.get(normalizar(id)));
    }

    public List<PluginCarregado> listar() {
        return plugins.values().stream().sorted(Comparator.comparing(PluginCarregado::id)).toList();
    }

    public List<String> ids() {
        return listar().stream().map(PluginCarregado::id).toList();
    }

    private static String normalizar(String id) {
        return id.trim().toLowerCase(Locale.ROOT);
    }
}
