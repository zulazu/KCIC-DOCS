import { expect, test } from '@playwright/test';
import { adminAccount, login, logout } from './helpers';

test('admin reaches administration pages from the sidebar and deep links', async ({ page }) => {
  await login(page, adminAccount());

  const sidebar = page.getByRole('navigation', { name: 'Navigasi utama' });
  await expect(sidebar.getByRole('link', { name: 'User Management' })).toBeVisible();
  await expect(sidebar.getByRole('link', { name: 'Audit Trail' })).toBeVisible();

  await page.goto('/admin/users');
  await expect(page.getByRole('heading', { level: 1, name: 'User Management' })).toBeVisible();
  await expect(page.getByRole('table')).toBeVisible();

  await page.goto('/admin/audit');
  await expect(page.getByRole('heading', { level: 1, name: 'Audit Trail' })).toBeVisible();

  await logout(page);
});

test('audit trail filters to login events', async ({ page }) => {
  await login(page, adminAccount());
  await page.goto('/admin/audit');

  await page.getByRole('textbox', { name: 'Aksi' }).fill('LOGIN_SUCCESS');
  await page.getByRole('button', { name: 'Telusuri' }).click();

  const table = page.getByRole('table', { name: 'Daftar audit event' });
  await expect(table.locator('tbody tr').first()).toBeVisible();
  await expect(table).toContainText('LOGIN_SUCCESS');
  await expect(page.getByText(/Menampilkan \d+–\d+ dari \d+ event/)).toBeVisible();

  await logout(page);
});

test('unknown routes show the not-found page', async ({ page }) => {
  await login(page, adminAccount());
  await page.goto('/halaman-tidak-ada');
  await expect(page.getByRole('heading', { level: 1, name: 'Halaman tidak ditemukan' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Kembali ke dashboard' })).toBeVisible();
  await logout(page);
});
