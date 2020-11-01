package tech.blockchainers.crypyapi.http.common.proxy;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.utils.Numeric;
import tech.blockchainers.crypyapi.http.rest.paid.BestJokeEverService;
import tech.blockchainers.crypyapi.http.service.SignatureService;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class CorrelationService {

    private Map<String, PaymentDto> correlationIdToTrx = new HashMap<>();
    private final SignatureService signatureService;
    private final BestJokeEverService bestJokeEverService;
    private final Web3j httpWeb3;
    private final Credentials credentials;
    private BigInteger lastBlock = BigInteger.ZERO;

    public CorrelationService(SignatureService signatureService, Web3j httpWeb3, BestJokeEverService bestJokeEverService, Credentials credentials) {
        this.signatureService = signatureService;
        this.bestJokeEverService = bestJokeEverService;
        this.httpWeb3 = httpWeb3;
        this.credentials = credentials;
        determineBlocknumber();
    }

    private void determineBlocknumber() {
        try {
            EthBlock result = httpWeb3.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, true).send();
            lastBlock = result.getBlock().getNumber();
        } catch (Exception ex) {
            log.error("cannot determine current block number.");
        }
    }

    public String storeNewIdentifier(String address) {
        String randomId = RandomStringUtils.randomAlphabetic(6);
        correlationIdToTrx.put(randomId, PaymentDto.builder().address(address).build());
        return randomId;
    }

    public void notifyOfTransaction(String trxId, String trxHash) {
        correlationIdToTrx.get(trxId).setTrxHash(trxHash);
        correlationIdToTrx.get(trxId).setTrxId(trxId);
    }

    public String callService(String trxHash, String signedTrx) throws IOException, InterruptedException {
        PaymentDto paymentDto = getCorrelationByTrxHash(trxHash);
        if (paymentDto == null) {
            waitForTransaction(trxHash);
            paymentDto = getCorrelationByTrxHash(trxHash);
            if (paymentDto == null) {
                throw new IllegalStateException("Cannot correlate trxHash " + trxHash);
            }
        }
        String signerAddress = calculateSignerAddress(trxHash, signedTrx);
        if (signerAddress.toLowerCase().equals(paymentDto.getAddress().substring(2).toLowerCase())) {
            String joke = bestJokeEverService.getBestJokeEver();
            correlationIdToTrx.remove(paymentDto.getTrxId());
            return joke;
        }
        return null;
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

    public PaymentDto getCorrelationByTrxId(String trxId) {
        return correlationIdToTrx.get(trxId);
    }

    @Scheduled(fixedDelay = 200)
    private void waitForMoneyTransfer() throws IOException {
        String proxyAddress = credentials.getAddress();
        EthBlock result = httpWeb3.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send();
        EthBlock.Block block = result.getBlock();
        if (lastBlock.equals(block.getNumber())) {
            log.debug("No new block, resuming.");
        } else {
            log.debug("Retrieved block {}, processing transactions...", block.getNumber());
            BigInteger i = lastBlock.add(BigInteger.ONE);
            while (i.compareTo(block.getNumber()) <= 0) {
                log.debug("Processing block {} of {}", i.intValue(), block.getNumber().intValue());
                EthBlock.Block currentBlock = httpWeb3.ethGetBlockByNumber(DefaultBlockParameter.valueOf(i), true).send().getBlock();
                currentBlock.getTransactions().forEach(txob -> {
                    Transaction tx = ((EthBlock.TransactionObject) txob.get()).get();
                    log.debug("Processing transaction {}", tx.getHash());
                    handleReceivedTransaction(proxyAddress, tx);
                });
                i = i.add(BigInteger.ONE);
            }
            lastBlock = block.getNumber();
        }
    }

    private void handleReceivedTransaction(String proxyAddress, Transaction tx) {
        String input = tx.getInput();
        String value = (tx.getValue() != null) ? tx.getValue().toString() : BigDecimal.ZERO.toString();
        String from = tx.getFrom();
        String to = tx.getTo();
        log.debug("Got transaction to {} with data {}", to, input);
        if (StringUtils.isNotEmpty(to) && to.toLowerCase().equals(proxyAddress.toLowerCase())) {
            String trxId = new String(Hex.decode(input.substring(2).getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
            log.debug("Got transaction from {} with input {}", from, trxId);
            if (!correlationIdToTrx.containsKey(trxId)) {
                log.info("Cannot correlate trxId {}, no entry found, maybe request was already completed.", trxId);
            } else {
                if (correlationIdToTrx.get(trxId).getAddress().toLowerCase().equals(from)) {
                    notifyOfTransaction(trxId, tx.getHash());
                } else {
                    log.info("Cannot correlate trxId {}, from-address {} is different to stored {}", trxId, from, correlationIdToTrx.get(trxId).getAddress());
                }
            }
        }
    }

    private void waitForTransaction(String trxHash) throws IOException, InterruptedException {
        int tries = 0;
        while (tries < 50) {
            EthGetTransactionReceipt transactionReceipt = httpWeb3
                    .ethGetTransactionReceipt(trxHash)
                    .send();
            if (transactionReceipt.getResult() != null) {
                handleReceivedTransaction(credentials.getAddress(), httpWeb3.ethGetTransactionByHash(trxHash).send().getResult());
                break;
            }
            log.debug("Waiting for transaction {} to be mined.", trxHash);
            Thread.sleep(100);
            tries++;
        }
    }
}
