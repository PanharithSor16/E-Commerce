import { expect, test, type Page } from "@playwright/test";

/**
 * Critical journeys: registration, login, cart and checkout with payment.
 * Requires the dev seed catalog (backend dev profile) and PAYMENT_PROVIDER=MOCK.
 */

const password = "E2e-Test-Passphrase-1";
const uniqueEmail = () => `e2e-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.com`;

async function dismissConsent(page: Page) {
  const reject = page.getByRole("button", { name: "Reject" });
  if (await reject.isVisible().catch(() => false)) {
    await reject.click();
  }
}

async function register(page: Page, email: string, next = "") {
  await page.goto(`/register${next ? `?next=${encodeURIComponent(next)}` : ""}`);
  await dismissConsent(page);
  await page.getByLabel("First name").fill("End");
  await page.getByLabel("Last name").fill("ToEnd");
  await page.getByLabel("Email", { exact: true }).fill(email);
  await page.getByLabel("Password", { exact: true }).fill(password);
  await page.getByLabel("Confirm password").fill(password);
  await page.getByText("I accept the terms").click();
  await page.getByRole("button", { name: "Create account" }).click();
}

async function addFirstInStockProduct(page: Page) {
  await page.goto("/products");
  await dismissConsent(page);
  // Seed product "Daypack 22L" has a single default variant with plenty of stock.
  await page.getByRole("link", { name: /Daypack 22L/ }).click();
  await expect(page.getByRole("heading", { name: "Daypack 22L" })).toBeVisible();
  await page.getByRole("button", { name: "Add to cart" }).click();
  await expect(page.getByText("Added to cart")).toBeVisible();
}

test("registration validates input and creates a signed-in account", async ({ page }) => {
  await page.goto("/register");
  await dismissConsent(page);
  await page.getByRole("button", { name: "Create account" }).click();
  await expect(page.getByText("First name is required")).toBeVisible();
  await expect(page.getByText("You must accept the terms and privacy policy")).toBeVisible();

  await register(page, uniqueEmail());
  await expect(page).toHaveURL(/\/account/);
  await expect(page.getByRole("heading", { name: /Hello, End/ })).toBeVisible();
});

test("login rejects wrong passwords generically and accepts the right one", async ({ page }) => {
  const email = uniqueEmail();
  await register(page, email);
  await expect(page).toHaveURL(/\/account/);
  await page.getByRole("button", { name: "Account menu" }).click();
  await page.getByRole("menuitem", { name: "Sign out" }).click();

  await page.goto("/login");
  await page.getByLabel("Email", { exact: true }).fill(email);
  await page.getByLabel("Password").fill("definitely-wrong-password");
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page.getByText("Invalid email or password.")).toBeVisible();

  await page.getByLabel("Password").fill(password);
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page).toHaveURL(/\/account/);
});

test("protected pages redirect anonymous visitors to login", async ({ page }) => {
  await page.goto("/account/orders");
  await expect(page).toHaveURL(/\/login\?next=%2Faccount%2Forders/);
  await page.goto("/admin");
  await expect(page).toHaveURL(/\/login/);
});

test("anonymous cart is kept through registration and checkout completes with payment", async ({ page }) => {
  await addFirstInStockProduct(page);
  await page.goto("/cart");
  await expect(page.getByRole("link", { name: "Daypack 22L" })).toBeVisible();
  await page.getByRole("button", { name: /Increase quantity of Daypack/ }).click();
  await expect(page.getByText("$178.00").first()).toBeVisible();

  await page.getByLabel("Coupon code").fill("NOT-A-COUPON");
  await page.getByRole("button", { name: "Apply" }).click();
  await expect(page.getByText(/invalid or has expired/)).toBeVisible();

  await page.getByRole("link", { name: "Sign in to check out" }).click();
  await page.getByRole("link", { name: "Create an account" }).click();
  await register(page, uniqueEmail(), "/checkout");
  await expect(page).toHaveURL(/\/checkout/);

  await page.getByRole("button", { name: "Place order and pay" }).click();
  await expect(page.getByText("Address is required")).toBeVisible();

  await page.getByLabel("Address", { exact: true }).fill("1 Test Street");
  await page.getByLabel("City").fill("Phnom Penh");
  await page.getByLabel("Country code").fill("KH");
  await page.getByRole("button", { name: "Place order and pay" }).click();

  await expect(page).toHaveURL(/\/checkout\/payment\/\d+/);
  await page.getByRole("button", { name: "Simulate declined card" }).click();
  await expect(page.getByText(/declined/i)).toBeVisible();
  await page.getByRole("button", { name: "Pay now (test card)" }).click();
  await expect(page.getByText("Thank you! Your order is confirmed.")).toBeVisible();

  await page.getByRole("link", { name: "View order" }).click();
  await expect(page.getByText("Paid", { exact: true })).toBeVisible();
});

test("storefront is usable on a phone @mobile", async ({ page }) => {
  await page.goto("/");
  await dismissConsent(page);
  await page.getByRole("button", { name: "Open menu" }).click();
  await page.getByRole("link", { name: "All products" }).last().click();
  await expect(page.getByRole("heading", { name: "All products" })).toBeVisible();
});
