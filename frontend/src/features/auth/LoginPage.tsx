import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { z } from 'zod';
import { ApiError } from '../../shared/api/client';
import { Alert } from '../../shared/components/Feedback';
import { InputField } from '../../shared/components/Field';
import { Button } from '../../shared/components/Button';
import { useAuth } from '../auth/AuthContext';

const schema = z.object({
  username: z.string().trim().min(1, 'Username atau email wajib diisi.'),
  password: z.string().min(1, 'Password wajib diisi.'),
});

type LoginForm = z.infer<typeof schema>;

export function LoginPage() {
  const { status, me, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [submitError, setSubmitError] = useState<string | null>(null);
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
    <div className="grid min-h-dvh lg:grid-cols-[minmax(0,5fr)_minmax(0,6fr)]">
      <section className="relative hidden flex-col justify-between overflow-hidden bg-primary p-12 text-white lg:flex">
        <div
          aria-hidden="true"
          className="absolute top-0 right-0 h-40 w-40 rounded-bl-[120px] border-b border-l border-gold/30"
        />
        <div>
          <p className="text-sm font-bold tracking-[0.16em] text-gold">KCIC CONNECT</p>
          <p className="mt-1 text-xs text-white/75">Workspace dokumen internal</p>
        </div>
        <div className="relative max-w-lg">
          <div className="mb-6 h-px w-16 bg-gold" aria-hidden="true" />
          <h1 className="text-[34px] leading-tight font-bold tracking-tight">
            Satu tempat untuk menemukan dan mengelola dokumen perusahaan.
          </h1>
          <p className="mt-4 text-base text-white/75">
            Akses dokumen sesuai peran dan unit kerja Anda, ajukan permintaan dengan alur yang
            jelas, dan pantau setiap distribusi secara terkontrol.
          </p>
          <ul className="mt-8 space-y-3 text-sm text-white/85">
            <li className="flex items-start gap-3">
              <span className="mt-1.5 size-1.5 shrink-0 rounded-full bg-gold" aria-hidden="true" />
              Katalog dokumen dengan owner, klasifikasi, dan versi yang jelas.
            </li>
            <li className="flex items-start gap-3">
              <span className="mt-1.5 size-1.5 shrink-0 rounded-full bg-gold" aria-hidden="true" />
              Hak akses diperiksa di server untuk setiap tindakan.
            </li>
            <li className="flex items-start gap-3">
              <span className="mt-1.5 size-1.5 shrink-0 rounded-full bg-gold" aria-hidden="true" />
              Riwayat permintaan, distribusi, dan akses tercatat.
            </li>
          </ul>
        </div>
        <p className="text-xs text-white/50">Aplikasi internal KCIC — pilot terkontrol.</p>
      </section>

      <section className="flex flex-col justify-center bg-background px-4 py-10 sm:px-8">
        <div className="mx-auto w-full max-w-sm">
          <div className="mb-8 lg:hidden">
            <p className="text-sm font-bold tracking-[0.14em] text-primary">KCIC CONNECT</p>
            <p className="mt-1 text-xs text-ink-secondary">Workspace dokumen internal</p>
          </div>
          <h2 className="text-2xl font-bold tracking-tight text-ink">Masuk ke akun Anda</h2>
          <p className="mt-1 text-sm text-ink-secondary">
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
              type="password"
              required
              autoComplete="current-password"
              error={errors.password?.message}
              {...register('password')}
            />

            <Button type="submit" className="w-full" loading={isSubmitting}>
              {isSubmitting ? 'Memeriksa…' : 'Masuk'}
            </Button>
          </form>

          <p className="mt-6 text-xs text-ink-secondary">
            Tidak dapat masuk? Hubungi administrator untuk reset password atau aktivasi akun.
          </p>
        </div>
      </section>
    </div>
  );
}
