export type WebSessionConfig = {
  accessKey: string;
  refreshKey: string;
  redirectPath: string;
  sessionKey?: string;
};

type RefreshResult = 'refreshed' | 'invalid' | 'unavailable';

let refreshPromise: Promise<RefreshResult> | null = null;
let refreshTimer: ReturnType<typeof setTimeout> | null = null;
let activeConfig: WebSessionConfig | null = null;

function readToken(config: WebSessionConfig): string | null {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem(config.accessKey);
}

function readRefreshToken(config: WebSessionConfig): string | null {
  if (typeof window === 'undefined') return null;

  if (config.sessionKey) {
    const raw = localStorage.getItem(config.sessionKey);
    if (raw) {
      try {
        const session = JSON.parse(raw);
        if (session?.refreshToken) return String(session.refreshToken);
      } catch {
        // Fall through to the dedicated refresh-token key.
      }
    }
  }

  return localStorage.getItem(config.refreshKey);
}

function saveTokens(config: WebSessionConfig, accessToken: string, refreshToken: string): void {
  localStorage.setItem(config.accessKey, accessToken);
  localStorage.setItem(config.refreshKey, refreshToken);

  if (config.sessionKey) {
    const raw = localStorage.getItem(config.sessionKey);
    let session: Record<string, unknown> = {};
    if (raw) {
      try {
        session = JSON.parse(raw);
      } catch {
        session = {};
      }
    }
    session.token = accessToken;
    session.accessToken = accessToken;
    session.refreshToken = refreshToken;
    localStorage.setItem(config.sessionKey, JSON.stringify(session));
  }
}

function tokenExpiryMillis(token: string): number | null {
  try {
    const payload = token.split('.')[1];
    if (!payload) return null;
    const normalized = payload.replace(/-/g, '+').replace(/_/g, '/');
    const json = decodeURIComponent(
      atob(normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=').replace(/./g, (char, index) => {
        const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=';
        const binary = atob(normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '='));
        return '%' + ('00' + binary.charCodeAt(index).toString(16)).slice(-2);
      }))
    );
    const payloadData = JSON.parse(json);
    return Number(payloadData.exp) * 1000;
  } catch {
    try {
      const payload = token.split('.')[1];
      if (!payload) return null;
      const normalized = payload.replace(/-/g, '+').replace(/_/g, '/');
      const json = atob(normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '='));
      return Number(JSON.parse(json).exp) * 1000;
    } catch {
      return null;
    }
  }
}

function clearSession(config: WebSessionConfig): void {
  localStorage.removeItem(config.accessKey);
  localStorage.removeItem(config.refreshKey);
  if (config.sessionKey) localStorage.removeItem(config.sessionKey);
}

export function redirectToLogin(config: WebSessionConfig): never {
  clearSession(config);
  window.location.href = config.redirectPath;
  throw new Error('Your session has expired. Please sign in again.');
}

export async function refreshWebSession(config: WebSessionConfig): Promise<RefreshResult> {
  if (refreshPromise) return refreshPromise;

  const refreshToken = readRefreshToken(config);
  if (!refreshToken) return 'invalid';

  refreshPromise = (async () => {
    try {
      const baseUrl = process.env.NEXT_PUBLIC_API_BASE_URL?.replace(/\/$/, '') || 'http://localhost:8080';
      const response = await fetch(baseUrl + '/api/v1/auth/refresh', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      });

      if (response.status === 401 || response.status === 403) return 'invalid';
      if (!response.ok) return 'unavailable';

      const result = await response.json();
      if (!result?.accessToken || !result?.refreshToken) return 'unavailable';

      saveTokens(config, String(result.accessToken), String(result.refreshToken));
      return 'refreshed';
    } catch {
      return 'unavailable';
    } finally {
      refreshPromise = null;
    }
  })();

  return refreshPromise;
}

function scheduleNext(config: WebSessionConfig, delayMs: number): void {
  if (refreshTimer) clearTimeout(refreshTimer);
  refreshTimer = setTimeout(() => void renewBeforeExpiry(config), Math.max(1000, delayMs));
}

async function renewBeforeExpiry(config: WebSessionConfig): Promise<void> {
  const token = readToken(config);
  if (!token) return;

  const expiry = tokenExpiryMillis(token);
  if (!expiry) {
    scheduleNext(config, 5 * 60 * 1000);
    return;
  }

  const now = Date.now();
  const refreshLead = 2 * 60 * 1000;
  const delay = expiry - now - refreshLead;

  if (delay > 0) {
    scheduleNext(config, delay);
    return;
  }

  const result = await refreshWebSession(config);
  if (result === 'invalid') {
    redirectToLogin(config);
    return;
  }

  if (result === 'unavailable') {
    // A temporary network/backend problem must not log out an active user.
    // Retry shortly; the normal 401 path remains the final fallback.
    scheduleNext(config, 30 * 1000);
    return;
  }

  const refreshedToken = readToken(config);
  const refreshedExpiry = refreshedToken ? tokenExpiryMillis(refreshedToken) : null;
  scheduleNext(config, refreshedExpiry ? Math.max(30 * 1000, refreshedExpiry - Date.now() - refreshLead) : 5 * 60 * 1000);
}

export function startWebSessionRefresh(config: WebSessionConfig): void {
  if (typeof window === 'undefined') return;
  activeConfig = config;
  if (refreshTimer) clearTimeout(refreshTimer);
  void renewBeforeExpiry(config);
}

export function stopWebSessionRefresh(): void {
  if (refreshTimer) clearTimeout(refreshTimer);
  refreshTimer = null;
  activeConfig = null;
}

export function logoutWebSession(config: WebSessionConfig): void {
  stopWebSessionRefresh();
  clearSession(config);
}
