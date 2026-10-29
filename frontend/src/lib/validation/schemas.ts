import { z } from "zod";

/**
 * Client-side validation mirrors the backend rules for fast feedback. The backend validates
 * again on every request; these schemas are a convenience, not a security boundary.
 */

const COMMON_PASSWORDS = new Set(["password123", "password1234", "1234567890", "qwertyuiop", "letmein123", "welcome123"]);

export const passwordSchema = z
  .string()
  .min(10, "Use at least 10 characters")
  .max(128, "Use at most 128 characters")
  .refine((v) => !COMMON_PASSWORDS.has(v.toLowerCase()), "This password is too common")
  .refine((v) => new Set(v).size >= 4, "Use a less repetitive password");

const email = z.string().trim().min(1, "Email is required").max(320).pipe(z.email("Enter a valid email address"));

export const loginSchema = z.object({
  email,
  password: z.string().min(1, "Password is required").max(128),
});
export type LoginValues = z.infer<typeof loginSchema>;

export const registerSchema = z
  .object({
    firstName: z.string().trim().min(1, "First name is required").max(100),
    lastName: z.string().trim().min(1, "Last name is required").max(100),
    email,
    password: passwordSchema,
    confirmPassword: z.string(),
    acceptTerms: z.boolean().refine((v) => v, "You must accept the terms and privacy policy"),
    analyticsConsent: z.boolean(),
    marketingConsent: z.boolean(),
  })
  .refine((v) => v.password === v.confirmPassword, { message: "Passwords do not match", path: ["confirmPassword"] });
export type RegisterValues = z.infer<typeof registerSchema>;

export const forgotPasswordSchema = z.object({ email });

export const resetPasswordSchema = z
  .object({ newPassword: passwordSchema, confirmPassword: z.string() })
  .refine((v) => v.newPassword === v.confirmPassword, { message: "Passwords do not match", path: ["confirmPassword"] });

export const changePasswordSchema = z
  .object({ currentPassword: z.string().min(1, "Current password is required"), newPassword: passwordSchema, confirmPassword: z.string() })
  .refine((v) => v.newPassword === v.confirmPassword, { message: "Passwords do not match", path: ["confirmPassword"] })
  .refine((v) => v.newPassword !== v.currentPassword, { message: "Choose a different password", path: ["newPassword"] });

const phone = z.string().trim().max(32).regex(/^[0-9+()\-\s]*$/, "Enter a valid phone number").optional().or(z.literal(""));

export const profileSchema = z.object({
  firstName: z.string().trim().min(1, "First name is required").max(100),
  lastName: z.string().trim().min(1, "Last name is required").max(100),
  phoneNumber: phone,
});

export const addressSchema = z.object({
  recipientName: z.string().trim().min(1, "Name is required").max(200),
  phoneNumber: phone,
  line1: z.string().trim().min(1, "Address is required").max(200),
  line2: z.string().trim().max(200).optional().or(z.literal("")),
  city: z.string().trim().min(1, "City is required").max(100),
  region: z.string().trim().max(100).optional().or(z.literal("")),
  postalCode: z.string().trim().max(20).optional().or(z.literal("")),
  countryCode: z.string().trim().regex(/^[A-Za-z]{2}$/, "Use a 2-letter country code (e.g. US)"),
});
export type AddressValues = z.infer<typeof addressSchema>;

export const checkoutSchema = z.object({
  shipping: addressSchema,
  billingSameAsShipping: z.boolean(),
  // Only validated when it differs from the shipping address (see superRefine).
  billing: z.custom<AddressValues>().optional(),
  shippingMethodCode: z.string().min(1, "Choose a shipping method"),
  couponCode: z.string().trim().max(40).optional().or(z.literal("")),
  note: z.string().max(500).optional().or(z.literal("")),
  saveAddress: z.boolean(),
}).superRefine((v, ctx) => {
  if (!v.billingSameAsShipping) {
    const r = addressSchema.safeParse(v.billing);
    if (!r.success) {
      for (const issue of r.error.issues) {
        ctx.addIssue({ code: "custom", message: issue.message, path: ["billing", ...issue.path] });
      }
    }
  }
});
export type CheckoutValues = z.infer<typeof checkoutSchema>;

const money = z.string().trim().regex(/^\d{1,13}(\.\d{1,2})?$/, "Enter an amount like 19.99");

export const productSchema = z
  .object({
    name: z.string().trim().min(1, "Name is required").max(200),
    sku: z.string().trim().min(1, "SKU is required").max(64).regex(/^[A-Za-z0-9._-]+$/, "Letters, digits, '.', '_' and '-' only"),
    categoryId: z.coerce.number<number>().int().positive("Choose a category"),
    brand: z.string().trim().max(100).optional().or(z.literal("")),
    shortDescription: z.string().trim().max(500).optional().or(z.literal("")),
    description: z.string().max(20000).optional().or(z.literal("")),
    basePrice: money,
    discountPrice: money.optional().or(z.literal("")),
    status: z.enum(["DRAFT", "ACTIVE", "ARCHIVED"]),
    featured: z.boolean(),
    initialStock: z.coerce.number<number>().int().min(0).max(1_000_000).optional(),
  })
  .refine((v) => !v.discountPrice || Number(v.discountPrice) < Number(v.basePrice), {
    message: "Discount price must be lower than the base price",
    path: ["discountPrice"],
  });
export type ProductValues = z.infer<typeof productSchema>;

export const categorySchema = z.object({
  name: z.string().trim().min(1, "Name is required").max(120),
  description: z.string().max(1000).optional().or(z.literal("")),
  parentId: z.string().optional(),
  sortOrder: z.coerce.number<number>().int().min(0).max(100000),
  active: z.boolean(),
});
export type CategoryValues = z.infer<typeof categorySchema>;

export const couponSchema = z
  .object({
    code: z.string().trim().min(1, "Code is required").max(40).regex(/^[A-Za-z0-9_-]+$/, "Letters, digits, '_' and '-' only"),
    description: z.string().max(255).optional().or(z.literal("")),
    discountType: z.enum(["PERCENTAGE", "FIXED_AMOUNT", "FREE_SHIPPING"]),
    discountValue: z.string().trim().regex(/^\d{1,13}(\.\d{1,2})?$/, "Enter a number"),
    minOrderAmount: money.optional().or(z.literal("")),
    usageLimit: z.string().regex(/^\d*$/, "Whole number").optional(),
    perUserLimit: z.string().regex(/^\d*$/, "Whole number").optional(),
    endsAt: z.string().optional(),
    active: z.boolean(),
  })
  .refine((v) => v.discountType !== "PERCENTAGE" || (Number(v.discountValue) > 0 && Number(v.discountValue) <= 100), {
    message: "Percentage must be between 1 and 100",
    path: ["discountValue"],
  });
export type CouponValues = z.infer<typeof couponSchema>;
