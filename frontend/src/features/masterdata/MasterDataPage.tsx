import { useState } from 'react';
import type { DocumentCategory, DocumentClassification, DocumentTag, DocumentType } from '../../shared/api/types';
import { PageHeader } from '../../shared/components/Surface';
import { FilePolicyPanel } from './FilePolicyPanel';
import { MasterDataPanel } from './MasterDataPanel';
import type { MasterPanelConfig } from './MasterDataPanel';
import {
  createCategory,
  createClassification,
  createTag,
  createType,
  listCategories,
  listClassifications,
  listTags,
  listTypes,
  updateCategory,
  updateClassification,
  updateTag,
  updateType,
} from './api';

const tabs = [
  { key: 'categories', label: 'Kategori' },
  { key: 'types', label: 'Tipe Dokumen' },
  { key: 'classifications', label: 'Klasifikasi' },
  { key: 'tags', label: 'Tag' },
  { key: 'file-policies', label: 'File Policy' },
] as const;

type TabKey = (typeof tabs)[number]['key'];

const categoryConfig: MasterPanelConfig<DocumentCategory> = {
  key: 'categories',
  title: 'Kategori',
  description: 'Struktur kategori dokumen yang dapat bertingkat.',
  list: listCategories,
  create: (payload) => createCategory(payload as Parameters<typeof createCategory>[0]),
  update: (id, payload) => updateCategory(id, payload as Parameters<typeof updateCategory>[1]),
  codeOf: (item) => item.categoryCode,
  nameOf: (item) => item.categoryName,
  statusOf: (item) => item.active,
  meta: (item, all) =>
    item.parentId
      ? `Di bawah ${all.find((candidate) => candidate.id === item.parentId)?.categoryName ?? 'kategori tidak ditemukan'}`
      : 'Kategori tingkat atas',
  fields: (mode, item, all) => {
    const code = {
      key: 'categoryCode',
      label: 'Kode kategori',
      type: 'text' as const,
      required: true,
      helper: 'Kode tidak dapat diubah setelah dibuat.',
      pattern: {
        value: /^[A-Za-z0-9._-]{2,60}$/,
        message: 'Kode 2-60 karakter: huruf, angka, titik, garis bawah, atau strip.',
      },
    };
    const name = {
      key: 'categoryName',
      label: 'Nama kategori',
      type: 'text' as const,
      required: true,
    };
    const parent = {
      key: 'parentId',
      label: 'Kategori induk',
      type: 'select' as const,
      helper: 'Kosongkan untuk kategori tingkat atas.',
      options: [
        { value: '', label: 'Tanpa kategori induk' },
        ...all
          .filter((candidate) => candidate.active && candidate.id !== item?.id)
          .map((candidate) => ({
            value: candidate.id,
            label: `${candidate.categoryName} (${candidate.categoryCode})`,
          })),
      ],
    };
    const description = { key: 'description', label: 'Deskripsi', type: 'textarea' as const };
    const active = {
      key: 'active',
      label: 'Kategori aktif',
      type: 'checkbox' as const,
      helper: 'Kategori nonaktif tidak dapat dipilih untuk dokumen baru.',
    };
    return mode === 'create' ? [code, name, parent, description] : [name, parent, description, active];
  },
};

const typeConfig: MasterPanelConfig<DocumentType> = {
  key: 'types',
  title: 'Tipe Dokumen',
  description: 'Jenis dokumen yang dipakai sebagai metadata pencarian.',
  list: listTypes,
  create: (payload) => createType(payload as Parameters<typeof createType>[0]),
  update: (id, payload) => updateType(id, payload as Parameters<typeof updateType>[1]),
  codeOf: (item) => item.typeCode,
  nameOf: (item) => item.typeName,
  statusOf: (item) => item.active,
  meta: (item) => item.description ?? 'Tipe dokumen',
  fields: (mode) => {
    const code = {
      key: 'typeCode',
      label: 'Kode tipe',
      type: 'text' as const,
      required: true,
      helper: 'Kode tidak dapat diubah setelah dibuat.',
      pattern: {
        value: /^[A-Za-z0-9._-]{2,60}$/,
        message: 'Kode 2-60 karakter: huruf, angka, titik, garis bawah, atau strip.',
      },
    };
    const name = { key: 'typeName', label: 'Nama tipe', type: 'text' as const, required: true };
    const description = { key: 'description', label: 'Deskripsi', type: 'textarea' as const };
    const active = { key: 'active', label: 'Tipe aktif', type: 'checkbox' as const };
    return mode === 'create' ? [code, name, description] : [name, description, active];
  },
};

