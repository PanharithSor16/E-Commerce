package com.org.ecommerce.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.org.ecommerce.config.AppProperties;
import com.org.ecommerce.dto.cart.CartDtos;
import com.org.ecommerce.entity.Cart;
import com.org.ecommerce.entity.CartItem;
import com.org.ecommerce.entity.Coupon;
import com.org.ecommerce.entity.Enums.CartStatus;
import com.org.ecommerce.entity.Enums.ProductStatus;
import com.org.ecommerce.entity.ProductVariant;
import com.org.ecommerce.entity.ShippingMethod;
import com.org.ecommerce.entity.User;
import com.org.ecommerce.exception.ApiException;
import com.org.ecommerce.mapper.CatalogMapper;
import com.org.ecommerce.repository.CartRepository;
import com.org.ecommerce.repository.ProductVariantRepository;
import com.org.ecommerce.repository.ShippingMethodRepository;
import com.org.ecommerce.repository.UserRepository;
import com.org.ecommerce.security.TokenHasher;

/**
 * Shopping carts for signed-in users (by user id) and anonymous shoppers (by an opaque cart cookie
 * whose hash is stored). Totals are always recomputed from current prices.
 */
@Service
@Transactional
public class CartService {

    private static final int MAX_LINE_QUANTITY = 99;
    private static final int MAX_LINES = 50;

    private final CartRepository carts;
    private final ProductVariantRepository variants;
    private final ShippingMethodRepository shippingMethods;
    private final UserRepository users;
    private final PricingService pricing;
    private final CouponService couponService;
    private final TokenHasher hasher;
    private final AppProperties properties;
    private final Clock clock;

    public CartService(CartRepository carts, ProductVariantRepository variants, ShippingMethodRepository shippingMethods,
                       UserRepository users, PricingService pricing, CouponService couponService, TokenHasher hasher,
                       AppProperties properties, Clock clock) {
        this.carts = carts;
        this.variants = variants;
        this.shippingMethods = shippingMethods;
        this.users = users;
        this.pricing = pricing;
        this.couponService = couponService;
        this.hasher = hasher;
        this.properties = properties;
        this.clock = clock;
    }

    /** Who owns the cart for this request: a user, or an anonymous cookie token (possibly absent). */
    public record CartOwner(Long userId, String cartToken) {
    }

    /** Result of a mutation; {@code newCartToken} is set when an anonymous cart was just created. */
    public record CartResult(CartDtos.CartResponse cart, String newCartToken) {
    }

    @Transactional(readOnly = true)
    public CartDtos.CartResponse view(CartOwner owner) {
        return find(owner).map(c -> toResponse(c, new ArrayList<>())).orElseGet(this::empty);
    }

    public CartResult addItem(CartOwner owner, Long variantId, int quantity) {
        ProductVariant variant = variants.findWithProductById(variantId)
                .filter(v -> v.isActive() && v.getProduct().getStatus() == ProductStatus.ACTIVE)
                .orElseThrow(() -> ApiException.notFound("Product variant"));
        String newToken = null;
        Cart cart = find(owner).orElse(null);
        if (cart == null) {
            cart = new Cart();
            if (owner.userId() != null) {
                cart.setUser(users.getReferenceById(owner.userId()));
            } else {
                newToken = hasher.newToken();
                cart.setCartTokenHash(hasher.sha256(newToken));
                cart.setExpiresAt(clock.instant().plus(properties.retention().anonymousCarts()));
            }
            carts.save(cart);
        }
        CartItem item = cart.getItems().stream().filter(i -> i.getVariant().getId().equals(variantId)).findFirst()
                .orElse(null);
        int newQuantity = (item == null ? 0 : item.getQuantity()) + quantity;
        checkQuantity(variant, newQuantity);
        if (item == null) {
            if (cart.getItems().size() >= MAX_LINES) {
                throw ApiException.unprocessable("CART_FULL", "Your cart cannot hold more than " + MAX_LINES + " products.");
            }
            item = new CartItem();
            item.setCart(cart);
            item.setVariant(variant);
            cart.getItems().add(item);
        }
        item.setQuantity(newQuantity);
        touch(cart);
        return new CartResult(toResponse(cart, new ArrayList<>()), newToken);
    }

