package dev.henrique.collector.feed;

public class FeedFetchException extends RuntimeException {
    public FeedFetchException(String message) { super(message); }
    public FeedFetchException(String message, Throwable cause) { super(message, cause); }
}
