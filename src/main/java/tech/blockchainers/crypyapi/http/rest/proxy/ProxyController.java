package tech.blockchainers.crypyapi.http.rest.proxy;

import org.bouncycastle.util.encoders.Hex;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.web3j.crypto.Credentials;
import tech.blockchainers.crypyapi.http.common.CredentialsUtil;
import tech.blockchainers.crypyapi.http.service.SignatureService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
public class ProxyController {

    private final CorrelationService correlationService;
    private final SignatureService signatureService;
    private final Credentials serviceCredentials;

    public ProxyController(CorrelationService correlationService, SignatureService signatureService, Credentials serviceCredentials) {
        this.correlationService = correlationService;
        this.signatureService = signatureService;
        this.serviceCredentials = serviceCredentials;
    }

    @GetMapping("/setup")
    public PaymentDto setupServicePayment(@RequestParam String address) {
        PaymentDto paymentDto =  new PaymentDto.PaymentDtoBuilder().trxId(correlationService.storeNewIdentifier(address)).build();
        paymentDto.setTrxIdHex("0x" + Hex.toHexString(paymentDto.getTrxId().getBytes(StandardCharsets.UTF_8)));
        paymentDto.setServiceAddress(serviceCredentials.getAddress());
        return paymentDto;
    }

    @GetMapping("/signMessage")
    public PaymentDto signMessage(@RequestParam String trxId, @RequestParam String privateKey) {
        Credentials signer = CredentialsUtil.createFromPrivateKey(privateKey);
        PaymentDto paymentDto = correlationService.getCorrelationByTrxId(trxId);
        String signedTrxId = signatureService.sign(trxId, signer);
        paymentDto.setSignedTrxId(signedTrxId);
        return paymentDto;
   }

    @GetMapping("/request")
    //@Payable()
    public String requestService(@RequestHeader("CPA-Transaction-Hash") String trxHash, @RequestHeader("CPA-Signed-Identifier") String signedTrxId) throws IOException, InterruptedException {
        return correlationService.callService(trxHash, signedTrxId);
    }
}
