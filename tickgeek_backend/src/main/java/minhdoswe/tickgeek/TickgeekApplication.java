package minhdoswe.tickgeek;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TickgeekApplication {

	public static void main(String[] args) {
		SpringApplication.run(TickgeekApplication.class, args);
	}

}
