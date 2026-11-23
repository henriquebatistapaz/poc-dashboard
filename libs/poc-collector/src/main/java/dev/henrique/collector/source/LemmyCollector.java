package dev.henrique.collector.source;

import dev.henrique.collector.feed.FeedItem;
import java.net.URI;
import java.util.List;
import java.util.Map;

public final class LemmyCollector implements FeedCollector {
    private static final URI ENDPOINT = URI.create("https://lemmy.world/api/v3/search");
    private final RestJsonClient client;

    public LemmyCollector() { this(ENDPOINT); }
    public LemmyCollector(URI endpoint) { this.client = new RestJsonClient(endpoint); }

    @Override public List<FeedItem> collect(String input) {
        String query = RestJsonSupport.requiredInput(input, 200, "Lemmy query");
        URI uri = RestJsonSupport.appendQuery(client.baseUri(), Map.of("q", query, "type_", "Posts", "limit", "40"));
        return RestJsonSupport.items(client.get(uri, Map.of()), "posts", CollectorSource.LEMMY, node -> {
            var post = node.path("post");
            String id = RestJsonSupport.value(post, "id");
            String title = RestJsonSupport.cleanTitle(RestJsonSupport.value(post, "name"));
            URI link = RestJsonSupport.safeUri(RestJsonSupport.first(post, "url", "ap_id"));
            return RestJsonSupport.item(CollectorSource.LEMMY, id, title, link,
                    RestJsonSupport.instant(RestJsonSupport.value(post, "published")));
        });
    }
}
