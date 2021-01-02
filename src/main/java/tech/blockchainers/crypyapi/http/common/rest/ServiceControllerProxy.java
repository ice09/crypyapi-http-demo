package tech.blockchainers.crypyapi.http.common.rest;

import com.google.common.collect.Maps;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.web3j.crypto.Credentials;
import tech.blockchainers.crypyapi.http.common.CredentialsUtil;
import tech.blockchainers.crypyapi.http.common.annotation.Payable;
import tech.blockchainers.crypyapi.http.common.proxy.CorrelationService;
import tech.blockchainers.crypyapi.http.common.proxy.PaymentDto;
import tech.blockchainers.crypyapi.http.service.SignatureService;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public abstract class ServiceControllerProxy {

    protected final CorrelationService correlationService;
    private final SignatureService signatureService;
    private final Credentials serviceCredentials;
    private final Map<String, Integer> costsOfServices = Maps.newHashMap();

    public ServiceControllerProxy(CorrelationService correlationService, SignatureService signatureService, Credentials serviceCredentials) {
        this.correlationService = correlationService;
        this.signatureService = signatureService;
        this.serviceCredentials = serviceCredentials;
        initializeCostsOfServices();
    }

    private void initializeCostsOfServices() {
        Method[] methods = this.getClass().getDeclaredMethods();
        for (Method method : methods) {
            int modifiers = method.getModifiers();
            if (Modifier.isPublic(modifiers) && method.isAnnotationPresent(Payable.class)) {
                Payable payable = method.getAnnotation(Payable.class);
                costsOfServices.put(payable.service(), payable.equivalentValueInWei());
            }
        }
    }

    @GetMapping("/setup")
    public PaymentDto setupServicePayment(@RequestParam String address, @RequestParam String service) {
        if (!costsOfServices.containsKey(service)) {
            throw new IllegalArgumentException("Unknown Service.");
        }
        PaymentDto paymentDto =  PaymentDto.builder().trxId(correlationService.storeNewIdentifier(address)).build();
        paymentDto.setTrxIdHex("0x" + Hex.toHexString(paymentDto.getTrxId().getBytes(StandardCharsets.UTF_8)));
        paymentDto.setServiceAddress(serviceCredentials.getAddress());
        paymentDto.setAmount(costsOfServices.get(service));
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

    public boolean isServiceCallAllowed(int amountInWei, String trxHash, String signedTrxId) throws IOException, InterruptedException {
        return correlationService.isServiceCallAllowed(amountInWei, trxHash, signedTrxId);
    }
}
