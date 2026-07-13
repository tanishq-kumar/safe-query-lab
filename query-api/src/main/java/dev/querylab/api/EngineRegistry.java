package dev.querylab.api;

import dev.querylab.common.search.TransactionSearchPort;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * The engine-switching trick in one constructor: Spring injects a
 * {@code Map<String, TransactionSearchPort>} auto-keyed by BEAN NAME, so the
 * five {@code @Bean(name = "jdbc"|"jpa"|...)} definitions become a lookup
 * table with no registration code at all.
 */
@Component
public class EngineRegistry {

    public static final String DEFAULT_ENGINE = "jdbc";

    private final Map<String, TransactionSearchPort> engines;

    public EngineRegistry(Map<String, TransactionSearchPort> engines) {
        this.engines = engines;
    }

    public TransactionSearchPort get(String engine) {
        String key = engine == null || engine.isBlank() ? DEFAULT_ENGINE : engine.toLowerCase();
        TransactionSearchPort port = engines.get(key);
        if (port == null) {
            throw new UnknownEngineException(engine, engines.keySet());
        }
        return port;
    }

    public Set<String> engineNames() {
        return engines.keySet();
    }
}
