package com.org.ecommerce.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.org.ecommerce.entity.Coupon;
import com.org.ecommerce.entity.CouponRedemption;
import com.org.ecommerce.exception.ApiException;
import com.org.ecommerce.repository.CouponRedemptionRepository;
import com.org.ecommerce.repository.CouponRepository;

@Service
public class CouponService {

    private final CouponRepository coupons;
    private final CouponRedemptionRepository redemptions;
    private final Clock clock;

    public CouponService(CouponRepository coupons, CouponRedemptionRepository redemptions, Clock clock) {
        this.coupons = coupons;
        this.redemptions = redemptions;
        this.clock = clock;
    }

    /** Returns the coupon if it can be used for this subtotal (and user, when known); otherwise 422. */
    @Transactional(readOnly = true)
    public Coupon validate(String rawCode, BigDecimal subtotal, Long userId) {
        String code = normalize(rawCode);
        Coupon c = coupons.findByCode(code).orElseThrow(CouponService::invalid);
        Instant now = clock.instant();
        if (!c.isActive() || (c.getStartsAt() != null && now.isBefore(c.getStartsAt()))
                || (c.getEndsAt() != null && !now.isBefore(c.getEndsAt()))) {
            throw invalid();
        }
        if (c.getUsageLimit() != null && c.getTimesUsed() >= c.getUsageLimit()) {
            throw ApiException.unprocessable("COUPON_EXHAUSTED", "This coupon has reached its usage limit.");
        }
        if (userId != null && c.getPerUserLimit() != null
                && redemptions.countByCouponIdAndUserId(c.getId(), userId) >= c.getPerUserLimit()) {
            throw ApiException.unprocessable("COUPON_ALREADY_USED", "You have already used this coupon.");
        }
        if (c.getMinOrderAmount() != null && subtotal.compareTo(c.getMinOrderAmount()) < 0) {
            throw ApiException.unprocessable("COUPON_MINIMUM_NOT_MET",
                    "This coupon requires a minimum order of " + c.getMinOrderAmount().setScale(2) + ".");
        }
        return c;
    }

    /** Consumes one use inside the order transaction; the conditional UPDATE prevents over-redemption. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void redeem(Coupon coupon, Long userId, Long orderId, BigDecimal discount) {
        if (coupons.incrementUsage(coupon.getId()) == 0) {
            throw ApiException.unprocessable("COUPON_EXHAUSTED", "This coupon has reached its usage limit.");
        }
        CouponRedemption r = new CouponRedemption();
        r.setCouponId(coupon.getId());
        r.setUserId(userId);
        r.setOrderId(orderId);
        r.setDiscountAmount(discount);
        redemptions.save(r);
    }

    /** Gives the use back when an unpaid order is cancelled. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void release(Long orderId) {
        redemptions.findByOrderId(orderId).ifPresent(r -> {
            coupons.decrementUsage(r.getCouponId());
            redemptions.delete(r);
        });
    }

    public static String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    private static ApiException invalid() {
        return ApiException.unprocessable("INVALID_COUPON", "This coupon code is invalid or has expired.");
    }
}
