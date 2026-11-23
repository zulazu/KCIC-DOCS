import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { ApiError } from '../../../shared/api/client';
import { useCapability } from '../../auth/AuthContext';
import { Badge } from '../../../shared/components/Badge';
import { Alert, EmptyState, ErrorState, LoadingBlock } from '../../../shared/components/Feedback';
import { Card, CardHeader, PageHeader } from '../../../shared/components/Surface';
import { listEmployees, listUnlinkedAccounts } from '../api';

function StatTile({ label, value, tone }: { label: string; value: string; tone?: 'warning' }) {
  return (
    <div className="tile-hover rounded-card border border-line bg-surface p-4 shadow-card">
      <p className="text-xs font-semibold tracking-[0.06em] text-ink-secondary uppercase">{label}</p>
      <p
        className={
          tone === 'warning'
            ? 'font-display mt-1 text-[28px] leading-9 font-bold text-warning tabular-nums'
            : 'font-display mt-1 text-[28px] leading-9 font-bold text-ink tabular-nums'
        }
      >
        {value}
      </p>
    </div>
  );
}

export function MigrationPage() {
  const canViewEmployees = useCapability('employee.view');
  const canManageUsers = useCapability('user.manage');

  const unlinkedEmployeesQuery = useQuery({
    queryKey: ['migration', 'employees-without-account'],
    queryFn: () => listEmployees({ linked: false, page: 0, size: 10 }),
    enabled: canViewEmployees,
  });
  const linkedEmployeesQuery = useQuery({
    queryKey: ['migration', 'employees-with-account'],
    queryFn: () => listEmployees({ linked: true, page: 0, size: 1 }),
    enabled: canViewEmployees,
  });
  const unlinkedAccountsQuery = useQuery({
    queryKey: ['migration', 'accounts-without-employee'],
    queryFn: () => listUnlinkedAccounts({ page: 0, size: 10 }),
    enabled: canManageUsers,
  });

  return (
    <>
      <PageHeader
        title="Migrasi akun"
        description="Kesiapan tautan satu-ke-satu employee dan akun sebelum cutover."
      />

      <div className="space-y-5">
        <Alert tone="info">
          Data employee hanya berasal dari sumber HR resmi; halaman ini hanya membaca, tidak membuat
          atau menebak data. Akun bootstrap administrator tetap tanpa employee sampai ada data HR sah.
        </Alert>

        <div className="grid gap-3 sm:grid-cols-3">
          <StatTile
            label="Employee tanpa akun"
            value={unlinkedEmployeesQuery.data?.totalItems.toString() ?? '—'}
            tone="warning"
          />
          <StatTile
            label="Employee dengan akun"
            value={linkedEmployeesQuery.data?.totalItems.toString() ?? '—'}
          />
          <StatTile
            label="Akun belum terhubung"
            value={canManageUsers ? (unlinkedAccountsQuery.data?.totalItems.toString() ?? '—') : '—'}
          />
        </div>

        <Card>
          <CardHeader
            title="Employee belum memiliki akun"
            description="Setiap employee pada daftar ini belum dapat memakai aplikasi sampai akun dibuat atau dihubungkan dari halaman detail employee."
            action={
              <Link className="text-xs font-semibold text-primary hover:underline" to="/admin/employees">
                Buka daftar employee
              </Link>
            }
          />
          {!canViewEmployees ? (
            <EmptyState
              title="Tidak dapat menampilkan data employee"
              description="Akun Anda tidak memiliki capability employee.view."
            />
          ) : unlinkedEmployeesQuery.isPending ? (
            <div className="px-5 py-4">
              <LoadingBlock label="Memuat employee tanpa akun…" />
            </div>
          ) : unlinkedEmployeesQuery.isError ? (
            <ErrorState
              message={
                unlinkedEmployeesQuery.error instanceof ApiError
                  ? unlinkedEmployeesQuery.error.message
                  : 'Daftar employee tidak dapat dimuat.'
              }
              onRetry={() => void unlinkedEmployeesQuery.refetch()}
            />
          ) : !unlinkedEmployeesQuery.data || unlinkedEmployeesQuery.data.items.length === 0 ? (
            <EmptyState
              title="Semua employee sudah memiliki akun"
              description="Tidak ada employee aktif atau nonaktif yang menunggu pembuatan akun."
            />
          ) : (
            <ul className="divide-y divide-line">
              {unlinkedEmployeesQuery.data.items.map((employee) => (
                <li
                  key={employee.id}
                  className="flex flex-wrap items-center justify-between gap-2 px-5 py-3"
                >
                  <div className="min-w-0">
                    <Link
                      to={`/admin/employees/${employee.id}`}
                      className="text-sm font-semibold text-ink hover:text-primary"
                    >
                      {employee.fullName}
                    </Link>
                    <p className="text-xs text-ink-secondary">
                      <span className="font-mono">{employee.nip}</span> · {employee.email} ·{' '}
                      {employee.divisionName ?? 'Belum ditempatkan'}
                    </p>
                  </div>
                  <Badge tone="warning">Tanpa akun</Badge>
                </li>
              ))}
            </ul>
          )}
        </Card>

        <Card>
          <CardHeader
            title="Akun belum terhubung ke employee"
            description="Akun lama dapat dihubungkan dari detail employee. Satu akun hanya boleh terhubung ke satu employee."
            action={
              canManageUsers ? (
                <Link className="text-xs font-semibold text-primary hover:underline" to="/admin/users">
                  Buka daftar user
                </Link>
              ) : undefined
            }
          />
          {!canManageUsers ? (
            <EmptyState
              title="Tidak dapat menampilkan akun"
              description="Daftar akun belum terhubung memerlukan capability user.manage."
            />
          ) : unlinkedAccountsQuery.isPending ? (
            <div className="px-5 py-4">
              <LoadingBlock label="Memuat akun belum terhubung…" />
            </div>
          ) : unlinkedAccountsQuery.isError ? (
            <ErrorState
              message={
                unlinkedAccountsQuery.error instanceof ApiError
                  ? unlinkedAccountsQuery.error.message
                  : 'Daftar akun tidak dapat dimuat.'
              }
              onRetry={() => void unlinkedAccountsQuery.refetch()}
            />
          ) : !unlinkedAccountsQuery.data || unlinkedAccountsQuery.data.items.length === 0 ? (
            <EmptyState
              title="Semua akun sudah terhubung"
              description="Aktifkan proses migrasi bila akun lama masih perlu dipetakan ke employee."
            />
          ) : (
            <ul className="divide-y divide-line">
              {unlinkedAccountsQuery.data.items.map((account) => (
                <li
                  key={account.id}
                  className="flex flex-wrap items-center justify-between gap-2 px-5 py-3"
                >
                  <div className="min-w-0">
                    <Link
                      to={`/admin/users/${account.id}`}
                      className="text-sm font-semibold text-ink hover:text-primary"
                    >
                      {account.displayName}
                    </Link>
                    <p className="text-xs text-ink-secondary">@{account.username}</p>
                  </div>
                  <Badge tone="neutral">Tanpa employee</Badge>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>
    </>
  );
}
