export type WebSessionConfig = {
  accessKey: string;
  refreshKey: string;
  redirectPath: string;
  sessionKey?: string;
};

type RefreshResult = 'refreshed' | 'invalid' | 'unavailable';

const refreshPromises = new Map<string, Promise<RefreshResult>>();
const refreshTimers = new Map<string, ReturnType<typeof setTimeout>>();

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
    const json = atob(normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '='));
    const exp = Number(JSON.parse(json).exp);
    return Number.isFinite(exp) && exp > 0 ? exp * 1000 : null;
  } catch {
    return null;
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
  const existing = refreshPromises.get(config.accessKey);
  if (existing) return existing;

  const refreshToken = readRefreshToken(config);
  if (!refreshToken) return 'invalid';

  const promise = (async () => {
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
      refreshPromises.delete(config.accessKey);
    }
  })();

  refreshPromises.set(config.accessKey, promise);
  return promise;
}

function scheduleNext(config: WebSessionConfig, delayMs: number): void {
  const existing = refreshTimers.get(config.accessKey);
  if (existing) clearTimeout(existing);
  const timer = setTimeout(() => {
    refreshTimers.delete(config.accessKey);
    void renewBeforeExpiry(config);
  }, Math.max(1000, delayMs));
  refreshTimers.set(config.accessKey, timer);
}

async function renewBeforeExpiry(config: WebSessionConfig): Promise<void> {
  const token = readToken(config);
  if (!token) return;

  const expiry = tokenExpiryMillis(token);
  if (!expiry) {
    scheduleNext(config, 5 * 60 * 1000);
    return;
  }

  const refreshLead = 2 * 60 * 1000;
  const delay = expiry - Date.now() - refreshLead;

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
    scheduleNext(config, 30 * 1000);
    return;
  }

  const refreshedToken = readToken(config);
  const refreshedExpiry = refreshedToken ? tokenExpiryMillis(refreshedToken) : null;
  scheduleNext(config, refreshedExpiry
    ? Math.max(30 * 1000, refreshedExpiry - Date.now() - refreshLead)
    : 5 * 60 * 1000);
}

export function startWebSessionRefresh(config: WebSessionConfig): void {
  if (typeof window === 'undefined') return;
  const existing = refreshTimers.get(config.accessKey);
  if (existing) clearTimeout(existing);
  void renewBeforeExpiry(config);
}

export function stopWebSessionRefresh(config: WebSessionConfig): void {
  const existing = refreshTimers.get(config.accessKey);
  if (existing) clearTimeout(existing);
  refreshTimers.delete(config.accessKey);
}

export function logoutWebSession(config: WebSessionConfig): void {
  stopWebSessionRefresh(config);
  clearSession(config);
}
