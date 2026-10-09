package systems.porto.api.plugin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import systems.porto.business.config.Connector;
import systems.porto.business.config.Property;

import java.util.List;

/**
 * Processor plugin YAML: porto-core {@code connectors} and {@code properties}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProcessorPluginYaml(
    List<Connector> connectors,
    List<Property> properties
) {
}
