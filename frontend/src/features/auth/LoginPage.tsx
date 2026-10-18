import { FormEvent, useState } from "react";
import { CalendarPage } from "../calendar/CalendarPage";
import { DashboardPage } from "../dashboard/DashboardPage";
import { CustodyPlanPage } from "../plan/CustodyPlanPage";
import { TimelinePage } from "../timeline/TimelinePage";
import { DocumentsPage } from "../documents/DocumentsPage";
import { ImportPage } from "../imports/ImportPage";
import { ReportsPage } from "../reports/ReportsPage";
import { AuditPage } from "../audit/AuditPage";
import { SettingsPage } from "../settings/SettingsPage";

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
  const [view, setView] = useState<
    "painel" | "calendario" | "plano" | "historico" | "documentos" | "importacao" | "relatorios" | "auditoria" | "configuracoes"
  >("painel");

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
      setView("painel");
    }
  }

  if (session) {
    return (
      <>
        <div className="bar">
          <button type="button" className="ghost" onClick={() => setView("painel")}>
            Painel
          </button>
          <button type="button" className="ghost" onClick={() => setView("calendario")}>
            Calendário
          </button>
          <button type="button" className="ghost" onClick={() => setView("plano")}>
            Plano
          </button>
          <button type="button" className="ghost" onClick={() => setView("historico")}>
            Histórico
          </button>
          <button type="button" className="ghost" onClick={() => setView("documentos")}>
            Documentos
          </button>
          <button type="button" className="ghost" onClick={() => setView("importacao")}>
            Importação
          </button>
          <button type="button" className="ghost" onClick={() => setView("relatorios")}>
            Relatórios
          </button>
          <button type="button" className="ghost" onClick={() => setView("auditoria")}>
            Auditoria
          </button>
          <button type="button" className="ghost" onClick={() => setView("configuracoes")}>
            Configurações
          </button>
          <button type="button" onClick={logout}>
            Sair
          </button>
        </div>
        {view === "calendario" ? (
          <CalendarPage accessToken={session.accessToken} onUnauthorized={logout} />
        ) : view === "plano" ? (
          <CustodyPlanPage accessToken={session.accessToken} onUnauthorized={logout} />
        ) : view === "historico" ? (
          <TimelinePage accessToken={session.accessToken} onUnauthorized={logout} />
        ) : view === "documentos" ? (
          <DocumentsPage accessToken={session.accessToken} onUnauthorized={logout} />
        ) : view === "importacao" ? (
          <ImportPage accessToken={session.accessToken} onUnauthorized={logout} />
        ) : view === "relatorios" ? (
          <ReportsPage accessToken={session.accessToken} onUnauthorized={logout} />
        ) : view === "auditoria" ? (
          <AuditPage accessToken={session.accessToken} onUnauthorized={logout} />
        ) : view === "configuracoes" ? (
          <SettingsPage />
        ) : (
          <DashboardPage accessToken={session.accessToken} onUnauthorized={logout} />
        )}
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
