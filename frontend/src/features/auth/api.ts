import { apiRequest } from '../../shared/api/client';
import type { MeResponse } from '../../shared/api/types';

export interface LoginPayload {
  username: string;
  password: string;
}

export interface ChangePasswordPayload {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
}

export function login(payload: LoginPayload): Promise<MeResponse> {
  return apiRequest<MeResponse>('/api/v1/auth/login', { method: 'POST', body: payload });
}

export function logout(): Promise<void> {
  return apiRequest<void>('/api/v1/auth/logout', { method: 'POST' });
}

export function fetchMe(): Promise<MeResponse> {
  return apiRequest<MeResponse>('/api/v1/auth/me');
}

export function changePassword(payload: ChangePasswordPayload): Promise<void> {
  return apiRequest<void>('/api/v1/auth/change-password', {
    method: 'POST',
    body: payload,
  });
}
