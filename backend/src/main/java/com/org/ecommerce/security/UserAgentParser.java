package com.org.ecommerce.security;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.org.ecommerce.entity.Enums.DeviceCategory;

/**
 * Small, dependency-free User-Agent classifier. It only needs to be good enough for
 * security review and device/browser reports, not exhaustive.
 */
public final class UserAgentParser {

    public record Parsed(String browser, String browserVersion, String os, DeviceCategory device) {
    }

    private static final Pattern BOT = Pattern.compile("(?i)bot|crawler|spider|crawling|headless|slurp|lighthouse");
    private static final Pattern[][] BROWSERS = {
            {Pattern.compile("Edg(?:e|A|iOS)?/([\\d.]+)")},
            {Pattern.compile("OPR/([\\d.]+)")},
            {Pattern.compile("SamsungBrowser/([\\d.]+)")},
            {Pattern.compile("Firefox/([\\d.]+)")},
            {Pattern.compile("FxiOS/([\\d.]+)")},
            {Pattern.compile("CriOS/([\\d.]+)")},
            {Pattern.compile("Chrome/([\\d.]+)")},
            {Pattern.compile("Version/([\\d.]+).*Safari/")},
    };
    private static final String[] BROWSER_NAMES = {"Edge", "Opera", "Samsung Internet", "Firefox", "Firefox", "Chrome",
            "Chrome", "Safari"};

    private UserAgentParser() {
    }

    public static Parsed parse(String ua) {
        if (ua == null || ua.isBlank()) {
            return new Parsed(null, null, null, DeviceCategory.UNKNOWN);
        }
        if (BOT.matcher(ua).find()) {
            return new Parsed("Bot", null, os(ua), DeviceCategory.BOT);
        }
        String browser = "Other";
        String version = null;
        for (int i = 0; i < BROWSERS.length; i++) {
            Matcher m = BROWSERS[i][0].matcher(ua);
            if (m.find()) {
                browser = BROWSER_NAMES[i];
                version = majorMinor(m.group(1));
                break;
            }
        }
        return new Parsed(browser, version, os(ua), device(ua));
    }

    private static String os(String ua) {
        if (ua.contains("Windows NT")) {
            return "Windows";
        }
        if (ua.contains("iPhone") || ua.contains("iPad") || ua.contains("iPod")) {
            return "iOS";
        }
        if (ua.contains("Android")) {
            return "Android";
        }
        if (ua.contains("Mac OS X") || ua.contains("Macintosh")) {
            return "macOS";
        }
        if (ua.contains("CrOS")) {
            return "ChromeOS";
        }
        if (ua.contains("Linux")) {
            return "Linux";
        }
        return "Other";
    }

    private static DeviceCategory device(String ua) {
        if (ua.contains("iPad") || ua.contains("Tablet") || (ua.contains("Android") && !ua.contains("Mobile"))) {
            return DeviceCategory.TABLET;
        }
        if (ua.contains("Mobi") || ua.contains("iPhone") || ua.contains("iPod")) {
            return DeviceCategory.MOBILE;
        }
        if (ua.contains("Windows") || ua.contains("Macintosh") || ua.contains("X11") || ua.contains("CrOS")) {
            return DeviceCategory.DESKTOP;
        }
        return DeviceCategory.UNKNOWN;
    }

    private static String majorMinor(String v) {
        String[] parts = v.split("\\.");
        return parts.length > 1 ? parts[0] + "." + parts[1] : parts[0];
    }
}
