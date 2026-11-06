package com.henrique.collector.feed;

import com.henrique.collector.source.CollectorSource;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

public final class RssFeedParser {
    public static final int MAX_ITEMS = 100;

    public List<FeedItem> parse(byte[] document, CollectorSource source) {
        XMLStreamReader reader = null;
        try {
            XMLInputFactory factory = XMLInputFactory.newFactory();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
            factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
            factory.setXMLResolver((publicId, systemId, baseUri, namespace) -> {
                throw new XMLStreamException("External XML entities are disabled.");
            });
            reader = factory.createXMLStreamReader(new ByteArrayInputStream(document));
            List<FeedItem> items = new ArrayList<>();
            MutableItem current = null;
            boolean recognizedRoot = false;
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.DTD || event == XMLStreamConstants.ENTITY_REFERENCE) {
                    throw new FeedParseException("DTD and entity references are not permitted in feeds.");
                }
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String name = reader.getLocalName();
                    if (current == null && (name.equals("rss") || name.equals("feed") || name.equals("RDF"))) {
                        recognizedRoot = true;
                    }
                    if (name.equals("item") || name.equals("entry")) {
                        current = new MutableItem();
                    } else if (current != null) {
                        switch (name) {
                            case "title" -> current.title = reader.getElementText();
                            case "guid", "id" -> current.id = reader.getElementText();
                            case "link" -> {
                                String href = attribute(reader, "href");
                                if (href != null) {
                                    String rel = attribute(reader, "rel");
                                    if (rel == null || rel.equals("alternate")) current.link = href;
                                    reader.next(); // Atom link is an empty element; advance safely for either feed form.
                                } else {
                                    current.link = reader.getElementText();
                                }
                            }
                            case "pubDate", "published", "updated" -> current.publishedAt = parseDate(reader.getElementText());
                            default -> { }
                        }
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT && current != null
                        && (reader.getLocalName().equals("item") || reader.getLocalName().equals("entry"))) {
                    if (current.title != null && !current.title.isBlank() && current.link != null) {
                        URI link = httpUri(current.link);
                        String id = current.id == null || current.id.isBlank() ? link.toString() : current.id.trim();
                        items.add(new FeedItem(source, id, current.title.trim(), link, current.publishedAt, Instant.now()));
                        if (items.size() > MAX_ITEMS) throw new FeedParseException("Feed exceeds the item limit.");
                    }
                    current = null;
                }
            }
            if (!recognizedRoot) throw new FeedParseException("Document is not a supported RSS or Atom feed.");
            return List.copyOf(items);
        } catch (XMLStreamException | IllegalArgumentException e) {
            throw new FeedParseException("Feed XML is invalid or contains a rejected link.", e);
        } finally {
            if (reader != null) try { reader.close(); } catch (XMLStreamException ignored) { }
        }
    }

    private static String attribute(XMLStreamReader reader, String name) {
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            if (reader.getAttributeLocalName(i).equals(name)) return reader.getAttributeValue(i);
        }
        return null;
    }

    private static URI httpUri(String value) {
        URI uri = URI.create(value.trim());
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Feed item link must be an absolute HTTP(S) URL.");
        }
        return uri;
    }

    private static Instant parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Instant.parse(value.trim()); } catch (DateTimeParseException ignored) { }
        try { return OffsetDateTime.parse(value.trim()).toInstant(); } catch (DateTimeParseException ignored) { }
        try { return ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant(); }
        catch (DateTimeParseException ignored) { return null; }
    }

    private static final class MutableItem {
        private String id;
        private String title;
        private String link;
        private Instant publishedAt;
    }
}
