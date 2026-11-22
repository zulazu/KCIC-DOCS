import type { FulfillmentType, RequestStatus, RequestType } from '../../shared/api/types';

export const REQUEST_TYPE_OPTIONS: { value: RequestType; label: string }[] = [
  { value: 'ACCESS', label: 'Akses dokumen' },
  { value: 'DOCUMENT_DELIVERY', label: 'Pengiriman dokumen' },
  { value: 'TEMPLATE', label: 'Permintaan template' },
];

export const REQUEST_STATUS_OPTIONS: { value: RequestStatus; label: string }[] = [
  { value: 'SUBMITTED', label: 'Menunggu review' },
  { value: 'UNDER_REVIEW', label: 'Dalam review' },
  { value: 'APPROVED', label: 'Disetujui' },
  { value: 'IN_PROGRESS', label: 'Sedang dipenuhi' },
  { value: 'FULFILLED', label: 'Terpenuhi' },
  { value: 'REJECTED', label: 'Ditolak' },
  { value: 'CLOSED', label: 'Ditutup' },
  { value: 'CANCELLED', label: 'Dibatalkan' },
  { value: 'DRAFT', label: 'Draft' },
];

export const FULFILLMENT_TYPE_LABELS: Record<FulfillmentType, string> = {
  ACCESS_GRANTED: 'Akses diberikan',
  DOCUMENT_LINKED: 'Dokumen ditautkan',
  DOCUMENT_UPLOADED: 'Dokumen diunggah',
  EXTERNAL_LINK_REGISTERED: 'Tautan eksternal dicatat',
};

export function requestTypeLabel(type: RequestType | string): string {
  return REQUEST_TYPE_OPTIONS.find((option) => option.value === type)?.label ?? type;
}
