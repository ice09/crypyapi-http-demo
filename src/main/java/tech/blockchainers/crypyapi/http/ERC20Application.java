package tech.blockchainers.crypyapi.http;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import tech.blockchainers.crypyapi.http.client.EthereumClient;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

@SpringBootApplication
@Slf4j
@EnableScheduling
public class ERC20Application {

	public static void main(String[] args) throws InterruptedException, ExecutionException, IOException {
		System.setProperty("spring.config.name", "crypyapi");
		String mode = System.getenv("mode");
		log.info("Arguments: " + mode);
		if ("client".equals(mode)) {
			new EthereumClient().shouldCallCompletePaymentFlow();
		} else {
			SpringApplication.run(ERC20Application.class, args);
		}
	}

}
