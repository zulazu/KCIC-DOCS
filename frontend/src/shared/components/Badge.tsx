import type { ReactNode } from 'react';
import { cn } from '../utils/cn';

export type BadgeTone = 'success' | 'warning' | 'danger' | 'info' | 'neutral' | 'gold';

const tones: Record<BadgeTone, string> = {
  success: 'bg-success-bg text-success',
  warning: 'bg-warning-bg text-warning',
  danger: 'bg-danger-bg text-danger',
  info: 'bg-info-bg text-info',
  neutral: 'bg-neutral-bg text-neutral',
  gold: 'bg-gold-soft text-gold-foreground',
};

const statusTones: Record<string, BadgeTone> = {
  ACTIVE: 'success',
  APPROVED: 'success',
  PUBLISHED: 'success',
  FULFILLED: 'success',
  COMPLETED: 'success',
  PENDING: 'warning',
  UNDER_REVIEW: 'warning',
  IN_PROGRESS: 'warning',
  EXPIRING: 'warning',
  SUBMITTED: 'info',
  SENT: 'info',
  READ: 'success',
  PARTIALLY_REVOKED: 'warning',
  EXPIRED: 'neutral',
  REJECTED: 'danger',
  REVOKED: 'danger',
  FAILED: 'danger',
  LOCKED: 'danger',
  DRAFT: 'neutral',
  INACTIVE: 'neutral',
  TERMINATED: 'danger',
  ARCHIVED: 'neutral',
  CLOSED: 'neutral',
};

interface BadgeProps {
  tone?: BadgeTone;
  children: ReactNode;
  className?: string;
}

export function Badge({ tone = 'neutral', children, className }: BadgeProps) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-semibold whitespace-nowrap',
        tones[tone],
        className,
      )}
    >
      {children}
    </span>
  );
}

export function StatusBadge({ status, label }: { status: string; label?: string }) {
  const normalized = status.toUpperCase();
  return <Badge tone={statusTones[normalized] ?? 'neutral'}>{label ?? status}</Badge>;
}
