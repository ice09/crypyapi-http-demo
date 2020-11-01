package tech.blockchainers.crypyapi.http.rest.paid;

import org.apache.commons.lang3.RandomUtils;
import org.springframework.stereotype.Service;

@Service
public class BestJokeEverService {

    private String[] jokes = {
            "MacGyver can build an airplane out of gum and paper clips. Chuck Norris can kill him and take it.",
            "Chuck Norris doesn't read books. He stares them down until he gets the information he wants.",
            "Chuck Norris lost his virginity before his dad did.",
            "Chuck Norris sheds his skin twice a year.",
            "Chuck Norris once challenged Lance Armstrong in a \"Who has more testicles?\" contest. Chuck Norris won by 5.",
            "Chuck Norris doesn't shower, he only takes blood baths.",
            "In the Bible, Jesus turned water into wine. But then Chuck Norris turned that wine into beer.",
            "Chuck Norris has two speeds: Walk and Kill."
    };

    public String getBestJokeEver() {
        return jokes[RandomUtils.nextInt(0, jokes.length)];
    }


}
