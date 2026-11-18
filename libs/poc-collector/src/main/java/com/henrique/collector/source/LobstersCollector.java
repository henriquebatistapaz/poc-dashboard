package com.henrique.collector.source;

import com.henrique.collector.feed.FeedItem;
import java.net.URI;
import java.util.List;

public final class LobstersCollector implements FeedCollector {
    private static final URI ENDPOINT = URI.create("https://lobste.rs");
    private final RestJsonClient client;

    public LobstersCollector() { this(ENDPOINT); }
    public LobstersCollector(URI endpoint) { this.client = new RestJsonClient(endpoint); }

    @Override public List<FeedItem> collect(String input) {
        String tag = input == null || input.isBlank() ? "hottest"
                : RestJsonSupport.requiredInput(input, 100, "Lobsters tag");
        if (!tag.equals("hottest") && !tag.matches("[A-Za-z0-9]{1,100}")) {
            throw new IllegalArgumentException("Invalid Lobsters tag.");
        }
        URI uri = client.baseUri().resolve(tag.equals("hottest") ? "/hottest.json" : "/t/" + tag + ".json");
        return RestJsonSupport.items(client.get(uri, java.util.Map.of()), null, CollectorSource.LOBSTERS, node -> {
            String id = RestJsonSupport.value(node, "short_id");
            String title = RestJsonSupport.cleanTitle(RestJsonSupport.value(node, "title"));
            URI link = RestJsonSupport.safeUri(RestJsonSupport.first(node, "url", "comments_url"));
            return RestJsonSupport.item(CollectorSource.LOBSTERS, id, title, link,
                    RestJsonSupport.instant(RestJsonSupport.value(node, "created_at")));
        });
    }
}
