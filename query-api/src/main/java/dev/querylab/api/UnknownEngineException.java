package dev.querylab.api;

import java.util.Collection;

public class UnknownEngineException extends IllegalArgumentException {

    public UnknownEngineException(String requested, Collection<String> available) {
        super("Unknown engine '" + requested + "'. Available: " + String.join(", ", available.stream().sorted().toList()));
    }
}
