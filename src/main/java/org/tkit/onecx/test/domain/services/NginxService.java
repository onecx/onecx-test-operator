package org.tkit.onecx.test.domain.services;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class NginxService {

    private static final Logger log = LoggerFactory.getLogger(NginxService.class);

    static final Pattern PATTERN_LOCATION = Pattern.compile("location\\s+.*?\\{([^}]*(proxy_pass[^}]*)[^}]*)\\}");
    static final Pattern PATTERN_LOCATION_PATH = Pattern.compile("(?<=location\\s)(.*?)(?=\\s\\{)");
    static final Pattern PATTERN_LOCATION_PROXY_PASS = Pattern.compile("(?<=proxy_pass\\s)([^;]+)");

    public Map<String, String> getProxyPassLocation(String output) {

        var result = new HashMap<String, String>();

        var matcher = PATTERN_LOCATION.matcher(output);
        while (matcher.find()) {
            var location = matcher.group(0);

            var pm = PATTERN_LOCATION_PATH.matcher(location);
            if (!pm.find()) {
                continue;
            }

            var xm = PATTERN_LOCATION_PROXY_PASS.matcher(location);
            if (!xm.find()) {
                continue;
            }

            result.put(pm.group(0), xm.group(0));
        }

        return result;
    }

}
