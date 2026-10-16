import { QueryClient, QueryClientProvider, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";

type Guardian = { id: string; name: string };
type Child = { id: string; name: string; guardians: Guardian[] };
type Observation = { category: string; text: string };
type DayRow = {
  date: string;
  weekday: string;
  guardianId: string | null;
  status: string | null;
  exception: boolean;
  realizedGuardianId: string | null;
  realizedStartTime: string | null;
  realizedEndTime: string | null;
  observation: Observation | null;
};
type Snapshot = {
  childName: string;
  counts: Record<string, number>;
  days: DayRow[];
};
type ReportVersion = { id: string; version: number; generatedAt: string };
type Report = {
  id: string;
  year: number;
  month: number;
  version: number;
  generatedAt: string;
  contentSnapshot: Snapshot;
};

const COUNT_LABELS = [
  ["PLANEJADO", "Planejado"],
  ["REALIZADO", "Realizado"],
  ["ALTERADO", "Alterado"],
  ["NAO_REALIZADO", "Não realizado"],
] as const;

const STATUS_LABELS: Record<string, string> = {
  PLANEJADO: "Planejado",
  REALIZADO: "Realizado",
  ALTERADO: "Alterado",
  NAO_REALIZADO: "Não realizado",
};

const WEEKDAYS: Record<string, string> = {
  MONDAY: "Segunda",
  TUESDAY: "Terça",
  WEDNESDAY: "Quarta",
  THURSDAY: "Quinta",
  FRIDAY: "Sexta",
  SATURDAY: "Sábado",
  SUNDAY: "Domingo",
};

type ReportsProps = {
  accessToken: string;
  today?: string;
  onUnauthorized?: () => void;
};

function brazilToday() {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "America/Sao_Paulo" }).format(new Date());
}

async function getJson<T>(path: string, accessToken: string, onUnauthorized?: () => void): Promise<T> {
  const response = await fetch(path, { headers: { Authorization: `Bearer ${accessToken}` } });
  if (response.status === 401) {
    onUnauthorized?.();
    throw new Error("Sessão expirada");
  }
  if (!response.ok) {
    throw new Error("Não foi possível carregar os relatórios.");
  }
  return response.json() as Promise<T>;
}

async function errorMessage(response: Response, fallback: string) {
  const body = (await response.json().catch(() => ({}))) as { detail?: string; message?: string };
  return body.detail || body.message || fallback;
}

