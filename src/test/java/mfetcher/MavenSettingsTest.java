package mfetcher;

import org.apache.maven.settings.Proxy;
import org.junit.*;
import static org.junit.Assert.*;

public class MavenSettingsTest {

    @Test public void dumpProxySettings () {
        MavenSettings ms = new MavenSettings();
        System.out.println("Maven proxies: -----------------");
        for (Proxy proxy : ms.settings.getProxies()) {
            System.out.println("Username: " + proxy.getUsername());
            System.out.println("Protocol: " + proxy.getProtocol());
            System.out.println("Host: " + proxy.getHost());
            System.out.println("Port: " + proxy.getPort());
            System.out.println("Non-proxy hosts: " + proxy.getNonProxyHosts());
        }
        System.out.println("End Maven proxies: -----------------");
    }
}
