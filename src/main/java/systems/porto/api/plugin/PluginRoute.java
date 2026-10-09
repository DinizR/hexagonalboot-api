package systems.porto.api.plugin;

import lombok.Getter;

@Getter
public final class PluginRoute {
    private final String method;
    private final String pathPattern;
    private final String processorId;
    private final String operation;

    public PluginRoute(final String method, final String pathPattern, final String processorId, final String operation) {
        this.method = method;
        this.pathPattern = pathPattern;
        this.processorId = processorId;
        this.operation = operation;
    }
}
