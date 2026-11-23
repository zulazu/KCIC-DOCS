import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import type { ReactNode } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ApiError } from '../../../shared/api/client';
import { Badge, StatusBadge } from '../../../shared/components/Badge';
import { Button, ButtonLink } from '../../../shared/components/Button';
import { EmptyState, ErrorState, LoadingBlock } from '../../../shared/components/Feedback';
import { Icon } from '../../../shared/components/Icon';
import { Card, CardHeader, PageHeader } from '../../../shared/components/Surface';
import { Table, Td, Th, THead, Tr } from '../../../shared/components/Table';
import { formatDate, formatDateTime } from '../../../shared/utils/format';
import { getUnit, listAllUnits, listUnitMembers } from '../api';
import { UnitEditDialog } from './UnitEditDialog';
import { UNIT_TYPE_LABELS, typeLabel, unitPath } from './unitLabels';

function apiMessage(error: unknown): string {
  return error instanceof ApiError ? error.message : 'Data tidak dapat dimuat. Coba lagi.';
}

const TYPE_PAGE_ROUTES: Record<string, string> = {
  DIRECTORATE: '/admin/organization/directorates',
  DEPARTMENT: '/admin/organization/departments',
  DIVISION: '/admin/organization/divisions',
};

function InfoRow({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="flex flex-col gap-0.5 py-2.5 sm:flex-row sm:items-baseline sm:gap-4">
      <dt className="w-36 shrink-0 text-xs font-semibold tracking-[0.06em] text-ink-secondary uppercase">
        {label}
      </dt>
      <dd className="text-sm text-ink">{value}</dd>
    </div>
  );
}

