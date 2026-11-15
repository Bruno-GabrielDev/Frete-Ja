package br.edu.ifsp.freteja.nucleo.cotacao;

import br.edu.ifsp.freteja.nucleo.plugin.OuvinteDePlugins;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

public class InvalidadorDeCache implements OuvinteDePlugins {

    private final CacheManager cacheManager;

    public InvalidadorDeCache(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    public void pluginCarregado(String id) {
        limpar();
    }

    @Override
    public void pluginRemovido(String id) {
        limpar();
    }

    private void limpar() {
        try {
            Cache cotacoes = cacheManager.getCache("cotacoes");
            if (cotacoes != null) {
                cotacoes.clear();
            }
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Não foi possível limpar o cache de cotações: " + e.getMessage());
        }
    }

    private static final System.Logger LOG = System.getLogger(InvalidadorDeCache.class.getName());
}
