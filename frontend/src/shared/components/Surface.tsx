import type { ReactNode } from 'react';
import { cn } from '../utils/cn';

export function Card({
  children,
  className,
  as: Tag = 'section',
}: {
  children: ReactNode;
  className?: string;
  as?: 'section' | 'div' | 'article';
}) {
  return (
    <Tag className={cn('rounded-card border border-line bg-surface shadow-card', className)}>
      {children}
    </Tag>
  );
}

export function CardHeader({
  title,
  description,
  action,
}: {
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <div className="flex flex-wrap items-start justify-between gap-3 border-b border-line px-5 py-4">
      <div>
        <h2 className="text-[15px] font-bold text-ink">{title}</h2>
        {description ? (
          <p className="mt-0.5 text-[13px] leading-5 text-ink-secondary">{description}</p>
        ) : null}
      </div>
      {action}
    </div>
  );
}

export function PageHeader({
  title,
  description,
  action,
  meta,
}: {
  title: string;
  description?: string;
  action?: ReactNode;
  meta?: ReactNode;
}) {
  return (
    <header className="mb-5 flex flex-wrap items-end justify-between gap-4">
      <div className="min-w-0">
        <h1 className="font-display text-[30px] leading-9 font-bold tracking-tight text-ink sm:text-[34px] sm:leading-10">
          {title}
        </h1>
        <span aria-hidden="true" className="mt-1.5 block h-[3px] w-12 rounded-full bg-gold" />
        {description ? (
          <p className="mt-2 max-w-3xl text-sm text-ink-secondary">{description}</p>
        ) : null}
        {meta ? <div className="mt-2 flex flex-wrap items-center gap-2">{meta}</div> : null}
      </div>
      {action ? <div className="flex flex-wrap items-center gap-2 sm:justify-end">{action}</div> : null}
    </header>
  );
}

export function DetailRow({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid grid-cols-[120px_1fr] gap-3 border-b border-line py-2.5 last:border-b-0 sm:grid-cols-[140px_1fr]">
      <dt className="text-xs font-semibold text-ink-secondary">{label}</dt>
      <dd className="text-sm break-words text-ink">{children}</dd>
    </div>
  );
}