export function UnitDetailPage() {
  const { unitId = '' } = useParams();
  const [editOpen, setEditOpen] = useState(false);

  const unitQuery = useQuery({
    queryKey: ['organization-units', 'detail', unitId],
    queryFn: () => getUnit(unitId),
    enabled: unitId !== '',
  });
  const allUnitsQuery = useQuery({
    queryKey: ['organization-units', 'all'],
    queryFn: listAllUnits,
  });
  const membersQuery = useQuery({
    queryKey: ['organization-unit-members', unitId],
    queryFn: () => listUnitMembers(unitId),
    enabled: unitId !== '',
  });

  if (unitQuery.isPending) {
    return <LoadingBlock label="Memuat detail unit…" />;
  }
  if (unitQuery.isError) {
    if (unitQuery.error instanceof ApiError && unitQuery.error.status === 404) {
      return (
        <EmptyState
          title="Unit tidak ditemukan"
          description="Unit mungkin telah dihapus atau Anda tidak memiliki akses."
          action={
            <ButtonLink to="/admin/organization" variant="secondary">
              <Icon name="arrow-left" size={16} />
              Kembali ke organisasi
            </ButtonLink>
          }
        />
      );
    }
    return (
      <ErrorState message={apiMessage(unitQuery.error)} onRetry={() => void unitQuery.refetch()} />
    );
  }

  const unit = unitQuery.data;
  const byId = new Map((allUnitsQuery.data ?? []).map((item) => [item.id, item]));
  const chain = unitPath(unit, byId);
  const children = (allUnitsQuery.data ?? [])
    .filter((item) => item.parentId === unit.id)
    .sort((a, b) => a.unitName.localeCompare(b.unitName, 'id'));
  const backTo =
    (unit.unitType ? TYPE_PAGE_ROUTES[unit.unitType] : undefined) ?? '/admin/organization';
  const childType = unit.unitType ? expectedChildType(unit.unitType) : null;

  return (
    <>
      <PageHeader
        title={unit.unitName}
        description={unit.description ?? undefined}
        meta={
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-mono text-xs text-ink-secondary">{unit.unitCode}</span>
            <Badge tone="gold">{typeLabel(unit.unitType)}</Badge>
            <StatusBadge
              status={unit.status}
              label={unit.status === 'ACTIVE' ? 'Aktif' : 'Nonaktif'}
            />
          </div>
        }
        action={
          <div className="flex gap-2">
            <ButtonLink to={backTo} variant="secondary">
              <Icon name="arrow-left" size={16} />
              Kembali
            </ButtonLink>
            <Button onClick={() => setEditOpen(true)}>
              <Icon name="edit" size={16} />
              Ubah unit
            </Button>
          </div>
        }
      />

      <Card className="mb-4 px-5 py-4">
        <h2 className="mb-2 text-xs font-semibold tracking-[0.08em] text-ink-secondary uppercase">
          Hierarki
        </h2>
        <nav aria-label="Hierarki unit">
          <ol className="flex flex-wrap items-center gap-1.5 text-sm">
            {chain.map((node, index) => {
              const isLast = index === chain.length - 1;
              return (
                <li key={node.id} className="flex items-center gap-1.5">
                  {index > 0 ? (
                    <Icon name="chevron-right" size={14} className="text-ink-disabled" />
                  ) : null}
                  {isLast ? (
                    <span aria-current="page" className="font-bold text-primary">
                      {node.unitName}
                    </span>
                  ) : (
                    <Link
                      to={`/admin/organization/units/${node.id}`}
                      className="text-ink hover:text-primary hover:underline"
                    >
                      {node.unitName}
                    </Link>
                  )}
                  <span className="text-xs text-ink-secondary">({typeLabel(node.unitType)})</span>
                </li>
              );
            })}
          </ol>
        </nav>
      </Card>

      <div className="grid gap-4 xl:grid-cols-[minmax(0,5fr)_minmax(0,7fr)]">
        <Card>
          <CardHeader title="Informasi unit" />
          <dl className="divide-y divide-line px-5 py-2">
            <InfoRow label="Kode" value={<span className="font-mono">{unit.unitCode}</span>} />
            <InfoRow label="Tipe" value={typeLabel(unit.unitType)} />
            <InfoRow
              label="Unit induk"
              value={
                unit.parentId ? (
                  byId.get(unit.parentId) ? (
                    <Link
                      to={`/admin/organization/units/${unit.parentId}`}
                      className="text-primary hover:underline"
                    >
                      {byId.get(unit.parentId)?.unitName}
                    </Link>
                  ) : (
                    'Unit tidak ditemukan'
                  )
                ) : (
                  'Tanpa induk (tingkat atas)'
                )
              }
            />
            <InfoRow
              label="Status"
              value={
                <StatusBadge
                  status={unit.status}
                  label={unit.status === 'ACTIVE' ? 'Aktif' : 'Nonaktif'}
                />
              }
            />
            <InfoRow label="Dibuat" value={formatDateTime(unit.createdAt)} />
            <InfoRow label="Diperbarui" value={formatDateTime(unit.updatedAt)} />
            <InfoRow label="Deskripsi" value={unit.description ?? '—'} />
          </dl>
        </Card>

        <div className="space-y-4">
          <Card>
            <CardHeader
              title="Unit di bawahnya"
              description={
                childType
                  ? `${UNIT_TYPE_LABELS[childType]} yang berada di bawah unit ini.`
                  : undefined
              }
            />
            {allUnitsQuery.isPending ? (
              <div className="px-5 py-4">
                <LoadingBlock label="Memuat unit turunan…" />
              </div>
            ) : allUnitsQuery.isError ? (
              <ErrorState
                message={apiMessage(allUnitsQuery.error)}
                onRetry={() => void allUnitsQuery.refetch()}
              />
            ) : children.length === 0 ? (
              <EmptyState
                title="Belum ada unit turunan"
                description="Unit ini belum memiliki unit di bawahnya."
              />
            ) : (
              <Table caption={`Unit di bawah ${unit.unitName}`}>
                <THead>
                  <Th>Unit</Th>
                  <Th className="hidden sm:table-cell">Tipe</Th>
                  <Th>Status</Th>
                  <Th align="right">Aksi</Th>
                </THead>
                <tbody>
                  {children.map((child) => (
                    <Tr key={child.id}>
                      <Td>
                        <span className="block font-semibold text-ink">{child.unitName}</span>
                        <span className="font-mono text-[11px] text-ink-secondary">
                          {child.unitCode}
                        </span>
                      </Td>
                      <Td className="hidden sm:table-cell">{typeLabel(child.unitType)}</Td>
                      <Td>
                        <StatusBadge
                          status={child.status}
                          label={child.status === 'ACTIVE' ? 'Aktif' : 'Nonaktif'}
                        />
                      </Td>
                      <Td>
                        <div className="flex justify-end">
                          <ButtonLink
                            to={`/admin/organization/units/${child.id}`}
                            variant="secondary"
                            size="compact"
                          >
                            Detail
                            <Icon name="chevron-right" size={14} />
                          </ButtonLink>
                        </div>
                      </Td>
                    </Tr>
                  ))}
                </tbody>
              </Table>
            )}
          </Card>

          <Card>
            <CardHeader
              title="Anggota unit"
              description="Akun yang ditugaskan ke unit ini beserta masa berlaku penugasan."
            />
            {membersQuery.isPending ? (
              <div className="px-5 py-4">
                <LoadingBlock label="Memuat anggota unit…" />
              </div>
            ) : membersQuery.isError ? (
              <ErrorState
                message={apiMessage(membersQuery.error)}
                onRetry={() => void membersQuery.refetch()}
              />
            ) : (membersQuery.data ?? []).length === 0 ? (
              <EmptyState
                title="Belum ada anggota"
                description="Penugasan akun ke unit dilakukan melalui halaman Pengguna."
              />
            ) : (
              <Table caption={`Anggota unit ${unit.unitName}`}>
                <THead>
                  <Th>Akun</Th>
                  <Th className="hidden sm:table-cell">Penugasan</Th>
                  <Th>Berlaku sampai</Th>
                  <Th>Status</Th>
                </THead>
                <tbody>
                  {(membersQuery.data ?? []).map((member) => (
                    <Tr key={member.assignmentId}>
                      <Td>
                        <span className="block font-semibold text-ink">{member.displayName}</span>
                        <span className="text-xs text-ink-secondary">{member.username}</span>
                      </Td>
                      <Td className="hidden sm:table-cell">
                        {member.assignmentType === 'PRIMARY' ? 'Utama' : 'Tambahan'}
                      </Td>
                      <Td className="text-xs tabular-nums">
                        {member.validUntil ? formatDate(member.validUntil) : 'Tanpa batas'}
                      </Td>
                      <Td>
                        <StatusBadge
                          status={member.active ? 'ACTIVE' : 'INACTIVE'}
                          label={member.active ? 'Aktif' : 'Berakhir'}
                        />
                      </Td>
                    </Tr>
                  ))}
                </tbody>
              </Table>
            )}
          </Card>
        </div>
      </div>

      <UnitEditDialog unit={editOpen ? unit : null} onClose={() => setEditOpen(false)} />
    </>
  );
}

function expectedChildType(type: string): 'DIRECTORATE' | 'DEPARTMENT' | 'DIVISION' | null {
  switch (type) {
    case 'COMPANY':
      return 'DIRECTORATE';
    case 'DIRECTORATE':
      return 'DEPARTMENT';
    case 'DEPARTMENT':
      return 'DIVISION';
    default:
      return null;
  }
}