package com.henrique.collector.source;

import com.henrique.collector.feed.FeedItem;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class StackExchangeCollector implements FeedCollector {
    private static final URI ENDPOINT = URI.create("https://api.stackexchange.com/2.3/search");
    private final RestJsonClient client;

    public StackExchangeCollector() { this(ENDPOINT); }
    public StackExchangeCollector(URI endpoint) { this.client = new RestJsonClient(endpoint); }

    @Override public List<FeedItem> collect(String input) {
        String query = RestJsonSupport.requiredInput(input, 200, "Stack Exchange query");
        URI uri = RestJsonSupport.appendQuery(client.baseUri(), Map.of("order", "desc", "sort", "activity",
                "intitle", query, "site", "stackoverflow", "pagesize", "30"));
        return RestJsonSupport.items(client.get(uri, Map.of()), "items", CollectorSource.STACK_EXCHANGE, node -> {
            String id = RestJsonSupport.value(node, "question_id");
            String title = RestJsonSupport.cleanTitle(RestJsonSupport.value(node, "title"));
            URI link = RestJsonSupport.safeUri(RestJsonSupport.value(node, "link"));
            Instant publishedAt = null;
            if (node.path("creation_date").canConvertToLong()) {
                try { publishedAt = Instant.ofEpochSecond(node.path("creation_date").asLong()); }
                catch (RuntimeException ignored) { }
            }
            return RestJsonSupport.item(CollectorSource.STACK_EXCHANGE, id, title, link, publishedAt);
        });
    }
}
