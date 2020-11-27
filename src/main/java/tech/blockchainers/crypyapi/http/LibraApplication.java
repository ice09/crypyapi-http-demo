package tech.blockchainers.crypyapi.http;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import tech.blockchainers.crypyapi.http.client.LibraClient;

import java.io.IOException;

@SpringBootApplication
@Slf4j
@EnableScheduling
public class LibraApplication {

	public static void main(String[] args) throws IOException, InterruptedException {
		System.setProperty("spring.config.name", "crypyapi");
		String mode = System.getenv("mode");
		log.info("Arguments: " + mode);
		if ("client".equals(mode)) {
			new LibraClient().shouldCallCompletePaymentFlow();
		} else {
			SpringApplication.run(LibraApplication.class, args);
		}
	}

}