const classificationConfig: MasterPanelConfig<DocumentClassification> = {
  key: 'classifications',
  title: 'Klasifikasi',
  description: 'Tingkat sensitivitas yang membatasi akses dan distribusi dokumen.',
  list: listClassifications,
  create: (payload) => createClassification(payload as Parameters<typeof createClassification>[0]),
  update: (id, payload) =>
    updateClassification(id, payload as Parameters<typeof updateClassification>[1]),
  codeOf: (item) => item.classificationCode,
  nameOf: (item) => item.classificationName,
  statusOf: (item) => item.active,
  meta: (item) => item.description ?? 'Klasifikasi dokumen',
  fields: (mode) => {
    const code = {
      key: 'classificationCode',
      label: 'Kode klasifikasi',
      type: 'text' as const,
      required: true,
      helper: 'Kode tidak dapat diubah setelah dibuat.',
      pattern: {
        value: /^[A-Za-z0-9._-]{2,40}$/,
        message: 'Kode 2-40 karakter: huruf, angka, titik, garis bawah, atau strip.',
      },
    };
    const name = {
      key: 'classificationName',
      label: 'Nama klasifikasi',
      type: 'text' as const,
      required: true,
    };
    const rank = {
      key: 'sensitivityRank',
      label: 'Peringkat sensitivitas',
      type: 'number' as const,
      required: true,
      min: 1,
      max: 100,
      helper: 'Semakin tinggi peringkat, semakin ketat aksesnya.',
    };
    const description = { key: 'description', label: 'Deskripsi', type: 'textarea' as const };
    const active = { key: 'active', label: 'Klasifikasi aktif', type: 'checkbox' as const };
    return mode === 'create' ? [code, name, rank, description] : [name, rank, description, active];
  },
  extraColumns: [
    { header: 'Sensitivitas', render: (item) => `Peringkat ${item.sensitivityRank}` },
  ],
};

const tagConfig: MasterPanelConfig<DocumentTag> = {
  key: 'tags',
  title: 'Tag',
  description: 'Label taksonomi untuk membantu pencarian dokumen.',
  list: listTags,
  create: (payload) => createTag(payload as Parameters<typeof createTag>[0]),
  update: (id, payload) => updateTag(id, payload as Parameters<typeof updateTag>[1]),
  codeOf: (item) => item.tagCode,
  nameOf: (item) => item.tagName,
  statusOf: (item) => item.active,
  meta: () => 'Tag dokumen',
  fields: (mode) => {
    const code = {
      key: 'tagCode',
      label: 'Kode tag',
      type: 'text' as const,
      required: true,
      helper: 'Kode tidak dapat diubah setelah dibuat.',
      pattern: {
        value: /^[A-Za-z0-9._-]{2,60}$/,
        message: 'Kode 2-60 karakter: huruf, angka, titik, garis bawah, atau strip.',
      },
    };
    const name = { key: 'tagName', label: 'Nama tag', type: 'text' as const, required: true };
    const active = { key: 'active', label: 'Tag aktif', type: 'checkbox' as const };
    return mode === 'create' ? [code, name] : [name, active];
  },
};

const descriptions: Record<TabKey, string> = {
  categories: categoryConfig.description,
  types: typeConfig.description,
  classifications: classificationConfig.description,
  tags: tagConfig.description,
  'file-policies':
    'Allowlist ekstensi, MIME type, dan ukuran file yang ditegakkan backend saat upload.',
};

export function MasterDataPage() {
  const [tab, setTab] = useState<TabKey>('categories');

  return (
    <>
      <PageHeader
        title="Master Data"
        description="Konfigurasi katalog dokumen. Perubahan memengaruhi form dan validasi backend secara langsung."
      />

      <div className="mb-4 overflow-x-auto">
        <div role="tablist" aria-label="Jenis master data" className="flex min-w-max gap-1 border-b border-line">
          {tabs.map((item) => (
            <button
              key={item.key}
              type="button"
              role="tab"
              aria-selected={tab === item.key}
              onClick={() => setTab(item.key)}
              className={
                tab === item.key
                  ? 'border-b-2 border-primary px-4 py-2.5 text-sm font-semibold text-primary'
                  : 'border-b-2 border-transparent px-4 py-2.5 text-sm font-medium text-ink-secondary hover:text-ink'
              }
            >
              {item.label}
            </button>
          ))}
        </div>
      </div>

      <p className="mb-4 text-sm text-ink-secondary">{descriptions[tab]}</p>

      {tab === 'categories' ? <MasterDataPanel config={categoryConfig} /> : null}
      {tab === 'types' ? <MasterDataPanel config={typeConfig} /> : null}
      {tab === 'classifications' ? <MasterDataPanel config={classificationConfig} /> : null}
      {tab === 'tags' ? <MasterDataPanel config={tagConfig} /> : null}
      {tab === 'file-policies' ? <FilePolicyPanel /> : null}
    </>
  );
}
