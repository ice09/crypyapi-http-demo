package tech.blockchainers.crypyapi.http.common;

import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import java.util.concurrent.TimeUnit;

@Configuration
public class ApplicationConfiguration {

    @Value("${rpc.url}")
    private String rpcUrl;

    @Bean
    public Web3j web3j() {
        return Web3j.build(new HttpService(rpcUrl, createOkHttpClient()));
    }

    @Bean
    public Credentials createCredentials() {
        return CredentialsUtil.createRandomCredentials();
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
