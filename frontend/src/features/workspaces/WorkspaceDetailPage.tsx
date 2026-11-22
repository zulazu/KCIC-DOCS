import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useParams } from 'react-router-dom';
import { z } from 'zod';
import { ApiError } from '../../shared/api/client';
import type { RepositoryFolder, WorkspaceStatus } from '../../shared/api/types';
import { Badge, StatusBadge } from '../../shared/components/Badge';
import { Button } from '../../shared/components/Button';
import { Dialog } from '../../shared/components/Dialog';
import {
  Alert,
  EmptyState,
  ErrorState,
  ForbiddenState,
  LoadingBlock,
} from '../../shared/components/Feedback';
import { InputField, SelectField, TextareaField } from '../../shared/components/Field';
import { Icon } from '../../shared/components/Icon';
import { Card, CardHeader, DetailRow, PageHeader } from '../../shared/components/Surface';
import { useToast } from '../../shared/components/Toast';
import { apiMessage } from '../../shared/utils/apiMessage';
import { formatDateTime } from '../../shared/utils/format';
import { AccessPanel } from '../access/AccessPanel';
import {
  archiveFolder,
  createFolder,
  getWorkspace,
  listActiveUnits,
  listFolders,
  updateFolder,
  updateWorkspace,
} from './api';

const workspaceSchema = z.object({
  workspaceName: z.string().trim().min(1, 'Nama workspace wajib diisi.').max(160),
  description: z.string().max(4000),
  ownerOrganizationUnitId: z.string().min(1, 'Unit pemilik wajib dipilih.'),
  status: z.enum(['ACTIVE', 'INACTIVE', 'ARCHIVED']),
});
type WorkspaceForm = z.infer<typeof workspaceSchema>;

const folderSchema = z.object({
  folderName: z.string().trim().min(1, 'Nama folder wajib diisi.').max(160),
  description: z.string().max(4000),
  parentFolderId: z.string(),
});
type FolderForm = z.infer<typeof folderSchema>;

interface FolderDialogState {
  mode: 'create' | 'edit';
  folder: RepositoryFolder | null;
  parentId: string;
}

const statusLabels: Record<WorkspaceStatus, string> = {
  ACTIVE: 'Aktif',
  INACTIVE: 'Nonaktif',
  ARCHIVED: 'Diarsipkan',
};

function flattenFolders(folders: RepositoryFolder[]): { folder: RepositoryFolder; depth: number }[] {
  const children = new Map<string, RepositoryFolder[]>();
  for (const folder of folders) {
    const key = folder.parentFolderId ?? 'root';
    const bucket = children.get(key) ?? [];
    bucket.push(folder);
    children.set(key, bucket);
  }
  for (const bucket of children.values()) {
    bucket.sort((a, b) => a.folderName.localeCompare(b.folderName, 'id'));
  }
  const ordered: { folder: RepositoryFolder; depth: number }[] = [];
  const walk = (parentKey: string, depth: number) => {
    for (const folder of children.get(parentKey) ?? []) {
      ordered.push({ folder, depth });
      walk(folder.id, depth + 1);
    }
  };
  walk('root', 0);
  return ordered;
}

function descendantIds(folders: RepositoryFolder[], rootId: string): Set<string> {
  const ids = new Set<string>([rootId]);
  let added = true;
  while (added) {
    added = false;
    for (const folder of folders) {
      if (folder.parentFolderId && ids.has(folder.parentFolderId) && !ids.has(folder.id)) {
        ids.add(folder.id);
        added = true;
      }
    }
  }
  return ids;
}

