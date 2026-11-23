const dateTimeFormatter = new Intl.DateTimeFormat('id-ID', {
  dateStyle: 'medium',
  timeStyle: 'short',
  timeZone: 'Asia/Jakarta',
});

const dateFormatter = new Intl.DateTimeFormat('id-ID', {
  dateStyle: 'medium',
  timeZone: 'Asia/Jakarta',
});

export function formatDateTime(value: string | null | undefined): string {
  if (!value) {
    return '—';
  }
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? '—' : dateTimeFormatter.format(parsed);
}

export function formatDate(value: string | null | undefined): string {
  if (!value) {
    return '—';
  }
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? '—' : dateFormatter.format(parsed);
}

export function initials(displayName: string): string {
  const parts = displayName.trim().split(/\s+/).slice(0, 2);
  return parts.map((part) => part.charAt(0).toUpperCase()).join('') || '?';
}

export function humanizeStatus(status: string): string {
  return status
    .split('_')
    .map((part) => part.charAt(0) + part.slice(1).toLowerCase())
    .join(' ');
}

const ROLE_LABELS: Record<string, string> = {
  SYSTEM_ADMIN: 'System Administrator',
  ADMINISTRATOR: 'Administrator',
  DOCUMENT_ADMIN: 'Document Administrator',
  ADMIN: 'Admin',
  UNIT_MANAGER: 'Unit Manager',
  PIC: 'PIC',
  CONTRIBUTOR: 'Contributor',
  EMPLOYEE: 'User (Employee)',
  AUDITOR: 'Auditor',
};

export function roleLabel(roleCode: string): string {
  return ROLE_LABELS[roleCode] ?? humanizeStatus(roleCode);
}
