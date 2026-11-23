import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../features/auth/AuthContext';
import { ForbiddenState, LoadingBlock } from '../../shared/components/Feedback';

export function FullPageStatus({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-dvh items-center justify-center bg-background px-4">
      <div className="w-full max-w-sm text-center">
        <p className="mb-6 text-lg font-bold tracking-tight text-primary">KCIC DOCS</p>
        {children}
      </div>
    </div>
  );
}

export function RequireAuth({
  children,
  allowPasswordChangeRequired = false,
}: {
  children: ReactNode;
  allowPasswordChangeRequired?: boolean;
}) {
  const { status, me, loadError, refresh } = useAuth();
  const location = useLocation();

  if (status === 'loading') {
    return (
      <FullPageStatus>
        <LoadingBlock label="Memeriksa session…" />
      </FullPageStatus>
    );
  }

  if (status === 'error') {
    return (
      <FullPageStatus>
        <p className="text-sm font-semibold text-ink">Session tidak dapat diperiksa</p>
        <p className="mt-1 text-sm text-ink-secondary">{loadError}</p>
        <button
          type="button"
          onClick={() => void refresh()}
          className="mt-4 h-10 rounded-lg bg-primary px-4 text-sm font-semibold text-white"
        >
          Coba lagi
        </button>
      </FullPageStatus>
    );
  }

  if (status === 'anonymous' || !me) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  }

  if (me.mustChangePassword && !allowPasswordChangeRequired) {
    return <Navigate to="/change-password" replace />;
  }

  return <>{children}</>;
}

export function RequireCapability({
  capability,
  children,
}: {
  capability: string;
  children: ReactNode;
}) {
  const { me } = useAuth();
  if (!me?.capabilities.includes(capability)) {
    return (
      <div className="rounded-card border border-line bg-surface">
        <ForbiddenState />
      </div>
    );
  }
  return <>{children}</>;
}
