package jug.org.qr;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LinkedInNormalizer {

    private static final Pattern LINKEDIN_IN_URL = Pattern.compile(
            "(?i)(?:https?://)?(?:[a-z0-9-]+\\.)?linkedin\\.com/(?:mwlite/)?in/([^/?#]+)"
    );
        private static final Pattern LINKEDIN_IN_SUFFIX = Pattern.compile("(?i)^(?:/)?in/([^/?#]+)(?:/)?$");
    private static final Pattern LINKEDIN_HANDLE = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9-]{2,99}$");
    private static final Pattern EMAIL_LIKE = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private LinkedInNormalizer() {
    }

    /**
     * Returns a canonical LinkedIn profile URL like {@code https://www.linkedin.com/in/<handle>/}.
     * Accepts:
     * - handle: {@code altuga}
     * - suffix: {@code in/altuga} or {@code /in/altuga}
     * - full URL: {@code https://www.linkedin.com/in/altuga}
     */
    public static String normalizeLinkedInProfileUrl(String raw) {
        String input = safeTrim(raw);
        if (input.isEmpty()) {
            return "";
        }

        input = stripQueryAndFragment(input);

        // If it's a full URL (or URL-ish) containing linkedin.com/in/<handle>
        Matcher urlMatcher = LINKEDIN_IN_URL.matcher(input);
        if (urlMatcher.find()) {
            String handle = cleanHandle(urlMatcher.group(1));
            if (!handle.isEmpty()) {
                return canonicalProfileUrl(handle);
            }
        }

        // If user enters just the suffix like in/altuga
        Matcher suffixMatcher = LINKEDIN_IN_SUFFIX.matcher(input);
        if (suffixMatcher.find()) {
            String handle = cleanHandle(suffixMatcher.group(1));
            if (!handle.isEmpty()) {
                return canonicalProfileUrl(handle);
            }
        }

        // If user enters just the handle
        String candidate = cleanHandle(input);
        if (LINKEDIN_HANDLE.matcher(candidate).matches()) {
            return canonicalProfileUrl(candidate);
        }

        return "";
    }

    /**
     * Normalizes user input into the string to embed in QR:
     * - LinkedIn handle/URL/suffix => canonical LinkedIn profile URL
     * - Email address => mailto:<email>
     * - Any other non-empty string => returned as-is (trimmed)
     */
    public static String normalizeToQrPayload(String raw) {
        String input = safeTrim(raw);
        if (input.isEmpty()) {
            return "";
        }

        String linkedIn = normalizeLinkedInProfileUrl(input);
        if (!linkedIn.isEmpty()) {
            return linkedIn;
        }

        // Fallback: keep old behavior usable if someone pastes an email.
        if (EMAIL_LIKE.matcher(input).matches()) {
            return "mailto:" + input;
        }

        // Allow arbitrary URLs (some users may want to encode a website).
        String stripped = stripQueryAndFragment(input);
        if (startsWithHttpScheme(stripped)) {
            return stripped;
        }

        // Unknown format => treat as invalid so we don't silently encode junk.
        return "";
    }

    private static boolean startsWithHttpScheme(String value) {
        String lower = value.toLowerCase();
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private static String canonicalProfileUrl(String handle) {
        // Always use www + trailing slash for consistent QR payload.
        return "https://www.linkedin.com/in/" + handle + "/";
    }

    private static String cleanHandle(String handle) {
        String h = safeTrim(handle);
        if (h.startsWith("@")) {
            h = h.substring(1);
        }
        // LinkedIn handles are case-insensitive in practice, but keep user's casing.
        // Remove any accidental trailing slashes.
        while (h.endsWith("/")) {
            h = h.substring(0, h.length() - 1);
        }
        return h;
    }

    private static String stripQueryAndFragment(String input) {
        int q = input.indexOf('?');
        int hash = input.indexOf('#');
        int cut;
        if (q == -1 && hash == -1) {
            cut = -1;
        } else if (q == -1) {
            cut = hash;
        } else if (hash == -1) {
            cut = q;
        } else {
            cut = Math.min(q, hash);
        }
        if (cut == -1) {
            return input;
        }
        return input.substring(0, cut);
    }

    private static String safeTrim(String s) {
        if (s == null) {
            return "";
        }
        String trimmed = s.trim();
        // Common copy/paste artifacts
        if (trimmed.startsWith("<") && trimmed.endsWith(">") && trimmed.length() > 2) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        // Normalize whitespace runs
        trimmed = trimmed.replaceAll("\\s+", " ");
        return trimmed;
    }
}
