package com.henrique.collector.source;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;

final class RedditJson {
    static final ObjectMapper MAPPER = new ObjectMapper(JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder()
                    .maxNestingDepth(32).maxStringLength(16_384).maxNumberLength(128).build())
            .build());

    private RedditJson() { }
}
