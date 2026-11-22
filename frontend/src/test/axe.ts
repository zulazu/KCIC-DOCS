import axe from 'axe-core';

const WCAG_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'];

// jsdom cannot compute layout, so color-contrast is verified in the browser instead.
const JSDOM_DISABLED_RULES = { 'color-contrast': { enabled: false } };
const PAGE_LEVEL_RULES = [
  'region',
  'landmark-one-main',
  'page-has-heading-one',
  'bypass',
  'landmark-banner-is-top-level',
  'landmark-contentinfo-is-top-level',
  'landmark-main-is-top-level',
  'landmark-no-duplicate-banner',
  'landmark-no-duplicate-contentinfo',
];

export interface AxeFinding {
  id: string;
  impact: string | null;
  help: string;
  nodes: string[];
}

export async function runAxe(node: Element, pageLevel = false): Promise<AxeFinding[]> {
  const disabledRules: Record<string, { enabled: boolean }> = { ...JSDOM_DISABLED_RULES };
  if (!pageLevel) {
    for (const rule of PAGE_LEVEL_RULES) {
      disabledRules[rule] = { enabled: false };
    }
  }

  const results = await axe.run(node, {
    runOnly: { type: 'tag', values: WCAG_TAGS },
    rules: disabledRules,
  });

  return results.violations.map((violation) => ({
    id: violation.id,
    impact: violation.impact ?? null,
    help: violation.help,
    nodes: violation.nodes.map((n) => n.html),
  }));
}
