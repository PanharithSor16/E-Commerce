package com.org.ecommerce.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.org.ecommerce.config.AppProperties;
import com.org.ecommerce.entity.Coupon;
import com.org.ecommerce.entity.Enums.DiscountType;
import com.org.ecommerce.entity.Product;
import com.org.ecommerce.entity.ProductVariant;
import com.org.ecommerce.entity.ShippingMethod;

class PricingServiceTest {

    private final PricingService pricing = new PricingService(props("0.00"));

    @Test
    void sumsLinesUsingDiscountPriceAndVariantOverride() {
        ProductVariant a = variant("50.00", "39.99", null);
        ProductVariant b = variant("20.00", null, "25.00");
        PricingService.Quote q = pricing.quote(List.of(new PricingService.Line(a, 2), new PricingService.Line(b, 1)),
                null, null);
        assertThat(q.subtotal()).isEqualByComparingTo("104.98");
        assertThat(q.grandTotal()).isEqualByComparingTo("104.98");
    }

    @Test
    void percentageCouponIsRoundedAndCapped() {
        ProductVariant v = variant("33.33", null, null);
        Coupon tenPercent = coupon(DiscountType.PERCENTAGE, "10", null);
        PricingService.Quote q = pricing.quote(List.of(new PricingService.Line(v, 3)), tenPercent, null);
        assertThat(q.discountTotal()).isEqualByComparingTo("10.00"); // 9.999 -> 10.00 half-even

        Coupon capped = coupon(DiscountType.PERCENTAGE, "50", "5.00");
        assertThat(pricing.quote(List.of(new PricingService.Line(v, 3)), capped, null).discountTotal())
                .isEqualByComparingTo("5.00");
    }

    @Test
    void fixedCouponNeverExceedsSubtotal() {
        ProductVariant v = variant("8.00", null, null);
        Coupon c = coupon(DiscountType.FIXED_AMOUNT, "20.00", null);
        PricingService.Quote q = pricing.quote(List.of(new PricingService.Line(v, 1)), c, null);
        assertThat(q.discountTotal()).isEqualByComparingTo("8.00");
        assertThat(q.grandTotal()).isEqualByComparingTo("0.00");
    }

    @Test
    void shippingIsFreeAboveThresholdAndWithFreeShippingCoupon() {
        ShippingMethod standard = new ShippingMethod();
        standard.setFee(new BigDecimal("5.00"));
        standard.setFreeShippingThreshold(new BigDecimal("75.00"));
        ProductVariant cheap = variant("10.00", null, null);
        ProductVariant pricey = variant("80.00", null, null);

        assertThat(pricing.quote(List.of(new PricingService.Line(cheap, 1)), null, standard).shippingTotal())
                .isEqualByComparingTo("5.00");
        assertThat(pricing.quote(List.of(new PricingService.Line(pricey, 1)), null, standard).shippingTotal())
                .isEqualByComparingTo("0.00");
        assertThat(pricing.quote(List.of(new PricingService.Line(cheap, 1)),
                coupon(DiscountType.FREE_SHIPPING, "0", null), standard).shippingTotal()).isEqualByComparingTo("0.00");
    }

    @Test
    void taxAppliesToDiscountedMerchandiseAndTotalsReconcile() {
        PricingService taxed = new PricingService(props("0.10"));
        ShippingMethod ship = new ShippingMethod();
        ship.setFee(new BigDecimal("4.99"));
        ProductVariant v = variant("19.99", null, null);
        PricingService.Quote q = taxed.quote(List.of(new PricingService.Line(v, 2)),
                coupon(DiscountType.FIXED_AMOUNT, "5.00", null), ship);
        assertThat(q.taxTotal()).isEqualByComparingTo("3.50"); // (39.98 - 5.00) * 10% = 3.498
        assertThat(q.grandTotal()).isEqualByComparingTo(
                q.subtotal().subtract(q.discountTotal()).add(q.shippingTotal()).add(q.taxTotal()));
    }

    @Test
    void discountAllocationAcrossLinesAddsUpExactly() {
        ProductVariant a = variant("10.00", null, null);
        ProductVariant b = variant("20.00", null, null);
        ProductVariant c = variant("30.00", null, null);
        PricingService.Quote q = pricing.quote(List.of(new PricingService.Line(a, 1), new PricingService.Line(b, 1),
                new PricingService.Line(c, 1)), coupon(DiscountType.FIXED_AMOUNT, "10.00", null), null);
        BigDecimal allocated = q.lines().stream().map(PricingService.PricedLine::discountAllocation)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(allocated).isEqualByComparingTo(q.discountTotal());
    }

    // ------------------------------------------------------------------------------------------

    static ProductVariant variant(String base, String discount, String override) {
        Product p = new Product();
        p.setBasePrice(new BigDecimal(base));
        p.setDiscountPrice(discount == null ? null : new BigDecimal(discount));
        ProductVariant v = new ProductVariant();
        v.setProduct(p);
        v.setPriceOverride(override == null ? null : new BigDecimal(override));
        return v;
    }

    static Coupon coupon(DiscountType type, String value, String max) {
        Coupon c = new Coupon();
        c.setDiscountType(type);
        c.setDiscountValue(new BigDecimal(value));
        c.setMaxDiscountAmount(max == null ? null : new BigDecimal(max));
        return c;
    }

    static AppProperties props(String taxRate) {
        return new AppProperties("http://localhost:3000", null, null, null, null, null,
                new AppProperties.Pricing(new BigDecimal(taxRate)), null, null, null);
    }
}
