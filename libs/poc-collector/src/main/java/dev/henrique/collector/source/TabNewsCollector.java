package dev.henrique.collector.source;

import dev.henrique.collector.feed.FeedItem;
import java.net.URI;
import java.util.List;
import java.util.Map;

public final class TabNewsCollector implements FeedCollector {
    private static final URI ENDPOINT = URI.create("https://www.tabnews.com.br/api/v1/contents");
    private final RestJsonClient client;

    public TabNewsCollector() { this(ENDPOINT); }
    public TabNewsCollector(URI endpoint) { this.client = new RestJsonClient(endpoint); }

    @Override public List<FeedItem> collect(String input) {
        String value = RestJsonSupport.requiredInput(input, 200, "TabNews input");
        URI uri;
        String filter = null;
        if (value.startsWith("@")) {
            String username = value.substring(1);
            if (!username.matches("[A-Za-z0-9_-]{1,39}")) throw new IllegalArgumentException("Invalid TabNews username.");
            uri = URI.create(client.baseUri().toASCIIString() + "/" + username + "?per_page=30");
        } else if (value.equals("relevant") || value.equals("new") || value.equals("old")) {
            uri = RestJsonSupport.appendQuery(client.baseUri(), Map.of("strategy", value, "per_page", "30"));
        } else {
            filter = value.toLowerCase(java.util.Locale.ROOT);
            uri = RestJsonSupport.appendQuery(client.baseUri(), Map.of("strategy", "relevant", "per_page", "100"));
        }
        List<FeedItem> items = RestJsonSupport.items(client.get(uri, Map.of()), null, CollectorSource.TABNEWS, node -> {
            String id = RestJsonSupport.value(node, "id");
            String title = RestJsonSupport.cleanTitle(RestJsonSupport.value(node, "title"));
            String owner = RestJsonSupport.value(node, "owner_username");
            String slug = RestJsonSupport.value(node, "slug");
            String canonical = owner == null || slug == null || !owner.matches("[A-Za-z0-9_-]{1,39}")
                    || !slug.matches("[A-Za-z0-9_-]{1,200}") ? null
                    : "https://www.tabnews.com.br/" + owner + "/" + slug;
            URI link = RestJsonSupport.safeUri(canonical);
            return RestJsonSupport.item(CollectorSource.TABNEWS, id, title, link,
                    RestJsonSupport.instant(RestJsonSupport.value(node, "published_at")));
        });
        if (filter == null) return items;
        String titleFilter = filter;
        return items.stream().filter(item -> item.title().toLowerCase(java.util.Locale.ROOT).contains(titleFilter)).toList();
    }
}
