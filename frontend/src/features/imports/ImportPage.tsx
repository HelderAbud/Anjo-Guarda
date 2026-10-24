import { QueryClient, QueryClientProvider, useQuery } from "@tanstack/react-query";
import { FormEvent, useState } from "react";
import { authorizedFetch } from "../auth/session";

type Child = { id: string; name: string };
type Proposed = Record<string, string>;
type ImportItem = {
  id: string;
  type: string;
  proposedData: Proposed;
  conflict: boolean;
  approved: boolean | null;
};
type ImportRecord = {
  id: string;
  filename: string;
  sourceType: string;
  status: string;
  extractedText: string;
  items: ImportItem[];
};
type Choice = { approved: boolean; resolution: string };

const TYPE_LABELS: Record<string, string> = {
  OBSERVACAO: "Observação",
  CONFIRMACAO: "Confirmação",
  EXCECAO: "Exceção",
};

type ImportProps = {
  accessToken: string;
  onUnauthorized?: () => void;
};

async function getJson<T>(path: string, accessToken: string, onUnauthorized?: () => void): Promise<T> {
  const response = await authorizedFetch(path, { headers: { Authorization: `Bearer ${accessToken}` } });
  if (response.status === 401) {
    onUnauthorized?.();
    throw new Error("Sessão expirada");
  }
  if (!response.ok) {
    throw new Error("Não foi possível carregar a importação.");
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

export function ImportPage({ accessToken, onUnauthorized }: ImportProps) {
  const [client] = useState(() => new QueryClient({ defaultOptions: { queries: { retry: false } } }));
  return (
    <QueryClientProvider client={client}>
      <ImportContent accessToken={accessToken} onUnauthorized={onUnauthorized} />
    </QueryClientProvider>
  );
}

function ImportContent({ accessToken, onUnauthorized }: ImportProps) {
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [current, setCurrent] = useState<ImportRecord | null>(null);
  const [choices, setChoices] = useState<Record<string, Choice>>({});
  const [error, setError] = useState("");
  const children = useQuery({
    queryKey: ["children", accessToken],
    queryFn: () => getJson<Child[]>("/api/v1/children", accessToken, onUnauthorized),
  });
  const child = children.data?.find((item) => item.id === selectedId) ?? children.data?.[0];

  if (children.isError) {
    return (
      <main className="panel">
        <p role="alert">Não foi possível carregar a importação.</p>
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

  function choose(itemId: string, patch: Partial<Choice>) {
    setChoices((currentChoices) => {
      const previous = currentChoices[itemId] ?? { approved: false, resolution: "" };
      const next = { ...previous, ...patch };
      if (patch.approved === false) {
        next.resolution = "";
      }
      return { ...currentChoices, [itemId]: next };
    });
  }

  async function conclude() {
    if (!current) {
      return;
    }
    const decisions = [];
    for (const item of current.items) {
      const choice = choices[item.id];
      if (!choice) {
        setError("Decisões inválidas");
        return;
      }
      if (!choice.approved) {
        decisions.push({ itemId: item.id, approved: false });
        continue;
      }
      if (item.conflict && !choice.resolution) {
        setError("Conflito exige resolução");
        return;
      }
      if (!item.conflict) {
        decisions.push({ itemId: item.id, approved: true });
        continue;
      }
      decisions.push({ itemId: item.id, approved: true, resolution: choice.resolution });
    }
    setError("");
    try {
      const response = await authorizedFetch(`/api/v1/imports/${current.id}/approve`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${accessToken}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ decisions }),
      });
      if (response.status === 401) {
        onUnauthorized?.();
        return;
      }
      if (!response.ok) {
        setError(await errorMessage(response, "Não foi possível concluir a revisão."));
        return;
      }
      setCurrent((await response.json()) as ImportRecord);
      setChoices({});
    } catch {
      setError("Não foi possível concluir a revisão.");
    }
  }

  const reviewed = current?.status === "REVISADO";
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
              setCurrent(null);
              setChoices({});
              setError("");
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
      <UploadForm
        key={child.id}
        childId={child.id}
        accessToken={accessToken}
        onUnauthorized={onUnauthorized}
        onStarted={(started) => {
          setCurrent(started);
          setChoices({});
          setError("");
        }}
      />
      {current ? (
        <section aria-label="Revisão">
          <p>{current.filename}</p>
          <p>{current.sourceType}</p>
          {reviewed ? <p>Revisado</p> : null}
          {current.extractedText.split("\n").map((line, index) => (
            <p key={`${index}-${line}`}>{line}</p>
          ))}
          {current.items.length === 0 ? <p>Nenhum item encontrado</p> : null}
          <ul aria-label="Itens">
            {current.items.map((item) => {
              const choice = choices[item.id];
              return (
                <li key={item.id}>
                  <span>{TYPE_LABELS[item.type] ?? item.type}</span>{" "}
                  {item.proposedData.date ? <span>{formatDate(item.proposedData.date)}</span> : null}{" "}
                  {item.proposedData.category ? <span>{item.proposedData.category}</span> : null}{" "}
                  {item.proposedData.status ? <span>{item.proposedData.status}</span> : null}{" "}
                  {item.proposedData.text ? <span>{item.proposedData.text}</span> : null}{" "}
                  {item.proposedData.reason ? <span>{item.proposedData.reason}</span> : null}{" "}
                  {item.conflict ? <span>conflito</span> : null}
                  {reviewed ? null : (
                    <>
                      <label>
                        <input
                          type="radio"
                          name={`item-${item.id}`}
                          checked={choice?.approved === true}
                          onChange={() => choose(item.id, { approved: true })}
                        />
                        Aprovar
                      </label>
                      <label>
                        <input
                          type="radio"
                          name={`item-${item.id}`}
                          checked={choice?.approved === false}
                          onChange={() => choose(item.id, { approved: false })}
                        />
                        Rejeitar
                      </label>
                      {item.conflict && choice?.approved ? (
                        <label>
                          Resolução
                          <select
                            value={choice.resolution}
                            onChange={(event) => choose(item.id, { resolution: event.target.value })}
                          >
                            <option value="">Escolha</option>
                            <option value="MANTER">Manter</option>
                            <option value="SUBSTITUIR">Substituir</option>
                            {item.type === "OBSERVACAO" ? <option value="MESCLAR">Mesclar</option> : null}
                          </select>
                        </label>
                      ) : null}
                    </>
                  )}
                </li>
              );
            })}
          </ul>
          {reviewed ? null : (
            <button type="button" onClick={conclude}>
              Concluir revisão
            </button>
          )}
          {error ? <p role="alert">{error}</p> : null}
        </section>
      ) : null}
    </main>
  );
}

