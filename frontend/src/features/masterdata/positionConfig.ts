import type { Position } from '../../shared/api/types';
import type { MasterPanelConfig } from './MasterDataPanel';
import { createPosition, listPositions, updatePosition } from './api';

export const positionConfig: MasterPanelConfig<Position> = {
  key: 'positions',
  title: 'Jabatan',
  description:
    'Katalog jabatan (position) yang dipilih pada data employee. Struktur organisasi tetap Direktorat → Departemen → Biro/Division.',
  list: listPositions,
  create: (payload) => createPosition(payload as Parameters<typeof createPosition>[0]),
  update: (id, payload) => updatePosition(id, payload as Parameters<typeof updatePosition>[1]),
  codeOf: (item) => item.positionCode,
  nameOf: (item) => item.positionName,
  statusOf: (item) => item.active,
  meta: (item) => item.description ?? 'Jabatan employee',
  fields: (mode) => {
    const code = {
      key: 'positionCode',
      label: 'Kode jabatan',
      type: 'text' as const,
      required: true,
      helper: 'Kode tidak dapat diubah setelah dibuat.',
      pattern: {
        value: /^[A-Za-z0-9._-]{2,40}$/,
        message: 'Kode 2-40 karakter: huruf, angka, titik, garis bawah, atau strip.',
      },
    };
    const name = { key: 'positionName', label: 'Nama jabatan', type: 'text' as const, required: true };
    const description = { key: 'description', label: 'Deskripsi', type: 'textarea' as const };
    const active = {
      key: 'active',
      label: 'Jabatan aktif',
      type: 'checkbox' as const,
      helper: 'Jabatan nonaktif tidak dapat dipilih untuk employee baru; employee yang sudah memakai tetap tersimpan.',
    };
    return mode === 'create' ? [code, name, description] : [name, description, active];
  },
};