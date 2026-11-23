package dev.henrique.collector.source;

import dev.henrique.collector.feed.FeedItem;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class WikipediaCollector implements FeedCollector {
    private static final URI ENDPOINT = URI.create("https://en.wikipedia.org/w/api.php");
    private final RestJsonClient client;

    public WikipediaCollector() { this(ENDPOINT); }
    public WikipediaCollector(URI endpoint) { this.client = new RestJsonClient(endpoint); }

    @Override public List<FeedItem> collect(String input) {
        String term = RestJsonSupport.requiredInput(input, 200, "Wikipedia query");
        URI uri = RestJsonSupport.appendQuery(client.baseUri(), Map.of(
                "action", "query", "list", "search", "srsearch", term, "format", "json"));
        var root = client.get(uri, Map.of());
        var results = root.path("query").path("search");
        if (!results.isArray()) throw new SourceAccessException("REST source returned an unexpected JSON shape.");
        List<FeedItem> items = new ArrayList<>();
        for (var result : results) {
            String id = RestJsonSupport.value(result, "pageid");
            String title = RestJsonSupport.cleanTitle(RestJsonSupport.value(result, "title"));
            String language = "pt.wikipedia.org".equalsIgnoreCase(client.baseUri().getHost()) ? "pt" : "en";
            URI link = id == null ? null : RestJsonSupport.safeUri("https://" + language + ".wikipedia.org/?curid=" + id);
            var item = RestJsonSupport.item(CollectorSource.WIKIPEDIA, id, title, link,
                    RestJsonSupport.instant(RestJsonSupport.value(result, "timestamp")));
            if (item != null) items.add(item);
        }
        return List.copyOf(items);
    }
}
