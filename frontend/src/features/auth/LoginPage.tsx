import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { z } from 'zod';
import { ApiError } from '../../shared/api/client';
import { Alert } from '../../shared/components/Feedback';
import { InputField } from '../../shared/components/Field';
import { Button } from '../../shared/components/Button';
import { Icon } from '../../shared/components/Icon';
import type { IconName } from '../../shared/components/Icon';
import { useAuth } from '../auth/AuthContext';

const schema = z.object({
  username: z.string().trim().min(1, 'Username atau email wajib diisi.'),
  password: z.string().min(1, 'Password wajib diisi.'),
});

type LoginForm = z.infer<typeof schema>;

const HIGHLIGHTS: { icon: IconName; title: string; description: string; wide?: boolean }[] = [
  {
    icon: 'document',
    title: 'Katalog dokumen terkendali',
    description: 'Owner, klasifikasi, dan riwayat versi yang jelas untuk setiap dokumen.',
    wide: true,
  },
  {
    icon: 'shield',
    title: 'Akses diperiksa server',
    description: 'Setiap tindakan tunduk pada peran, scope, dan ACL.',
  },
  {
    icon: 'link',
    title: 'Distribusi terlacak',
    description: 'Permintaan, persetujuan, dan distribusi tercatat.',
  },
];

export function LoginPage() {
  const { status, me, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [showPassword, setShowPassword] = useState(false);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginForm>({
    resolver: zodResolver(schema),
    defaultValues: { username: '', password: '' },
  });

  if (status === 'authenticated' && me) {
    return <Navigate to={me.mustChangePassword ? '/change-password' : '/'} replace />;
  }

  const state = location.state as { from?: string } | null;
  const redirectTo =
    state?.from && state.from.startsWith('/') && !state.from.startsWith('//') ? state.from : '/';

  const onSubmit = handleSubmit(async (values) => {
    setSubmitError(null);
    try {
      const authenticated = await login(values);
      navigate(authenticated.mustChangePassword ? '/change-password' : redirectTo, {
        replace: true,
      });
    } catch (error) {
      setSubmitError(
        error instanceof ApiError ? error.message : 'Login tidak dapat diproses. Coba lagi.',
      );
    }
  });

  return (
    <div className="grid min-h-dvh lg:grid-cols-[minmax(0,11fr)_minmax(0,9fr)]">
      <section className="relative hidden flex-col justify-between overflow-hidden bg-primary p-12 text-white lg:flex">
        <div
          aria-hidden="true"
          className="pointer-events-none absolute inset-0 opacity-60"
          style={{
            backgroundImage: 'radial-gradient(rgb(255 255 255 / 0.07) 1px, transparent 1px)',
            backgroundSize: '26px 26px',
          }}
        />
        <div
          aria-hidden="true"
          className="absolute top-0 right-0 h-44 w-44 rounded-bl-[140px] border-b border-l border-gold/30"
        />
        <div
          aria-hidden="true"
          className="absolute bottom-0 left-0 h-32 w-32 rounded-tr-[100px] border-t border-r border-gold/20"
        />

        <div className="relative flex items-center gap-3">
          <span className="flex size-10 items-center justify-center rounded-lg bg-primary-pressed font-display text-lg font-bold text-gold">
            K
          </span>
          <div>
            <p className="text-sm font-bold tracking-[0.16em] text-gold">KCIC DOCS</p>
            <p className="mt-0.5 text-xs text-white/75">Workspace dokumen internal</p>
          </div>
        </div>

        <div className="relative max-w-xl">
          <div className="mb-6 h-px w-16 bg-gold" aria-hidden="true" />
          <h1 className="font-display text-[42px] leading-[1.15] font-bold tracking-tight">
            Satu tempat untuk menemukan dan mengelola{' '}
            <span className="text-gold">dokumen perusahaan.</span>
          </h1>
          <p className="mt-4 max-w-lg text-base text-white/75">
            Akses dokumen sesuai peran dan unit kerja Anda, ajukan permintaan dengan alur yang
            jelas, dan pantau setiap distribusi secara terkontrol.
          </p>

          <div className="mt-9 grid grid-cols-2 gap-3">
            {HIGHLIGHTS.map((item) => (
              <div
                key={item.title}
                className={
                  item.wide
                    ? 'col-span-2 rounded-xl border border-white/15 bg-white/5 p-4'
                    : 'rounded-xl border border-white/15 bg-white/5 p-4'
                }
              >
                <div className="flex items-center gap-2.5">
                  <span className="flex size-7 shrink-0 items-center justify-center rounded-lg bg-white/10 text-gold">
                    <Icon name={item.icon} size={15} />
                  </span>
                  <p className="text-sm font-bold">{item.title}</p>
                </div>
                <p className="mt-1.5 text-xs leading-5 text-white/75">{item.description}</p>
              </div>
            ))}
          </div>
        </div>

        <p className="relative text-xs text-white/75">
          Aplikasi internal KCIC — pilot terkontrol.
        </p>
      </section>

      <section className="flex flex-col justify-center bg-background px-4 py-10 sm:px-8">
        <div className="mx-auto w-full max-w-sm">
          <div className="mb-6 overflow-hidden rounded-card bg-primary p-5 text-white shadow-card lg:hidden">
            <div className="flex items-center gap-3">
              <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-primary-pressed font-display text-base font-bold text-gold">
                K
              </span>
              <div>
                <p className="text-sm font-bold tracking-[0.14em] text-gold">KCIC DOCS</p>
                <p className="mt-0.5 text-xs text-white/75">Workspace dokumen internal</p>
              </div>
            </div>
            <p className="font-display mt-3 text-lg leading-6 font-bold">
              Satu tempat untuk menemukan dan mengelola{' '}
              <span className="text-gold">dokumen perusahaan.</span>
            </p>
          </div>

          <div className="rounded-card border border-line bg-surface p-6 shadow-card sm:p-8">
            <h2 className="font-display text-[28px] leading-9 font-bold tracking-tight text-ink">
              Masuk ke akun Anda
            </h2>
            <span aria-hidden="true" className="mt-1.5 block h-[3px] w-12 rounded-full bg-gold" />
            <p className="mt-2 text-sm text-ink-secondary">
              Gunakan akun internal yang diberikan administrator.
            </p>

            <form className="mt-6 space-y-4" onSubmit={onSubmit} noValidate>
              {submitError ? <Alert tone="danger">{submitError}</Alert> : null}

              <InputField
                label="Username atau email"
                required
                autoComplete="username"
                autoFocus
                error={errors.username?.message}
                {...register('username')}
              />
              <InputField
                label="Password"
                type={showPassword ? 'text' : 'password'}
                required
                autoComplete="current-password"
                error={errors.password?.message}
                trailing={
                  <button
                    type="button"
                    onClick={() => setShowPassword((value) => !value)}
                    aria-label={showPassword ? 'Sembunyikan password' : 'Tampilkan password'}
                    aria-pressed={showPassword}
                    className="flex size-8 items-center justify-center rounded-md text-ink-secondary hover:bg-surface-muted hover:text-ink"
                  >
                    <Icon name={showPassword ? 'eye-off' : 'eye'} size={16} />
                  </button>
                }
                {...register('password')}
              />

              <Button type="submit" className="w-full" loading={isSubmitting}>
                {isSubmitting ? 'Memeriksa…' : 'Masuk'}
              </Button>
            </form>
          </div>

          <p className="mt-6 text-xs text-ink-secondary">
            Tidak dapat masuk? Hubungi administrator untuk reset password atau aktivasi akun.
          </p>
        </div>
      </section>
    </div>
  );
}