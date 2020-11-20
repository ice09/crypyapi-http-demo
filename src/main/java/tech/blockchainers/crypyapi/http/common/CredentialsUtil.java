package tech.blockchainers.crypyapi.http.common;

import lombok.extern.slf4j.Slf4j;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.utils.Numeric;

import java.math.BigInteger;


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

    public static Credentials createFromPrivateKey(String privateKeyHex) {
        return Credentials.create(privateKeyHex);
    }

}
