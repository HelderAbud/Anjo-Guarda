import { QueryClient, QueryClientProvider, useQuery, useQueryClient } from "@tanstack/react-query";
import { FormEvent, useState } from "react";
import { authorizedFetch } from "../auth/session";

type Child = { id: string; name: string };
type DocumentItem = {
  id: string;
  childId: string;
  category: string;
  filename: string;
  mimeType: string;
  size: number;
};

type DocumentsProps = {
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
    throw new Error("Não foi possível carregar os documentos.");
  }
  return response.json() as Promise<T>;
}

async function errorMessage(response: Response, fallback: string) {
  const body = (await response.json().catch(() => ({}))) as { detail?: string; message?: string };
  return body.detail || body.message || fallback;
}

export function DocumentsPage({ accessToken, onUnauthorized }: DocumentsProps) {
  const [client] = useState(() => new QueryClient({ defaultOptions: { queries: { retry: false } } }));
  return (
    <QueryClientProvider client={client}>
      <DocumentsContent accessToken={accessToken} onUnauthorized={onUnauthorized} />
    </QueryClientProvider>
  );
}

function DocumentsContent({ accessToken, onUnauthorized }: DocumentsProps) {
  const client = useQueryClient();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const children = useQuery({
    queryKey: ["children", accessToken],
    queryFn: () => getJson<Child[]>("/api/v1/children", accessToken, onUnauthorized),
  });
  const child = children.data?.find((item) => item.id === selectedId) ?? children.data?.[0];
  const documents = useQuery({
    queryKey: ["documents", accessToken, child?.id],
    enabled: Boolean(child),
    queryFn: () =>
      getJson<DocumentItem[]>(`/api/v1/documents?childId=${child?.id}`, accessToken, onUnauthorized),
  });

  if (children.isError || documents.isError) {
    return (
      <main className="panel">
        <p role="alert">Não foi possível carregar os documentos.</p>
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
  if (!child || documents.isPending) {
    return null;
  }

  const needle = query.trim().toLowerCase();
  const visible = (documents.data ?? []).filter((item) => {
    if (!needle) {
      return true;
    }
    return item.filename.toLowerCase().includes(needle) || item.category.toLowerCase().includes(needle);
  });

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
      <UploadForm
        key={child.id}
        childId={child.id}
        accessToken={accessToken}
        onUnauthorized={onUnauthorized}
        onStored={() => client.invalidateQueries({ queryKey: ["documents", accessToken, child.id] })}
      />
      <label>
        Busca
        <input value={query} onChange={(event) => setQuery(event.target.value)} />
      </label>
      {visible.length === 0 ? <p>Nenhum documento</p> : null}
      <ul aria-label="Documentos">
        {visible.map((item) => (
          <li key={item.id}>
            <span>{item.filename}</span> <span>{item.category}</span> <span>{item.size} bytes</span>{" "}
            <button type="button" onClick={() => download(item, accessToken, onUnauthorized)}>
              Baixar
            </button>
          </li>
        ))}
      </ul>
    </main>
  );
}

function UploadForm({
  childId,
  accessToken,
  onUnauthorized,
  onStored,
}: {
  childId: string;
  accessToken: string;
  onUnauthorized?: () => void;
  onStored: () => Promise<void>;
}) {
  const [category, setCategory] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState("");

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const trimmed = category.trim();
    if (!trimmed || trimmed.length > 40) {
      setError("Categoria inválida");
      return;
    }
    if (!file) {
      setError("Arquivo inválido");
      return;
    }
    setError("");
    const form = event.currentTarget;
    const body = new FormData();
    body.set("childId", childId);
    body.set("category", trimmed);
    body.set("file", file);
    try {
      const response = await authorizedFetch("/api/v1/documents", {
        method: "POST",
        headers: { Authorization: `Bearer ${accessToken}` },
        body,
      });
      if (response.status === 401) {
        onUnauthorized?.();
        return;
      }
      if (!response.ok) {
        setError(await errorMessage(response, "Não foi possível enviar o documento."));
        return;
      }
      setCategory("");
      setFile(null);
      form.reset();
      await onStored();
    } catch {
      setError("Não foi possível enviar o documento.");
    }
  }

  return (
    <form onSubmit={onSubmit}>
      <label>
        Categoria
        <input value={category} onChange={(event) => setCategory(event.target.value)} />
      </label>
      <label>
        Arquivo
        <input
          type="file"
          accept="application/pdf,image/jpeg,image/png,image/webp"
          onChange={(event) => setFile(event.target.files?.[0] ?? null)}
        />
      </label>
      <button type="submit">Enviar</button>
      {error ? <p role="alert">{error}</p> : null}
    </form>
  );
}

async function download(item: DocumentItem, accessToken: string, onUnauthorized?: () => void) {
  const response = await authorizedFetch(`/api/v1/documents/${item.id}/download`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  if (response.status === 401) {
    onUnauthorized?.();
    return;
  }
  if (!response.ok) {
    return;
  }
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = item.filename;
  link.click();
  URL.revokeObjectURL(url);
}
