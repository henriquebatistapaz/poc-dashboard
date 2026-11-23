package dev.henrique.collector.source;

import dev.henrique.collector.feed.FeedItem;
import java.net.URI;
import java.util.List;
import java.util.Map;

public final class GitHubCollector implements FeedCollector {
    private static final URI ENDPOINT = URI.create("https://api.github.com/search/repositories");
    private final RestJsonClient client;

    public GitHubCollector() { this(ENDPOINT); }
    public GitHubCollector(URI endpoint) { this.client = new RestJsonClient(endpoint); }

    @Override public List<FeedItem> collect(String input) {
        String query = RestJsonSupport.requiredInput(input, 200, "GitHub query");
        URI uri = RestJsonSupport.appendQuery(client.baseUri(), Map.of("q", query, "sort", "updated", "per_page", "30"));
        return RestJsonSupport.items(client.get(uri, Map.of("Accept", "application/vnd.github+json")),
                "items", CollectorSource.GITHUB, node -> {
                    String id = RestJsonSupport.value(node, "id");
                    String name = RestJsonSupport.cleanTitle(RestJsonSupport.value(node, "full_name"));
                    String description = RestJsonSupport.cleanTitle(RestJsonSupport.value(node, "description"));
                    String title = name == null ? null : RestJsonSupport.cleanTitle(description == null ? name : name + " — " + description);
                    URI link = RestJsonSupport.safeUri(RestJsonSupport.value(node, "html_url"));
                    return RestJsonSupport.item(CollectorSource.GITHUB, id, title, link,
                            RestJsonSupport.instant(RestJsonSupport.value(node, "updated_at")));
                });
    }
}
