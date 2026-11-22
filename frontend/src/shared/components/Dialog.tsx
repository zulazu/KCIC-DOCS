import { useEffect, useId, useRef } from 'react';
import type { ReactNode } from 'react';
import { cn } from '../utils/cn';
import { Button } from './Button';
import { Icon } from './Icon';

interface DialogProps {
  open: boolean;
  title: string;
  description?: string;
  onClose: () => void;
  children: ReactNode;
  footer?: ReactNode;
  size?: 'sm' | 'md' | 'lg';
}

const sizes = {
  sm: 'max-w-md',
  md: 'max-w-xl',
  lg: 'max-w-3xl',
};

export function Dialog({
  open,
  title,
  description,
  onClose,
  children,
  footer,
  size = 'md',
}: DialogProps) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const descriptionId = useId();

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) {
      return;
    }
    if (open && !dialog.open) {
      dialog.showModal();
    } else if (!open && dialog.open) {
      dialog.close();
    }
  }, [open]);

  return (
    <dialog
      ref={ref}
      aria-labelledby={titleId}
      aria-describedby={description ? descriptionId : undefined}
      onClose={onClose}
      onClick={(event) => {
        if (event.target === event.currentTarget) {
          onClose();
        }
      }}
      className={cn(
        'w-[calc(100%-2rem)] rounded-card border border-line bg-surface p-0 text-ink shadow-xl backdrop:bg-ink/45',
        sizes[size],
      )}
    >
      <div className="flex items-start justify-between gap-4 border-b border-line px-5 py-4">
        <div>
          <h2 id={titleId} className="text-lg font-semibold text-ink">
            {title}
          </h2>
          {description ? (
            <p id={descriptionId} className="mt-0.5 text-xs text-ink-secondary">
              {description}
            </p>
          ) : null}
        </div>
        <button
          type="button"
          onClick={onClose}
          aria-label="Tutup dialog"
          className="rounded-md p-1.5 text-ink-secondary hover:bg-surface-muted hover:text-ink"
        >
          <Icon name="close" size={18} />
        </button>
      </div>
      <div className="max-h-[65vh] overflow-y-auto px-5 py-4">{children}</div>
      {footer ? (
        <div className="flex flex-wrap justify-end gap-2 border-t border-line bg-background px-5 py-3">
          {footer}
        </div>
      ) : null}
    </dialog>
  );
}

interface ConfirmDialogProps {
  open: boolean;
  title: string;
  message: ReactNode;
  confirmLabel: string;
  onConfirm: () => void;
  onClose: () => void;
  loading?: boolean;
  tone?: 'danger' | 'primary';
}

export function ConfirmDialog({
  open,
  title,
  message,
  confirmLabel,
  onConfirm,
  onClose,
  loading,
  tone = 'danger',
}: ConfirmDialogProps) {
  return (
    <Dialog
      open={open}
      title={title}
      onClose={onClose}
      size="sm"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Batal
          </Button>
          <Button
            variant={tone === 'danger' ? 'danger' : 'primary'}
            onClick={onConfirm}
            loading={loading}
          >
            {confirmLabel}
          </Button>
        </>
      }
    >
      <div className="text-sm text-ink-secondary">{message}</div>
    </Dialog>
  );
}
