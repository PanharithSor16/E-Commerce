import { describe, expect, it } from "vitest";

import { checkoutSchema, couponSchema, loginSchema, productSchema, registerSchema } from "./schemas";

const validRegistration = {
  firstName: "Ann", lastName: "Lee", email: "ann@example.com", password: "correct horse battery",
  confirmPassword: "correct horse battery", acceptTerms: true, analyticsConsent: false, marketingConsent: false,
};

const address = { recipientName: "Ann Lee", line1: "1 Main St", city: "Phnom Penh", countryCode: "KH" };

describe("loginSchema", () => {
  it("requires a valid email and a password", () => {
    expect(loginSchema.safeParse({ email: "nope", password: "" }).success).toBe(false);
    expect(loginSchema.safeParse({ email: " ann@example.com ", password: "x" }).success).toBe(true);
  });
});

describe("registerSchema", () => {
  it("accepts a valid registration", () => {
    expect(registerSchema.safeParse(validRegistration).success).toBe(true);
  });

  it("rejects short, common and repetitive passwords", () => {
    for (const password of ["short", "password123", "aaaaaaaaaaaa"]) {
      const r = registerSchema.safeParse({ ...validRegistration, password, confirmPassword: password });
      expect(r.success, password).toBe(false);
    }
  });

  it("requires matching passwords and accepted terms", () => {
    const mismatch = registerSchema.safeParse({ ...validRegistration, confirmPassword: "something else entirely" });
    expect(mismatch.error?.issues[0].path).toEqual(["confirmPassword"]);
    expect(registerSchema.safeParse({ ...validRegistration, acceptTerms: false }).success).toBe(false);
  });
});

describe("checkoutSchema", () => {
  const base = { shipping: address, billingSameAsShipping: true, shippingMethodCode: "STANDARD", saveAddress: false };

  it("ignores the hidden billing form when billing equals shipping", () => {
    const blank = { recipientName: "", line1: "", city: "", countryCode: "" };
    expect(checkoutSchema.safeParse({ ...base, billing: blank }).success).toBe(true);
  });

  it("validates billing fields when billing differs", () => {
    const r = checkoutSchema.safeParse({ ...base, billingSameAsShipping: false, billing: { ...address, countryCode: "" } });
    expect(r.success).toBe(false);
    expect(r.error?.issues.some((i) => i.path.join(".") === "billing.countryCode")).toBe(true);
  });

  it("requires a 2-letter country code", () => {
    expect(checkoutSchema.safeParse({ ...base, shipping: { ...address, countryCode: "KHM" } }).success).toBe(false);
  });
});

describe("productSchema", () => {
  const product = { name: "Shoe", sku: "SHOE-1", categoryId: "1", basePrice: "100.00", status: "ACTIVE", featured: false };

  it("coerces the category id and accepts 2-decimal prices", () => {
    const r = productSchema.safeParse(product);
    expect(r.success).toBe(true);
    expect(r.data?.categoryId).toBe(1);
  });

  it("rejects a sale price that is not lower than the base price", () => {
    expect(productSchema.safeParse({ ...product, discountPrice: "100.00" }).success).toBe(false);
    expect(productSchema.safeParse({ ...product, basePrice: "10.999" }).success).toBe(false);
  });
});

describe("couponSchema", () => {
  it("limits percentages to 1-100", () => {
    const c = { code: "SAVE", discountType: "PERCENTAGE", discountValue: "150", active: true };
    expect(couponSchema.safeParse(c).success).toBe(false);
    expect(couponSchema.safeParse({ ...c, discountValue: "15" }).success).toBe(true);
  });
});
