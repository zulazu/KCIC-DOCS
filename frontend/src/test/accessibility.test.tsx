import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { Badge, StatusBadge } from '../shared/components/Badge';
import { Button, ButtonLink } from '../shared/components/Button';
import { ConfirmDialog, Dialog } from '../shared/components/Dialog';
import {
  Alert,
  EmptyState,
  ErrorState,
  ForbiddenState,
  LoadingBlock,
} from '../shared/components/Feedback';
import { InputField, SelectField, TextareaField } from '../shared/components/Field';
import { FilterPanel } from '../shared/components/FilterPanel';
import { Icon } from '../shared/components/Icon';
import { PaginationBar } from '../shared/components/PaginationBar';
import { Card, CardHeader, DetailRow, PageHeader } from '../shared/components/Surface';
import { Table, Td, Th, THead, Tr } from '../shared/components/Table';
import { ToastProvider, useToast } from '../shared/components/Toast';
import { runAxe } from './axe';

async function expectAccessible(node: Element, pageLevel = false) {
  const findings = await runAxe(node, pageLevel);
  expect(findings, JSON.stringify(findings, null, 2)).toEqual([]);
}

describe('accessibility — shared primitives (axe-core, WCAG 2.2 AA)', () => {
  it('Button variants have accessible names and states', async () => {
    const { container } = render(
      <div>
        <Button>Simpan</Button>
        <Button variant="secondary">Batal</Button>
        <Button variant="ghost" size="compact">
          Reset
        </Button>
        <Button variant="danger" loading>
          Cabut akses
        </Button>
        <Button variant="secondary" aria-label="Tutup dialog">
          <Icon name="close" size={16} />
        </Button>
        <Button disabled>Nonaktif</Button>
      </div>,
    );
    await expectAccessible(container);
  });

  it('Badge and StatusBadge communicate status with text', async () => {
    const { container } = render(
      <div>
        <Badge tone="gold">Internal</Badge>
        <StatusBadge status="ACTIVE" label="Aktif" />
        <StatusBadge status="REVOKED" label="Dicabut" />
        <StatusBadge status="PENDING" label="Menunggu" />
      </div>,
    );
    await expectAccessible(container);
  });

  it('PageHeader exposes a single h1 with identifier and status', async () => {
    const { container } = render(
      <PageHeader
        title="SOP Operasional"
        meta={
          <>
            <span className="font-mono text-xs">SOP-OPS-001</span>
            <StatusBadge status="ACTIVE" label="Aktif" />
          </>
        }
        action={
          <Button>
            <Icon name="plus" size={16} />
            Upload dokumen
          </Button>
        }
      />,
    );
    await expectAccessible(container);
  });

  it('Card, CardHeader and DetailRow keep definition-list semantics', async () => {
    const { container } = render(
      <Card>
        <CardHeader title="Ringkasan" description="Metadata dokumen." action={<Button variant="ghost">Ubah</Button>} />
        <dl className="px-5 py-2">
          <DetailRow label="Owner">Operations</DetailRow>
          <DetailRow label="Klasifikasi">Internal</DetailRow>
        </dl>
      </Card>,
    );
    await expectAccessible(container);
  });

  it('FilterPanel toggle is labelled and wired to its panel', async () => {
    const { container } = render(
      <FilterPanel activeCount={2}>
        <form>
          <InputField label="Kata kunci" name="keyword" />
          <SelectField label="Status" name="status">
            <option value="">Semua status</option>
          </SelectField>
          <Button type="submit">Terapkan</Button>
        </form>
      </FilterPanel>,
    );
    await expectAccessible(container);
  });

  it('PaginationBar labels the page-size control and announces the range', async () => {
    const { container } = render(
      <PaginationBar
        page={1}
        size={10}
        totalItems={196}
        totalPages={20}
        itemLabel="event"
        onPageChange={() => {}}
        onSizeChange={() => {}}
      />,
    );
    await expectAccessible(container);
  });

  it('Dialog and ConfirmDialog are named and described', async () => {
    const { container } = render(
      <div>
        <Dialog open title="Ubah pengguna" description="Perbarui data akun." onClose={() => {}} footer={<Button>Simpan</Button>}>
          <InputField label="Nama" name="displayName" />
        </Dialog>
        <ConfirmDialog
          open
          title="Cabut akses?"
          message="Penerima kehilangan akses unduh segera."
          confirmLabel="Cabut akses"
          onConfirm={() => {}}
          onClose={() => {}}
        />
      </div>,
    );
    await expectAccessible(container);
  });

  it('Form fields associate labels, helpers and errors', async () => {
    const { container } = render(
      <form>
        <InputField label="Username" name="username" required helper="Huruf kecil, tanpa spasi." />
        <InputField label="Email" name="email" error="Format email tidak valid." />
        <SelectField label="Role" name="role" required>
          <option value="">Pilih role</option>
          <option value="EMPLOYEE">Employee</option>
        </SelectField>
        <TextareaField label="Catatan" name="note" helper="Opsional." />
      </form>,
    );
    await expectAccessible(container);
  });

  it('Feedback states are announced and actionable', async () => {
    const { container } = render(
      <div>
        <Alert tone="danger" title="Gagal menyimpan">
          Periksa kembali data yang diisi.
        </Alert>
        <Alert tone="info">Data employee hanya berasal dari sumber HR resmi.</Alert>
        <LoadingBlock label="Memuat dokumen…" />
        <EmptyState title="Belum ada dokumen" description="Upload dokumen pertama untuk memulai." action={<Button>Upload dokumen</Button>} />
        <ErrorState message="Daftar dokumen tidak dapat dimuat." onRetry={() => {}} />
        <ForbiddenState />
      </div>,
    );
    await expectAccessible(container);
  });

  it('Toast is a live region with a labelled dismiss control', async () => {
    function Trigger() {
      const { notify } = useToast();
      return <Button onClick={() => notify('Perubahan disimpan')}>Simpan</Button>;
    }
    const { container } = render(
      <ToastProvider>
        <Trigger />
      </ToastProvider>,
    );
    await userEvent.click(screen.getByRole('button', { name: 'Simpan' }));
    await expectAccessible(container);
  });
});

