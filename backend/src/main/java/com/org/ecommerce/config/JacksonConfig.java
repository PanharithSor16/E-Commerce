package com.org.ecommerce.config;

import java.math.BigDecimal;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;

@Configuration(proxyBeanMethods = false)
public class JacksonConfig {

    /**
     * Money is serialized as a decimal string with at least 2 decimals ("179.98", not 179.98 or
     * "179.9800") so clients never round-trip it through floats.
     * Omitted optional booleans/ints in requests default to false/0 (Jackson 3 rejects them by default).
     */
    @Bean
    JsonMapperBuilderCustomizer jsonCustomizer() {
        return builder -> builder
                .addModule(new SimpleModule("money").addSerializer(BigDecimal.class, new MoneySerializer()))
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
    }

    static final class MoneySerializer extends StdSerializer<BigDecimal> {

        MoneySerializer() {
            super(BigDecimal.class);
        }

        @Override
        public void serialize(BigDecimal value, JsonGenerator gen, SerializationContext ctxt) {
            gen.writeString(format(value));
        }

        static String format(BigDecimal value) {
            BigDecimal v = value.stripTrailingZeros();
            if (v.scale() < 2) {
                v = v.setScale(2);
            }
            return v.toPlainString();
        }
    }
}
