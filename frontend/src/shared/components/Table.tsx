import type { ReactNode, TdHTMLAttributes, ThHTMLAttributes } from 'react';
import { cn } from '../utils/cn';

export function Table({
  children,
  className,
  caption,
}: {
  children: ReactNode;
  className?: string;
  caption?: string;
}) {
  return (
    <div className="overflow-x-auto">
      <table className={cn('w-full border-collapse text-sm', className)}>
        {caption ? <caption className="sr-only">{caption}</caption> : null}
        {children}
      </table>
    </div>
  );
}

export function THead({ children }: { children: ReactNode }) {
  return (
    <thead>
      <tr className="border-b border-line">{children}</tr>
    </thead>
  );
}

export function Th({
  children,
  className,
  align = 'left',
  ...rest
}: ThHTMLAttributes<HTMLTableCellElement> & { align?: 'left' | 'right' }) {
  return (
    <th
      scope="col"
      className={cn(
        'bg-surface-muted px-4 py-2.5 text-[11px] font-semibold tracking-[0.08em] text-ink-secondary uppercase md:px-5',
        align === 'right' ? 'text-right' : 'text-left',
        className,
      )}
      {...rest}
    >
      {children}
    </th>
  );
}

export function Tr({
  children,
  className,
  hover = true,
}: {
  children: ReactNode;
  className?: string;
  hover?: boolean;
}) {
  return (
    <tr
      className={cn(
        'border-b border-line last:border-b-0',
        hover && 'transition-colors hover:bg-primary-subtle/40',
        className,
      )}
    >
      {children}
    </tr>
  );
}

export function Td({ children, className, ...rest }: TdHTMLAttributes<HTMLTableCellElement>) {
  return (
    <td className={cn('px-4 py-3 md:px-5', className)} {...rest}>
      {children}
    </td>
  );
}