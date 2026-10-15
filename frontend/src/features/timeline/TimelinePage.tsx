import { QueryClient, QueryClientProvider, useQuery } from "@tanstack/react-query";
import { useState } from "react";

type Guardian = { id: string; name: string };
type Child = { id: string; name: string; guardians: Guardian[] };
type TimelineEvent = {
  date: string;
  type: string;
  newGuardianId?: string | null;
  status?: string | null;
  category?: string | null;
  text?: string | null;
  action?: string | null;
};
type Timeline = { events: TimelineEvent[] };

const TYPES = [
  { value: "", label: "Todos" },
  { value: "EXCECAO", label: "Exceção" },
  { value: "CONFIRMACAO", label: "Confirmação" },
  { value: "OBSERVACAO", label: "Observação" },
  { value: "AUDITORIA", label: "Auditoria" },
] as const;

type TimelineProps = {
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
  return {
    from: `${year}-${padded}-01`,
    to: `${year}-${padded}-${String(last).padStart(2, "0")}`,
  };
}

function formatDate(date: string) {
  const [year, month, day] = date.split("-").map(Number);
  return new Intl.DateTimeFormat("pt-BR", { timeZone: "UTC" }).format(
    new Date(Date.UTC(year, month - 1, day)),
  );
}

function labelOf(type: string) {
  return TYPES.find((item) => item.value === type)?.label ?? type;
}

async function getJson<T>(path: string, accessToken: string, onUnauthorized?: () => void): Promise<T> {
  const response = await fetch(path, { headers: { Authorization: `Bearer ${accessToken}` } });
  if (response.status === 401) {
    onUnauthorized?.();
    throw new Error("Sessão expirada");
  }
  if (!response.ok) {
    throw new Error("Não foi possível carregar a linha do tempo.");
  }
  return response.json() as Promise<T>;
}

export function TimelinePage({ accessToken, today = brazilToday(), onUnauthorized }: TimelineProps) {
  const [client] = useState(() => new QueryClient({ defaultOptions: { queries: { retry: false } } }));
  return (
    <QueryClientProvider client={client}>
      <TimelineContent accessToken={accessToken} today={today} onUnauthorized={onUnauthorized} />
    </QueryClientProvider>
  );
}

function TimelineContent({ accessToken, today, onUnauthorized }: TimelineProps & { today: string }) {
  const bounds = monthBounds(today);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [from, setFrom] = useState(bounds.from);
  const [to, setTo] = useState(bounds.to);
  const [type, setType] = useState("");
  const inverted = from > to;
  const children = useQuery({
    queryKey: ["children", accessToken],
    queryFn: () => getJson<Child[]>("/api/v1/children", accessToken, onUnauthorized),
  });
  const child = children.data?.find((item) => item.id === selectedId) ?? children.data?.[0];
  const timeline = useQuery({
    queryKey: ["timeline", accessToken, child?.id, from, to, type],
    enabled: Boolean(child) && !inverted,
    queryFn: () => {
      const params = new URLSearchParams({ from, to });
      if (type) {
        params.set("type", type);
      }
      return getJson<Timeline>(
        `/api/v1/children/${child?.id}/timeline?${params.toString()}`,
        accessToken,
        onUnauthorized,
      );
    },
  });

  if (children.isError || timeline.isError) {
    return (
      <main className="panel">
        <p role="alert">Não foi possível carregar a linha do tempo.</p>
      </main>
    );
  }
  if (children.data?.length === 0) {
    return (
      <main className="panel">
        <p>Nenhuma criança cadastrada</p>
      </main>
    );
  }
  if (!child || (!inverted && timeline.isPending)) {
    return null;
  }

  const names = new Map(child.guardians.map((guardian) => [guardian.id, guardian.name]));
  return (
    <main className="panel">
      <h1>{child.name}</h1>
      {children.data && children.data.length > 1 ? (
        <label>
          Criança
          <select value={child.id} onChange={(event) => setSelectedId(event.target.value)}>
            {children.data.map((item) => (
              <option key={item.id} value={item.id}>
                {item.name}
              </option>
            ))}
          </select>
        </label>
      ) : null}
      <label>
        De
        <input type="date" value={from} onChange={(event) => setFrom(event.target.value)} />
      </label>
      <label>
        Até
        <input type="date" value={to} onChange={(event) => setTo(event.target.value)} />
      </label>
      <label>
        Tipo
        <select value={type} onChange={(event) => setType(event.target.value)}>
          {TYPES.map((item) => (
            <option key={item.label} value={item.value}>
              {item.label}
            </option>
          ))}
        </select>
      </label>
      {inverted ? <p role="alert">Período inválido</p> : null}
      {!inverted && timeline.data?.events.length === 0 ? <p>Nenhum evento no período</p> : null}
      <ul aria-label="Linha do tempo">
        {(inverted ? [] : (timeline.data?.events ?? [])).map((event, index) => (
          <li key={`${event.date}-${event.type}-${index}`}>
            <span>{formatDate(event.date)}</span> <span>{labelOf(event.type)}</span>{" "}
            {details(event, names).map((detail) => (
              <span key={detail}>{detail} </span>
            ))}
          </li>
        ))}
      </ul>
    </main>
  );
}

function details(event: TimelineEvent, names: Map<string, string>) {
  if (event.type === "EXCECAO") {
    return [names.get(event.newGuardianId ?? "") ?? "Responsável"];
  }
  if (event.type === "CONFIRMACAO") {
    return [event.status ?? ""];
  }
  if (event.type === "OBSERVACAO") {
    return [event.category ?? "", event.text ?? ""].filter(Boolean);
  }
  if (event.type === "AUDITORIA") {
    return [event.action ?? ""].filter(Boolean);
  }
  return [];
}
