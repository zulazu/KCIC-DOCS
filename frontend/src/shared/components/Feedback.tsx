import type { ReactNode } from 'react';
import { cn } from '../utils/cn';
import { Button } from './Button';
import { Icon } from './Icon';

type AlertTone = 'danger' | 'warning' | 'info' | 'success';

const alertTones: Record<AlertTone, string> = {
  danger: 'border-danger/25 bg-danger-bg text-danger',
  warning: 'border-warning/25 bg-warning-bg text-warning',
  info: 'border-info/20 bg-info-bg text-info',
  success: 'border-success/25 bg-success-bg text-success',
};

export function Alert({
  tone = 'danger',
  title,
  children,
}: {
  tone?: AlertTone;
  title?: string;
  children: ReactNode;
}) {
  return (
    <div
      role={tone === 'danger' ? 'alert' : 'status'}
      className={cn('flex gap-2.5 rounded-lg border px-3.5 py-3 text-sm', alertTones[tone])}
    >
      <Icon name={tone === 'success' ? 'check' : 'alert'} className="mt-0.5 shrink-0" />
      <div className="space-y-0.5">
        {title ? <p className="font-semibold">{title}</p> : null}
        <div className="[&_a]:underline">{children}</div>
      </div>
    </div>
  );
}

export function LoadingBlock({ label = 'Memuat data…' }: { label?: string }) {
  return (
    <div role="status" className="space-y-3 py-2">
      <span className="sr-only">{label}</span>
      <div className="skeleton h-5 w-40" />
      <div className="skeleton h-16 w-full" />
      <div className="skeleton h-16 w-full" />
      <div className="skeleton h-16 w-full" />
    </div>
  );
}

export function EmptyState({
  title,
  description,
  action,
}: {
  title: string;
  description: string;
  action?: ReactNode;
}) {
  return (
    <div className="flex flex-col items-center gap-2 px-6 py-12 text-center">
      <span className="mb-1 flex size-11 items-center justify-center rounded-full bg-surface-muted text-ink-secondary">
        <Icon name="folder" size={20} />
      </span>
      <p className="text-base font-semibold text-ink">{title}</p>
      <p className="max-w-md text-sm text-ink-secondary">{description}</p>
      {action ? <div className="mt-3">{action}</div> : null}
    </div>
  );
}

export function ErrorState({
  title = 'Data tidak dapat dimuat',
  message,
  onRetry,
}: {
  title?: string;
  message: string;
  onRetry?: () => void;
}) {
  return (
    <div className="flex flex-col items-center gap-2 px-6 py-12 text-center">
      <span className="mb-1 flex size-11 items-center justify-center rounded-full bg-danger-bg text-danger">
        <Icon name="alert" size={20} />
      </span>
      <p className="text-base font-semibold text-ink">{title}</p>
      <p className="max-w-md text-sm text-ink-secondary">{message}</p>
      {onRetry ? (
        <Button variant="secondary" className="mt-3" onClick={onRetry}>
          Coba lagi
        </Button>
      ) : null}
    </div>
  );
}

export function ForbiddenState({ message }: { message?: string }) {
  return (
    <div className="flex flex-col items-center gap-2 px-6 py-16 text-center">
      <span className="mb-1 flex size-11 items-center justify-center rounded-full bg-warning-bg text-warning">
        <Icon name="lock" size={20} />
      </span>
      <p className="text-lg font-semibold text-ink">Akses tidak diizinkan</p>
      <p className="max-w-md text-sm text-ink-secondary">
        {message ?? 'Akun Anda tidak memiliki izin untuk membuka halaman ini.'}
      </p>
    </div>
  );
}
