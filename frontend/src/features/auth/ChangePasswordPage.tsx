import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useNavigate } from 'react-router-dom';
import { z } from 'zod';
import { ApiError } from '../../shared/api/client';
import { Alert } from '../../shared/components/Feedback';
import { Button } from '../../shared/components/Button';
import { InputField } from '../../shared/components/Field';
import { useToast } from '../../shared/components/Toast';
import { useAuth } from './AuthContext';

const schema = z
  .object({
    currentPassword: z.string().min(1, 'Password saat ini wajib diisi.'),
    newPassword: z.string().min(12, 'Password baru minimal 12 karakter.'),
    confirmPassword: z.string().min(1, 'Konfirmasi password wajib diisi.'),
  })
  .refine((values) => values.newPassword === values.confirmPassword, {
    path: ['confirmPassword'],
    message: 'Konfirmasi password tidak sama.',
  })
  .refine((values) => values.newPassword !== values.currentPassword, {
    path: ['newPassword'],
    message: 'Password baru harus berbeda dari password saat ini.',
  });

type ChangePasswordForm = z.infer<typeof schema>;

export function ChangePasswordPage() {
  const { me, changePassword, logout } = useAuth();
  const navigate = useNavigate();
  const { notify } = useToast();
  const [submitError, setSubmitError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<ChangePasswordForm>({
    resolver: zodResolver(schema),
    defaultValues: { currentPassword: '', newPassword: '', confirmPassword: '' },
  });

  const onSubmit = handleSubmit(async (values) => {
    setSubmitError(null);
    try {
      await changePassword(values);
      notify('Password berhasil diperbarui.');
      navigate('/', { replace: true });
    } catch (error) {
      setSubmitError(
        error instanceof ApiError
          ? error.message
          : 'Password tidak dapat diperbarui. Coba lagi.',
      );
    }
  });

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
          <h1 className="text-2xl font-bold tracking-tight text-ink">Ubah password</h1>
          <p className="mt-1 text-sm text-ink-secondary">
            {me?.mustChangePassword
              ? 'Akun Anda memakai password sementara. Buat password pribadi sebelum melanjutkan.'
              : 'Gunakan password yang kuat dan tidak dipakai pada layanan lain.'}
          </p>

          <form className="mt-6 space-y-4" onSubmit={onSubmit} noValidate>
            {submitError ? <Alert tone="danger">{submitError}</Alert> : null}

            <InputField
              label="Password saat ini"
              type="password"
              required
              autoComplete="current-password"
              error={errors.currentPassword?.message}
              {...register('currentPassword')}
            />
            <InputField
              label="Password baru"
              type="password"
              required
              autoComplete="new-password"
              helper="Minimal 12 karakter. Gunakan kombinasi yang tidak mudah ditebak."
              error={errors.newPassword?.message}
              {...register('newPassword')}
            />
            <InputField
              label="Konfirmasi password baru"
              type="password"
              required
              autoComplete="new-password"
              error={errors.confirmPassword?.message}
              {...register('confirmPassword')}
            />

            <Button type="submit" className="w-full" loading={isSubmitting}>
              Simpan password
            </Button>
          </form>
        </div>
      </main>
    </div>
  );
}
