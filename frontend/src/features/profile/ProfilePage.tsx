import { useQuery } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { ApiError } from '../../shared/api/client';
import { Badge, StatusBadge } from '../../shared/components/Badge';
import { Alert, EmptyState, ErrorState, LoadingBlock } from '../../shared/components/Feedback';
import { Card, CardHeader, PageHeader } from '../../shared/components/Surface';
import { formatDate, formatDateTime, roleLabel } from '../../shared/utils/format';
import { useAuth } from '../auth/AuthContext';
import { ChangePasswordForm } from '../auth/ChangePasswordForm';
import { fetchMyEmployee } from '../auth/api';

function InfoRow({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex flex-wrap items-baseline justify-between gap-2 px-5 py-3">
      <dt className="text-xs font-semibold tracking-[0.04em] text-ink-secondary uppercase">
        {label}
      </dt>
      <dd className="min-w-0 text-sm text-ink">{children}</dd>
    </div>
  );
}

export function ProfilePage() {
  const { me } = useAuth();
  const employeeQuery = useQuery({
    queryKey: ['me', 'employee'],
    queryFn: fetchMyEmployee,
    retry: false,
  });

  if (!me) {
    return <LoadingBlock label="Memuat profil…" />;
  }

  const employee = employeeQuery.data?.employee;
  const activePlacement = employeeQuery.data?.placements.find((placement) => placement.active);
  const notLinked =
    employeeQuery.isError &&
    employeeQuery.error instanceof ApiError &&
    employeeQuery.error.status === 404;

  return (
    <>
      <PageHeader
        title="Profil saya"
        description="Data akun, data kepegawaian, dan pengaturan password Anda."
        meta={
          <>
            <span className="font-mono text-xs text-ink-secondary">@{me.username}</span>
            {me.mustChangePassword ? (
              <Badge tone="warning">Password sementara</Badge>
            ) : (
              <Badge tone="success">Password personal</Badge>
            )}
          </>
        }
      />

      <div className="grid items-start gap-4 xl:grid-cols-[minmax(0,7fr)_minmax(0,5fr)]">
        <div className="min-w-0 space-y-4">
          <Card>
            <CardHeader
              title="Data akun"
              description="Identitas akun aplikasi Anda; perubahan dilakukan administrator."
            />
            <dl className="divide-y divide-line">
              <InfoRow label="Nama tampilan">{me.displayName}</InfoRow>
              <InfoRow label="Username">
                <span className="font-mono">{me.username}</span>
              </InfoRow>
              <InfoRow label="Email">{me.email ?? 'Belum diisi'}</InfoRow>
              <InfoRow label="Role">
                <span className="flex flex-wrap justify-end gap-1.5">
                  {me.roles.length > 0 ? (
                    me.roles.map((role) => (
                      <Badge key={role} tone="gold">
                        {roleLabel(role)}
                      </Badge>
                    ))
                  ) : (
                    <Badge tone="neutral">Tanpa role</Badge>
                  )}
                </span>
              </InfoRow>
              <InfoRow label="Unit organisasi">
                {me.organizationUnits.length > 0 ? (
                  <span className="flex flex-col items-end gap-1">
                    {me.organizationUnits.map((unit) => (
                      <span key={unit.id} className="text-right">
                        {unit.unitName}{' '}
                        <span className="font-mono text-xs text-ink-secondary">
                          {unit.unitCode}
                        </span>{' '}
                        <Badge tone={unit.assignmentType === 'PRIMARY' ? 'info' : 'neutral'}>
                          {unit.assignmentType === 'PRIMARY' ? 'Utama' : 'Tambahan'}
                        </Badge>
                      </span>
                    ))}
                  </span>
                ) : (
                  'Belum tergabung'
                )}
              </InfoRow>
              <InfoRow label="Login terakhir">{formatDateTime(me.lastLoginAt)}</InfoRow>
            </dl>
          </Card>

          <Card>
            <CardHeader
              title="Ubah password"
              description="Password baru minimal 12 karakter dan berbeda dari password saat ini."
            />
            <div className="px-5 py-4">
              {me.mustChangePassword ? (
                <div className="mb-4">
                  <Alert tone="warning">
                    Akun Anda masih memakai password sementara. Segera buat password pribadi.
                  </Alert>
                </div>
              ) : null}
              <ChangePasswordForm />
            </div>
          </Card>
        </div>

        <Card>
          <CardHeader
            title="Data kepegawaian"
            description="Berasal dari data HR resmi yang tertaut ke akun Anda."
          />
          {employeeQuery.isPending ? (
            <div className="px-5 py-4">
              <LoadingBlock label="Memuat data kepegawaian…" />
            </div>
          ) : notLinked ? (
            <EmptyState
              title="Belum terhubung ke data employee"
              description="Akun ini belum ditautkan ke data HR. Administrator dapat menautkannya dari halaman detail employee."
            />
          ) : employeeQuery.isError ? (
            <ErrorState
              message={
                employeeQuery.error instanceof ApiError
                  ? employeeQuery.error.message
                  : 'Data kepegawaian tidak dapat dimuat.'
              }
              onRetry={() => void employeeQuery.refetch()}
            />
          ) : employee ? (
            <>
              <dl className="divide-y divide-line">
                <InfoRow label="NIP">
                  <span className="font-mono">{employee.nip}</span>
                </InfoRow>
                <InfoRow label="Nama lengkap">{employee.fullName}</InfoRow>
                <InfoRow label="Email HR">{employee.email}</InfoRow>
                <InfoRow label="Jabatan">{employee.positionName ?? 'Belum diisi'}</InfoRow>
                <InfoRow label="Status kepegawaian">
                  <StatusBadge status={employee.employmentStatus} />
                </InfoRow>
                <InfoRow label="Penempatan aktif">
                  {activePlacement ? (
                    <span className="text-right">
                      {activePlacement.directorateName ?? '—'} ·{' '}
                      {activePlacement.departmentName ?? '—'} ·{' '}
                      {activePlacement.divisionName ?? '—'}
                    </span>
                  ) : (
                    'Belum ditempatkan'
                  )}
                </InfoRow>
              </dl>
              <div className="border-t border-line px-5 py-4">
                <h3 className="text-xs font-semibold tracking-[0.04em] text-ink-secondary uppercase">
                  Riwayat penempatan
                </h3>
                <ul className="mt-2 space-y-2">
                  {(employeeQuery.data?.placements ?? []).map((placement) => (
                    <li
                      key={placement.id}
                      className="rounded-lg border border-line bg-background px-3 py-2 text-xs"
                    >
                      <p className="font-semibold text-ink">
                        {placement.divisionName ?? 'Tanpa nama unit'}{' '}
                        {placement.active ? (
                          <Badge tone="success">Aktif</Badge>
                        ) : (
                          <Badge tone="neutral">Selesai</Badge>
                        )}
                      </p>
                      <p className="mt-0.5 text-ink-secondary">
                        {placement.departmentName ?? '—'} · {placement.directorateName ?? '—'}
                      </p>
                      <p className="mt-0.5 text-ink-secondary">
                        {formatDate(placement.validFrom)} —{' '}
                        {placement.validUntil ? formatDate(placement.validUntil) : 'sekarang'}
                      </p>
                    </li>
                  ))}
                </ul>
              </div>
            </>
          ) : (
            <EmptyState
              title="Data kepegawaian kosong"
              description="Tidak ada data employee yang tertaut ke akun ini."
            />
          )}
        </Card>
      </div>
    </>
  );
}