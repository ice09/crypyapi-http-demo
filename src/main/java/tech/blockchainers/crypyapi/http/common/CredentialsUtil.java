package tech.blockchainers.crypyapi.http.common;

import dev.jlibra.AccountAddress;
import dev.jlibra.AuthenticationKey;
import dev.jlibra.serialization.ByteArray;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jcajce.provider.asymmetric.edec.BCEdDSAPrivateKey;
import org.bouncycastle.jcajce.provider.asymmetric.edec.BCEdDSAPublicKey;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;

@Slf4j
public class CredentialsUtil {

    public static Credentials createRandomEthereumCredentials() {
        try {
            // create new private/public key pair
            ECKeyPair keyPair = Keys.createEcKeyPair();

            BigInteger publicKey = keyPair.getPublicKey();
            String publicKeyHex = Numeric.toHexStringWithPrefix(publicKey);

            BigInteger privateKey = keyPair.getPrivateKey();
            String privateKeyHex = Numeric.toHexStringWithPrefix(privateKey);

            // create credentials + address from private/public key pair
            Credentials credentials = Credentials.create(new ECKeyPair(privateKey, publicKey));
            String address = credentials.getAddress();

            // print resulting data of new account
            log.info("private key: '" + privateKeyHex + "'");
            log.debug("public key: '" + publicKeyHex + "'");
            log.info("address:     '" + address + "'\n");
            return credentials;
        } catch (Exception ex) {
            log.error("Cannot create Credentials.", ex);
            return null;
        }
    }
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

    public static Credentials createFromPrivateKey(String privateKeyHex) {
        return Credentials.create(privateKeyHex);
    }

}