describe('accessibility — representative page composition (landmarks included)', () => {
  it('skip link, header, main and footer satisfy page-level rules', async () => {
    const { container } = render(
      <div>
        <a href="#main-content" className="sr-only focus:not-sr-only">
          Lewati ke konten utama
        </a>
        <header>
          <nav aria-label="Breadcrumb">
            <ol>
              <li>KCIC Docs</li>
              <li aria-current="page">Dokumen</li>
            </ol>
          </nav>
        </header>
        <main id="main-content">
          <PageHeader
            title="Dokumen"
            action={
              <Button>
                <Icon name="plus" size={16} />
                Upload dokumen
              </Button>
            }
          />
          <FilterPanel activeCount={0}>
            <form>
              <InputField label="Kata kunci" name="keyword" />
              <Button type="submit">Terapkan</Button>
            </form>
          </FilterPanel>
          <Card>
            <Table caption="Daftar dokumen">
              <THead>
                <Th>Dokumen</Th>
                <Th>Status</Th>
                <Th align="right">Aksi</Th>
              </THead>
              <tbody>
                <Tr>
                  <Td>SOP Operasional</Td>
                  <Td>
                    <StatusBadge status="ACTIVE" label="Aktif" />
                  </Td>
                  <Td>
                    <MemoryRouter>
                      <ButtonLink to="/documents/1" variant="secondary" size="compact">
                        Buka
                        <Icon name="chevron-right" size={14} />
                      </ButtonLink>
                    </MemoryRouter>
                  </Td>
                </Tr>
              </tbody>
            </Table>
            <PaginationBar
              page={0}
              size={10}
              totalItems={1}
              totalPages={1}
              itemLabel="dokumen"
              onPageChange={() => {}}
              onSizeChange={() => {}}
            />
          </Card>
        </main>
        <footer>KCIC Docs — pilot internal</footer>
      </div>,
    );
    await expectAccessible(container, true);
  });
});
