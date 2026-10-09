package systems.porto.api.plugin;

import lombok.Data;
import lombok.NoArgsConstructor;
import systems.porto.dto.Dependencies;
import systems.porto.dto.DynamicDTO;

@Data
@NoArgsConstructor
public class PluginDescriptor implements DynamicDTO {
    private String id;
    private String version;
    private String label;
    private String description;
    private String className;
    private String jarFile;
    private Dependencies dependencies;
}
