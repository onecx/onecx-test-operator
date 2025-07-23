package org.tkit.onecx.test.domain.services;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;

import org.tkit.onecx.test.domain.models.ProxyConfiguration;

@ApplicationScoped
public class NginxService {

    @SuppressWarnings({ "java:S5998", "java:S5843" })
    static final Pattern PATTERN_LOCATION = Pattern
            .compile("location\\s+[^\\{]+\\{(?:[^{}]*\\{[^{}]*\\}[^{}]*|[^{}])*\\s*proxy_pass\\s+[^\\}]*\\;[^}]*\\}");
    static final Pattern PATTERN_LOCATION_PATH = Pattern.compile("(?<=location\\s)(.*?)(?=\\s\\{)");
    static final Pattern PATTERN_LOCATION_PROXY_PASS = Pattern.compile("(?<=proxy_pass\\s+)(https?:\\/\\/[^\\/\\s;]+\\/?)");
    static final Pattern PATTERN_LOCATION_PROXY_PASS_FULL = Pattern.compile("(?<=proxy_pass\\s+)(https?:\\/\\/+[^;]+)");

    public List<ProxyConfiguration> getProxyPassLocation(String output) {

        var result = new ArrayList<ProxyConfiguration>();

        var matcher = PATTERN_LOCATION.matcher(output);
        while (matcher.find()) {
            var location = matcher.group(0);

            var pm = PATTERN_LOCATION_PATH.matcher(location);
            pm.find();

            var xm = PATTERN_LOCATION_PROXY_PASS.matcher(location);
            xm.find();

            var xmf = PATTERN_LOCATION_PROXY_PASS_FULL.matcher(location);
            xmf.find();

            result.add(new ProxyConfiguration(pm.group(0), xm.group(0), xmf.group(0)));
        }

        return result;
    }

}
