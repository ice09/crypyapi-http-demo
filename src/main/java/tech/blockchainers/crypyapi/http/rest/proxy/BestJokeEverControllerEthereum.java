package tech.blockchainers.crypyapi.http.rest.proxy;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.web3j.crypto.Credentials;
import tech.blockchainers.crypyapi.http.common.annotation.Payable;
import tech.blockchainers.crypyapi.http.common.annotation.enums.StableCoin;
import tech.blockchainers.crypyapi.http.common.proxy.EthereumCorrelationService;
import tech.blockchainers.crypyapi.http.common.rest.EthereumServiceControllerProxy;
import tech.blockchainers.crypyapi.http.rest.paid.BestJokeEverService;
import tech.blockchainers.crypyapi.http.service.SignatureService;
import tech.blockchainers.crypyapi.http.common.annotation.enums.Currency;

import java.io.IOException;

@RestController()
@RequestMapping("/jokeForXDai")
public class BestJokeEverControllerEthereum extends EthereumServiceControllerProxy {

    private final BestJokeEverService bestJokeEverService;

    public BestJokeEverControllerEthereum(EthereumCorrelationService ethereumCorrelationService, SignatureService signatureService, Credentials serviceCredentials, BestJokeEverService bestJokeEverService) {
        super(ethereumCorrelationService, signatureService, serviceCredentials);
        this.bestJokeEverService = bestJokeEverService;
    }

    @Payable(currency=Currency.wUSD, equivalentValue=100, accepted=StableCoin.XDAI)
    @GetMapping("/request")
    public String requestService(@RequestHeader("CPA-Transaction-Hash") String trxHash, @RequestHeader("CPA-Signed-Identifier") String signedTrxId) throws IOException, InterruptedException {
        return bestJokeEverService.getBestJokeEver();
    }

}
