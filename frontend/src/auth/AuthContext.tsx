import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';
import keycloak, { getUserId, getUsername } from './keycloak';
import { fetchMyPermissions } from '../api/client';

export interface EffectivePermission {
  resourceKey: string;
  objectTypeId: number | null;
  canRead: boolean;
  canWrite: boolean;
  canDelete: boolean;
  restrictedFields: string[];
}

interface PermissionsData {
  isAdmin: boolean;
  permissions: EffectivePermission[];
}

interface AuthState {
  authenticated: boolean;
  userId: string;
  username: string;
  isAdmin: boolean;
  canAccess(resourceKey: string): boolean;
  canRead(resourceKey: string, objectTypeId?: number): boolean;
  canWrite(resourceKey: string, objectTypeId?: number): boolean;
  canDelete(resourceKey: string, objectTypeId?: number): boolean;
  isFieldRestricted(resourceKey: string, fieldKey: string, objectTypeId?: number): boolean;
  refreshPermissions(): Promise<void>;
  logout(): void;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(false);
  const [permData, setPermData] = useState<PermissionsData>({ isAdmin: false, permissions: [] });
  const [, setTick] = useState(0);

  const loadPermissions = useCallback(async () => {
    try {
      const data = await fetchMyPermissions();
      setPermData({ isAdmin: data.isAdmin, permissions: data.permissions ?? [] });
    } catch (err) {
      console.error('[Auth] Failed to load permissions:', err);
    }
  }, []);

  useEffect(() => {
    keycloak.init({ onLoad: 'login-required', checkLoginIframe: false })
      .then(async (authenticated) => {
        if (authenticated) {
          await loadPermissions();
          setReady(true);
        }
      })
      .catch(err => {
        console.error('[Auth] Keycloak init failed:', err);
      });

    const interval = setInterval(() => {
      keycloak.updateToken(70)
        .then(refreshed => {
          if (refreshed) setTick(t => t + 1);
        })
        .catch(() => {
          console.warn('[Auth] Token refresh failed, redirecting to login');
          keycloak.login();
        });
    }, 60000);

    return () => clearInterval(interval);
  }, [loadPermissions]);

  if (!ready) {
    return <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100vh', color: 'var(--color-text-secondary)' }}>Anmeldung...</div>;
  }

  const findPerm = (resourceKey: string, objectTypeId?: number): EffectivePermission | undefined => {
    if (objectTypeId != null) {
      return permData.permissions.find(p => p.resourceKey === resourceKey && p.objectTypeId === objectTypeId);
    }
    // Resource-level: explizit nur null-TypeId matchen (konsistent mit Backend)
    return permData.permissions.find(p => p.resourceKey === resourceKey && p.objectTypeId == null);
  };

  const value: AuthState = {
    authenticated: true,
    userId: getUserId(),
    username: getUsername(),
    isAdmin: permData.isAdmin,

    canAccess(resourceKey: string): boolean {
      if (permData.isAdmin) return true;
      return permData.permissions.some(p => p.resourceKey === resourceKey && p.canRead);
    },

    canRead(resourceKey: string, objectTypeId?: number): boolean {
      if (permData.isAdmin) return true;
      const p = findPerm(resourceKey, objectTypeId);
      return p?.canRead ?? false;
    },

    canWrite(resourceKey: string, objectTypeId?: number): boolean {
      if (permData.isAdmin) return true;
      const p = findPerm(resourceKey, objectTypeId);
      return p?.canWrite ?? false;
    },

    canDelete(resourceKey: string, objectTypeId?: number): boolean {
      if (permData.isAdmin) return true;
      const p = findPerm(resourceKey, objectTypeId);
      return p?.canDelete ?? false;
    },

    isFieldRestricted(resourceKey: string, fieldKey: string, objectTypeId?: number): boolean {
      if (permData.isAdmin) return false;
      const p = findPerm(resourceKey, objectTypeId);
      return p?.restrictedFields?.includes(fieldKey) ?? false;
    },

    refreshPermissions: loadPermissions,
    logout: () => keycloak.logout(),
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
