import { expect, test } from '@playwright/test';
import { employeeAccount, login, logout } from './helpers';

test('unauthenticated deep link redirects to login', async ({ page }) => {
  await page.goto('/admin/audit');
  await expect(page).toHaveURL(/\/login/);
  await expect(page.getByRole('button', { name: 'Masuk' })).toBeVisible();
});

test('invalid credentials are rejected with a generic message', async ({ page }) => {
  await page.goto('/login');
  await page.getByRole('textbox', { name: 'Username atau email' }).fill('no.such.user');
  await page.getByRole('textbox', { name: 'Password' }).fill('Definitely-not-valid-2026!');
  await page.getByRole('button', { name: 'Masuk' }).click();
  await expect(page.getByRole('alert')).toContainText(/tidak sesuai/i);
  await expect(page).toHaveURL(/\/login/);
});

test('employee session shows permitted navigation and can log out', async ({ page }) => {
  await login(page, employeeAccount());

  const sidebar = page.getByRole('navigation', { name: 'Navigasi utama' });
  await expect(sidebar.getByRole('link', { name: 'Dokumen' })).toBeVisible();
  await expect(sidebar.getByRole('link', { name: 'User Management' })).toHaveCount(0);
  await expect(sidebar.getByRole('link', { name: 'Audit Trail' })).toHaveCount(0);

  await logout(page);
  await expect(page).toHaveURL(/\/login/);
});

test('employee cannot open administration pages', async ({ page }) => {
  await login(page, employeeAccount());

  for (const path of ['/admin/users', '/admin/audit']) {
    await page.goto(path);
    await expect(page.getByText('Akses tidak diizinkan')).toBeVisible();
  }

  await logout(page);
});
