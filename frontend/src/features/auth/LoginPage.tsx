import { FormEvent, useState } from "react";
import { DashboardPage } from "../dashboard/DashboardPage";

type Session = {
  accessToken: string;
  refreshToken: string;
  expiresInSeconds: number;
};

const SESSION_KEY = "anjo.session";

function loadSession(): Session | null {
  const raw = sessionStorage.getItem(SESSION_KEY);
  if (!raw) {
    return null;
  }
  return JSON.parse(raw) as Session;
}

export function LoginPage() {
  const [session, setSession] = useState<Session | null>(() => loadSession());
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      setError("Informe um e-mail válido");
      return;
    }
    if (password.trim() === "") {
      setError("Informe a senha");
      return;
    }
    setError("");
    try {
      const response = await fetch("/api/v1/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password }),
      });
      if (!response.ok) {
        const body = (await response.json().catch(() => ({}))) as { detail?: string; message?: string };
        setError(body.detail || body.message || "Credenciais inválidas");
        return;
      }
      const next = (await response.json()) as Session;
      sessionStorage.setItem(SESSION_KEY, JSON.stringify(next));
      setSession(next);
      setPassword("");
    } catch {
      setError("Não foi possível entrar. Tente de novo.");
    }
  }

  async function logout() {
    try {
      if (session) {
        await fetch("/api/v1/auth/logout", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ refreshToken: session.refreshToken }),
        });
      }
    } finally {
      sessionStorage.removeItem(SESSION_KEY);
      setSession(null);
    }
  }

  if (session) {
    return (
      <>
        <DashboardPage accessToken={session.accessToken} onUnauthorized={logout} />
        <div className="bar">
          <button type="button" onClick={logout}>
            Sair
          </button>
        </div>
      </>
    );
  }

  return (
    <main className="panel">
      <h1>Entrar</h1>
      <form onSubmit={onSubmit}>
        <label>
          E-mail
          <input
            type="email"
            autoComplete="username"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
          />
        </label>
        <label>
          Senha
          <input
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
        </label>
        <button type="submit">Entrar</button>
      </form>
      {error ? <p role="alert">{error}</p> : null}
    </main>
  );
}
