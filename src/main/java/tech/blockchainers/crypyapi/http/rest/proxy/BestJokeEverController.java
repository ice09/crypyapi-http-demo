package tech.blockchainers.crypyapi.http.rest.proxy;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.web3j.crypto.Credentials;
import tech.blockchainers.crypyapi.http.common.annotation.Payable;
import tech.blockchainers.crypyapi.http.common.annotation.enums.StableCoin;
import tech.blockchainers.crypyapi.http.common.proxy.CorrelationService;
import tech.blockchainers.crypyapi.http.common.rest.ServiceControllerProxy;
import tech.blockchainers.crypyapi.http.rest.paid.BestJokeEverService;
import tech.blockchainers.crypyapi.http.service.SignatureService;
import tech.blockchainers.crypyapi.http.common.annotation.enums.Currency;

import java.io.IOException;

@RestController()
@RequestMapping("/joke")
public class BestJokeEverController extends ServiceControllerProxy {

    private final BestJokeEverService bestJokeEverService;

    public BestJokeEverController(CorrelationService correlationService, SignatureService signatureService, Credentials serviceCredentials, BestJokeEverService bestJokeEverService) {
        super(correlationService, signatureService, serviceCredentials);
        this.bestJokeEverService = bestJokeEverService;
    }

    @Payable(currency=Currency.wUSD, equivalentValue=100, accepted={StableCoin.DAI, StableCoin.XDAI})
    @GetMapping("/request")
    public String requestService(@RequestHeader("CPA-Transaction-Hash") String trxHash, @RequestHeader("CPA-Signed-Identifier") String signedTrxId) throws IOException, InterruptedException {
        return bestJokeEverService.getBestJokeEver();
    }
}
