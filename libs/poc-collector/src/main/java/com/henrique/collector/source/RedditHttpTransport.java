package com.henrique.collector.source;

import java.net.URI;
import java.util.Map;

public interface RedditHttpTransport {
    RedditHttpResponse postForm(URI uri, Map<String, String> headers, String formBody);
    RedditHttpResponse get(URI uri, Map<String, String> headers);
}
