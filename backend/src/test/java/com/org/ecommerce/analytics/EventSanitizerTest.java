package com.org.ecommerce.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.org.ecommerce.entity.Enums.TrafficSource;

class EventSanitizerTest {

    @Test
    void pathDropsQueryStringAndFragment() {
        assertThat(EventSanitizer.path("/products/42?token=secret&email=a@b.c#reviews")).isEqualTo("/products/42");
    }

    @Test
    void pathRejectsAbsoluteAndProtocolRelativeUrls() {
        assertThat(EventSanitizer.path("https://evil.example/x")).isNull();
        assertThat(EventSanitizer.path("//evil.example/x")).isNull();
        assertThat(EventSanitizer.path("")).isNull();
    }

    @Test
    void sensitiveAreasAreExcluded() {
        assertThat(EventSanitizer.isExcludedPath("/account/orders/5")).isTrue();
        assertThat(EventSanitizer.isExcludedPath("/admin")).isTrue();
        assertThat(EventSanitizer.isExcludedPath("/reset-password")).isTrue();
        assertThat(EventSanitizer.isExcludedPath("/accounting-books")).isFalse();
        assertThat(EventSanitizer.isExcludedPath("/products/1")).isFalse();
    }

    @Test
    void referrerKeepsOnlySchemeHostAndPath() {
        assertThat(EventSanitizer.referrer("https://www.Google.com/search?q=my+email"))
                .isEqualTo("https://www.google.com/search");
        assertThat(EventSanitizer.referrer("javascript:alert(1)")).isNull();
        assertThat(EventSanitizer.referrer("not a url")).isNull();
    }

    @Test
    void campaignValuesAreRestrictedAndTruncated() {
        assertThat(EventSanitizer.campaign("<script>spring_sale</script>")).isEqualTo("scriptspring_salescript");
        assertThat(EventSanitizer.campaign("x".repeat(300))).hasSize(100);
    }

    @Test
    void searchQueriesContainingEmailsAreDropped() {
        assertThat(EventSanitizer.searchQuery("john@example.com")).isNull();
        assertThat(EventSanitizer.searchQuery("  red   shoes ")).isEqualTo("red shoes");
    }

    @Test
    void trafficSourceClassification() {
        assertThat(EventSanitizer.classify(null, "shop.test", null, null)).isEqualTo(TrafficSource.DIRECT);
        assertThat(EventSanitizer.classify("shop.test", "shop.test", null, null)).isEqualTo(TrafficSource.DIRECT);
        assertThat(EventSanitizer.classify("www.google.com", "shop.test", null, null)).isEqualTo(TrafficSource.ORGANIC);
        assertThat(EventSanitizer.classify("l.facebook.com", "shop.test", null, null)).isEqualTo(TrafficSource.SOCIAL);
        assertThat(EventSanitizer.classify("blog.example", "shop.test", null, null)).isEqualTo(TrafficSource.REFERRAL);
        assertThat(EventSanitizer.classify(null, "shop.test", "google", "cpc")).isEqualTo(TrafficSource.PAID);
        assertThat(EventSanitizer.classify(null, "shop.test", "newsletter", "email")).isEqualTo(TrafficSource.EMAIL);
        assertThat(EventSanitizer.classify(null, "shop.test", "partner", "banner")).isEqualTo(TrafficSource.CAMPAIGN);
    }
}
