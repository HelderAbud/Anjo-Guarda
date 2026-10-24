import { QueryClient, QueryClientProvider, useQuery, useQueryClient } from "@tanstack/react-query";
import { FormEvent, useState } from "react";
import { authorizedFetch } from "../auth/session";

type Guardian = { id: string; name: string };
type Child = { id: string; name: string; guardians: Guardian[] };
type Plan = {
  id: string;
  name: string;
  startDate: string;
  endDate: string | null;
  rule: { ruleType: string; configuration: Record<string, string> };
};

const RULES = [
  {
    type: "ALTERNATE_DAYS",
    label: "Dias alternados",
    fields: [
      { key: "start", label: "Começa com" },
      { key: "alternate", label: "Alterna com" },
    ],
  },
  {
    type: "WEEKENDS",
    label: "Fins de semana",
    fields: [
      { key: "weekday", label: "Dias de semana" },
      { key: "weekend", label: "Fim de semana" },
    ],
  },
  {
    type: "ALTERNATE_WEEKS",
    label: "Semanas alternadas",
    fields: [
      { key: "startWeek", label: "Semana inicial" },
      { key: "alternateWeek", label: "Semana seguinte" },
    ],
  },
  {
    type: "CUSTOM",
    label: "Personalizada",
    fields: [
      { key: "MONDAY", label: "Segunda" },
      { key: "TUESDAY", label: "Terça" },
      { key: "WEDNESDAY", label: "Quarta" },
      { key: "THURSDAY", label: "Quinta" },
      { key: "FRIDAY", label: "Sexta" },
      { key: "SATURDAY", label: "Sábado" },
      { key: "SUNDAY", label: "Domingo" },
    ],
  },
] as const;

type PlanProps = {
  accessToken: string;
  today?: string;
  onUnauthorized?: () => void;
};

function ruleOf(type: string) {
  return RULES.find((rule) => rule.type === type) ?? RULES[0];
}

function emptyConfiguration(type: string, guardians: Guardian[]) {
  const first = guardians[0]?.id ?? "";
  return Object.fromEntries(ruleOf(type).fields.map((field) => [field.key, first]));
}

function formatDate(date: string) {
  const [year, month, day] = date.split("-").map(Number);
  return new Intl.DateTimeFormat("pt-BR", { timeZone: "UTC" }).format(
    new Date(Date.UTC(year, month - 1, day)),
  );
}

function brazilToday() {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "America/Sao_Paulo" }).format(new Date());
}

async function getJson<T>(path: string, accessToken: string, onUnauthorized?: () => void): Promise<T> {
  const response = await authorizedFetch(path, { headers: { Authorization: `Bearer ${accessToken}` } });
  if (response.status === 401) {
    onUnauthorized?.();
    throw new Error("Sessão expirada");
  }
  if (!response.ok) {
    throw new Error("Não foi possível carregar o plano.");
  }
  return response.json() as Promise<T>;
}

