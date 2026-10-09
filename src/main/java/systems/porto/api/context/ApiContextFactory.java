package systems.porto.api.context;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import systems.porto.api.config.PortoApiProperties;

@Component
public class ApiContextFactory {

    private final PortoApiProperties properties;
    private final ApplicationContext applicationContext;

    public ApiContextFactory(
        final PortoApiProperties properties,
        final ApplicationContext applicationContext
    ) {
        this.properties = properties;
        this.applicationContext = applicationContext;
    }

    public ApiContext create() {
        ApiContext context = new ApiContext(
            properties.getHomeDirectory(),
            properties.getEnvironment(),
            properties.getApplication()
        );
        context.setApplicationContext(applicationContext);
        return context;
    }
}
