package com.henrique.collector.source;

import com.henrique.collector.feed.FeedItem;
import java.net.URI;
import java.util.List;
import java.util.Map;

public final class HackerNewsCollector implements FeedCollector {
    private static final URI ENDPOINT = URI.create("https://hn.algolia.com/api/v1/search");
    private final RestJsonClient client;

    public HackerNewsCollector() { this(ENDPOINT); }
    public HackerNewsCollector(URI endpoint) { this.client = new RestJsonClient(endpoint); }

    @Override public List<FeedItem> collect(String input) {
        String query = RestJsonSupport.requiredInput(input, 200, "Hacker News query");
        URI uri = RestJsonSupport.appendQuery(client.baseUri(), Map.of("query", query, "tags", "story", "hitsPerPage", "30"));
        return RestJsonSupport.items(client.get(uri, Map.of()), "hits", CollectorSource.HACKER_NEWS, node -> {
            String id = RestJsonSupport.value(node, "objectID");
            String title = RestJsonSupport.cleanTitle(RestJsonSupport.value(node, "title"));
            URI link = RestJsonSupport.safeUri(RestJsonSupport.value(node, "url"),
                    id == null ? null : "https://news.ycombinator.com/item?id=" + RestJsonSupport.query(id));
            return RestJsonSupport.item(CollectorSource.HACKER_NEWS, id, title, link,
                    RestJsonSupport.instant(RestJsonSupport.value(node, "created_at")));
        });
    }
}
