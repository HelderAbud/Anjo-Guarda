import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, test, vi } from "vitest";
import { TimelinePage } from "./TimelinePage";

const child = {
  id: "child-1",
  name: "Lia",
  guardians: [
    { id: "g1", name: "Ana" },
    { id: "g2", name: "Bruno" },
  ],
};

const events = [
  { date: "2026-10-03", type: "EXCECAO", newGuardianId: "g2" },
  { date: "2026-10-04", type: "CONFIRMACAO", status: "REALIZADO" },
  { date: "2026-10-05", type: "OBSERVACAO", category: "SAUDE", text: "Consulta" },
  { date: "2026-10-05", type: "AUDITORIA", action: "NOTE_UPDATED" },
];

test("lists the month in order and filters by type", async () => {
  const fetchMock = vi.fn(async (url: string) => {
    if (url === "/api/v1/children") {
      return { ok: true, status: 200, json: async () => [child] };
    }
    if (url.startsWith("/api/v1/children/child-1/timeline")) {
      const type = new URL(url, "http://local").searchParams.get("type");
      return {
        ok: true,
        status: 200,
        json: async () => ({ events: type === "EXCECAO" ? [events[0]] : events }),
      };
    }
    throw new Error(url);
  });
  vi.stubGlobal("fetch", fetchMock);
  const user = userEvent.setup();
  render(<TimelinePage accessToken="access-1" today="2026-10-15" />);

  expect(await screen.findByText("03/10/2026")).toBeTruthy();
  const items = screen.getAllByRole("listitem").map((item) => item.textContent);
  expect(items.map((text) => text?.slice(0, 10))).toEqual([
    "03/10/2026",
    "04/10/2026",
    "05/10/2026",
    "05/10/2026",
  ]);
  expect(screen.getByText("Bruno")).toBeTruthy();
  expect(screen.getByText("REALIZADO")).toBeTruthy();
  expect(screen.getByText("Consulta")).toBeTruthy();
  expect(screen.getByText("NOTE_UPDATED")).toBeTruthy();
  expect(String(fetchMock.mock.calls.find((call) => String(call[0]).includes("timeline"))?.[0])).toContain(
    "from=2026-10-01&to=2026-10-31",
  );

  await user.selectOptions(screen.getByLabelText("Tipo"), "EXCECAO");
  expect(await screen.findByText("Bruno")).toBeTruthy();
  expect(screen.queryByText("Consulta")).toBeNull();
  expect(fetchMock.mock.calls.some((call) => String(call[0]).includes("type=EXCECAO"))).toBe(true);
});

test("does not ask for an inverted period", async () => {
  const fetchMock = vi.fn(async (url: string) => {
    if (url === "/api/v1/children") {
      return { ok: true, status: 200, json: async () => [child] };
    }
    if (url.startsWith("/api/v1/children/child-1/timeline")) {
      return { ok: true, status: 200, json: async () => ({ events: [] }) };
    }
    throw new Error(url);
  });
  vi.stubGlobal("fetch", fetchMock);
  const user = userEvent.setup();
  render(<TimelinePage accessToken="access-1" today="2026-10-15" />);
  expect(await screen.findByText("Nenhum evento no período")).toBeTruthy();
  await user.clear(screen.getByLabelText("De"));
  await user.type(screen.getByLabelText("De"), "2026-10-20");
  await user.clear(screen.getByLabelText("Até"));
  await user.type(screen.getByLabelText("Até"), "2026-10-01");
  expect(screen.getByText("Período inválido")).toBeTruthy();
  expect(
    fetchMock.mock.calls.some((call) => {
      const params = new URL(String(call[0]), "http://local").searchParams;
      const start = params.get("from");
      const end = params.get("to");
      return Boolean(start && end && start > end);
    }),
  ).toBe(false);
});