function UploadForm({
  childId,
  accessToken,
  onUnauthorized,
  onStarted,
}: {
  childId: string;
  accessToken: string;
  onUnauthorized?: () => void;
  onStarted: (started: ImportRecord) => void;
}) {
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState("");

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!file) {
      setError("Arquivo inválido");
      return;
    }
    setError("");
    const form = event.currentTarget;
    const body = new FormData();
    body.set("childId", childId);
    body.set("file", file);
    try {
      const response = await authorizedFetch("/api/v1/imports", {
        method: "POST",
        headers: { Authorization: `Bearer ${accessToken}` },
        body,
      });
      if (response.status === 401) {
        onUnauthorized?.();
        return;
      }
      if (!response.ok) {
        setError(await errorMessage(response, "Não foi possível importar o arquivo."));
        return;
      }
      setFile(null);
      form.reset();
      onStarted((await response.json()) as ImportRecord);
    } catch {
      setError("Não foi possível importar o arquivo.");
    }
  }

  return (
    <form onSubmit={onSubmit}>
      <label>
        Arquivo
        <input
          type="file"
          accept="application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
          onChange={(event) => setFile(event.target.files?.[0] ?? null)}
        />
      </label>
      <button type="submit">Enviar</button>
      {error ? <p role="alert">{error}</p> : null}
    </form>
  );
}
