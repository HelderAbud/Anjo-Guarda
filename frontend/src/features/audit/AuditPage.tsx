import { QueryClient, QueryClientProvider, useQuery } from "@tanstack/react-query";
import { useState } from "react";

type AuditEntry = {
  id: string;
  action: string;
  entityType: string;
  entityId: string;
  createdAt: string;
};

const ACTIONS: Record<string, string> = {
  LOGIN: "Entrada",
  EXCEPTION_UPSERT: "Exceção gravada",
  CONFIRMATION_UPSERT: "Confirmação gravada",
  NOTE_CREATED: "Observação criada",
  NOTE_UPDATED: "Observação alterada",
  NOTE_DELETED: "Observação apagada",
  DOCUMENT_UPLOADED: "Documento enviado",
  IMPORT_APPROVED: "Importação revisada",
  REPORT_CREATED: "Relatório gerado",
};

const ENTITIES: Record<string, string> = {
  user: "Usuário",
  calendar_exception: "Exceção do calendário",
  calendar_confirmation: "Confirmação do calendário",
  daily_note: "Nota",
  document: "Documento",
  import: "Importação",
  monthly_report: "Relatório mensal",
};

type AuditProps = {
  accessToken: string;
  today?: string;
  onUnauthorized?: () => void;
};

function brazilToday() {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "America/Sao_Paulo" }).format(new Date());
}

function monthBounds(today: string) {
  const [year, month] = today.split("-").map(Number);
  const last = new Date(Date.UTC(year, month, 0)).getUTCDate();
  const padded = String(month).padStart(2, "0");
  return { from: `${year}-${padded}-01`, to: `${year}-${padded}-${String(last).padStart(2, "0")}` };
}

async function getJson<T>(path: string, accessToken: string, onUnauthorized?: () => void): Promise<T> {
  const response = await fetch(path, { headers: { Authorization: `Bearer ${accessToken}` } });
  if (response.status === 401) {
    onUnauthorized?.();
    throw new Error("Sessão expirada");
  }
  if (!response.ok) {
    throw new Error("Não foi possível carregar a auditoria.");
  }
  return response.json() as Promise<T>;
}

function formatInstant(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat("pt-BR", {
    timeZone: "America/Sao_Paulo",
    dateStyle: "short",
    timeStyle: "short",
  }).format(date);
}

export function AuditPage({ accessToken, today = brazilToday(), onUnauthorized }: AuditProps) {
  const [client] = useState(() => new QueryClient({ defaultOptions: { queries: { retry: false } } }));
  return (
    <QueryClientProvider client={client}>
      <AuditContent accessToken={accessToken} today={today} onUnauthorized={onUnauthorized} />
    </QueryClientProvider>
  );
}

function AuditContent({ accessToken, today = brazilToday(), onUnauthorized }: AuditProps) {
  const bounds = monthBounds(today);
  const [from, setFrom] = useState(bounds.from);
  const [to, setTo] = useState(bounds.to);
  const inverted = from > to;
  const audit = useQuery({
    queryKey: ["audit", accessToken, from, to],
    enabled: !inverted,
    queryFn: () => {
      const params = new URLSearchParams({ from, to });
      return getJson<AuditEntry[]>(`/api/v1/audit?${params.toString()}`, accessToken, onUnauthorized);
    },
  });

  if (!inverted && audit.isPending) {
    return null;
  }
  if (audit.isError) {
    return (
      <main className="panel">
        <p role="alert">Não foi possível carregar a auditoria.</p>
      </main>
    );
  }

  const entries = inverted ? [] : (audit.data ?? []);
  return (
    <main className="panel">
      <h1>Auditoria</h1>
      <label>
        De
        <input type="date" value={from} onChange={(event) => setFrom(event.target.value)} />
      </label>
      <label>
        Até
        <input type="date" value={to} onChange={(event) => setTo(event.target.value)} />
      </label>
      {inverted ? <p role="alert">Período inválido</p> : null}
      {!inverted && entries.length === 0 ? <p>Nenhum registro no período</p> : null}
      <ul aria-label="Registros">
        {entries.map((entry) => (
          <li key={entry.id}>
            <span>{formatInstant(entry.createdAt)}</span>{" "}
            <span>{ACTIONS[entry.action] ?? entry.action}</span>{" "}
            <span>{ENTITIES[entry.entityType] ?? entry.entityType}</span>
          </li>
        ))}
      </ul>
    </main>
  );
}
