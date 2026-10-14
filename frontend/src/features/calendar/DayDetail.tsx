import { useQueryClient } from "@tanstack/react-query";
import { useState } from "react";

const CATEGORIES = ["ROTINA", "ESCOLA", "ATIVIDADE", "SAUDE", "VIAGEM", "TROCA_DE_HORARIO", "OUTRO"] as const;

type Guardian = { id: string; name: string };
type Day = {
  date: string;
  guardianId: string | null;
  exception: boolean;
  status: string;
  realizedGuardianId?: string | null;
};
type Note = { id: string; date: string; category?: string; text: string };

type DayDetailProps = {
  accessToken: string;
  childId: string;
  day: Day;
  note: Note | null;
  guardians: Guardian[];
  onUnauthorized?: () => void;
};

function initialStatus(day: Day) {
  if (day.status === "PLANEJADO") {
    return day.guardianId ? "REALIZADO" : "NAO_REALIZADO";
  }
  return day.status;
}

function clock(value: string) {
  if (!value) {
    return undefined;
  }
  return value.length === 5 ? `${value}:00` : value;
}

async function errorMessage(response: Response) {
  const body = (await response.json().catch(() => ({}))) as { detail?: string; message?: string };
  return body.detail || body.message || "Não foi possível salvar.";
}

export function DayDetail({ accessToken, childId, day, note, guardians, onUnauthorized }: DayDetailProps) {
  const queryClient = useQueryClient();
  const names = new Map(guardians.map((guardian) => [guardian.id, guardian.name]));
  const [status, setStatus] = useState(initialStatus(day));
  const [realizedGuardianId, setRealizedGuardianId] = useState(
    day.realizedGuardianId || day.guardianId || guardians[0]?.id || "",
  );
  const [start, setStart] = useState("");
  const [end, setEnd] = useState("");
  const [category, setCategory] = useState(note?.category || "ROTINA");
  const [text, setText] = useState(note?.text ?? "");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  async function send(path: string, method: string, body?: unknown) {
    const response = await fetch(path, {
      method,
      headers: {
        Authorization: `Bearer ${accessToken}`,
        ...(body === undefined ? {} : { "Content-Type": "application/json" }),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    if (response.status === 401) {
      onUnauthorized?.();
      throw new Error("Sessão expirada");
    }
    if (!response.ok) {
      throw new Error(await errorMessage(response));
    }
    await queryClient.invalidateQueries({ queryKey: ["calendar"] });
    await queryClient.invalidateQueries({ queryKey: ["notes"] });
  }

  async function saveConfirmation() {
    setError("");
    setMessage("");
    if (status === "REALIZADO" && !day.guardianId) {
      setError("Dia sem planejamento");
      return;
    }
    const payload: Record<string, string | null> = {
      childId,
      date: day.date,
      status,
      note: null,
    };
    if (status === "ALTERADO") {
      if (!realizedGuardianId) {
        setError("Informe o responsável realizado");
        return;
      }
      const sameGuardian = realizedGuardianId === day.guardianId;
      if (sameGuardian && !start && !end) {
        setError("Aconteceu como planejado");
        return;
      }
      if (start && end && end < start) {
        setError("Horário final anterior ao inicial");
        return;
      }
      payload.realizedGuardianId = realizedGuardianId;
      const startTime = clock(start);
      const endTime = clock(end);
      if (startTime) {
        payload.realizedStartTime = startTime;
      }
      if (endTime) {
        payload.realizedEndTime = endTime;
      }
    }
    try {
      await send("/api/v1/calendar/confirmations", "POST", payload);
      setMessage("Confirmação salva");
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Não foi possível salvar.");
    }
  }

  async function saveNote() {
    setError("");
    setMessage("");
    if (text.trim() === "") {
      setError("Informe a observação");
      return;
    }
    try {
      if (note) {
        await send(`/api/v1/notes/${note.id}`, "PATCH", { category, text });
      } else {
        await send("/api/v1/notes", "POST", { childId, date: day.date, category, text });
      }
      setMessage("Observação salva");
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Não foi possível salvar.");
    }
  }

  async function deleteNote() {
    if (!note) {
      return;
    }
    setError("");
    setMessage("");
    try {
      await send(`/api/v1/notes/${note.id}`, "DELETE");
      setText("");
      setMessage("Observação apagada");
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Não foi possível salvar.");
    }
  }

  const statuses = day.guardianId
    ? ["REALIZADO", "ALTERADO", "NAO_REALIZADO"]
    : ["ALTERADO", "NAO_REALIZADO"];

  return (
    <section aria-label="Dia selecionado">
      <h2>
        {new Intl.DateTimeFormat("pt-BR", { timeZone: "UTC" }).format(
          new Date(`${day.date}T00:00:00Z`),
        )}
      </h2>
      <p>Responsável planejado: {day.guardianId ? names.get(day.guardianId) ?? "Responsável" : "Sem responsável"}</p>
      {day.exception ? <p>exceção</p> : null}
      <label htmlFor="day-status">
        Status
        <select id="day-status" value={status} onChange={(event) => setStatus(event.target.value)}>
          {statuses.map((item) => (
            <option key={item} value={item}>
              {item}
            </option>
          ))}
        </select>
      </label>
      {status === "ALTERADO" ? (
        <>
          <label htmlFor="day-guardian">
            Responsável realizado
            <select
              id="day-guardian"
              value={realizedGuardianId}
              onChange={(event) => setRealizedGuardianId(event.target.value)}
            >
              {guardians.map((guardian) => (
                <option key={guardian.id} value={guardian.id}>
                  {guardian.name}
                </option>
              ))}
            </select>
          </label>
          <label htmlFor="day-start">
            Início
            <input id="day-start" type="time" value={start} onChange={(event) => setStart(event.target.value)} />
          </label>
          <label htmlFor="day-end">
            Fim
            <input id="day-end" type="time" value={end} onChange={(event) => setEnd(event.target.value)} />
          </label>
        </>
      ) : null}
      <button type="button" onClick={saveConfirmation}>
        Salvar confirmação
      </button>
      <label htmlFor="day-category">
        Categoria
        <select id="day-category" value={category} onChange={(event) => setCategory(event.target.value)}>
          {CATEGORIES.map((item) => (
            <option key={item} value={item}>
              {item}
            </option>
          ))}
        </select>
      </label>
      <label htmlFor="day-note">
        Observação
        <textarea id="day-note" value={text} onChange={(event) => setText(event.target.value)} />
      </label>
      <button type="button" onClick={saveNote}>
        Salvar observação
      </button>
      {note ? (
        <button type="button" onClick={deleteNote}>
          Apagar observação
        </button>
      ) : null}
      {message ? <p>{message}</p> : null}
      {error ? <p role="alert">{error}</p> : null}
    </section>
  );
}
