package tech.blockchainers.crypyapi.http.common;

import dev.jlibra.AccountAddress;
import dev.jlibra.AuthenticationKey;
import dev.jlibra.PublicKey;
import dev.jlibra.client.LibraClient;
import dev.jlibra.client.views.Account;
import dev.jlibra.client.views.transaction.PeerToPeerWithMetadataScript;
import dev.jlibra.client.views.transaction.UserTransaction;
import dev.jlibra.faucet.Faucet;
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
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.annotation.EnableScheduling;
import tech.blockchainers.crypyapi.http.common.proxy.LibraCorrelationService;
import tech.blockchainers.crypyapi.http.service.SignatureService;

import java.io.IOException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.concurrent.ExecutionException;

import static dev.jlibra.poller.Conditions.accountHasPositiveBalance;
import static dev.jlibra.poller.Conditions.transactionFound;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@EnableScheduling
@Slf4j
public class LibraIntegrationTest {

    @Autowired
    private LibraCorrelationService libraCorrelationService;

    @Autowired
    private SignatureService signatureService;

    @Autowired
    private KeyPair proxyCredentials;

    @Autowired
    private LibraClient libraClient;

    @Test
    public void testPaymentFlow() throws IOException, InterruptedException, NoSuchAlgorithmException {
        log.debug("Signing Address {}", CredentialsUtil.deriveLibraAddress(proxyCredentials));
        String trxId = libraCorrelationService.storeNewIdentifier(CredentialsUtil.deriveLibraAddress(proxyCredentials));
        libraCorrelationService.notifyOfTransaction(0, trxId, "0xHASH");
        String signedTrxId = signatureService.sign(trxId, proxyCredentials);
        boolean isServiceCallAllowed = libraCorrelationService.isServiceCallAllowed(0, "0xHASH", signedTrxId);
        assertTrue(isServiceCallAllowed);
    }

    @Test
    public void testCompleteLifecycle() throws ExecutionException, InterruptedException, IOException, NoSuchAlgorithmException {
        KeyPair clientCredentials = mintAmount();
        String trxId = libraCorrelationService.storeNewIdentifier(CredentialsUtil.deriveLibraAddress(clientCredentials));
        String trxHash = sendLibraTestnetTransaction(clientCredentials, trxId);
        log.info("Sent transaction {}", trxHash);
        pause(2000);
        String signedTrxId = signatureService.sign(trxId, clientCredentials);
        boolean isServiceCallAllowed = libraCorrelationService.isServiceCallAllowed(0, trxHash, signedTrxId);
        assertTrue(isServiceCallAllowed);
    }

    private void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {}
    }

    private KeyPair mintAmount() {
        Faucet faucet = Faucet.builder().build();
        KeyPair clientCredentials = CredentialsUtil.createRandomLibraCredentials();
        AuthenticationKey authKey = CredentialsUtil.deriveLibraAuthenticationKey(clientCredentials.getPublic());

        faucet.mint(authKey, 100L * 1_000_000L, "Coin1");

        LibraClient client = LibraClient.builder()
                .withUrl("https://client.testnet.libra.org/v1/")
                .build();

        Wait.until(accountHasPositiveBalance(AccountAddress.fromAuthenticationKey(authKey), client));

        Account account = client.getAccount(AccountAddress.fromAuthenticationKey(authKey));
        log.info("Balance: {} {}", account.balances().get(0).amount() / 1_000_000,
                account.balances().get(0).currency());
        return clientCredentials;
    }

    private String sendLibraTestnetTransaction(KeyPair clientCredentials, String trxId) throws InterruptedException {
        AuthenticationKey authenticationKey = AuthenticationKey.fromPublicKey(CredentialsUtil.deriveLibraPublicKey(clientCredentials));
        AccountAddress sourceAccount = AccountAddress.fromAuthenticationKey(authenticationKey);
        log.info("Source account authentication key: {}, address: {}", authenticationKey, sourceAccount);
        Account accountState = libraClient.getAccount(sourceAccount);

        // If the account already exists, then the authentication key of the target
        // account is not required and the account address would be enough
        AuthenticationKey authenticationKeyTarget = AuthenticationKey
                .fromPublicKey(proxyCredentials.getPublic());

        long amount = 1;
        long sequenceNumber = accountState.sequenceNumber();

        log.info("Sending from {} to {}", AccountAddress.fromAuthenticationKey(authenticationKey),
                AccountAddress.fromAuthenticationKey(authenticationKeyTarget));

        // Arguments for the peer to peer transaction
        U64Argument amountArgument = U64Argument.from(amount * 1000000);
        AccountAddressArgument addressArgument = AccountAddressArgument.from(
                AccountAddress.fromAuthenticationKey(authenticationKeyTarget));
        U8VectorArgument metadataArgument = U8VectorArgument.from(
                ByteArray.from("This is the metadata, you can put anything here!".getBytes(UTF_8)));
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
                        .signature(Signature.signTransaction(transaction, CredentialsUtil.deriveLibraPrivateKey(clientCredentials)))
                        .build())
                .transaction(transaction)
                .build();

        libraClient.submit(signedTransaction);

        // get the transaction and read the metadata
        //Wait.until(transactionFound(AccountAddress.fromAuthenticationKey(authenticationKey), sequenceNumber, libraClient));
        Thread.sleep(2000);
        UserTransaction t = (UserTransaction) libraClient.getAccountTransaction(sourceAccount, sequenceNumber, true)
                .transaction();
        PeerToPeerWithMetadataScript script = (PeerToPeerWithMetadataScript) t.script();

        log.info("Metadata: {}", new String(Hex.decode(script.metadata())));

        return String.valueOf(t.sequenceNumber());
    }
}
