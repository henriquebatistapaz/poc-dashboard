package com.henrique.collector.source;

import java.util.List;
import java.util.Map;

public record RedditHttpResponse(int statusCode, String body, Map<String, List<String>> headers) {
    public String header(String name) {
        return headers.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .flatMap(entry -> entry.getValue().stream()).findFirst().orElse(null);
    }
}
