package tech.blockchainers.crypyapi.http.rest.proxy;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.blockchainers.crypyapi.http.common.annotation.Payable;
import tech.blockchainers.crypyapi.http.common.annotation.enums.Currency;
import tech.blockchainers.crypyapi.http.common.annotation.enums.StableCoin;
import tech.blockchainers.crypyapi.http.common.proxy.LibraCorrelationService;
import tech.blockchainers.crypyapi.http.common.rest.LibraServiceControllerProxy;
import tech.blockchainers.crypyapi.http.rest.paid.BestJokeEverService;
import tech.blockchainers.crypyapi.http.service.SignatureService;

import java.io.IOException;
import java.security.KeyPair;

@RestController()
@RequestMapping("/jokeForLibra")
public class BestJokeEverControllerLibra extends LibraServiceControllerProxy {

    private final BestJokeEverService bestJokeEverService;

    public BestJokeEverControllerLibra(LibraCorrelationService libraCorrelationService, SignatureService signatureService, KeyPair keyPair, BestJokeEverService bestJokeEverService) {
        super(libraCorrelationService, signatureService, keyPair);
        this.bestJokeEverService = bestJokeEverService;
    }

    @Payable(currency=Currency.wUSD, equivalentValue=100, accepted=StableCoin.LIBRA)
    @GetMapping("/request")
    public String requestServiceForLibra(@RequestHeader("CPA-Transaction-Hash") String trxHash, @RequestHeader("CPA-Signed-Identifier") String signedTrxId) throws IOException, InterruptedException {
        return bestJokeEverService.getBestJokeEver();
    }}