    public CartDtos.CartResponse updateItem(CartOwner owner, Long itemId, int quantity) {
        Cart cart = find(owner).orElseThrow(() -> ApiException.notFound("Cart item"));
        CartItem item = item(cart, itemId);
        checkQuantity(item.getVariant(), quantity);
        item.setQuantity(quantity);
        touch(cart);
        return toResponse(cart, new ArrayList<>());
    }

    public CartDtos.CartResponse removeItem(CartOwner owner, Long itemId) {
        Cart cart = find(owner).orElseThrow(() -> ApiException.notFound("Cart item"));
        cart.getItems().remove(item(cart, itemId));
        touch(cart);
        return toResponse(cart, new ArrayList<>());
    }

    public CartDtos.CartResponse applyCoupon(CartOwner owner, String code) {
        Cart cart = find(owner).filter(c -> !c.getItems().isEmpty())
                .orElseThrow(() -> ApiException.unprocessable("CART_EMPTY", "Add products before applying a coupon."));
        BigDecimal subtotal = pricing.quote(lines(cart), null, null).subtotal();
        cart.setCoupon(couponService.validate(code, subtotal, owner.userId()));
        touch(cart);
        return toResponse(cart, new ArrayList<>());
    }

    public CartDtos.CartResponse removeCoupon(CartOwner owner) {
        Cart cart = find(owner).orElseThrow(() -> ApiException.notFound("Cart"));
        cart.setCoupon(null);
        touch(cart);
        return toResponse(cart, new ArrayList<>());
    }

    /** On login: fold the anonymous cart into the user's active cart (quantities capped). */
    public void mergeAnonymousCart(String cartToken, Long userId) {
        Optional<Cart> anon = carts.findByCartTokenHashAndStatus(hasher.sha256(cartToken), CartStatus.ACTIVE);
        if (anon.isEmpty()) {
            return;
        }
        Cart source = anon.get();
        Cart target = carts.findByUserIdAndStatus(userId, CartStatus.ACTIVE).orElse(null);
        if (target == null) {
            // Adopt the anonymous cart as the user's cart.
            User user = users.getReferenceById(userId);
            source.setUser(user);
            source.setExpiresAt(null);
            touch(source);
            return;
        }
        for (CartItem si : source.getItems()) {
            CartItem existing = target.getItems().stream()
                    .filter(ti -> ti.getVariant().getId().equals(si.getVariant().getId())).findFirst().orElse(null);
            if (existing != null) {
                existing.setQuantity(Math.min(MAX_LINE_QUANTITY, existing.getQuantity() + si.getQuantity()));
            } else if (target.getItems().size() < MAX_LINES) {
                CartItem copy = new CartItem();
                copy.setCart(target);
                copy.setVariant(si.getVariant());
                copy.setQuantity(si.getQuantity());
                target.getItems().add(copy);
            }
        }
        if (target.getCoupon() == null) {
            target.setCoupon(source.getCoupon());
        }
        source.setStatus(CartStatus.MERGED);
        touch(target);
    }

    /** Active cart of a signed-in user, for checkout. */
    @Transactional(readOnly = true)
    public Optional<Cart> activeCartForUser(Long userId) {
        return carts.findByUserIdAndStatus(userId, CartStatus.ACTIVE);
    }

    // ---------------------------------------------------------------------------------------------

    private Optional<Cart> find(CartOwner owner) {
        if (owner.userId() != null) {
            return carts.findByUserIdAndStatus(owner.userId(), CartStatus.ACTIVE);
        }
        if (owner.cartToken() != null) {
            return carts.findByCartTokenHashAndStatus(hasher.sha256(owner.cartToken()), CartStatus.ACTIVE)
                    .filter(c -> c.getUser() == null)
                    .filter(c -> c.getExpiresAt() == null || c.getExpiresAt().isAfter(clock.instant()));
        }
        return Optional.empty();
    }

