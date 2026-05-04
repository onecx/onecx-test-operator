package org.tkit.onecx.test.domain.models;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ProxyConfiguration {

    String location;
    String proxyHost;
    String proxyPath;
    String servicePathKey;

    public ProxyConfiguration(String location, String proxyHost, String proxyPath,
            String servicePathKey) {
        this.location = location;
        this.proxyHost = proxyHost;
        this.proxyPath = proxyPath;
        this.servicePathKey = servicePathKey;
    }
}