async function loadPlan(childId: string, accessToken: string, onUnauthorized?: () => void): Promise<Plan | null> {
  const response = await authorizedFetch(`/api/v1/children/${childId}/custody-plan`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  if (response.status === 401) {
    onUnauthorized?.();
    throw new Error("Sessão expirada");
  }
  if (response.status === 404) {
    return null;
  }
  if (!response.ok) {
    throw new Error("Não foi possível carregar o plano.");
  }
  return response.json() as Promise<Plan>;
}

export function CustodyPlanPage({ accessToken, today = brazilToday(), onUnauthorized }: PlanProps) {
  const [client] = useState(() => new QueryClient({ defaultOptions: { queries: { retry: false } } }));
  return (
    <QueryClientProvider client={client}>
      <PlanContent accessToken={accessToken} today={today} onUnauthorized={onUnauthorized} />
    </QueryClientProvider>
  );
}

function PlanContent({ accessToken, today, onUnauthorized }: PlanProps & { today: string }) {
  const client = useQueryClient();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const children = useQuery({
    queryKey: ["children", accessToken],
    queryFn: () => getJson<Child[]>("/api/v1/children", accessToken, onUnauthorized),
  });
  const child = children.data?.find((item) => item.id === selectedId) ?? children.data?.[0];
  const plan = useQuery({
    queryKey: ["custody-plan", accessToken, child?.id],
    enabled: Boolean(child),
    queryFn: () => loadPlan(child?.id ?? "", accessToken, onUnauthorized),
  });

  if (children.isError || plan.isError) {
    return (
      <main className="panel">
        <p role="alert">Não foi possível carregar o plano.</p>
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
  if (!child || plan.isPending) {
    return null;
  }

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
      {plan.data ? (
        <PlanView plan={plan.data} guardians={child.guardians} />
      ) : (
        <PlanForm
          key={child.id}
          child={child}
          today={today}
          accessToken={accessToken}
          onUnauthorized={onUnauthorized}
          onSaved={(saved) => client.setQueryData(["custody-plan", accessToken, child.id], saved)}
        />
      )}
    </main>
  );
}

function PlanView({ plan, guardians }: { plan: Plan; guardians: Guardian[] }) {
  const names = new Map(guardians.map((guardian) => [guardian.id, guardian.name]));
  const rule = ruleOf(plan.rule.ruleType);
  const period = plan.endDate
    ? `${formatDate(plan.startDate)} a ${formatDate(plan.endDate)}`
    : formatDate(plan.startDate);
  return (
    <section aria-label="Plano ativo">
      <h2>{plan.name}</h2>
      <p>{period}</p>
      <p>{rule.label}</p>
      {rule.fields.map((field) => (
        <p key={field.key}>
          {field.label}: {names.get(plan.rule.configuration[field.key]) ?? "Responsável"}
        </p>
      ))}
    </section>
  );
}

function PlanForm({
  child,
  today,
  accessToken,
  onUnauthorized,
  onSaved,
}: {
  child: Child;
  today: string;
  accessToken: string;
  onUnauthorized?: () => void;
  onSaved: (plan: Plan) => void;
}) {
  const [name, setName] = useState("");
  const [startDate, setStartDate] = useState(today);
  const [endDate, setEndDate] = useState("");
  const [ruleType, setRuleType] = useState("ALTERNATE_DAYS");
  const [configuration, setConfiguration] = useState<Record<string, string>>(() =>
    emptyConfiguration("ALTERNATE_DAYS", child.guardians),
  );
  const [error, setError] = useState("");
  const rule = ruleOf(ruleType);

  function changeRule(next: string) {
    setRuleType(next);
    setConfiguration(emptyConfiguration(next, child.guardians));
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (name.trim() === "") {
      setError("Informe o nome");
      return;
    }
    if (endDate && endDate < startDate) {
      setError("Data final anterior à inicial");
      return;
    }
    if (Object.values(configuration).some((id) => id === "")) {
      setError("Escolha um responsável");
      return;
    }
    setError("");
    try {
      const response = await authorizedFetch(`/api/v1/children/${child.id}/custody-plan`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${accessToken}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          name: name.trim(),
          startDate,
          endDate: endDate || null,
          ruleType,
          configuration,
        }),
      });
      if (response.status === 401) {
        onUnauthorized?.();
        return;
      }
      if (!response.ok) {
        const body = (await response.json().catch(() => ({}))) as { detail?: string; message?: string };
        setError(body.detail || body.message || "Não foi possível salvar o plano.");
        return;
      }
      onSaved((await response.json()) as Plan);
    } catch {
      setError("Não foi possível salvar o plano.");
    }
  }

  return (
    <form onSubmit={onSubmit}>
      <label>
        Nome
        <input value={name} onChange={(event) => setName(event.target.value)} />
      </label>
      <label>
        Início
        <input type="date" value={startDate} onChange={(event) => setStartDate(event.target.value)} />
      </label>
      <label>
        Fim
        <input type="date" value={endDate} onChange={(event) => setEndDate(event.target.value)} />
      </label>
      <label>
        Regra
        <select value={ruleType} onChange={(event) => changeRule(event.target.value)}>
          {RULES.map((item) => (
            <option key={item.type} value={item.type}>
              {item.label}
            </option>
          ))}
        </select>
      </label>
      {rule.fields.map((field) => (
        <label key={field.key}>
          {field.label}
          <select
            value={configuration[field.key] ?? ""}
            onChange={(event) =>
              setConfiguration((current) => ({ ...current, [field.key]: event.target.value }))
            }
          >
            {child.guardians.map((guardian) => (
              <option key={guardian.id} value={guardian.id}>
                {guardian.name}
              </option>
            ))}
          </select>
        </label>
      ))}
      <button type="submit">Salvar plano</button>
      {error ? <p role="alert">{error}</p> : null}
    </form>
  );
}
