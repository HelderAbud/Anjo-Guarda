import { QueryClient, QueryClientProvider, useQuery } from "@tanstack/react-query";
import { useState } from "react";

const STATUSES = ["PLANEJADO", "REALIZADO", "ALTERADO", "NAO_REALIZADO"] as const;

type Guardian = { id: string; name: string };
type Child = { id: string; name: string; guardians: Guardian[] };
type Day = { date: string; guardianId: string | null; status: string };
type Month = { days: Day[] };
type Note = { id: string; date: string; category: string; text: string };

type DashboardProps = {
  accessToken: string;
  today?: string;
  onUnauthorized?: () => void;
};

async function getJson<T>(path: string, accessToken: string, onUnauthorized?: () => void): Promise<T> {
  const response = await fetch(path, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  if (response.status === 401) {
    onUnauthorized?.();
    throw new Error("Sessão expirada");
  }
  if (!response.ok) {
    throw new Error("Não foi possível carregar o painel.");
  }
  return response.json() as Promise<T>;
}

function partsOf(today: string) {
  const [year, month] = today.split("-").map(Number);
  return { year, month };
}

function nextMonth(year: number, month: number) {
  if (month === 12) {
    return { year: year + 1, month: 1 };
  }
  return { year, month: month + 1 };
}

function formatDate(date: string) {
  const [year, month, day] = date.split("-").map(Number);
  return new Intl.DateTimeFormat("pt-BR", { timeZone: "UTC" }).format(
    new Date(Date.UTC(year, month - 1, day)),
  );
}

function notesWindow(today: string) {
  const [year, month, day] = today.split("-").map(Number);
  const start = new Date(Date.UTC(year, month - 1, day));
  start.setUTCDate(start.getUTCDate() - 90);
  return { from: start.toISOString().slice(0, 10), to: today };
}

function withGuardian(days: Day[], today: string) {
  return days.filter((day) => day.guardianId && day.date >= today);
}

function brazilToday() {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "America/Sao_Paulo" }).format(new Date());
}

export function DashboardPage({ accessToken, today = brazilToday(), onUnauthorized }: DashboardProps) {
  const [client] = useState(
    () => new QueryClient({ defaultOptions: { queries: { retry: false } } }),
  );
  return (
    <QueryClientProvider client={client}>
      <DashboardContent accessToken={accessToken} today={today} onUnauthorized={onUnauthorized} />
    </QueryClientProvider>
  );
}

function DashboardContent({
  accessToken,
  today,
  onUnauthorized,
}: DashboardProps & { today: string }) {
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const { year, month } = partsOf(today);
  const children = useQuery({
    queryKey: ["children", accessToken],
    queryFn: () => getJson<Child[]>("/api/v1/children", accessToken, onUnauthorized),
  });
  const child =
    children.data?.find((item) => item.id === selectedId) ?? children.data?.[0];
  const calendar = useQuery({
    queryKey: ["calendar", accessToken, child?.id, year, month],
    enabled: Boolean(child),
    queryFn: () =>
      getJson<Month>(
        `/api/v1/children/${child?.id}/calendar?year=${year}&month=${month}`,
        accessToken,
        onUnauthorized,
      ),
  });
  const following = nextMonth(year, month);
  const upcomingNow = calendar.data ? withGuardian(calendar.data.days, today) : [];
  const needsNext = Boolean(calendar.data) && upcomingNow.length < 7;
  const nextCalendar = useQuery({
    queryKey: ["calendar", accessToken, child?.id, following.year, following.month],
    enabled: Boolean(child) && needsNext,
    queryFn: () =>
      getJson<Month>(
        `/api/v1/children/${child?.id}/calendar?year=${following.year}&month=${following.month}`,
        accessToken,
        onUnauthorized,
      ),
  });
  const notes = useQuery({
    queryKey: ["notes", accessToken, child?.id, today],
    enabled: Boolean(child),
    queryFn: () => {
      const window = notesWindow(today);
      return getJson<Note[]>(
        `/api/v1/notes?childId=${child?.id}&from=${window.from}&to=${window.to}`,
        accessToken,
        onUnauthorized,
      );
    },
  });

  if (children.isError || calendar.isError || notes.isError || (needsNext && nextCalendar.isError)) {
    return (
      <main className="panel">
        <p role="alert">Não foi possível carregar o painel.</p>
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

  if (!child || !calendar.data || (needsNext && !nextCalendar.data) || !notes.data) {
    return null;
  }

  const counts = Object.fromEntries(STATUSES.map((status) => [status, 0]));
  for (const day of calendar.data.days) {
    if (day.guardianId && counts[day.status] !== undefined) {
      counts[day.status] += 1;
    }
  }
  const upcoming = [...upcomingNow, ...withGuardian(nextCalendar.data?.days ?? [], today)].slice(0, 7);
  const latest = [...notes.data].sort((left, right) => right.date.localeCompare(left.date)).slice(0, 5);
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
      <section>
        <h2>Resumo do mês</h2>
        {STATUSES.map((status) => (
          <p key={status}>
            {status} {counts[status]}
          </p>
        ))}
      </section>
      <section>
        <h2>Próximos dias</h2>
        {upcoming.length === 0 ? <p>Nenhum dia previsto</p> : null}
        <ul>
          {upcoming.map((day) => (
            <li key={day.date}>
              {formatDate(day.date)} {names.get(day.guardianId ?? "") ?? "Responsável"}
            </li>
          ))}
        </ul>
      </section>
      <section>
        <h2>Últimas observações</h2>
        {latest.length === 0 ? <p>Nenhuma observação recente</p> : null}
        <ul>
          {latest.map((note) => (
            <li key={note.id}>
              {formatDate(note.date)} {note.category} {note.text}
            </li>
          ))}
        </ul>
      </section>
    </main>
  );
}
