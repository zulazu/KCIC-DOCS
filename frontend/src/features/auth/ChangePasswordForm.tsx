import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
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

type ChangePasswordValues = z.infer<typeof schema>;

export function ChangePasswordForm({ onSubmitted }: { onSubmitted?: () => void }) {
  const { changePassword } = useAuth();
  const { notify } = useToast();
  const [submitError, setSubmitError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<ChangePasswordValues>({
    resolver: zodResolver(schema),
    defaultValues: { currentPassword: '', newPassword: '', confirmPassword: '' },
  });

  const onSubmit = handleSubmit(async (values) => {
    setSubmitError(null);
    try {
      await changePassword(values);
      notify('Password berhasil diperbarui.');
      reset();
      onSubmitted?.();
    } catch (error) {
      if (error instanceof ApiError) {
        const detail = error.fieldErrors?.map((item) => item.message).join(' ');
        setSubmitError(detail || error.message);
      } else {
        setSubmitError('Password tidak dapat diperbarui. Coba lagi.');
      }
    }
  });

  return (
    <form className="space-y-4" onSubmit={onSubmit} noValidate>
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
  );
}