import { useId, useState } from 'react';
import type { ReactNode } from 'react';
import { cn } from '../utils/cn';
import { Button } from './Button';
import { Icon } from './Icon';
import { Card } from './Surface';

export function FilterPanel({
  children,
  activeCount = 0,
  className,
}: {
  children: ReactNode;
  activeCount?: number;
  className?: string;
}) {
  const [open, setOpen] = useState(false);
  const panelId = useId();

  return (
    <Card className={cn('mb-5', className)}>
      <div className="flex items-center justify-between gap-3 border-b border-line px-4 py-3 lg:hidden">
        <Button
          variant="secondary"
          size="compact"
          aria-expanded={open}
          aria-controls={panelId}
          onClick={() => setOpen((value) => !value)}
        >
          <Icon name="search" size={14} />
          Filter dan pencarian
          {activeCount > 0 ? (
            <span className="rounded-full bg-primary-subtle px-1.5 text-[11px] font-bold text-primary">
              {activeCount}
            </span>
          ) : null}
          <Icon
            name="chevron-down"
            size={14}
            className={cn('transition-transform', open && 'rotate-180')}
          />
        </Button>
      </div>
      <div id={panelId} className={cn('p-4', open ? 'block' : 'hidden lg:block')}>
        {children}
      </div>
    </Card>
  );
}
