import { ApiError } from '../api/client';

export function apiMessage(error: unknown): string {
  return error instanceof ApiError ? error.message : 'Tindakan tidak dapat diproses. Coba lagi.';
}
