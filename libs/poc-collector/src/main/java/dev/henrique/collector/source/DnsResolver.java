package dev.henrique.collector.source;

import java.net.InetAddress;
import java.net.UnknownHostException;

@FunctionalInterface
public interface DnsResolver {
    InetAddress[] resolve(String host) throws UnknownHostException;
}
