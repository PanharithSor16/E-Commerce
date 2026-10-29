package com.org.ecommerce.controller.admin;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.org.ecommerce.audit.AuditService;
import com.org.ecommerce.config.AppProperties;
import com.org.ecommerce.dto.admin.AdminDtos;
import com.org.ecommerce.entity.Coupon;
import com.org.ecommerce.entity.Enums.DiscountType;
import com.org.ecommerce.entity.ShippingMethod;
import com.org.ecommerce.exception.ApiException;
import com.org.ecommerce.repository.CouponRepository;
import com.org.ecommerce.repository.ShippingMethodRepository;
import com.org.ecommerce.service.CouponService;

import io.swagger.v3.oas.annotations.tags.Tag;

/** Coupons, shipping methods and read-only runtime settings. */
@Tag(name = "Admin - Settings")
@RestController
@RequestMapping("/api/v1/admin")
public class AdminSettingsController {

    private final CouponRepository coupons;
    private final ShippingMethodRepository shippingMethods;
    private final AuditService audit;
    private final AppProperties properties;

    public AdminSettingsController(CouponRepository coupons, ShippingMethodRepository shippingMethods,
                                   AuditService audit, AppProperties properties) {
        this.coupons = coupons;
        this.shippingMethods = shippingMethods;
        this.audit = audit;
        this.properties = properties;
    }

    @PreAuthorize("hasAuthority('SETTINGS_WRITE') or hasAuthority('CATALOG_READ')")
    @GetMapping("/settings")
    public AdminDtos.SettingsResponse settings() {
        return new AdminDtos.SettingsResponse(properties.payment().provider(), properties.payment().currency(),
                properties.pricing().taxRate(), properties.security().requireVerifiedEmailForCheckout(),
                properties.analytics().privacyPolicyVersion(),
                shippingMethods.findAll().stream().map(AdminSettingsController::toShipping).toList());
    }

    @PreAuthorize("hasAuthority('SETTINGS_WRITE')")
    @PutMapping("/shipping-methods/{id}")
    @Transactional
    public AdminDtos.ShippingMethodAdmin updateShipping(@PathVariable Long id, @Valid @RequestBody AdminDtos.ShippingMethodRequest body) {
        ShippingMethod m = shippingMethods.findById(id).orElseThrow(() -> ApiException.notFound("Shipping method"));
        if (body.estimatedDaysMin() != null && body.estimatedDaysMax() != null && body.estimatedDaysMin() > body.estimatedDaysMax()) {
            throw ApiException.badRequest("INVALID_DAYS", "Minimum days must not exceed maximum days.");
        }
        m.setName(body.name());
        m.setDescription(body.description());
        m.setFee(body.fee());
        m.setFreeShippingThreshold(body.freeShippingThreshold());
        m.setEstimatedDaysMin(body.estimatedDaysMin());
        m.setEstimatedDaysMax(body.estimatedDaysMax());
        m.setActive(body.active());
        audit.record("SHIPPING_METHOD_UPDATED", "ShippingMethod", id, Map.of("fee", body.fee().toPlainString()));
        return toShipping(m);
    }

    // ---------------------------------------------------------------- coupons

    @PreAuthorize("hasAuthority('COUPON_WRITE')")
    @GetMapping("/coupons")
    public List<AdminDtos.CouponResponse> coupons() {
        return coupons.findAll(org.springframework.data.domain.Sort.by("code")).stream()
                .map(AdminSettingsController::toCoupon).toList();
    }

    @PreAuthorize("hasAuthority('COUPON_WRITE')")
    @PostMapping("/coupons")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public AdminDtos.CouponResponse createCoupon(@Valid @RequestBody AdminDtos.CouponRequest body) {
        String code = CouponService.normalize(body.code());
        if (coupons.existsByCode(code)) {
            throw ApiException.conflict("COUPON_EXISTS", "A coupon with this code already exists.");
        }
        Coupon c = new Coupon();
        c.setCode(code);
        apply(c, body);
        coupons.save(c);
        audit.record("COUPON_CREATED", "Coupon", c.getId(), Map.of("code", code));
        return toCoupon(c);
    }

    @PreAuthorize("hasAuthority('COUPON_WRITE')")
    @PutMapping("/coupons/{id}")
    @Transactional
    public AdminDtos.CouponResponse updateCoupon(@PathVariable Long id, @Valid @RequestBody AdminDtos.CouponRequest body) {
        Coupon c = coupons.findById(id).orElseThrow(() -> ApiException.notFound("Coupon"));
        apply(c, body);
        audit.record("COUPON_UPDATED", "Coupon", id, Map.of("code", c.getCode()));
        return toCoupon(c);
    }

    @PreAuthorize("hasAuthority('COUPON_WRITE')")
    @DeleteMapping("/coupons/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deactivateCoupon(@PathVariable Long id) {
        Coupon c = coupons.findById(id).orElseThrow(() -> ApiException.notFound("Coupon"));
        c.setActive(false);
        audit.record("COUPON_DEACTIVATED", "Coupon", id, Map.of("code", c.getCode()));
    }

    private static void apply(Coupon c, AdminDtos.CouponRequest body) {
        if (body.discountType() == DiscountType.PERCENTAGE
                && (body.discountValue().signum() <= 0 || body.discountValue().compareTo(java.math.BigDecimal.valueOf(100)) > 0)) {
            throw ApiException.badRequest("INVALID_DISCOUNT", "Percentage must be between 0 and 100.");
        }
        if (body.discountType() == DiscountType.FIXED_AMOUNT && body.discountValue().signum() <= 0) {
            throw ApiException.badRequest("INVALID_DISCOUNT", "Amount must be greater than zero.");
        }
        if (body.startsAt() != null && body.endsAt() != null && !body.startsAt().isBefore(body.endsAt())) {
            throw ApiException.badRequest("INVALID_WINDOW", "Start must be before end.");
        }
        if (body.usageLimit() != null && body.usageLimit() < c.getTimesUsed()) {
            throw ApiException.badRequest("INVALID_LIMIT", "Usage limit cannot be below the number of uses so far.");
        }
        c.setDescription(body.description());
        c.setDiscountType(body.discountType());
        c.setDiscountValue(body.discountType() == DiscountType.FREE_SHIPPING ? java.math.BigDecimal.ZERO : body.discountValue());
        c.setMinOrderAmount(body.minOrderAmount());
        c.setMaxDiscountAmount(body.maxDiscountAmount());
        c.setStartsAt(body.startsAt());
        c.setEndsAt(body.endsAt());
        c.setUsageLimit(body.usageLimit());
        c.setPerUserLimit(body.perUserLimit());
        c.setActive(body.active());
    }

    static AdminDtos.CouponResponse toCoupon(Coupon c) {
        return new AdminDtos.CouponResponse(c.getId(), c.getCode(), c.getDescription(), c.getDiscountType(),
                c.getDiscountValue(), c.getMinOrderAmount(), c.getMaxDiscountAmount(), c.getStartsAt(), c.getEndsAt(),
                c.getUsageLimit(), c.getPerUserLimit(), c.getTimesUsed(), c.isActive());
    }

    static AdminDtos.ShippingMethodAdmin toShipping(ShippingMethod m) {
        return new AdminDtos.ShippingMethodAdmin(m.getId(), m.getCode(), m.getName(), m.getDescription(), m.getFee(),
                m.getFreeShippingThreshold(), m.getEstimatedDaysMin(), m.getEstimatedDaysMax(), m.isActive());
    }
}
