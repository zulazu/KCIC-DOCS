import { useNavigate } from 'react-router-dom';
import { useAuth } from './AuthContext';
import { ChangePasswordForm } from './ChangePasswordForm';

export function ChangePasswordPage() {
  const { me, logout } = useAuth();
  const navigate = useNavigate();

  return (
    <div className="min-h-dvh bg-background">
      <header className="flex h-16 items-center justify-between border-b border-line bg-primary px-4 sm:px-8">
        <div>
          <p className="text-sm font-bold tracking-[0.12em] text-white">KCIC CONNECT</p>
          <p className="text-[11px] text-white/75">Workspace dokumen internal</p>
        </div>
        <button
          type="button"
          onClick={() => void logout().then(() => navigate('/login', { replace: true }))}
          className="rounded-lg border border-white/25 px-3 py-2 text-xs font-semibold text-white hover:bg-white/10"
        >
          Keluar
        </button>
      </header>

      <main className="mx-auto w-full max-w-lg px-4 py-10 sm:px-6">
        <div className="rounded-card border border-line bg-surface p-6 shadow-card sm:p-8">
          <h1 className="font-display text-2xl font-bold tracking-tight text-ink">Ubah password</h1>
          <span aria-hidden="true" className="mt-1.5 block h-[3px] w-12 rounded-full bg-gold" />
          <p className="mt-2 text-sm text-ink-secondary">
            {me?.mustChangePassword
              ? 'Akun Anda memakai password sementara. Buat password pribadi sebelum melanjutkan.'
              : 'Gunakan password yang kuat dan tidak dipakai pada layanan lain.'}
          </p>

          <div className="mt-6">
            <ChangePasswordForm onSubmitted={() => navigate('/', { replace: true })} />
          </div>
        </div>
      </main>
    </div>
  );
}