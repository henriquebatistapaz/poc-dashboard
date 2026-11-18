package com.henrique.collector.source;

import com.henrique.collector.feed.FeedItem;
import java.net.URI;
import java.util.List;
import java.util.Map;

public final class MastodonCollector implements FeedCollector {
    private static final URI ENDPOINT = URI.create("https://mastodon.social/api/v1/timelines/tag");
    private final RestJsonClient client;

    public MastodonCollector() { this(ENDPOINT); }
    public MastodonCollector(URI endpoint) { this.client = new RestJsonClient(endpoint); }

    @Override public List<FeedItem> collect(String input) {
        String tag = RestJsonSupport.requiredInput(input, 101, "Mastodon hashtag");
        if (tag.startsWith("#")) tag = tag.substring(1);
        if (!tag.matches("[A-Za-z0-9_]{1,100}")) throw new IllegalArgumentException("Invalid Mastodon hashtag.");
        URI uri = URI.create(client.baseUri().toASCIIString() + "/" + tag + "?limit=40");
        return RestJsonSupport.items(client.get(uri, Map.of()), null, CollectorSource.MASTODON, node -> {
            String id = RestJsonSupport.value(node, "id");
            String title = RestJsonSupport.cleanTitle(RestJsonSupport.first(node, "content", "spoiler_text"));
            URI link = RestJsonSupport.safeUri(RestJsonSupport.first(node, "url", "uri"));
            return RestJsonSupport.item(CollectorSource.MASTODON, id, title, link,
                    RestJsonSupport.instant(RestJsonSupport.value(node, "created_at")));
        });
    }
}
