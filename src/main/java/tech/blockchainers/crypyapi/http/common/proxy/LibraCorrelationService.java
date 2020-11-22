package tech.blockchainers.crypyapi.http.common.proxy;

import dev.jlibra.AccountAddress;
import dev.jlibra.AuthenticationKey;
import dev.jlibra.KeyUtils;
import dev.jlibra.client.LibraClient;
import dev.jlibra.client.views.Account;
import dev.jlibra.client.views.event.Event;
import dev.jlibra.client.views.event.ReceivedPaymentEventData;
import dev.jlibra.client.views.transaction.Transaction;
import dev.jlibra.client.views.transaction.UserTransaction;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.stereotype.Service;
import tech.blockchainers.crypyapi.http.common.CredentialsUtil;
import tech.blockchainers.crypyapi.http.service.SignatureService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class LibraCorrelationService {

    private Map<String, PaymentDto> correlationIdToTrx = new HashMap<>();
    private final SignatureService signatureService;
    private final LibraClient libraClient;
    private final KeyPair credentials;
    private Long lastBlock = 0l;

    public LibraCorrelationService(SignatureService signatureService, LibraClient libraClient, KeyPair credentials) {
        this.signatureService = signatureService;
        this.libraClient = libraClient;
        this.credentials = credentials;
    }

    public String storeNewIdentifier(String address) {
        String randomId = RandomStringUtils.randomAlphabetic(6);
        correlationIdToTrx.put(randomId, PaymentDto.builder().address(address).build());
        return randomId;
    }

    public void notifyOfTransaction(int amount, String trxId, String trxHash, String pubKey) {
        correlationIdToTrx.get(trxId).setTrxHash(trxHash);
        correlationIdToTrx.get(trxId).setTrxId(trxId);
        correlationIdToTrx.get(trxId).setAmount(amount);
        correlationIdToTrx.get(trxId).setPublicKey(pubKey);
    }

    public boolean isServiceCallAllowed(int amountInWei, String sequenceNumber, String signedTrx) throws InterruptedException, NoSuchAlgorithmException, InvalidKeySpecException {
        PaymentDto paymentDto = getCorrelationByTrxHash(sequenceNumber);
        if (paymentDto == null) {
            waitForTransaction(sequenceNumber);
            paymentDto = getCorrelationByTrxHash(sequenceNumber);
            if (paymentDto == null) {
                throw new IllegalStateException("Cannot correlate trxHash " + sequenceNumber);
            }
        }
        if (paymentDto.getAmount() < amountInWei) {
            throw new IllegalStateException("Got only lousy " + paymentDto.getAmount() + " in the transaction, but wanted " + amountInWei + ". Try again next time!");
        }
        boolean verified = verifySignerSignature(sequenceNumber, signedTrx);
        if (verified) {
            correlationIdToTrx.remove(paymentDto.getTrxId());
        }
        return verified;
    }

    private long getAmountOfTransaction(String trxHash) throws IOException {
        String accountAddress = CredentialsUtil.deriveLibraAddress(credentials);
        List<dev.jlibra.client.views.transaction.Transaction> trxs = libraClient.getAccountTransactions(AccountAddress.fromHexString(accountAddress), 0, 1000, true);
        for (dev.jlibra.client.views.transaction.Transaction tx : trxs) {
            if (tx.hash() == trxHash) {
                for (Event event : tx.events()) {
                    if (event.data() instanceof ReceivedPaymentEventData) {
                        ReceivedPaymentEventData edata = (ReceivedPaymentEventData) event.data();
                        return edata.amount().amount();
                    }
                }
            }
        }
        return 0;
    }

    private boolean verifySignerSignature(String sequenceNumber, String signedTrx) throws NoSuchAlgorithmException, InvalidKeySpecException {
        PaymentDto paymentDto = getCorrelationByTrxHash(sequenceNumber);
        PublicKey pubKey = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Hex.decode(paymentDto.getPublicKey())));
        return signatureService.verify(paymentDto.getTrxId().getBytes(StandardCharsets.UTF_8), Hex.decode(signedTrx), pubKey);
    }

    public PaymentDto getCorrelationByTrxHash(String trxHash) {
        Optional<Map.Entry<String, PaymentDto>> value = correlationIdToTrx.entrySet().stream().filter(it -> (it.getValue().getTrxHash() != null) && it.getValue().getTrxHash().equals(trxHash)).findFirst();
        return value.map(Map.Entry::getValue).orElse(null);
    }

    //@Scheduled(fixedDelay = 200)
    private void waitForMoneyTransfer() {
        String proxyAddress = CredentialsUtil.deriveLibraAddress(credentials);
        List<dev.jlibra.client.views.transaction.Transaction> result = libraClient.getAccountTransactions(AccountAddress.fromHexString(proxyAddress), 0, 1000, false);
        long lastSeqNo = 0;
        for (dev.jlibra.client.views.transaction.Transaction tx : result) {
            if (tx instanceof UserTransaction) {
                Long txs = ((UserTransaction) tx).sequenceNumber();
                String pubKey = ((UserTransaction) tx).publicKey();
                if (txs > lastSeqNo) {
                    lastSeqNo = txs;
                }
                if (lastBlock == lastSeqNo) {
                    log.debug("No new transactions, resuming.");
                } else {
                    log.debug("Retrieved transactions {}, processing...", lastSeqNo);
                    for (Event event : tx.events()) {
                        if (event instanceof ReceivedPaymentEventData) {
                            handleReceivedTransaction(proxyAddress, (ReceivedPaymentEventData)event, event.sequenceNumber(), pubKey);
                            break;
                        }
                    }
                    lastBlock = lastSeqNo;
                }
            }
        }
    }

    private void handleReceivedTransaction(String proxyAddress, ReceivedPaymentEventData txEvent, Long txData, String pubKey) {
        String input = txEvent.metadata();
        if (StringUtils.isEmpty(input)) {
            return;
        }
        Long value = txEvent.amount().amount();
        String from = txEvent.sender();
        log.debug("Got transaction to {} with data {}", proxyAddress, input);
        if (StringUtils.isNotEmpty(txEvent.receiver()) && txEvent.receiver().toLowerCase().equals(proxyAddress.toLowerCase())) {
            String trxId = new String(Hex.decode(input.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
            log.debug("Got transaction from {} with input {}", from, trxId);
            if (!correlationIdToTrx.containsKey(trxId)) {
                log.info("Cannot correlate trxId {}, no entry found, maybe request was already completed.", trxId);
            } else {
                if (correlationIdToTrx.get(trxId).getAddress().toLowerCase().equals(from)) {
                    notifyOfTransaction(value.intValue(), trxId, String.valueOf(txData), "302a300506032b6570032100" + pubKey);
                } else {
                    log.info("Cannot correlate trxId {}, from-address {} is different to stored {}", trxId, from, correlationIdToTrx.get(trxId).getAddress());
                }
            }
        }
    }

    private void waitForTransaction(String sequenceNumber) throws InterruptedException {
        int tries = 0;
        AccountAddress accountAddress = AccountAddress.fromHexString(CredentialsUtil.deriveLibraAddress(credentials));
        while (tries < 5) {
            String receivedEvKey = libraClient.getAccount(accountAddress).receivedEventsKey();
            List<Event> results = libraClient.getEvents(receivedEvKey, 0 , 1000);
            for (Event result : results) {
                if (!(result.data() instanceof ReceivedPaymentEventData)) {
                    continue;
                }
                List<Transaction> trxs = libraClient.getTransactions(result.transactionVersion(), 1, false);
                String pubKey = ((UserTransaction) trxs.get(0).transaction()).publicKey();
                handleReceivedTransaction(CredentialsUtil.deriveLibraAddress(credentials), (ReceivedPaymentEventData) result.data(), result.transactionVersion(), pubKey);
            }
            log.debug("Waiting for transaction {} to be mined.", sequenceNumber);
            Thread.sleep(100);
            tries++;
        }
    }
}
