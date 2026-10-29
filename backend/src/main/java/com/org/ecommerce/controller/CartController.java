package com.org.ecommerce.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.org.ecommerce.config.AppProperties;
import com.org.ecommerce.dto.cart.CartDtos;
import com.org.ecommerce.security.CurrentUser;
import com.org.ecommerce.security.SessionCookies;
import com.org.ecommerce.service.CartService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Works for anonymous shoppers (cart cookie) and signed-in users (account cart). */
@Tag(name = "Cart")
@RestController
@RequestMapping("/api/v1/cart")
public class CartController {

    private final CartService cartService;
    private final SessionCookies cookies;
    private final AppProperties properties;

    public CartController(CartService cartService, SessionCookies cookies, AppProperties properties) {
        this.cartService = cartService;
        this.cookies = cookies;
        this.properties = properties;
    }

    @GetMapping
    public CartDtos.CartResponse get(HttpServletRequest request) {
        return cartService.view(owner(request));
    }

    @Operation(summary = "Add a product variant (quantities are merged)")
    @PostMapping("/items")
    public CartDtos.CartResponse add(@Valid @RequestBody CartDtos.AddItemRequest body, HttpServletRequest request,
                                     HttpServletResponse response) {
        CartService.CartResult result = cartService.addItem(owner(request), body.variantId(), body.quantity());
        if (result.newCartToken() != null) {
            cookies.writeCart(response, result.newCartToken(), properties.retention().anonymousCarts());
        }
        return result.cart();
    }

    @PutMapping("/items/{id}")
    public CartDtos.CartResponse update(@PathVariable Long id, @Valid @RequestBody CartDtos.UpdateItemRequest body,
                                        HttpServletRequest request) {
        return cartService.updateItem(owner(request), id, body.quantity());
    }

    @DeleteMapping("/items/{id}")
    public CartDtos.CartResponse remove(@PathVariable Long id, HttpServletRequest request) {
        return cartService.removeItem(owner(request), id);
    }

    @PostMapping("/coupon")
    public CartDtos.CartResponse applyCoupon(@Valid @RequestBody CartDtos.ApplyCouponRequest body,
                                             HttpServletRequest request) {
        return cartService.applyCoupon(owner(request), body.code());
    }

    @DeleteMapping("/coupon")
    public CartDtos.CartResponse removeCoupon(HttpServletRequest request) {
        return cartService.removeCoupon(owner(request));
    }

    private CartService.CartOwner owner(HttpServletRequest request) {
        Long userId = CurrentUser.idOrNull();
        return new CartService.CartOwner(userId, userId == null ? cookies.readCart(request).orElse(null) : null);
    }
}
