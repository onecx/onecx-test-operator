package org.tkit.onecx.test.domain.services;

public final class TestServiceUriUtil {

    private TestServiceUriUtil() {
    }

    public static String normalizeUri(String uri) {
        if (uri == null || uri.isBlank()) {
            return uri;
        }

        String normalized = uri;

        // Normalize double slashes while preserving scheme (e.g., https://)
        int schemeIndex = normalized.indexOf("://");
        if (schemeIndex >= 0) {
            String scheme = normalized.substring(0, schemeIndex + 3);
            String remainder = normalized.substring(schemeIndex + 3).replaceAll("/{2,}", "/");
            normalized = scheme + remainder;
        } else {
            normalized = normalized.replaceAll("/{2,}", "/");
        }

        // Replace path placeholders like {id}, {userId}, etc. with 1234
        normalized = normalized.replaceAll("\\{[^/}]+}", "1234");

        return normalized;
    }

    /**
     * Find overlap of 1st string end and start of 2nd string.
     *
     * @return length of the overlap
     */
    public static int findOverlapLength(String str1, String str2) {
        for (int i = 0; i < str1.length(); i++) {
            String substring = str1.substring(i);
            if (str2.startsWith(substring)) {
                return substring.length();
            }
        }
        return 0;
    }
}
