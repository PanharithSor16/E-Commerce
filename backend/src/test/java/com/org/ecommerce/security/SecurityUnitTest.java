package com.org.ecommerce.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.org.ecommerce.entity.Enums.DeviceCategory;
import com.org.ecommerce.validation.StrongPasswordValidator;

class SecurityUnitTest {

    @Test
    void userAgentClassification() {
        var chromeWin = UserAgentParser.parse(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.6723.70 Safari/537.36");
        assertThat(chromeWin.browser()).isEqualTo("Chrome");
        assertThat(chromeWin.browserVersion()).isEqualTo("130.0");
        assertThat(chromeWin.os()).isEqualTo("Windows");
        assertThat(chromeWin.device()).isEqualTo(DeviceCategory.DESKTOP);

        var iphone = UserAgentParser.parse(
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1");
        assertThat(iphone.browser()).isEqualTo("Safari");
        assertThat(iphone.os()).isEqualTo("iOS");
        assertThat(iphone.device()).isEqualTo(DeviceCategory.MOBILE);

        var ipad = UserAgentParser.parse("Mozilla/5.0 (iPad; CPU OS 17_5 like Mac OS X) Version/17.5 Safari/604.1");
        assertThat(ipad.device()).isEqualTo(DeviceCategory.TABLET);

        assertThat(UserAgentParser.parse("Googlebot/2.1 (+http://www.google.com/bot.html)").device())
                .isEqualTo(DeviceCategory.BOT);
        assertThat(UserAgentParser.parse(null).device()).isEqualTo(DeviceCategory.UNKNOWN);
    }

    @Test
    void emailMasking() {
        assertThat(TokenHasher.maskEmail(" John.Doe@Example.com ")).isEqualTo("j***@example.com");
        assertThat(TokenHasher.maskEmail("x")).isEqualTo("x***");
    }

    @Test
    void passwordPolicy() {
        StrongPasswordValidator v = new StrongPasswordValidator();
        assertThat(v.isValid("short1", null)).isFalse();
        assertThat(v.isValid("password123", null)).isFalse();
        assertThat(v.isValid("aaaaaaaaaaaa", null)).isFalse();
        assertThat(v.isValid("correct horse battery", null)).isTrue();
        assertThat(v.isValid("x".repeat(129), null)).isFalse();
    }
}