function formatDate(date: string) {
  const [year, month, day] = date.split("-").map(Number);
  if (!year || !month || !day) {
    return date;
  }
  return new Intl.DateTimeFormat("pt-BR", { timeZone: "UTC" }).format(new Date(Date.UTC(year, month - 1, day)));
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

function guardianName(id: string | null, guardians: Guardian[]) {
  if (!id) {
    return "";
  }
  return guardians.find((item) => item.id === id)?.name ?? "";
}

export function ReportsPage({ accessToken, today = brazilToday(), onUnauthorized }: ReportsProps) {
  const [client] = useState(() => new QueryClient({ defaultOptions: { queries: { retry: false } } }));
  return (
    <QueryClientProvider client={client}>
      <ReportsContent accessToken={accessToken} today={today} onUnauthorized={onUnauthorized} />
    </QueryClientProvider>
  );
}

function ReportsContent({ accessToken, today = brazilToday(), onUnauthorized }: ReportsProps) {
  const client = useQueryClient();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [monthValue, setMonthValue] = useState(today.slice(0, 7));
  const [current, setCurrent] = useState<Report | null>(null);
  const [error, setError] = useState("");
  const [year, month] = monthValue.split("-").map(Number);
  const children = useQuery({
    queryKey: ["children", accessToken],
    queryFn: () => getJson<Child[]>("/api/v1/children", accessToken, onUnauthorized),
  });
  const child = children.data?.find((item) => item.id === selectedId) ?? children.data?.[0];
  const versions = useQuery({
    queryKey: ["reports", accessToken, child?.id, year, month],
    enabled: Boolean(child) && Number.isFinite(year) && Number.isFinite(month),
    queryFn: () => {
      const params = new URLSearchParams({
        childId: child?.id ?? "",
        year: String(year),
        month: String(month),
      });
      return getJson<ReportVersion[]>(`/api/v1/reports/monthly?${params.toString()}`, accessToken, onUnauthorized);
    },
  });

  if (children.isError || versions.isError) {
    return (
      <main className="panel">
        <p role="alert">Não foi possível carregar os relatórios.</p>
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
  if (!child) {
    return null;
  }

  function clearOpen() {
    setCurrent(null);
    setError("");
  }

  async function generate() {
    setError("");
    const params = new URLSearchParams({
      childId: child?.id ?? "",
      year: String(year),
      month: String(month),
    });
    try {
      const response = await fetch(`/api/v1/reports/monthly?${params.toString()}`, {
        method: "POST",
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      if (response.status === 401) {
        onUnauthorized?.();
        return;
      }
      if (!response.ok) {
        setError(await errorMessage(response, "Não foi possível gerar o relatório."));
        return;
      }
      setCurrent((await response.json()) as Report);
      await client.invalidateQueries({ queryKey: ["reports", accessToken, child?.id, year, month] });
    } catch {
      setError("Não foi possível gerar o relatório.");
    }
  }

  async function openVersion(id: string) {
    setError("");
    try {
      const response = await fetch(`/api/v1/reports/monthly/${id}`, {
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      if (response.status === 401) {
        onUnauthorized?.();
        return;
      }
      if (!response.ok) {
        setError(await errorMessage(response, "Não foi possível carregar os relatórios."));
        return;
      }
      setCurrent((await response.json()) as Report);
    } catch {
      setError("Não foi possível carregar os relatórios.");
    }
  }

  async function downloadPdf() {
    if (!current) {
      return;
    }
    setError("");
    try {
      const response = await fetch(`/api/v1/reports/monthly/${current.id}/pdf`, {
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      if (response.status === 401) {
        onUnauthorized?.();
        return;
      }
      if (!response.ok) {
        setError(await errorMessage(response, "Não foi possível baixar o PDF."));
        return;
      }
      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = `relatorio-${current.year}-${String(current.month).padStart(2, "0")}-v${current.version}.pdf`;
      link.click();
      URL.revokeObjectURL(url);
    } catch {
      setError("Não foi possível baixar o PDF.");
    }
  }

  const snapshot = current?.contentSnapshot;
  return (
    <main className="panel">
      <h1>{child.name}</h1>
      {children.data && children.data.length > 1 ? (
        <label>
          Criança
          <select
            value={child.id}
            onChange={(event) => {
              setSelectedId(event.target.value);
              clearOpen();
            }}
          >
            {children.data.map((item) => (
              <option key={item.id} value={item.id}>
                {item.name}
              </option>
            ))}
          </select>
        </label>
      ) : null}
      <label>
        Mês
        <input
          type="month"
          value={monthValue}
          onChange={(event) => {
            setMonthValue(event.target.value);
            clearOpen();
          }}
        />
      </label>
      <button type="button" onClick={generate}>
        Gerar
      </button>
      <ul aria-label="Versões">
        {(versions.data ?? []).map((item) => (
          <li key={item.id}>
            <button type="button" onClick={() => openVersion(item.id)}>
              Versão {item.version}
            </button>
          </li>
        ))}
      </ul>
      {(versions.data ?? []).length === 0 && !versions.isPending && !current ? (
        <p>Nenhum relatório neste mês</p>
      ) : null}
      {snapshot && current ? (
        <section aria-label="Resumo">
          <p>Versão {current.version}</p>
          <p>{formatInstant(current.generatedAt)}</p>
          <ul aria-label="Contagens">
            {COUNT_LABELS.map(([key, label]) => (
              <li key={key}>
                {label} {snapshot.counts[key] ?? 0}
              </li>
            ))}
          </ul>
          <ul aria-label="Dias">
            {snapshot.days.map((day) => {
              const planned = guardianName(day.guardianId, child.guardians);
              const realized = guardianName(day.realizedGuardianId, child.guardians);
              return (
                <li key={day.date}>
                  <span>{formatDate(day.date)}</span> <span>{WEEKDAYS[day.weekday] ?? day.weekday}</span>{" "}
                  {planned ? <span>{planned}</span> : null}{" "}
                  {day.status ? <span>{STATUS_LABELS[day.status] ?? day.status}</span> : null}{" "}
                  {realized && realized !== planned ? <span>{realized}</span> : null}{" "}
                  {day.realizedStartTime ? <span>{day.realizedStartTime}</span> : null}{" "}
                  {day.realizedEndTime ? <span>{day.realizedEndTime}</span> : null}{" "}
                  {day.observation ? <span>{day.observation.category}</span> : null}{" "}
                  {day.observation ? <span>{day.observation.text}</span> : null}{" "}
                  {day.exception ? <span>exceção</span> : null}
                </li>
              );
            })}
          </ul>
          <button type="button" onClick={downloadPdf}>
            Baixar PDF
          </button>
        </section>
      ) : null}
      {error ? <p role="alert">{error}</p> : null}
    </main>
  );
}
