package br.edu.ifsp.freteja.nucleo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfiguracaoDeCache implements CachingConfigurer {

    private static final Logger LOG = LoggerFactory.getLogger(ConfiguracaoDeCache.class);

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object chave) {
                LOG.warn("Cache indisponível na leitura ({}): {}", chave, e.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object chave, Object valor) {
                LOG.warn("Cache indisponível na escrita ({}): {}", chave, e.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object chave) {
                LOG.warn("Cache indisponível ao remover ({}): {}", chave, e.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                LOG.warn("Cache indisponível ao limpar: {}", e.getMessage());
            }
        };
    }
}
