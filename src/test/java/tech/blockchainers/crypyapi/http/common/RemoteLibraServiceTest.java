package tech.blockchainers.crypyapi.http.common;

import dev.jlibra.AccountAddress;
import dev.jlibra.AuthenticationKey;
import dev.jlibra.PublicKey;
import dev.jlibra.client.LibraClient;
import dev.jlibra.client.views.Account;
import dev.jlibra.client.views.Amount;
import dev.jlibra.client.views.transaction.PeerToPeerWithMetadataScript;
import dev.jlibra.client.views.transaction.UserTransaction;
import dev.jlibra.move.Move;
import dev.jlibra.poller.Wait;
import dev.jlibra.serialization.ByteArray;
import dev.jlibra.transaction.ChainId;
import dev.jlibra.transaction.ImmutableScript;
import dev.jlibra.transaction.ImmutableSignedTransaction;
import dev.jlibra.transaction.ImmutableTransaction;
import dev.jlibra.transaction.ImmutableTransactionAuthenticatorEd25519;
import dev.jlibra.transaction.Signature;
import dev.jlibra.transaction.SignedTransaction;
import dev.jlibra.transaction.Struct;
import dev.jlibra.transaction.Transaction;
import dev.jlibra.transaction.argument.AccountAddressArgument;
import dev.jlibra.transaction.argument.U64Argument;
import dev.jlibra.transaction.argument.U8VectorArgument;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import tech.blockchainers.crypyapi.http.common.proxy.PaymentDto;
import tech.blockchainers.crypyapi.http.service.SignatureService;

import java.io.IOException;
import java.security.KeyPair;
import java.security.Security;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static dev.jlibra.poller.Conditions.transactionFound;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;

@Slf4j
public class RemoteLibraServiceTest {

    private LibraClient libraClient = new LibraClient.LibraClientBuilder().withUrl("https://client.testnet.libra.org/v1/").build();

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Test
    public void shouldCallCompletePaymentFlow() throws InterruptedException, IOException {
        KeyPair clientCredentials = CredentialsUtil.createRandomLibraCredentials();
        CredentialsUtil.mintAmount(clientCredentials);
        boolean stillMoneyForCheapJokes = getCurrentBalance(CredentialsUtil.deriveLibraAddress(clientCredentials)) > 1;
        while (stillMoneyForCheapJokes) {
            RestTemplate restTemplate = new RestTemplate();

            Map<String, String> params = new HashMap<>();
            params.put("address", CredentialsUtil.deriveLibraAddress(clientCredentials));
            PaymentDto response = restTemplate.getForObject("http://localhost:8889/jokeForLibra/setup?address={address}", PaymentDto.class, params);
            String trxId = response.getTrxId();
            log.debug("Send payment transaction with data '{}'", trxId);

            Long trxVersion = sendLibraTestnetTransaction(clientCredentials, response.getServiceAddress(), trxId);

            String signedTrxId = new SignatureService().sign(trxId, clientCredentials);

            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(Arrays.asList(MediaType.ALL));
            headers.set("CPA-Signed-Identifier", signedTrxId);
            headers.set("CPA-Transaction-Hash", String.valueOf(trxVersion));

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> joke = restTemplate.exchange("http://localhost:8889/jokeForLibra/request", HttpMethod.GET, entity, String.class);
            log.info(("Hold On: {}").toUpperCase(), joke.getBody().toUpperCase());
            stillMoneyForCheapJokes = getCurrentBalance(CredentialsUtil.deriveLibraAddress(clientCredentials)) > 1;
            if (stillMoneyForCheapJokes) {
                log.info("I still have some money left, lets go for another one.");
            }
        }
    }

    private long getCurrentBalance(String address) throws IOException {
        return libraClient.getAccount(AccountAddress.fromHexString(address)).balances().stream().mapToLong(Amount::amount).sum();
    }

    private Long sendLibraTestnetTransaction(KeyPair clientCredentials, String proxyAddress, String trxId) throws InterruptedException {
        AuthenticationKey authenticationKey = AuthenticationKey.fromPublicKey(CredentialsUtil.deriveLibraPublicKey(clientCredentials));
        AccountAddress sourceAccount = AccountAddress.fromAuthenticationKey(authenticationKey);
        log.info("Source account authentication key: {}, address: {}", authenticationKey, sourceAccount);
        Account accountState = libraClient.getAccount(sourceAccount);

        // If the account already exists, then the authentication key of the target
        // account is not required and the account address would be enough
        //AuthenticationKey authenticationKeyTarget = AuthenticationKey.fromPublicKey(proxyCredentials.getPublic());

        long amount = 1;
        long sequenceNumber = accountState.sequenceNumber();

        log.info("Sending from {} to {}", AccountAddress.fromAuthenticationKey(authenticationKey),
                proxyAddress);

        // Arguments for the peer to peer transaction
        U64Argument amountArgument = U64Argument.from(amount * 1000000);
        AccountAddressArgument addressArgument = AccountAddressArgument.from(
                AccountAddress.fromHexString(proxyAddress));
        U8VectorArgument metadataArgument = U8VectorArgument.from(
                ByteArray.from(trxId.getBytes(UTF_8)));
        // signature can be used for approved transactions, we are not doing that and
        // can set the signature as an empty byte array
        U8VectorArgument signatureArgument = U8VectorArgument.from(
                ByteArray.from(new byte[0]));

        Transaction transaction = ImmutableTransaction.builder()
                .sequenceNumber(sequenceNumber)
                .maxGasAmount(1640000)
                .gasCurrencyCode("Coin1")
                .gasUnitPrice(1)
                .sender(sourceAccount)
                .expirationTimestampSecs(Instant.now().getEpochSecond() + 60)
                .payload(ImmutableScript.builder()
                        .typeArguments(asList(Struct.typeTagForCurrency("Coin1")))
                        .code(Move.peerToPeerTransferWithMetadata())
                        .addArguments(addressArgument, amountArgument, metadataArgument,
                                signatureArgument)
                        .build())
                .chainId(ChainId.TESTNET)
                .build();

        SignedTransaction signedTransaction = ImmutableSignedTransaction.builder()
                .authenticator(ImmutableTransactionAuthenticatorEd25519.builder()
                        .publicKey(PublicKey.fromPublicKey(clientCredentials.getPublic()))
                        .signature(Signature.signTransaction(transaction, clientCredentials.getPrivate()))
                        .build())
                .transaction(transaction)
                .build();

        libraClient.submit(signedTransaction);

        // get the transaction and read the metadata
        Wait.until(transactionFound(AccountAddress.fromAuthenticationKey(authenticationKey), sequenceNumber, libraClient));
        dev.jlibra.client.views.transaction.Transaction tx = libraClient.getAccountTransaction(sourceAccount, sequenceNumber, false);
        UserTransaction t = (UserTransaction) tx.transaction();
        PeerToPeerWithMetadataScript script = (PeerToPeerWithMetadataScript) t.script();

        log.debug("Sending with Metadata: {}", new String(Hex.decode(script.metadata())));

        return tx.version();
    }}
