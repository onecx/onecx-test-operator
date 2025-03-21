package org.tkit.onecx.test.domain.services;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class NginxService {

    static final Pattern PATTERN_LOCATION = Pattern
            .compile("location\\s+[^\\{]+\\{(?:[^{}]*\\{[^{}]*\\}[^{}]*|[^{}])*\\s*proxy_pass\\s+[^\\}]*\\;[^}]*\\}\n");
    static final Pattern PATTERN_LOCATION_PATH = Pattern.compile("(?<=location\\s)(.*?)(?=\\s\\{)");
    static final Pattern PATTERN_LOCATION_PROXY_PASS = Pattern.compile("(?<=proxy_pass\\s)([^;]+)");

    public Map<String, String> getProxyPassLocation(String output) {

        var result = new HashMap<String, String>();

        var matcher = PATTERN_LOCATION.matcher(output);
        while (matcher.find()) {
            var location = matcher.group(0);

            var pm = PATTERN_LOCATION_PATH.matcher(location);
            pm.find();

            var xm = PATTERN_LOCATION_PROXY_PASS.matcher(location);
            xm.find();

            result.put(pm.group(0), xm.group(0));
        }

        return result;
    }

}
