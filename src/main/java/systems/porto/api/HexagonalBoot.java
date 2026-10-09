package systems.porto.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import systems.porto.api.dev.H2DevServer;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class HexagonalBoot {

	public static void main(String[] args) throws Exception {
		PortoApiHome.applyToSystemProperties();
		String environment = System.getenv().getOrDefault("API_ENV", "dev");
		if ("dev".equalsIgnoreCase(environment)) {
			H2DevServer.ensureRunning(PortoApiHome.resolve());
		}
		SpringApplication.run(HexagonalBoot.class, args);
	}

}
