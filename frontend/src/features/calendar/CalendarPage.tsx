import { QueryClient, QueryClientProvider, useQuery } from "@tanstack/react-query";
import { useState } from "react";

const WEEKDAYS = ["Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom"] as const;
const STATUSES = ["PLANEJADO", "REALIZADO", "ALTERADO", "NAO_REALIZADO"] as const;

type Guardian = { id: string; name: string };
type Child = { id: string; name: string; guardians: Guardian[] };
type Day = {
  date: string;
  guardianId: string | null;
  exception: boolean;
  status: string;
};
type Month = { days: Day[] };
type Note = { id: string; date: string; text: string };

type CalendarProps = {
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
    throw new Error("Não foi possível carregar o calendário.");
  }
  return response.json() as Promise<T>;
}

function brazilToday() {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "America/Sao_Paulo" }).format(new Date());
}

function partsOf(date: string) {
  const [year, month] = date.split("-").map(Number);
  return { year, month };
}

function shiftMonth(year: number, month: number, delta: number) {
  const cursor = new Date(Date.UTC(year, month - 1 + delta, 1));
  return { year: cursor.getUTCFullYear(), month: cursor.getUTCMonth() + 1 };
}

function monthTitle(year: number, month: number) {
  return new Intl.DateTimeFormat("pt-BR", {
    month: "long",
    year: "numeric",
    timeZone: "UTC",
  }).format(new Date(Date.UTC(year, month - 1, 1)));
}

function formatDate(date: string) {
  const [year, month, day] = date.split("-").map(Number);
  return new Intl.DateTimeFormat("pt-BR", { timeZone: "UTC" }).format(
    new Date(Date.UTC(year, month - 1, day)),
  );
}

function mondayOffset(date: string) {
  const [year, month, day] = date.split("-").map(Number);
  const sundayBased = new Date(Date.UTC(year, month - 1, day)).getUTCDay();
  return (sundayBased + 6) % 7;
}

function monthBounds(year: number, month: number) {
  const last = new Date(Date.UTC(year, month, 0)).getUTCDate();
  const padded = String(month).padStart(2, "0");
  return {
    from: `${year}-${padded}-01`,
    to: `${year}-${padded}-${String(last).padStart(2, "0")}`,
  };
}

export function CalendarPage({ accessToken, today = brazilToday(), onUnauthorized }: CalendarProps) {
  const [client] = useState(
    () => new QueryClient({ defaultOptions: { queries: { retry: false } } }),
  );
  return (
    <QueryClientProvider client={client}>
      <CalendarContent accessToken={accessToken} today={today} onUnauthorized={onUnauthorized} />
    </QueryClientProvider>
  );
}

function CalendarContent({ accessToken, today, onUnauthorized }: CalendarProps & { today: string }) {
  const start = partsOf(today);
  const [cursor, setCursor] = useState(start);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [selectedDate, setSelectedDate] = useState<string | null>(null);
  const { year, month } = cursor;

  const children = useQuery({
    queryKey: ["children", accessToken],
    queryFn: () => getJson<Child[]>("/api/v1/children", accessToken, onUnauthorized),
  });
  const child = children.data?.find((item) => item.id === selectedId) ?? children.data?.[0];
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
  const bounds = monthBounds(year, month);
  const notes = useQuery({
    queryKey: ["notes", accessToken, child?.id, bounds.from, bounds.to],
    enabled: Boolean(child),
    queryFn: () =>
      getJson<Note[]>(
        `/api/v1/notes?childId=${child?.id}&from=${bounds.from}&to=${bounds.to}`,
        accessToken,
        onUnauthorized,
      ),
  });

  if (children.isError || calendar.isError || notes.isError) {
    return (
      <main className="panel calendar">
        <p role="alert">Não foi possível carregar o calendário.</p>
      </main>
    );
  }
  if (children.data?.length === 0) {
    return (
      <main className="panel calendar">
        <p>Nenhuma criança cadastrada</p>
      </main>
    );
  }
  if (!child || !calendar.data || !notes.data) {
    return null;
  }

  const names = new Map(child.guardians.map((guardian) => [guardian.id, guardian.name]));
  const notesByDate = new Map(notes.data.map((note) => [note.date, note]));
  const days = [...calendar.data.days].sort((left, right) => left.date.localeCompare(right.date));
  const cells: Array<Day | null> = [...Array(days.length === 0 ? 0 : mondayOffset(days[0].date)).fill(null), ...days];
  while (cells.length % 7 !== 0) {
    cells.push(null);
  }
  const rows: Array<Array<Day | null>> = [];
  for (let index = 0; index < cells.length; index += 7) {
    rows.push(cells.slice(index, index + 7));
  }
  const selected = days.find((day) => day.date === selectedDate) ?? null;
  const guardianCounts = new Map<string, number>();
  const statusCounts = Object.fromEntries(STATUSES.map((status) => [status, 0]));
  let exceptions = 0;
  for (const day of days) {
    if (!day.guardianId) {
      continue;
    }
    const name = names.get(day.guardianId) ?? "Responsável";
    guardianCounts.set(name, (guardianCounts.get(name) ?? 0) + 1);
    if (statusCounts[day.status] !== undefined) {
      statusCounts[day.status] += 1;
    }
    if (day.exception) {
      exceptions += 1;
    }
  }

  return (
    <main className="panel calendar">
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
      <section aria-label="Resumo do mês">
        {[...guardianCounts.entries()].map(([name, count]) => (
          <p key={name}>
            {name} {count}
          </p>
        ))}
        {STATUSES.map((status) => (
          <p key={status}>
            {status} {statusCounts[status]}
          </p>
        ))}
        <p>exceções {exceptions}</p>
        <p>observações {notes.data.length}</p>
      </section>
      <div className="nav">
        <button type="button" onClick={() => setCursor(shiftMonth(year, month, -1))}>
          Mês anterior
        </button>
        <h2>{monthTitle(year, month)}</h2>
        <button type="button" onClick={() => setCursor(shiftMonth(year, month, 1))}>
          Próximo mês
        </button>
        <button type="button" onClick={() => setCursor(partsOf(today))}>
          Hoje
        </button>
      </div>
      <ul aria-label="Legenda">
        {STATUSES.map((status) => (
          <li key={status}>{status}</li>
        ))}
        <li>exceção</li>
        <li>observação</li>
      </ul>
      <table role="grid">
        <thead>
          <tr>
            {WEEKDAYS.map((weekday) => (
              <th key={weekday} scope="col">
                {weekday}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, rowIndex) => (
            <tr key={rowIndex}>
              {row.map((day, column) => (
                <td key={day?.date ?? `empty-${rowIndex}-${column}`} role="gridcell">
                  {day ? (
                    <button type="button" className="day" onClick={() => setSelectedDate(day.date)}>
                      <span>{Number(day.date.slice(8))}</span>
                      {day.guardianId ? <span>{names.get(day.guardianId) ?? "Responsável"}</span> : null}
                      <span>{day.status}</span>
                      {day.exception ? <span>exceção</span> : null}
                      {notesByDate.has(day.date) ? <span>observação</span> : null}
                    </button>
                  ) : null}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
      {selected ? (
        <section aria-label="Dia selecionado">
          <h2>{formatDate(selected.date)}</h2>
          <p>{selected.guardianId ? names.get(selected.guardianId) ?? "Responsável" : "Sem responsável"}</p>
          <p>{selected.status}</p>
          {selected.exception ? <p>exceção</p> : null}
          {notesByDate.get(selected.date) ? <p>{notesByDate.get(selected.date)?.text}</p> : null}
        </section>
      ) : null}
    </main>
  );
}
