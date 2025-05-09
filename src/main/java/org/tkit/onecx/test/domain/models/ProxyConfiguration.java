package org.tkit.onecx.test.domain.models;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ProxyConfiguration {

    String location;
    String proxyPass;
    String proxyPassFull;

    public ProxyConfiguration(String location, String proxyPass, String proxyPassFull) {
        this.location = location;
        this.proxyPass = proxyPass;
        this.proxyPassFull = proxyPassFull;
    }
}
