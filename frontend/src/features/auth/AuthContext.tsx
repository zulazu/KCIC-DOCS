import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { ApiError, onUnauthenticated, resetCsrfToken } from '../../shared/api/client';
import type { MeResponse } from '../../shared/api/types';
import * as authApi from './api';
import type { ChangePasswordPayload, LoginPayload } from './api';

type AuthStatus = 'loading' | 'authenticated' | 'anonymous' | 'error';

interface AuthContextValue {
  status: AuthStatus;
  me: MeResponse | null;
  loadError: string | null;
  login: (payload: LoginPayload) => Promise<MeResponse>;
  logout: () => Promise<void>;
  refresh: () => Promise<void>;
  changePassword: (payload: ChangePasswordPayload) => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<AuthStatus>('loading');
  const [me, setMe] = useState<MeResponse | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    try {
      const loaded = await authApi.fetchMe();
      setMe(loaded);
      setStatus('authenticated');
      setLoadError(null);
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        setMe(null);
        setStatus('anonymous');
        setLoadError(null);
      } else {
        setMe(null);
        setStatus('error');
        setLoadError(
          error instanceof ApiError
            ? error.message
            : 'Session tidak dapat diperiksa. Periksa koneksi ke server.',
        );
      }
    }
  }, []);

  useEffect(() => {
    // Initial session bootstrap reads the server-side session; this is the external-system boundary.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void refresh();
  }, [refresh]);

  useEffect(() => {
    return onUnauthenticated(() => {
      setMe(null);
      setStatus('anonymous');
      resetCsrfToken();
    });
  }, []);

  const login = useCallback(
    async (payload: LoginPayload) => {
      const authenticated = await authApi.login(payload);
      queryClient.clear();
      setMe(authenticated);
      setStatus('authenticated');
      setLoadError(null);
      return authenticated;
    },
    [queryClient],
  );

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } finally {
      queryClient.clear();
      setMe(null);
      setStatus('anonymous');
      resetCsrfToken();
    }
  }, [queryClient]);

  const changePassword = useCallback(
    async (payload: ChangePasswordPayload) => {
      await authApi.changePassword(payload);
      await refresh();
    },
    [refresh],
  );

  const value = useMemo(
    () => ({ status, me, loadError, login, logout, refresh, changePassword }),
    [status, me, loadError, login, logout, refresh, changePassword],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider');
  }
  return context;
}

export function useCapability(code: string): boolean {
  const { me } = useAuth();
  return me?.capabilities.includes(code) ?? false;
}
