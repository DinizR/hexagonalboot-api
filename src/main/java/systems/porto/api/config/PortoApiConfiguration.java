package systems.porto.api.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PortoApiProperties.class)
public class PortoApiConfiguration {
}
