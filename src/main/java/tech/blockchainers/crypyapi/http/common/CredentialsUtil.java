package tech.blockchainers.crypyapi.http.common;

import dev.jlibra.AccountAddress;
import dev.jlibra.AuthenticationKey;
import dev.jlibra.client.LibraClient;
import dev.jlibra.client.views.Account;
import dev.jlibra.faucet.Faucet;
import dev.jlibra.poller.Wait;
import dev.jlibra.serialization.ByteArray;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jcajce.provider.asymmetric.edec.BCEdDSAPrivateKey;
import org.bouncycastle.jcajce.provider.asymmetric.edec.BCEdDSAPublicKey;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;

import static dev.jlibra.poller.Conditions.accountHasPositiveBalance;

@Slf4j
public class CredentialsUtil {

    public static KeyPair createRandomLibraCredentials() {
        try {
            // create new private/public key pair
            KeyPairGenerator kpGen = KeyPairGenerator.getInstance("Ed25519", "BC");
            KeyPair keyPair = kpGen.generateKeyPair();

            BCEdDSAPrivateKey privateKey = (BCEdDSAPrivateKey) keyPair.getPrivate();

            BCEdDSAPublicKey publicKey = (BCEdDSAPublicKey) keyPair.getPublic();

            AuthenticationKey authenticationKey = AuthenticationKey.fromPublicKey(publicKey);
            log.info("Libra address: {}",
                    AccountAddress.fromAuthenticationKey(authenticationKey));
            log.info("Authentication key: {}", authenticationKey);
            log.info("Public key: {}", ByteArray.from(publicKey.getEncoded()));
            log.info("Private key: {}", ByteArray.from(privateKey.getEncoded()));
            return keyPair;
        } catch (Exception ex) {
            log.error("Cannot create Credentials.", ex);
            return null;
        }
    }

    public static BCEdDSAPrivateKey deriveLibraPrivateKey(KeyPair keyPair) {
        return (BCEdDSAPrivateKey) keyPair.getPrivate();
    }

    public static BCEdDSAPublicKey deriveLibraPublicKey(KeyPair keyPair) {
        return (BCEdDSAPublicKey) keyPair.getPublic();
    }

    public static String deriveLibraAddress(KeyPair keyPair) {
        return AccountAddress.fromAuthenticationKey(deriveLibraAuthenticationKey(deriveLibraPublicKey(keyPair))).toString();
    }

    public static AuthenticationKey deriveLibraAuthenticationKey(PublicKey publicKey) {
        return AuthenticationKey.fromPublicKey(publicKey);
    }

    public static void mintAmount(KeyPair credentials) {
        Faucet faucet = Faucet.builder().build();
        AuthenticationKey authKey = CredentialsUtil.deriveLibraAuthenticationKey(credentials.getPublic());

        faucet.mint(authKey, 100L * 1_000_000L, "Coin1");

        LibraClient client = LibraClient.builder()
                .withUrl("https://client.testnet.libra.org/v1/")
                .build();

        Wait.until(accountHasPositiveBalance(AccountAddress.fromAuthenticationKey(authKey), client));

        Account account = client.getAccount(AccountAddress.fromAuthenticationKey(authKey));
        log.info("Balance: {} {} {}", account.address(), account.balances().get(0).amount() / 1_000_000,
                account.balances().get(0).currency());
    }

}
