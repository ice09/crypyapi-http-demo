package tech.blockchainers.crypyapi.http.common.rest;

import org.bouncycastle.util.encoders.Hex;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import tech.blockchainers.crypyapi.http.common.CredentialsUtil;
import tech.blockchainers.crypyapi.http.common.proxy.LibraCorrelationService;
import tech.blockchainers.crypyapi.http.common.proxy.PaymentDto;
import tech.blockchainers.crypyapi.http.service.SignatureService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

public abstract class LibraServiceControllerProxy {

    protected final LibraCorrelationService libraCorrelationService;
    private final SignatureService signatureService;
    private final KeyPair keyPair;

    public LibraServiceControllerProxy(LibraCorrelationService libraCorrelationService, SignatureService signatureService, KeyPair keyPair) {
        this.libraCorrelationService = libraCorrelationService;
        this.signatureService = signatureService;
        this.keyPair = keyPair;
    }

    @GetMapping("/setup")
    public PaymentDto setupServicePayment(@RequestParam String address) {
        PaymentDto paymentDto =  PaymentDto.builder().trxId(libraCorrelationService.storeNewIdentifier(address)).build();
        paymentDto.setTrxIdHex("0x" + Hex.toHexString(paymentDto.getTrxId().getBytes(StandardCharsets.UTF_8)));
        paymentDto.setServiceAddress(CredentialsUtil.deriveLibraAddress(keyPair));
        return paymentDto;
    }

    public boolean isServiceCallAllowed(int amountInWei, String trxHash, String signedTrxId) throws IOException, InterruptedException, NoSuchAlgorithmException, InvalidKeySpecException {
        return libraCorrelationService.isServiceCallAllowed(amountInWei, trxHash, signedTrxId);
    }
}
