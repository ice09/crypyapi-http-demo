package tech.blockchainers.crypyapi.http.common;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.context.TestPropertySource;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.utils.Numeric;
import tech.blockchainers.crypyapi.http.common.proxy.CorrelationService;
import tech.blockchainers.crypyapi.http.service.SignatureService;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@EnableScheduling
@Slf4j
@TestPropertySource("classpath:crypyapi.properties")
public class IntegrationTest {

    @Autowired
    private CorrelationService correlationService;

    @Autowired
    private SignatureService signatureService;

    @Autowired
    private Credentials proxyCredentials;

    @Autowired
    private Web3j httpWeb3j;

    @Test
    public void testPaymentFlow() throws IOException, InterruptedException {
        log.debug("Signing Address {}", proxyCredentials.getAddress());
        String trxId = correlationService.storeNewIdentifier(proxyCredentials.getAddress());
        correlationService.notifyOfTransaction(0, trxId, "0xHASH");
        String signedTrxId = signatureService.sign(trxId, proxyCredentials);
        boolean isServiceCallAllowed = correlationService.isServiceCallAllowed(0, "0xHASH", signedTrxId);
        assertTrue(isServiceCallAllowed);
    }

    @Test
    public void testCompleteLifecycle() throws ExecutionException, InterruptedException, IOException {
        String senderSokolPk = "83c8310520992006616ffd7a9e0a9a070f17d9e3443044273c4f2b35fa654e48";
        Credentials testnetCredentials = Credentials.create(senderSokolPk);
        String trxId = correlationService.storeNewIdentifier(testnetCredentials.getAddress());
        String trxHash = sendSokolTestTransaction(testnetCredentials, trxId);
        log.debug("Sent transaction {}", trxHash);
        pause(10000);
        String signedTrxId = signatureService.sign(trxId, testnetCredentials);
        boolean isServiceCallAllowed = correlationService.isServiceCallAllowed(0, trxHash, signedTrxId);
        assertTrue(isServiceCallAllowed);
    }

    private void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {}
    }
    private String sendSokolTestTransaction(Credentials credentials, String trxId) throws ExecutionException, InterruptedException, IOException {
        EthGetTransactionCount ethGetTransactionCount = httpWeb3j.ethGetTransactionCount(
                credentials.getAddress(), DefaultBlockParameterName.LATEST).sendAsync().get();

        BigInteger nonce = ethGetTransactionCount.getTransactionCount();

        RawTransaction trx =
                RawTransaction.createTransaction(
                        nonce,
                        DefaultGasProvider.GAS_PRICE.divide(BigInteger.valueOf(4)),
                        DefaultGasProvider.GAS_LIMIT,
                        proxyCredentials.getAddress(),
                        BigInteger.ONE,
                        Numeric.toHexString(trxId.getBytes(StandardCharsets.UTF_8)));
        byte[] signedTrx = TransactionEncoder.signMessage(trx, credentials);

        String hexValue = Numeric.toHexString(signedTrx);
        EthSendTransaction res = httpWeb3j.ethSendRawTransaction(hexValue).send();
        return res.getTransactionHash();
    }
}
