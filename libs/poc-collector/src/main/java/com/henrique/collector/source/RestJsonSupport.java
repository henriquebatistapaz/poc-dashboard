package com.henrique.collector.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.henrique.collector.feed.FeedItem;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class RestJsonSupport {
    private static final Pattern SCRIPT = Pattern.compile("(?is)<(script|style)\\b[^>]*>.*?</\\1>");
    private static final Pattern TAG = Pattern.compile("(?s)<[^>]*>");
    private static final Pattern ENTITY = Pattern.compile("&(#(?:x[0-9a-fA-F]+|[0-9]+)|amp|lt|gt|quot|apos|#39|nbsp);", Pattern.CASE_INSENSITIVE);

    private RestJsonSupport() { }

    static String requiredInput(String input, int max, String source) {
        String value = input == null ? "" : input.trim();
        if (value.isEmpty() || value.length() > max || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid " + source + " input.");
        }
        return value;
    }

    static String query(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    static String text(String value) {
        if (value == null) return "";
        String decoded = decodeEntities(value);
        String stripped = TAG.matcher(SCRIPT.matcher(decoded).replaceAll(" ")).replaceAll(" ");
        return stripped.replaceAll("\\s+", " ").trim();
    }

    private static String decodeEntities(String value) {
        Matcher matcher = ENTITY.matcher(value);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) matcher.appendReplacement(output, Matcher.quoteReplacement(decodeEntity(matcher.group(1))));
        matcher.appendTail(output);
        return output.toString();
    }

    private static String decodeEntity(String entity) {
        String key = entity.toLowerCase(Locale.ROOT);
        return switch (key) {
            case "amp" -> "&"; case "lt" -> "<"; case "gt" -> ">";
            case "quot" -> "\""; case "apos", "#39" -> "'"; case "nbsp" -> " ";
            default -> {
                try {
                    int codePoint = key.startsWith("#x") ? Integer.parseInt(key.substring(2), 16)
                            : Integer.parseInt(key.substring(1));
                    yield Character.isValidCodePoint(codePoint) && !Character.isSurrogate((char) codePoint)
                            ? new String(Character.toChars(codePoint)) : "";
                } catch (IllegalArgumentException exception) { yield ""; }
            }
        };
    }

    static String value(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText(null);
    }

    static String first(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = value(node, field);
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    static Instant instant(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Instant.parse(value); } catch (RuntimeException exception) { return null; }
    }

    static URI safeUri(String... candidates) {
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank() || candidate.length() > 2_000) continue;
            try {
                URI uri = URI.create(candidate.trim());
                String scheme = uri.getScheme();
                if (uri.getHost() != null && uri.getUserInfo() == null
                        && ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))) return uri;
            } catch (IllegalArgumentException ignored) { }
            return null;
        }
        return null;
    }

    static String cleanTitle(String value) {
        String title = text(value);
        if (title.isBlank()) return null;
        return title.length() <= 1_000 ? title : title.substring(0, 1_000);
    }

    static List<FeedItem> items(JsonNode root, String listField, CollectorSource source,
                                ItemMapper mapper) {
        JsonNode array = root.isArray() && listField == null ? root : root.get(listField);
        if (array == null || !array.isArray()) throw new SourceAccessException("REST source returned an unexpected JSON shape.");
        List<FeedItem> items = new ArrayList<>();
        for (JsonNode node : array) {
            FeedItem item = mapper.map(node);
            if (item != null) items.add(item);
        }
        return List.copyOf(items);
    }

    @FunctionalInterface
    interface ItemMapper { FeedItem map(JsonNode node); }

    static FeedItem item(CollectorSource source, String id, String title, URI link, Instant publishedAt) {
        if (id == null || id.isBlank() || id.length() > 2_000 || title == null || link == null) return null;
        return new FeedItem(source, id, title, link, publishedAt, Instant.now());
    }

    static URI appendQuery(URI base, Map<String, String> parameters) {
        String separator = base.getRawQuery() == null ? "?" : "&";
        String query = parameters.entrySet().stream()
                .map(entry -> query(entry.getKey()) + "=" + query(entry.getValue()))
                .collect(java.util.stream.Collectors.joining("&"));
        return URI.create(base.toASCIIString() + separator + query);
    }
}