export function WorkspaceDetailPage() {
  const { workspaceId = '' } = useParams();
  const queryClient = useQueryClient();
  const { notify } = useToast();
  const [includeArchived, setIncludeArchived] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [folderDialog, setFolderDialog] = useState<FolderDialogState | null>(null);
  const [archiveTarget, setArchiveTarget] = useState<RepositoryFolder | null>(null);
  const [accessFolder, setAccessFolder] = useState<RepositoryFolder | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const workspaceQuery = useQuery({
    queryKey: ['workspace', workspaceId],
    queryFn: () => getWorkspace(workspaceId),
    enabled: workspaceId !== '',
  });
  const foldersQuery = useQuery({
    queryKey: ['folders', workspaceId, includeArchived],
    queryFn: () => listFolders(workspaceId, includeArchived),
    enabled: workspaceId !== '',
  });
  const unitsQuery = useQuery({ queryKey: ['organization-units', 'active'], queryFn: listActiveUnits });

  const workspace = workspaceQuery.data;

  const editForm = useForm<WorkspaceForm>({
    resolver: zodResolver(workspaceSchema),
    values: workspace
      ? {
          workspaceName: workspace.workspaceName,
          description: workspace.description ?? '',
          ownerOrganizationUnitId: workspace.ownerOrganizationUnitId,
          status: workspace.status,
        }
      : { workspaceName: '', description: '', ownerOrganizationUnitId: '', status: 'ACTIVE' },
  });

  const folderForm = useForm<FolderForm>({
    resolver: zodResolver(folderSchema),
    values: folderDialog
      ? {
          folderName: folderDialog.folder?.folderName ?? '',
          description: folderDialog.folder?.description ?? '',
          parentFolderId: folderDialog.parentId,
        }
      : { folderName: '', description: '', parentFolderId: '' },
  });

  const updateWorkspaceMutation = useMutation({
    mutationFn: (values: WorkspaceForm) =>
      updateWorkspace(workspaceId, {
        workspaceName: values.workspaceName,
        description: values.description || undefined,
        ownerOrganizationUnitId: values.ownerOrganizationUnitId,
        status: values.status,
      }),
    onSuccess: (updated) => {
      notify(`Workspace ${updated.workspaceName} diperbarui.`);
      setEditOpen(false);
      setActionError(null);
      void queryClient.invalidateQueries({ queryKey: ['workspace', workspaceId] });
      void queryClient.invalidateQueries({ queryKey: ['workspaces'] });
    },
    onError: (error) => setActionError(apiMessage(error)),
  });

  const saveFolderMutation = useMutation({
    mutationFn: (values: FolderForm) => {
      const payload = {
        folderName: values.folderName,
        description: values.description || undefined,
        parentFolderId: values.parentFolderId || null,
      };
      return folderDialog?.mode === 'edit' && folderDialog.folder
        ? updateFolder(workspaceId, folderDialog.folder.id, payload)
        : createFolder(workspaceId, payload);
    },
    onSuccess: (folder) => {
      notify(`Folder ${folder.folderName} berhasil disimpan.`);
      setFolderDialog(null);
      setActionError(null);
      void queryClient.invalidateQueries({ queryKey: ['folders', workspaceId] });
      void queryClient.invalidateQueries({ queryKey: ['workspace', workspaceId] });
      void queryClient.invalidateQueries({ queryKey: ['workspaces'] });
    },
    onError: (error) => {
      setActionError(apiMessage(error));
      if (error instanceof ApiError) {
        for (const fieldError of error.fieldErrors) {
          if (fieldError.field === 'folderName' || fieldError.field === 'parentFolderId') {
            folderForm.setError(fieldError.field, { message: fieldError.message });
          }
        }
      }
    },
  });

  const archiveMutation = useMutation({
    mutationFn: (folder: RepositoryFolder) => archiveFolder(workspaceId, folder.id),
    onSuccess: (folder) => {
      notify(`Folder ${folder.folderName} diarsipkan.`);
      setArchiveTarget(null);
      setActionError(null);
      void queryClient.invalidateQueries({ queryKey: ['folders', workspaceId] });
      void queryClient.invalidateQueries({ queryKey: ['workspace', workspaceId] });
      void queryClient.invalidateQueries({ queryKey: ['workspaces'] });
    },
    onError: (error) => setActionError(apiMessage(error)),
  });

  // Server errors from a previous dialog must not leak into the next one.
  const openFolderDialog = (state: FolderDialogState) => {
    setActionError(null);
    folderForm.clearErrors();
    setFolderDialog(state);
  };
  const openEditWorkspace = () => {
    setActionError(null);
    setEditOpen(true);
  };
  const openArchiveDialog = (folder: RepositoryFolder) => {
    setActionError(null);
    setArchiveTarget(folder);
  };
  const closeFolderDialog = () => {
    setActionError(null);
    setFolderDialog(null);
  };
  const closeArchiveDialog = () => {
    setActionError(null);
    setArchiveTarget(null);
  };

  const orderedFolders = useMemo(
    () => flattenFolders(foldersQuery.data ?? []),
    [foldersQuery.data],
  );

  if (workspaceQuery.isPending) {
    return <LoadingBlock label="Memuat workspace…" />;
  }
  if (workspaceQuery.isError || !workspace) {
    if (workspaceQuery.error instanceof ApiError && workspaceQuery.error.status === 403) {
      return <ForbiddenState message="Anda tidak memiliki akses ke workspace ini." />;
    }
    return (
      <ErrorState
        message={apiMessage(workspaceQuery.error)}
        onRetry={() => void workspaceQuery.refetch()}
      />
    );
  }

  const foldersMutable = workspace.status === 'ACTIVE';
  const excludedParentIds = folderDialog?.folder
    ? descendantIds(foldersQuery.data ?? [], folderDialog.folder.id)
    : new Set<string>();

  return (
    <>
      <Link
        to="/workspaces"
        className="mb-4 inline-flex items-center gap-1.5 text-xs font-semibold text-ink-secondary hover:text-primary"
      >
        <Icon name="arrow-left" size={14} />
        Semua workspace
      </Link>

      <PageHeader
        title={workspace.workspaceName}
        description={workspace.description ?? 'Workspace dokumen internal.'}
        meta={
          <>
            <Badge tone="neutral">
              <span className="font-mono">{workspace.workspaceCode}</span>
            </Badge>
            <StatusBadge status={workspace.status} label={statusLabels[workspace.status]} />
            <span className="text-xs text-ink-secondary">
              {workspace.activeFolderCount} folder aktif
            </span>
          </>
        }
        action={
          workspace.canManage ? (
            <>
              <Button variant="secondary" onClick={openEditWorkspace}>
                Ubah workspace
              </Button>
              {foldersMutable ? (
                <Button
                  onClick={() =>
                    openFolderDialog({ mode: 'create', folder: null, parentId: '' })
                  }
                >
                  <Icon name="plus" size={16} />
                  Folder baru
                </Button>
              ) : null}
            </>
          ) : undefined
        }
      />

      {actionError && !editOpen && !folderDialog && !archiveTarget ? (
        <div className="mb-4">
          <Alert tone="danger">{actionError}</Alert>
        </div>
      ) : null}

      {!foldersMutable ? (
        <div className="mb-4">
          <Alert tone="warning" title="Workspace tidak aktif">
            Workspace berstatus {statusLabels[workspace.status].toLowerCase()}. Struktur folder tidak
            dapat diubah sampai workspace diaktifkan kembali melalui menu Ubah workspace.
          </Alert>
        </div>
      ) : null}

      <div className="grid gap-5 xl:grid-cols-[1fr_320px]">
        <Card>
          <CardHeader
            title="Struktur folder"
            description="Hierarki folder di dalam workspace. Folder arsip tidak muncul pada tampilan default."
            action={
              <label className="flex items-center gap-2 text-xs font-medium text-ink-secondary">
                <input
                  type="checkbox"
                  checked={includeArchived}
                  onChange={(event) => setIncludeArchived(event.target.checked)}
                  className="size-4 rounded border-line-strong accent-[#8B1A1A]"
                />
                Tampilkan arsip
              </label>
            }
          />
          {foldersQuery.isPending ? (
            <div className="px-5 py-4">
              <LoadingBlock label="Memuat folder…" />
            </div>
          ) : foldersQuery.isError ? (
            <ErrorState
              message={apiMessage(foldersQuery.error)}
              onRetry={() => void foldersQuery.refetch()}
            />
          ) : orderedFolders.length === 0 ? (
            <EmptyState
              title="Belum ada folder"
              description="Folder membantu menyusun dokumen di dalam workspace."
              action={
                workspace.canManage && foldersMutable ? (
                  <Button
                    onClick={() =>
                      openFolderDialog({ mode: 'create', folder: null, parentId: '' })
                    }
                  >
                    Buat folder
                  </Button>
                ) : undefined
              }
            />
          ) : (
            <ul className="divide-y divide-line">
              {orderedFolders.map(({ folder, depth }) => (
                <li
                  key={folder.id}
                  className="flex flex-wrap items-center justify-between gap-3 px-4 py-3 hover:bg-background/70 md:px-5"
                >
                  <div
                    className="flex min-w-0 items-start gap-2.5"
                    style={{ paddingLeft: `${Math.min(depth, 8) * 20}px` }}
                  >
                    <span className="mt-0.5 flex size-7 shrink-0 items-center justify-center rounded-md bg-primary-subtle text-primary">
                      <Icon name="folder" size={14} />
                    </span>
                    <div className="min-w-0">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="font-semibold text-ink">{folder.folderName}</span>
                        {folder.status === 'ARCHIVED' ? (
                          <Badge tone="neutral">Diarsipkan</Badge>
                        ) : null}
                      </div>
                      <p className="line-clamp-2 text-xs text-ink-secondary">
                        {folder.description ?? 'Tanpa deskripsi'} · Diperbarui{' '}
                        {formatDateTime(folder.updatedAt)}
                      </p>
                    </div>
                  </div>
                  {folder.canManage ? (
                    <div className="flex shrink-0 flex-wrap items-center justify-end gap-1">
                      <Button
                        size="compact"
                        variant="secondary"
                        onClick={() => {
                          setActionError(null);
                          setAccessFolder(folder);
                        }}
                      >
                        <Icon name="lock" size={14} />
                        Akses
                      </Button>
                      {foldersMutable && folder.status === 'ACTIVE' ? (
                        <>
                          <Button
                            size="compact"
                            variant="secondary"
                            onClick={() =>
                              openFolderDialog({ mode: 'create', folder: null, parentId: folder.id })
                            }
                          >
                            Sub-folder
                          </Button>
                          <Button
                            size="compact"
                            variant="secondary"
                            onClick={() => openFolderDialog({ mode: 'edit', folder, parentId: folder.parentFolderId ?? '' })}
                          >
                            Ubah
                          </Button>
                          <Button
                            size="compact"
                            variant="ghost"
                            className="text-danger hover:bg-danger-bg"
                            onClick={() => openArchiveDialog(folder)}
                          >
                            Arsipkan
                          </Button>
                        </>
                      ) : null}
                    </div>
                  ) : null}
                </li>
              ))}
            </ul>
          )}
        </Card>

        <Card>
          <CardHeader title="Ringkasan" description="Metadata workspace dan batas aksesnya." />
          <div className="px-5 py-3">
            <dl>
              <DetailRow label="Kode">
                <span className="font-mono text-xs">{workspace.workspaceCode}</span>
              </DetailRow>
              <DetailRow label="Unit pemilik">
                {workspace.ownerOrganizationUnitName ?? '—'}
              </DetailRow>
              <DetailRow label="Status">
                <StatusBadge status={workspace.status} label={statusLabels[workspace.status]} />
              </DetailRow>
              <DetailRow label="Folder aktif">{workspace.activeFolderCount}</DetailRow>
              <DetailRow label="Dibuat">{formatDateTime(workspace.createdAt)}</DetailRow>
              <DetailRow label="Diperbarui">{formatDateTime(workspace.updatedAt)}</DetailRow>
            </dl>
            <p className="mt-3 rounded-lg bg-background px-3 py-2 text-xs text-ink-secondary">
              Akses dokumen di dalam workspace diperiksa ulang oleh backend berdasarkan role,
              scope unit, dan resource ACL.
            </p>
          </div>
        </Card>

        {workspace.canManage ? (
          <AccessPanel
            target={{ type: 'WORKSPACE', workspaceId }}
            canManage
            onChanged={() => {
              void queryClient.invalidateQueries({ queryKey: ['documents'] });
              void queryClient.invalidateQueries({ queryKey: ['folders', workspaceId] });
              void queryClient.invalidateQueries({ queryKey: ['workspace', workspaceId] });
            }}
          />
        ) : null}
      </div>

      <Dialog
        open={editOpen}
        title="Ubah workspace"
        description={`Kode ${workspace.workspaceCode} bersifat tetap.`}
        onClose={() => {
          setEditOpen(false);
          setActionError(null);
        }}
        footer={
          <>
            <Button
              variant="secondary"
              onClick={() => {
                setEditOpen(false);
                setActionError(null);
              }}
            >
              Batal
            </Button>
            <Button
              loading={updateWorkspaceMutation.isPending}
              onClick={() =>
                void editForm.handleSubmit((values) => updateWorkspaceMutation.mutate(values))()
              }
            >
              Simpan perubahan
            </Button>
          </>
        }
      >
        {editOpen ? (
          <form noValidate className="space-y-4">
            {actionError ? <Alert tone="danger">{actionError}</Alert> : null}
            <InputField
              label="Nama workspace"
              required
              error={editForm.formState.errors.workspaceName?.message}
              {...editForm.register('workspaceName')}
            />
            <SelectField
              label="Unit pemilik"
              required
              helper={
                unitsQuery.isPending
                  ? 'Memuat unit…'
                  : 'Memindahkan workspace memerlukan izin kelola pada unit tujuan.'
              }
              error={editForm.formState.errors.ownerOrganizationUnitId?.message}
              {...editForm.register('ownerOrganizationUnitId')}
            >
              <option value="">Pilih unit</option>
              {(unitsQuery.data ?? []).map((unit) => (
                <option key={unit.id} value={unit.id}>
                  {unit.unitName} ({unit.unitCode})
                </option>
              ))}
              {!unitsQuery.data?.some((unit) => unit.id === workspace.ownerOrganizationUnitId) ? (
                <option value={workspace.ownerOrganizationUnitId}>
                  {workspace.ownerOrganizationUnitName ?? 'Unit saat ini'}
                </option>
              ) : null}
            </SelectField>
            <SelectField
              label="Status"
              required
              helper="Arsip menyembunyikan workspace dari daftar default; histori dokumen tetap tersimpan."
              error={editForm.formState.errors.status?.message}
              {...editForm.register('status')}
            >
              <option value="ACTIVE">Aktif</option>
              <option value="INACTIVE">Nonaktif</option>
              <option value="ARCHIVED">Diarsipkan</option>
            </SelectField>
            <TextareaField label="Deskripsi" {...editForm.register('description')} />
          </form>
        ) : null}
      </Dialog>

      <Dialog
        open={folderDialog !== null}
        title={folderDialog?.mode === 'edit' ? 'Ubah folder' : 'Folder baru'}
        description={
          folderDialog?.mode === 'create' && folderDialog.parentId
            ? 'Folder akan dibuat sebagai sub-folder.'
            : 'Nama folder harus unik pada tingkat yang sama.'
        }
        onClose={closeFolderDialog}
        footer={
          <>
            <Button variant="secondary" onClick={closeFolderDialog}>
              Batal
            </Button>
            <Button
              loading={saveFolderMutation.isPending}
              onClick={() =>
                void folderForm.handleSubmit((values) => saveFolderMutation.mutate(values))()
              }
            >
              {folderDialog?.mode === 'edit' ? 'Simpan perubahan' : 'Buat folder'}
            </Button>
          </>
        }
      >
        {folderDialog ? (
          <form noValidate className="space-y-4">
            {actionError ? <Alert tone="danger">{actionError}</Alert> : null}
            <InputField
              label="Nama folder"
              required
              error={folderForm.formState.errors.folderName?.message}
              {...folderForm.register('folderName')}
            />
            <SelectField
              label="Folder induk"
              helper="Pilih folder lain untuk memindahkan folder beserta isinya."
              error={folderForm.formState.errors.parentFolderId?.message}
              {...folderForm.register('parentFolderId')}
            >
              <option value="">Tingkat atas workspace</option>
              {(foldersQuery.data ?? [])
                .filter(
                  (candidate) =>
                    candidate.status === 'ACTIVE' && !excludedParentIds.has(candidate.id),
                )
                .map((candidate) => (
                  <option key={candidate.id} value={candidate.id}>
                    {candidate.folderName}
                  </option>
                ))}
            </SelectField>
            <TextareaField label="Deskripsi" {...folderForm.register('description')} />
          </form>
        ) : null}
      </Dialog>

      <Dialog
        open={archiveTarget !== null}
        title="Arsipkan folder"
        onClose={closeArchiveDialog}
        footer={
          <>
            <Button variant="secondary" onClick={closeArchiveDialog}>
              Batal
            </Button>
            <Button
              variant="danger"
              loading={archiveMutation.isPending}
              onClick={() => archiveTarget && archiveMutation.mutate(archiveTarget)}
            >
              Arsipkan folder
            </Button>
          </>
        }
      >
        {actionError ? <Alert tone="danger">{actionError}</Alert> : null}
        <p className="text-sm text-ink">
          Folder <span className="font-semibold">{archiveTarget?.folderName}</span> akan diarsipkan.
          Folder yang masih memiliki sub-folder aktif tidak dapat diarsipkan.
        </p>
      </Dialog>
      <Dialog
        open={accessFolder !== null}
        title={accessFolder ? `Akses folder ${accessFolder.folderName}` : 'Akses folder'}
        description="Grant pada folder diwarisi ke dokumen di folder tersebut dan sub-folder-nya, bukan ke folder sibling. Penolakan eksplisit mengalahkan izin yang diwarisi."
        size="lg"
        onClose={() => setAccessFolder(null)}
        footer={
          <Button variant="secondary" onClick={() => setAccessFolder(null)}>
            Tutup
          </Button>
        }
      >
        {accessFolder ? (
          <AccessPanel
            showHeader={false}
            target={{ type: 'FOLDER', workspaceId, folderId: accessFolder.id }}
            canManage={accessFolder.canManage}
            onChanged={() => {
              void queryClient.invalidateQueries({ queryKey: ['documents'] });
              void queryClient.invalidateQueries({ queryKey: ['folders', workspaceId] });
              void queryClient.invalidateQueries({ queryKey: ['workspace', workspaceId] });
            }}
          />
        ) : null}
      </Dialog>
    </>
  );
}
