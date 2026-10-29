package com.org.ecommerce.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.org.ecommerce.entity.Coupon;
import com.org.ecommerce.entity.Enums.DiscountType;
import com.org.ecommerce.exception.ApiException;
import com.org.ecommerce.repository.CouponRedemptionRepository;
import com.org.ecommerce.repository.CouponRepository;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");

    @Mock
    CouponRepository coupons;
    @Mock
    CouponRedemptionRepository redemptions;

    CouponService service;

    @BeforeEach
    void setUp() {
        service = new CouponService(coupons, redemptions, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void unknownCodeIsRejected() {
        when(coupons.findByCode("NOPE")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.validate("nope", BigDecimal.TEN, 1L))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("INVALID_COUPON");
    }

    @Test
    void codeIsNormalizedBeforeLookup() {
        Coupon c = coupon();
        when(coupons.findByCode("SAVE10")).thenReturn(Optional.of(c));
        assertThat(service.validate("  save10 ", BigDecimal.TEN, null)).isSameAs(c);
    }

    @Test
    void expiredAndNotYetStartedCouponsAreRejected() {
        Coupon expired = coupon();
        expired.setEndsAt(NOW.minusSeconds(1));
        when(coupons.findByCode("SAVE10")).thenReturn(Optional.of(expired));
        assertThatThrownBy(() -> service.validate("SAVE10", BigDecimal.TEN, 1L)).isInstanceOf(ApiException.class);

        Coupon future = coupon();
        future.setStartsAt(NOW.plusSeconds(60));
        when(coupons.findByCode("SAVE10")).thenReturn(Optional.of(future));
        assertThatThrownBy(() -> service.validate("SAVE10", BigDecimal.TEN, 1L)).isInstanceOf(ApiException.class);
    }

    @Test
    void minimumOrderAmountIsEnforced() {
        Coupon c = coupon();
        c.setMinOrderAmount(new BigDecimal("50.00"));
        when(coupons.findByCode("SAVE10")).thenReturn(Optional.of(c));
        assertThatThrownBy(() -> service.validate("SAVE10", new BigDecimal("49.99"), 1L))
                .extracting("code").isEqualTo("COUPON_MINIMUM_NOT_MET");
        assertThat(service.validate("SAVE10", new BigDecimal("50.00"), 1L)).isSameAs(c);
    }

    @Test
    void perUserLimitIsEnforced() {
        Coupon c = coupon();
        c.setPerUserLimit(1);
        when(coupons.findByCode("SAVE10")).thenReturn(Optional.of(c));
        when(redemptions.countByCouponIdAndUserId(any(), any())).thenReturn(1L);
        assertThatThrownBy(() -> service.validate("SAVE10", BigDecimal.TEN, 7L))
                .extracting("code").isEqualTo("COUPON_ALREADY_USED");
    }

    @Test
    void redeemFailsWhenGlobalLimitWasReachedConcurrently() {
        Coupon c = coupon();
        when(coupons.incrementUsage(any())).thenReturn(0);
        assertThatThrownBy(() -> service.redeem(c, 1L, 2L, BigDecimal.ONE))
                .extracting("code").isEqualTo("COUPON_EXHAUSTED");
        verify(redemptions, never()).save(any());
    }

    private static Coupon coupon() {
        Coupon c = new Coupon();
        c.setCode("SAVE10");
        c.setDiscountType(DiscountType.PERCENTAGE);
        c.setDiscountValue(BigDecimal.TEN);
        c.setActive(true);
        return c;
    }
}