    private static CartItem item(Cart cart, Long itemId) {
        // Ownership is implied: only items of the caller's own cart are searched.
        return cart.getItems().stream().filter(i -> i.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Cart item"));
    }

    private static void checkQuantity(ProductVariant variant, int quantity) {
        if (quantity > MAX_LINE_QUANTITY) {
            throw ApiException.unprocessable("QUANTITY_LIMIT", "You can order at most " + MAX_LINE_QUANTITY + " of an item.");
        }
        if (quantity > variant.getStockQuantity()) {
            throw ApiException.conflict("INSUFFICIENT_STOCK", variant.getStockQuantity() == 0
                    ? "This item is out of stock."
                    : "Only " + variant.getStockQuantity() + " left in stock.");
        }
    }

    private void touch(Cart cart) {
        if (cart.getCartTokenHash() != null && cart.getUser() == null) {
            cart.setExpiresAt(clock.instant().plus(properties.retention().anonymousCarts()));
        }
        // Item-only changes do not dirty the cart row; bump UpdatedAt explicitly (used for abandonment reports).
        cart.touch();
        // Flush so newly added items have ids in the response.
        carts.saveAndFlush(cart);
    }

    private static List<PricingService.Line> lines(Cart cart) {
        return cart.getItems().stream()
                .filter(i -> i.getVariant().isActive() && i.getVariant().getProduct().getStatus() == ProductStatus.ACTIVE)
                .map(i -> new PricingService.Line(i.getVariant(), i.getQuantity()))
                .toList();
    }

    private ShippingMethod cheapestShipping() {
        return shippingMethods.findByActiveTrueOrderBySortOrderAsc().stream()
                .min(Comparator.comparing(ShippingMethod::getFee)).orElse(null);
    }

    private CartDtos.CartResponse toResponse(Cart cart, List<String> warnings) {
        Coupon coupon = cart.getCoupon();
        BigDecimal rawSubtotal = pricing.quote(lines(cart), null, null).subtotal();
        if (coupon != null) {
            try {
                couponService.validate(coupon.getCode(), rawSubtotal, cart.getUser() == null ? null : cart.getUser().getId());
            } catch (ApiException e) {
                warnings.add("Coupon " + coupon.getCode() + " no longer applies: " + e.getMessage());
                coupon = null;
            }
        }
        PricingService.Quote quote = pricing.quote(lines(cart), coupon, cheapestShipping());
        List<CartDtos.CartItemResponse> items = cart.getItems().stream().map(i -> {
            ProductVariant v = i.getVariant();
            boolean available = v.isActive() && v.getProduct().getStatus() == ProductStatus.ACTIVE && v.getStockQuantity() > 0;
            if (!available) {
                warnings.add(v.getProduct().getName() + " is no longer available and will not be ordered.");
            } else if (i.getQuantity() > v.getStockQuantity()) {
                warnings.add("Only " + v.getStockQuantity() + " of " + v.getProduct().getName() + " left in stock.");
            }
            BigDecimal unit = v.unitPrice();
            return new CartDtos.CartItemResponse(i.getId(), v.getId(), v.getProduct().getId(), v.getProduct().getName(),
                    v.getProduct().getSlug(), v.describe(), v.getSku(), CatalogMapper.primaryImageUrl(v.getProduct()),
                    unit, i.getQuantity(), unit.multiply(BigDecimal.valueOf(i.getQuantity())), available,
                    Math.min(v.getStockQuantity(), MAX_LINE_QUANTITY));
        }).toList();
        int count = cart.getItems().stream().mapToInt(CartItem::getQuantity).sum();
        return new CartDtos.CartResponse(cart.getPublicId(), items, count, coupon == null ? null : coupon.getCode(),
                quote.subtotal(), quote.discountTotal(), quote.shippingTotal(), quote.taxTotal(), quote.grandTotal(),
                properties.payment().currency(), warnings);
    }

    private CartDtos.CartResponse empty() {
        BigDecimal zero = BigDecimal.ZERO.setScale(2);
        return new CartDtos.CartResponse(null, List.of(), 0, null, zero, zero, zero, zero, zero,
                properties.payment().currency(), List.of());
    }
}
