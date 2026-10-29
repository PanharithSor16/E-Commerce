package com.org.ecommerce.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.org.ecommerce.config.AppProperties;
import com.org.ecommerce.entity.Coupon;
import com.org.ecommerce.entity.Enums.DiscountType;
import com.org.ecommerce.entity.ProductVariant;
import com.org.ecommerce.entity.ShippingMethod;

/**
 * The single place where money is calculated. Inputs are entities loaded from the database;
 * nothing price-related is ever taken from the client.
 * <p>
 * Rounding: catalog prices have 2 decimals, so line totals are exact; percentage discounts and
 * tax are rounded half-even to 2 decimals. grandTotal = subtotal - discount + shipping + tax.
 */
@Service
public class PricingService {

    static final int SCALE = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final BigDecimal taxRate;

    public PricingService(AppProperties properties) {
        this.taxRate = properties.pricing().taxRate();
    }

    public record Line(ProductVariant variant, int quantity) {
    }

    public record PricedLine(ProductVariant variant, int quantity, BigDecimal unitPrice, BigDecimal lineTotal,
                             BigDecimal discountAllocation) {
    }

    public record Quote(List<PricedLine> lines, BigDecimal subtotal, BigDecimal discountTotal, BigDecimal shippingTotal,
                        BigDecimal taxTotal, BigDecimal grandTotal) {
    }

    /**
     * @param coupon   an already-validated coupon or null
     * @param shipping the chosen method, or null to omit shipping (e.g. empty cart)
     */
    public Quote quote(List<Line> lines, Coupon coupon, ShippingMethod shipping) {
        List<PricedLine> priced = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (Line l : lines) {
            BigDecimal unit = l.variant().unitPrice().setScale(SCALE, RoundingMode.HALF_EVEN);
            BigDecimal lineTotal = unit.multiply(BigDecimal.valueOf(l.quantity()));
            priced.add(new PricedLine(l.variant(), l.quantity(), unit, lineTotal, BigDecimal.ZERO));
            subtotal = subtotal.add(lineTotal);
        }

        BigDecimal discount = discountFor(coupon, subtotal);
        BigDecimal merchandise = subtotal.subtract(discount);

        BigDecimal shippingTotal = BigDecimal.ZERO;
        if (shipping != null && !lines.isEmpty()) {
            shippingTotal = coupon != null && coupon.getDiscountType() == DiscountType.FREE_SHIPPING
                    ? BigDecimal.ZERO
                    : shipping.feeFor(merchandise).setScale(SCALE, RoundingMode.HALF_EVEN);
        }
        BigDecimal tax = merchandise.multiply(taxRate).setScale(SCALE, RoundingMode.HALF_EVEN);
        BigDecimal grand = merchandise.add(shippingTotal).add(tax).setScale(SCALE, RoundingMode.HALF_EVEN);

        return new Quote(allocate(priced, discount, subtotal), subtotal.setScale(SCALE, RoundingMode.HALF_EVEN),
                discount, shippingTotal, tax, grand);
    }

    static BigDecimal discountFor(Coupon coupon, BigDecimal subtotal) {
        if (coupon == null || subtotal.signum() == 0) {
            return BigDecimal.ZERO.setScale(SCALE);
        }
        BigDecimal d = switch (coupon.getDiscountType()) {
            case PERCENTAGE -> subtotal.multiply(coupon.getDiscountValue()).divide(HUNDRED, SCALE, RoundingMode.HALF_EVEN);
            case FIXED_AMOUNT -> coupon.getDiscountValue().setScale(SCALE, RoundingMode.HALF_EVEN);
            case FREE_SHIPPING -> BigDecimal.ZERO;
        };
        if (coupon.getMaxDiscountAmount() != null && d.compareTo(coupon.getMaxDiscountAmount()) > 0) {
            d = coupon.getMaxDiscountAmount();
        }
        return d.min(subtotal).setScale(SCALE, RoundingMode.HALF_EVEN);
    }

    /** Spreads the order discount across lines proportionally; the last line absorbs rounding. */
    private static List<PricedLine> allocate(List<PricedLine> lines, BigDecimal discount, BigDecimal subtotal) {
        if (discount.signum() == 0 || lines.isEmpty()) {
            return lines;
        }
        List<PricedLine> result = new ArrayList<>();
        BigDecimal remaining = discount;
        for (int i = 0; i < lines.size(); i++) {
            PricedLine l = lines.get(i);
            BigDecimal share = i == lines.size() - 1
                    ? remaining
                    : discount.multiply(l.lineTotal()).divide(subtotal, SCALE, RoundingMode.HALF_EVEN).min(remaining);
            remaining = remaining.subtract(share);
            result.add(new PricedLine(l.variant(), l.quantity(), l.unitPrice(), l.lineTotal(), share));
        }
        return result;
    }
}
