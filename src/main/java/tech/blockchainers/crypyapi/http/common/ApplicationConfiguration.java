package tech.blockchainers.crypyapi.http.common;

import dev.jlibra.client.LibraClient;
import okhttp3.OkHttpClient;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.util.concurrent.TimeUnit;

@Configuration
public class ApplicationConfiguration {

    @Value("${libra.rpc.url}")
    private String libraRpcUrl;

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Bean
    public LibraClient libraClient() {
        return LibraClient.builder().withUrl(libraRpcUrl).build();
    }
    @Bean
    public KeyPair createLibraCredentials() throws NoSuchProviderException, NoSuchAlgorithmException {
        KeyPairGenerator kpGen = KeyPairGenerator.getInstance("Ed25519", "BC");
        KeyPair keyPair = kpGen.generateKeyPair();
        return keyPair;
    }

    private OkHttpClient createOkHttpClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        configureTimeouts(builder);
        return builder.build();
    }

    private void configureTimeouts(OkHttpClient.Builder builder) {
        long tos = 8000L;
        builder.connectTimeout(tos, TimeUnit.SECONDS);
        builder.readTimeout(tos, TimeUnit.SECONDS);  // Sets the socket timeout too
        builder.writeTimeout(tos, TimeUnit.SECONDS);
    }
}
