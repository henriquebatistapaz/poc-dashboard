package dev.henrique.collectoroauth;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

/**
 * Uso: MASTODON_ACCESS_TOKEN=... java -jar poc-collector-oauth.jar "termo de busca" [limite]
 * MASTODON_INSTANCE é opcional (padrão https://mastodon.social). O token vem só do ambiente.
 */
public final class MastodonCli {
    private static final String DEFAULT_INSTANCE = "https://mastodon.social";

    private MastodonCli() {
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Uso: informe o termo de busca. Ex.: \"open data\" 20");
            System.exit(2);
        }
        int limit = 20;
        String query = args[0];
        if (args.length > 1) {
            try {
                limit = Integer.parseInt(args[args.length - 1]);
                query = String.join(" ", Arrays.asList(args).subList(0, args.length - 1));
            } catch (NumberFormatException ignored) {
                query = String.join(" ", args);
            }
        }
        try {
            MastodonConfig config = configFromEnvironment();
            List<MastodonPost> posts = new MastodonSearchService(config).searchPublicStatuses(query, limit);
            for (MastodonPost post : posts) {
                System.out.println(post.id() + "\t" + post.createdAt() + "\t" + post.url() + "\t" + oneLine(post.text()));
            }
            System.out.println("Total: " + posts.size());
        } catch (IllegalArgumentException | MastodonException exception) {
            System.err.println("Erro: " + exception.getMessage());
            System.exit(1);
        }
    }

    static MastodonConfig configFromEnvironment() {
        String instance = System.getenv().getOrDefault("MASTODON_INSTANCE", DEFAULT_INSTANCE);
        String token = System.getenv("MASTODON_ACCESS_TOKEN");
        return new MastodonConfig(URI.create(instance), token);
    }

    private static String oneLine(String text) {
        return text == null ? "" : text.replace('\n', ' ').replace('\t', ' ');
    }
}
