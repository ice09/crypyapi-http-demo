package tech.blockchainers.crypyapi.http.common.rest;

import org.bouncycastle.util.encoders.Hex;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.web3j.crypto.Credentials;
import tech.blockchainers.crypyapi.http.common.CredentialsUtil;
import tech.blockchainers.crypyapi.http.common.proxy.EthereumCorrelationService;
import tech.blockchainers.crypyapi.http.common.proxy.PaymentDto;
import tech.blockchainers.crypyapi.http.service.SignatureService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public abstract class EthereumServiceControllerProxy {

    protected final EthereumCorrelationService ethereumCorrelationService;
    private final SignatureService signatureService;
    private final Credentials serviceCredentials;

    public EthereumServiceControllerProxy(EthereumCorrelationService ethereumCorrelationService, SignatureService signatureService, Credentials serviceCredentials) {
        this.ethereumCorrelationService = ethereumCorrelationService;
        this.signatureService = signatureService;
        this.serviceCredentials = serviceCredentials;
    }

    @GetMapping("/setup")
    public PaymentDto setupServicePayment(@RequestParam String address) {
        PaymentDto paymentDto =  PaymentDto.builder().trxId(ethereumCorrelationService.storeNewIdentifier(address)).build();
        paymentDto.setTrxIdHex("0x" + Hex.toHexString(paymentDto.getTrxId().getBytes(StandardCharsets.UTF_8)));
        paymentDto.setServiceAddress(serviceCredentials.getAddress());
        return paymentDto;
    }

    public boolean isServiceCallAllowed(int amountInWei, String trxHash, String signedTrxId) throws IOException, InterruptedException {
        return ethereumCorrelationService.isServiceCallAllowed(amountInWei, trxHash, signedTrxId);
    }
}
