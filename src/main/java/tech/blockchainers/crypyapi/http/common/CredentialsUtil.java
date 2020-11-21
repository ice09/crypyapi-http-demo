package tech.blockchainers.crypyapi.http.common;

import dev.jlibra.AccountAddress;
import dev.jlibra.AuthenticationKey;
import dev.jlibra.serialization.ByteArray;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jcajce.provider.asymmetric.edec.BCEdDSAPrivateKey;
import org.bouncycastle.jcajce.provider.asymmetric.edec.BCEdDSAPublicKey;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;

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

}
