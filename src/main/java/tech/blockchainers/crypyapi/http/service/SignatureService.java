package tech.blockchainers.crypyapi.http.service;

import dev.jlibra.Hash;
import dev.jlibra.LibraRuntimeException;
import dev.jlibra.serialization.ByteArray;
import dev.jlibra.transaction.ImmutableSignature;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

@Slf4j
@Service
public class SignatureService {

    public String sign(String message, KeyPair keyPair) {
        return Hex.toHexString(sign(message.getBytes(StandardCharsets.UTF_8), keyPair.getPrivate()));
    }

    private byte[] sign(byte[] payload, PrivateKey privateKey) {
        byte[] signature;
        try {
            java.security.Signature sgr = java.security.Signature.getInstance("Ed25519", "BC");
            sgr.initSign(privateKey);
            sgr.update(createProof(payload));
            signature = sgr.sign();
        } catch (Exception var5) {
            throw new LibraRuntimeException("Signing the transaction failed", var5);
        }

        return ImmutableSignature.builder().signature(ByteArray.from(signature)).build().getSignature().toArray();
    }

    public byte[] createProof(byte[] payload) {
        return Hash.ofInput(ByteArray.from(payload)).hash(ByteArray.from("LIBRA::RawTransaction".getBytes())).toArray();
    }

    public boolean verify(byte[] payload, byte[] signature, PublicKey publicKey) {
        try {
            java.security.Signature sgr = java.security.Signature.getInstance("Ed25519", "BC");
            sgr.initVerify(publicKey);
            sgr.update(createProof(payload));
            return sgr.verify(signature);
        } catch (Exception var5) {
            throw new LibraRuntimeException("Verifying the transaction failed", var5);
        }
    }


}