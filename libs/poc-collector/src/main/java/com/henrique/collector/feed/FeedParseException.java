package com.henrique.collector.feed;

public class FeedParseException extends RuntimeException {
    public FeedParseException(String message) { super(message); }
    public FeedParseException(String message, Throwable cause) { super(message, cause); }
}
