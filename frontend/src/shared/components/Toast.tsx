import { createContext, useCallback, useContext, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { cn } from '../utils/cn';
import { Icon } from './Icon';

type ToastTone = 'success' | 'danger' | 'info';

interface ToastItem {
  id: number;
  tone: ToastTone;
  message: string;
}

interface ToastContextValue {
  notify: (message: string, tone?: ToastTone) => void;
}

const ToastContext = createContext<ToastContextValue | null>(null);

const toneStyles: Record<ToastTone, string> = {
  success: 'border-success/30 bg-surface text-ink',
  danger: 'border-danger/30 bg-surface text-ink',
  info: 'border-info/30 bg-surface text-ink',
};

const toneIcon: Record<ToastTone, ReactNode> = {
  success: <Icon name="check" className="text-success" />,
  danger: <Icon name="alert" className="text-danger" />,
  info: <Icon name="bell" className="text-info" />,
};

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([]);
  const counter = useRef(0);

  const dismiss = useCallback((id: number) => {
    setItems((current) => current.filter((item) => item.id !== id));
  }, []);

  const notify = useCallback(
    (message: string, tone: ToastTone = 'success') => {
      counter.current += 1;
      const id = counter.current;
      setItems((current) => [...current, { id, tone, message }]);
      window.setTimeout(() => dismiss(id), 4500);
    },
    [dismiss],
  );

  const value = useMemo(() => ({ notify }), [notify]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div
        aria-live="polite"
        aria-atomic="false"
        className="pointer-events-none fixed right-4 bottom-4 z-50 flex w-[calc(100%-2rem)] max-w-sm flex-col gap-2"
      >
        {items.map((item) => (
          <div
            key={item.id}
            role="status"
            className={cn(
              'pointer-events-auto flex items-start gap-2.5 rounded-lg border px-3.5 py-3 text-sm shadow-lg',
              toneStyles[item.tone],
            )}
          >
            <span className="mt-0.5 shrink-0">{toneIcon[item.tone]}</span>
            <p className="flex-1">{item.message}</p>
            <button
              type="button"
              onClick={() => dismiss(item.id)}
              aria-label="Tutup notifikasi"
              className="rounded p-0.5 text-ink-secondary hover:bg-surface-muted"
            >
              <Icon name="close" size={15} />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastContextValue {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error('useToast must be used inside ToastProvider');
  }
  return context;
}
