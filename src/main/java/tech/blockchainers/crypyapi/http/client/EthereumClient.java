package tech.blockchainers.crypyapi.http.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGetBalance;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.utils.Numeric;
import tech.blockchainers.crypyapi.http.common.CredentialsUtil;
import tech.blockchainers.crypyapi.http.common.proxy.PaymentDto;
import tech.blockchainers.crypyapi.http.service.SignatureService;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Slf4j
public class EthereumClient {

    private Web3j web3j = Web3j.build(new HttpService("https://sokol.poa.network"));

    private String host = Files.exists(Path.of("/.dockerenv")) ? "host.docker.internal" : "localhost";

    public void shouldCallCompletePaymentFlow() throws InterruptedException, ExecutionException, IOException {
        Credentials credentials = CredentialsUtil.createRandomEthereumCredentials();
        waitForMoney(credentials.getAddress());
        boolean stillMoneyForCheapJokes = getCurrentBalance(credentials.getAddress()).compareTo(BigInteger.ONE.divide(BigInteger.TEN)) > 0;
        while (stillMoneyForCheapJokes) {
            RestTemplate restTemplate = new RestTemplate();

            Map<String, String> params = new HashMap<>();
            params.put("address", credentials.getAddress());
            params.put("service", "jokeRequestService");
            PaymentDto response = restTemplate.getForObject("http://" + host + ":8889/jokeForEthereum/setup?address={address}&service={service}", PaymentDto.class, params);
            String trxId = response.getTrxId();
            log.debug("Send payment transaction with data '{}'", trxId);

            String trxHash = sendSokolTestTransaction(credentials, response.getAmount(), trxId, response.getServiceAddress());
            waitForTransaction(trxHash);

            String signedTrxId = new SignatureService().sign(trxId, credentials);

            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(Arrays.asList(MediaType.ALL));
            headers.set("CPA-Signed-Identifier", signedTrxId);
            headers.set("CPA-Transaction-Hash", trxHash);

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> joke = restTemplate.exchange("http://" + host + ":8889/jokeForEthereum/request", HttpMethod.GET, entity, String.class);
            log.info(("Hold On: {}").toUpperCase(), joke.getBody().toUpperCase());
            BigInteger currentBalance = getCurrentBalance(credentials.getAddress());
            stillMoneyForCheapJokes = currentBalance.compareTo(BigInteger.ONE.divide(BigInteger.TEN)) > 0;
            if (stillMoneyForCheapJokes) {
                log.info("I still have some money left ({} wXDai), lets go for another one.", currentBalance);
            }
        }
    }

    private BigInteger getCurrentBalance(String address) throws IOException {
        return web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send().getBalance();
    }

    private void waitForTransaction(String trxHash) throws IOException, InterruptedException {
        while (true) {
            EthGetTransactionReceipt transactionReceipt = web3j
                    .ethGetTransactionReceipt(trxHash)
                    .send();
            if (transactionReceipt.getResult() != null) {
                break;
            }
            log.debug("Waiting for transaction {} to be mined.", trxHash);
            Thread.sleep(100);
        }
    }

    private void waitForMoney(String address) throws IOException, InterruptedException {
        boolean enoughMoneyReceived = false;
        log.info("Waiting for xDais at address {}", address);
        while (!enoughMoneyReceived) {
            EthGetBalance xDaiBalance = web3j
                    .ethGetBalance(address, DefaultBlockParameterName.LATEST)
                    .send();
            if (xDaiBalance.getBalance().compareTo(BigInteger.ZERO) > 0) {
                enoughMoneyReceived = true;
            }
            Thread.sleep(100);
        }
        log.info("Thanks! Going to pay Chuck now.");
    }
    private String sendSokolTestTransaction(Credentials credentials, int amount, String trxId, String serviceAddress) throws ExecutionException, InterruptedException, IOException {
        EthGetTransactionCount ethGetTransactionCount = web3j.ethGetTransactionCount(
                credentials.getAddress(), DefaultBlockParameterName.LATEST).sendAsync().get();

        BigInteger nonce = ethGetTransactionCount.getTransactionCount();

        RawTransaction trx =
                RawTransaction.createTransaction(
                        nonce,
                        DefaultGasProvider.GAS_PRICE.divide(BigInteger.valueOf(4)),
                        DefaultGasProvider.GAS_LIMIT,
                        serviceAddress,
                        BigInteger.valueOf(amount),
                        Numeric.toHexString(trxId.getBytes(StandardCharsets.UTF_8)));
        byte[] signedTrx = TransactionEncoder.signMessage(trx, credentials);

        String hexValue = Numeric.toHexString(signedTrx);
        EthSendTransaction res = web3j.ethSendRawTransaction(hexValue).send();
        return res.getTransactionHash();
    }
}
