package systems.porto.api.plugin;

import org.springframework.stereotype.Component;
import systems.porto.api.route.RouteRegistrar;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class PluginRouteDispatcher implements RouteRegistrar {
    private final List<PluginRoute> routes = new ArrayList<>();

    @Override
    public void register(final String method, final String pathPattern, final String processorId, final String operation) {
        routes.add(new PluginRoute(method.toUpperCase(Locale.ROOT), pathPattern, processorId, operation));
    }

    public Optional<PluginRoute> match(final String method, final String path) {
        String normalizedMethod = method.toUpperCase(Locale.ROOT);
        for (PluginRoute route : routes) {
            if (!route.getMethod().equals(normalizedMethod)) {
                continue;
            }
            if (matches(route.getPathPattern(), path)) {
                return Optional.of(route);
            }
        }
        return Optional.empty();
    }

    public Optional<Integer> extractPathId(final String pattern, final String path) {
        if (!pattern.contains("{id}")) {
            return Optional.empty();
        }
        String prefix = pattern.substring(0, pattern.indexOf("{id}"));
        if (!path.startsWith(prefix)) {
            return Optional.empty();
        }
        String suffix = pattern.substring(pattern.indexOf("{id}") + "{id}".length());
        String idPart = path.substring(prefix.length(), path.length() - suffix.length());
        try {
            return Optional.of(Integer.parseInt(idPart));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private boolean matches(final String pattern, final String path) {
        if (pattern.contains("{id}")) {
            String prefix = pattern.substring(0, pattern.indexOf("{id}"));
            String suffix = pattern.substring(pattern.indexOf("{id}") + "{id}".length());
            if (!path.startsWith(prefix) || !path.endsWith(suffix)) {
                return false;
            }
            String middle = path.substring(prefix.length(), path.length() - suffix.length());
            return !middle.isBlank() && middle.chars().allMatch(Character::isDigit);
        }
        return pattern.equals(path);
    }
}
