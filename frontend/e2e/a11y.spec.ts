import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { adminAccount, login, logout } from './helpers';

const WCAG_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'];

const ADMIN_ROUTES = [
  '/',
  '/documents',
  '/workspaces',
  '/requests',
  '/distributions',
  '/templates',
  '/notifications',
  '/admin/users',
  '/admin/employees',
  '/admin/roles',
  '/admin/organization',
  '/admin/master-data',
  '/admin/audit',
  '/admin/migration',
  '/change-password',
];

function formatViolations(violations: Awaited<ReturnType<AxeBuilder['analyze']>>['violations']) {
  return violations
    .map(
      (v) =>
        `${v.id} (${v.impact}): ${v.help}\n` +
        v.nodes.map((n) => `  - ${n.html}\n    ${n.failureSummary ?? ''}`).join('\n'),
    )
    .join('\n\n');
}

async function scan(page: import('@playwright/test').Page, route: string) {
  await page.goto(route);
  await page.waitForLoadState('networkidle');
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  const results = await new AxeBuilder({ page }).withTags(WCAG_TAGS).analyze();
  expect(results.violations, formatViolations(results.violations)).toEqual([]);
}

test('login page has no WCAG 2.2 AA violations', async ({ page }) => {
  await page.goto('/login');
  await page.waitForLoadState('networkidle');
  const results = await new AxeBuilder({ page }).withTags(WCAG_TAGS).analyze();
  expect(results.violations, formatViolations(results.violations)).toEqual([]);
});

test('authenticated pages have no WCAG 2.2 AA violations', async ({ page }) => {
  await login(page, adminAccount());
  for (const route of ADMIN_ROUTES) {
    await scan(page, route);
  }
  await logout(page);
});

test('dashboard and documents pass axe at mobile width', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await login(page, adminAccount());
  await scan(page, '/');
  await scan(page, '/documents');
  await logout(page);
});
