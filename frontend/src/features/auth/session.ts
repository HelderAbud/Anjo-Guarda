export type Session = {
  accessToken: string;
  refreshToken: string;
  expiresInSeconds: number;
};

const SESSION_KEY = "anjo.session";
const listeners = new Set<(session: Session | null) => void>();
let refreshing: Promise<Session | null> | null = null;

export function readSession(): Session | null {
  const raw = sessionStorage.getItem(SESSION_KEY);
  if (!raw) {
    return null;
  }
  return JSON.parse(raw) as Session;
}

export function writeSession(session: Session | null) {
  if (session) {
    sessionStorage.setItem(SESSION_KEY, JSON.stringify(session));
  } else {
    sessionStorage.removeItem(SESSION_KEY);
  }
  listeners.forEach((listener) => listener(session));
}

export function subscribeSession(listener: (session: Session | null) => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

export function refreshSession(): Promise<Session | null> {
  if (!refreshing) {
    refreshing = refreshOnce().finally(() => {
      refreshing = null;
    });
  }
  return refreshing;
}

async function refreshOnce(): Promise<Session | null> {
  const current = readSession();
  if (!current?.refreshToken) {
    writeSession(null);
    return null;
  }
  const response = await fetch("/api/v1/auth/refresh", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refreshToken: current.refreshToken }),
  });
  if (!response.ok) {
    writeSession(null);
    return null;
  }
  const next = (await response.json()) as Session;
  writeSession(next);
  return next;
}

function isLogin(url: string) {
  return url.includes("/api/v1/auth/login");
}

export async function authorizedFetch(input: string, init?: RequestInit): Promise<Response> {
  const response = await fetch(input, init);
  if (response.status !== 401 || isLogin(input)) {
    return response;
  }
  const next = await refreshSession();
  if (!next) {
    return response;
  }
  const headers = new Headers(init?.headers);
  headers.set("Authorization", `Bearer ${next.accessToken}`);
  return fetch(input, { ...init, headers });
}
