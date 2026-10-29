import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { CartSummary } from "@/components/cart/shopping-cart";
import { Pagination } from "@/components/common/pagination";
import { ProductCard } from "@/components/product/product-card";
import type { ProductSummary } from "@/lib/api/types";

const push = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push, replace: vi.fn(), refresh: vi.fn() }),
  useSearchParams: () => new URLSearchParams("next=/checkout"),
  usePathname: () => "/login",
}));

const login = vi.fn();
vi.mock("@/lib/api/endpoints", () => ({
  authApi: { login: (...args: unknown[]) => login(...args), me: vi.fn() },
  cartApi: { get: vi.fn(() => Promise.resolve(null)) },
}));

const product: ProductSummary = {
  id: 1, name: "Trail Runner X", slug: "trail-runner-x", sku: "TRX", brand: "Acme", shortDescription: null,
  price: "99.00", basePrice: "129.00", discountPrice: "99.00", currency: "USD", imageUrl: null, categoryId: 1,
  categoryName: "Running", inStock: false, featured: true, publishedAt: null,
};

describe("ProductCard", () => {
  it("shows the sale price, the struck original price and stock badges", () => {
    render(<ProductCard product={product} />);
    expect(screen.getByRole("link")).toHaveAttribute("href", "/products/1");
    expect(screen.getByText("$99.00")).toBeInTheDocument();
    expect(screen.getByText("$129.00")).toHaveClass("line-through");
    expect(screen.getByText("Sale")).toBeInTheDocument();
    expect(screen.getByText("Sold out")).toBeInTheDocument();
  });
});

describe("CartSummary", () => {
  it("renders server-provided totals and the coupon discount", () => {
    render(<CartSummary cart={{ subtotal: "198.00", discountTotal: "19.80", shippingEstimate: "0.00", taxTotal: "0.00",
      total: "178.20", currency: "USD", couponCode: "WELCOME10" }} />);
    expect(screen.getByText("Discount (WELCOME10)")).toBeInTheDocument();
    expect(screen.getByText("−$19.80")).toBeInTheDocument();
    expect(screen.getByText("$178.20")).toBeInTheDocument();
  });
});

describe("Pagination", () => {
  it("disables previous on the first page and reports the next page", async () => {
    const onPageChange = vi.fn();
    render(<Pagination page={0} totalPages={3} totalElements={30} onPageChange={onPageChange} />);
    expect(screen.getByRole("button", { name: "Previous page" })).toBeDisabled();
    await userEvent.click(screen.getByRole("button", { name: "Next page" }));
    expect(onPageChange).toHaveBeenCalledWith(1);
  });
});

describe("LoginForm", () => {
  async function renderForm() {
    const { LoginForm } = await import("@/components/auth/auth-forms");
    const { AuthProvider } = await import("@/components/providers/auth-provider");
    render(<AuthProvider initialUser={null}><LoginForm /></AuthProvider>);
  }

  it("shows validation errors and does not call the API for invalid input", async () => {
    await renderForm();
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));
    expect(await screen.findByText("Email is required")).toBeInTheDocument();
    expect(screen.getByText("Password is required")).toBeInTheDocument();
    expect(login).not.toHaveBeenCalled();
  });

  it("signs in and redirects only to safe same-site destinations", async () => {
    login.mockResolvedValueOnce({ id: "u1", email: "ann@example.com", firstName: "Ann", lastName: "Lee", roles: ["ROLE_CUSTOMER"], permissions: [], emailVerified: true, phoneNumber: null, createdAt: "" });
    await renderForm();
    await userEvent.type(screen.getByLabelText("Email"), "ann@example.com");
    await userEvent.type(screen.getByLabelText("Password"), "whatever-password");
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));
    expect(login).toHaveBeenCalledWith({ email: "ann@example.com", password: "whatever-password" });
    expect(push).toHaveBeenCalledWith("/checkout");
  });

  it("rejects open-redirect targets", async () => {
    const { safeNext } = await import("@/components/auth/auth-forms");
    expect(safeNext("//evil.example")).toBe("/account");
    expect(safeNext("https://evil.example")).toBe("/account");
    expect(safeNext("/\\evil.example")).toBe("/account");
    expect(safeNext("/orders")).toBe("/orders");
  });
});
