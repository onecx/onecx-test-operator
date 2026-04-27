package org.tkit.onecx.test.domain.services;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;

import org.tkit.onecx.test.domain.models.ProxyConfiguration;

@ApplicationScoped
public class NginxService {

    static final Pattern PATTERN_LOCATION_START = Pattern.compile("\\blocation\\s+[^{}]+\\{");
    static final Pattern PATTERN_LOCATION_PATH = Pattern.compile("(?<=location\\s)(.*?)(?=\\s\\{)");
    static final Pattern PATTERN_PROXY_PASS = Pattern.compile("proxy_pass\\s+([^;\\s]+)");
    static final Pattern PATTERN_LOCATION_PROXY_PASS = Pattern.compile("proxy_pass\\s+(https?://[^/\\s;]+/?)");

    public List<ProxyConfiguration> getProxyPassLocation(String output) {

        var result = new ArrayList<ProxyConfiguration>();

        for (String location : extractLocationBlocks(output)) {
            if (!location.contains("proxy_pass")) {
                continue;
            }

            var pathMatcher = PATTERN_LOCATION_PATH.matcher(location);
            if (!pathMatcher.find()) {
                continue;
            }
            var locationPath = normalizeLocationPath(pathMatcher.group(0));
            if (locationPath.startsWith("@")) {
                continue;
            }

            var fullProxyMatcher = PATTERN_PROXY_PASS.matcher(location);
            if (!fullProxyMatcher.find()) {
                continue;
            }
            var proxyPassFull = fullProxyMatcher.group(1).trim();

            var hostProxyMatcher = PATTERN_LOCATION_PROXY_PASS.matcher(location);
            if (!hostProxyMatcher.find()) {
                continue;
            }
            var proxyPass = hostProxyMatcher.group(1);

            var proxyHost = getProxyHost(proxyPassFull);
            var proxyPath = normalizeProxyPath(getProxyPath(proxyPassFull));
            var servicePathKey = toServicePathKey(proxyPath);

            result.add(new ProxyConfiguration(locationPath, proxyPass, proxyPassFull, proxyHost, proxyPath, servicePathKey));
        }

        return result;
    }

    private List<String> extractLocationBlocks(String output) {
        var blocks = new ArrayList<String>();
        var matcher = PATTERN_LOCATION_START.matcher(output);
        while (matcher.find()) {
            int blockStart = matcher.start();
            int openBraceIndex = output.indexOf('{', matcher.end() - 1);
            if (openBraceIndex < 0) {
                continue;
            }

            int depth = 0;
            int blockEnd = -1;
            for (int i = openBraceIndex; i < output.length(); i++) {
                char ch = output.charAt(i);
                if (ch == '{') {
                    depth++;
                } else if (ch == '}') {
                    depth--;
                    if (depth == 0) {
                        blockEnd = i;
                        break;
                    }
                }
            }

            if (blockEnd > blockStart) {
                blocks.add(output.substring(blockStart, blockEnd + 1));
            }
        }
        return blocks;
    }

    private String normalizeLocationPath(String rawLocation) {
        var location = rawLocation.trim();
        var parts = location.split("\\s+");
        if (parts.length == 0) {
            return location;
        }
        if (("=".equals(parts[0]) || "~".equals(parts[0]) || "~*".equals(parts[0]) || "^~".equals(parts[0]))
                && parts.length > 1) {
            return parts[1];
        }
        return parts[0];
    }

    private String getProxyHost(String proxyPassFull) {
        try {
            var uri = URI.create(proxyPassFull);
            return uri.getScheme() + "://" + uri.getHost()
                    + ((uri.getPort() != -1 && uri.getPort() != 80 && uri.getPort() != 443) ? ":" + uri.getPort() : "");
        } catch (Exception ex) {
            return null;
        }
    }

    private String getProxyPath(String proxyPassFull) {
        try {
            var path = URI.create(proxyPassFull).getPath();
            return path == null ? "" : path;
        } catch (Exception ex) {
            return "";
        }
    }

    private String normalizeProxyPath(String proxyPath) {
        if (proxyPath == null || proxyPath.isBlank() || "/".equals(proxyPath)) {
            return "";
        }
        if (proxyPath.endsWith("/")) {
            return proxyPath.substring(0, proxyPath.length() - 1);
        }
        return proxyPath;
    }

    private String toServicePathKey(String proxyPath) {
        if (proxyPath == null || proxyPath.isBlank()) {
            return null;
        }
        return proxyPath;
    }
}
