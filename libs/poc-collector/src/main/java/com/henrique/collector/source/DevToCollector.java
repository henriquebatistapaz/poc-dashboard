package com.henrique.collector.source;

import com.henrique.collector.feed.FeedItem;
import java.net.URI;
import java.util.List;
import java.util.Map;

public final class DevToCollector implements FeedCollector {
    private static final URI ENDPOINT = URI.create("https://dev.to/api/articles");
    private final RestJsonClient client;

    public DevToCollector() { this(ENDPOINT); }
    public DevToCollector(URI endpoint) { this.client = new RestJsonClient(endpoint); }

    @Override public List<FeedItem> collect(String input) {
        String value = RestJsonSupport.requiredInput(input, 101, "DEV.to tag or username");
        String key = "tag";
        if (value.startsWith("@")) {
            value = value.substring(1);
            key = "username";
        }
        if (!value.matches("[A-Za-z0-9_-]{1,100}")) throw new IllegalArgumentException("Invalid DEV.to input.");
        URI uri = RestJsonSupport.appendQuery(client.baseUri(), Map.of(key, value, "per_page", "30"));
        return RestJsonSupport.items(client.get(uri, Map.of()), null, CollectorSource.DEV_TO, node -> {
            String id = RestJsonSupport.value(node, "id");
            String title = RestJsonSupport.cleanTitle(RestJsonSupport.value(node, "title"));
            URI link = RestJsonSupport.safeUri(RestJsonSupport.first(node, "url", "canonical_url"));
            return RestJsonSupport.item(CollectorSource.DEV_TO, id, title, link,
                    RestJsonSupport.instant(RestJsonSupport.value(node, "published_at")));
        });
    }
}
