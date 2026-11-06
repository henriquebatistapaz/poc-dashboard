package com.henrique.collector;

import com.henrique.collector.feed.FeedItem;
import java.util.List;

public final class FeedItemJson {
    private FeedItemJson() { }

    public static String encode(List<FeedItem> items) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) json.append(',');
            FeedItem item = items.get(i);
            json.append("{\"source\":\"").append(escape(item.source().name())).append("\",\"sourceId\":\"")
                    .append(escape(item.sourceId())).append("\",\"title\":\"").append(escape(item.title()))
                    .append("\",\"link\":\"").append(escape(item.link().toString())).append("\",\"publishedAt\":")
                    .append(item.publishedAt() == null ? "null" : "\"" + item.publishedAt() + "\"")
                    .append(",\"retrievedAt\":\"").append(item.retrievedAt()).append("\"}");
        }
        return json.append(']').toString();
    }

    private static String escape(String value) {
        StringBuilder out = new StringBuilder();
        for (char character : value.toCharArray()) {
            switch (character) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (character < 0x20) out.append(String.format("\\u%04x", (int) character));
                    else out.append(character);
                }
            }
        }
        return out.toString();
    }
}
