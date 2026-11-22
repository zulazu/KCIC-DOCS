import type { Page } from '@playwright/test';

export interface E2eAccount {
  username: string;
  password: string;
}

function requireEnv(name: string): string {
  const value = process.env[name];
  if (!value) {
    throw new Error(
      `${name} is not set. Export E2E credentials before running the suite (see README "E2E tests").`,
    );
  }
  return value;
}

export function adminAccount(): E2eAccount {
  return {
    username: requireEnv('KCIC_E2E_ADMIN_USERNAME'),
    password: requireEnv('KCIC_E2E_ADMIN_PASSWORD'),
  };
}

export function employeeAccount(): E2eAccount {
  return {
    username: requireEnv('KCIC_E2E_EMPLOYEE_USERNAME'),
    password: requireEnv('KCIC_E2E_EMPLOYEE_PASSWORD'),
  };
}

export async function login(page: Page, account: E2eAccount) {
  await page.goto('/login');
  await page.getByRole('textbox', { name: 'Username atau email' }).fill(account.username);
  await page.getByRole('textbox', { name: 'Password' }).fill(account.password);
  await page.getByRole('button', { name: 'Masuk' }).click();
  await page.getByRole('heading', { level: 1, name: /Selamat datang/ }).waitFor();
}

export async function logout(page: Page) {
  await page.getByRole('button', { name: 'Keluar dari aplikasi' }).click();
  await page.getByRole('button', { name: 'Masuk' }).waitFor();
}
