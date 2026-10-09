package systems.porto.api.plugin;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class PluginRegistryFile {
    private List<PluginDescriptor> dtos = new ArrayList<>();

    private List<PluginDescriptor> datasources = new ArrayList<>();

    @JsonProperty("entry-adapters")
    private List<PluginDescriptor> entryAdapters = new ArrayList<>();

    private List<PluginDescriptor> processors = new ArrayList<>();

    @JsonProperty("client-adapters")
    private List<PluginDescriptor> clientAdapters = new ArrayList<>();

    @JsonProperty("scheduled-jobs")
    private List<PluginDescriptor> scheduledJobs = new ArrayList<>();
}
