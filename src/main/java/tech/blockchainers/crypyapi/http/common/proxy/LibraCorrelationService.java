package tech.blockchainers.crypyapi.http.common.proxy;

import dev.jlibra.AccountAddress;
import dev.jlibra.client.LibraClient;
import dev.jlibra.client.views.event.Event;
import dev.jlibra.client.views.event.ReceivedPaymentEventData;
import dev.jlibra.client.views.transaction.Transaction;
import dev.jlibra.client.views.transaction.UserTransaction;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;
import tech.blockchainers.crypyapi.http.common.CredentialsUtil;
import tech.blockchainers.crypyapi.http.service.SignatureService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
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

    public void notifyOfTransaction(int amount, String trxId, String trxHash) {
        correlationIdToTrx.get(trxId).setTrxHash(trxHash);
        correlationIdToTrx.get(trxId).setTrxId(trxId);
        correlationIdToTrx.get(trxId).setAmount(amount);
    }

    public boolean isServiceCallAllowed(int amountInWei, String sequenceNumber, String signedTrx) throws IOException, InterruptedException {
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
        String signerAddress = calculateSignerAddress(sequenceNumber, signedTrx);
        boolean addressMatch = (signerAddress.toLowerCase().equals(paymentDto.getAddress().substring(2).toLowerCase()));
        if (addressMatch) {
            correlationIdToTrx.remove(paymentDto.getTrxId());
        }
        return addressMatch;
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

    private String calculateSignerAddress(String trxHash, String signedTrx) {
        PaymentDto paymentDto = getCorrelationByTrxHash(trxHash);
        byte[] proof = signatureService.createProof(Hash.sha3(paymentDto.getTrxId().getBytes(StandardCharsets.UTF_8)));
        return signatureService.ecrecoverAddress(Hash.sha3(proof), Numeric.hexStringToByteArray(signedTrx.substring(2)), paymentDto.getAddress());
    }

    public PaymentDto getCorrelationByTrxHash(String trxHash) {
        Optional<Map.Entry<String, PaymentDto>> value = correlationIdToTrx.entrySet().stream().filter(it -> (it.getValue().getTrxHash() != null) && it.getValue().getTrxHash().equals(trxHash)).findFirst();
        return value.map(Map.Entry::getValue).orElse(null);
    }

    @Scheduled(fixedDelay = 200)
    private void waitForMoneyTransfer() {
        String proxyAddress = CredentialsUtil.deriveLibraAddress(credentials);
        List<dev.jlibra.client.views.transaction.Transaction> result = libraClient.getAccountTransactions(AccountAddress.fromHexString(proxyAddress), 0, 1000, false);
        long lastSeqNo = 0;
        for (dev.jlibra.client.views.transaction.Transaction tx : result) {
            if (tx instanceof UserTransaction) {
                Long txs = ((UserTransaction) tx).sequenceNumber();
                if (txs > lastSeqNo) {
                    lastSeqNo = txs;
                }
                if (lastBlock == lastSeqNo) {
                    log.debug("No new transactions, resuming.");
                } else {
                    log.debug("Retrieved transactions {}, processing...", lastSeqNo);
                    for (Event event : tx.events()) {
                        if (event instanceof ReceivedPaymentEventData) {
                            handleReceivedTransaction(proxyAddress, (ReceivedPaymentEventData)event, (UserTransaction)tx);
                            break;
                        }
                    }
                    lastBlock = lastSeqNo;
                }
            }
        }
    }

    private void handleReceivedTransaction(String proxyAddress, ReceivedPaymentEventData txEvent, UserTransaction txData) {
        String input = txEvent.metadata();
        Long value = txEvent.amount().amount();
        String from = txEvent.sender();
        log.debug("Got transaction to {} with data {}", proxyAddress, input);
        if (StringUtils.isNotEmpty(txEvent.receiver()) && txEvent.receiver().toLowerCase().equals(proxyAddress.toLowerCase())) {
            String trxId = new String(Hex.decode(input.substring(2).getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
            log.debug("Got transaction from {} with input {}", from, trxId);
            if (!correlationIdToTrx.containsKey(trxId)) {
                log.info("Cannot correlate trxId {}, no entry found, maybe request was already completed.", trxId);
            } else {
                if (correlationIdToTrx.get(trxId).getAddress().toLowerCase().equals(from)) {
                    notifyOfTransaction(value.intValue(), trxId, String.valueOf(txData.sequenceNumber()));
                } else {
                    log.info("Cannot correlate trxId {}, from-address {} is different to stored {}", trxId, from, correlationIdToTrx.get(trxId).getAddress());
                }
            }
        }
    }

    private void waitForTransaction(String sequenceNumber) throws InterruptedException {
        String proxyAddress = CredentialsUtil.deriveLibraAddress(credentials);
        int tries = 0;
        while (tries < 50) {
            Transaction result = libraClient.getAccountTransaction(AccountAddress.fromHexString(proxyAddress), Long.valueOf(sequenceNumber), true);
            if (!(result instanceof UserTransaction)) {
                continue;
            }
            for (Event event : result.events()) {
                if (event instanceof ReceivedPaymentEventData) {
                    handleReceivedTransaction(CredentialsUtil.deriveLibraAddress(credentials), (ReceivedPaymentEventData) event, (UserTransaction)result);
                    break;
                }
            }
            log.debug("Waiting for transaction {} to be mined.", sequenceNumber);
            Thread.sleep(100);
            tries++;
        }
    }
}
