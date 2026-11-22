import type { ButtonHTMLAttributes, ReactNode } from 'react';
import { Link } from 'react-router-dom';
import type { LinkProps } from 'react-router-dom';
import { cn } from '../utils/cn';

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger';

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant;
  size?: 'default' | 'compact';
  loading?: boolean;
  children: ReactNode;
};

const variants: Record<ButtonVariant, string> = {
  primary:
    'bg-primary text-white hover:bg-primary-hover active:bg-primary-pressed disabled:bg-primary/50',
  secondary:
    'border border-line-strong bg-surface text-ink hover:bg-surface-muted disabled:text-ink-disabled',
  ghost: 'bg-transparent text-ink hover:bg-surface-muted disabled:text-ink-disabled',
  danger:
    'bg-danger text-white hover:bg-danger-hover active:bg-danger-pressed disabled:bg-danger/50',
};

function buttonClasses(
  variant: ButtonVariant,
  size: 'default' | 'compact',
  className?: string,
): string {
  return cn(
    'inline-flex items-center justify-center gap-2 rounded-lg font-semibold transition-colors duration-150',
    'disabled:cursor-not-allowed',
    size === 'default' ? 'h-10 px-4 text-sm' : 'h-8 px-3 text-xs',
    variants[variant],
    className,
  );
}

export function Button({
  variant = 'primary',
  size = 'default',
  loading = false,
  disabled,
  className,
  children,
  type = 'button',
  ...rest
}: ButtonProps) {
  return (
    <button
      type={type}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={buttonClasses(variant, size, className)}
      {...rest}
    >
      {loading ? <Spinner className="size-4" /> : null}
      {children}
    </button>
  );
}

export function ButtonLink({
  variant = 'primary',
  size = 'default',
  className,
  children,
  ...rest
}: LinkProps & {
  variant?: ButtonVariant;
  size?: 'default' | 'compact';
  children: ReactNode;
}) {
  return (
    <Link className={buttonClasses(variant, size, className)} {...rest}>
      {children}
    </Link>
  );
}

export function Spinner({ className }: { className?: string }) {
  return (
    <svg
      className={cn('animate-spin', className ?? 'size-5')}
      viewBox="0 0 24 24"
      fill="none"
      aria-hidden="true"
    >
      <circle cx="12" cy="12" r="9" stroke="currentColor" strokeOpacity="0.25" strokeWidth="3" />
      <path d="M21 12a9 9 0 0 0-9-9" stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
    </svg>
  );
}
