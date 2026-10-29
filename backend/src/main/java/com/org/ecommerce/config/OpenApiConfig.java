package com.org.ecommerce.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenAPI ecommerceOpenApi(AppProperties properties) {
        String cookieName = properties.security().sessionCookieName();
        return new OpenAPI()
                .info(new Info()
                        .title("E-Commerce API")
                        .version("v1")
                        .description("""
                                REST API for the storefront, customer account and admin dashboard.
                                Authentication uses an HTTP-only session cookie. State-changing requests
                                require the X-XSRF-TOKEN header (value of the XSRF-TOKEN cookie; call
                                GET /api/v1/auth/csrf first)."""))
                .components(new Components()
                        .addSecuritySchemes("sessionCookie", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name(cookieName)))
                .addSecurityItem(new SecurityRequirement().addList("sessionCookie"));
    }
}
