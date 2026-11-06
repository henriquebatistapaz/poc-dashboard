package com.henrique.collector.source;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class FakeRedditHttpTransport implements RedditHttpTransport {
    final List<String> calls = new ArrayList<>();
    URI lastUri;
    Map<String, String> lastHeaders;
    String lastForm;
    RedditHttpResponse postResponse = new RedditHttpResponse(200,
            "{\"access_token\":\"access-123\",\"token_type\":\"bearer\",\"expires_in\":3600}", Map.of());
    RedditHttpResponse getResponse = new RedditHttpResponse(200, "{\"data\":{\"children\":[]}}", Map.of());

    @Override
    public RedditHttpResponse postForm(URI uri, Map<String, String> headers, String formBody) {
        calls.add("POST"); lastUri = uri; lastHeaders = headers; lastForm = formBody;
        return postResponse;
    }

    @Override
    public RedditHttpResponse get(URI uri, Map<String, String> headers) {
        calls.add("GET"); lastUri = uri; lastHeaders = headers;
        return getResponse;
    }
}
