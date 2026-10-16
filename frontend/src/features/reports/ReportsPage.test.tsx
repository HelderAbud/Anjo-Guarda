import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, test, vi } from "vitest";
import { ReportsPage } from "./ReportsPage";

const child = {
  id: "child-1",
  name: "Lia",
  guardians: [{ id: "g1", name: "Ana" }],
};

const snapshot = {
  childId: "child-1",
  childName: "Lia",
  year: 2026,
  month: 10,
  counts: { PLANEJADO: 0, REALIZADO: 1, ALTERADO: 0, NAO_REALIZADO: 0 },
  days: [
    {
      date: "2026-10-15",
      weekday: "THURSDAY",
      guardianId: "g1",
      status: "REALIZADO",
      exception: false,
      realizedGuardianId: "g1",
      realizedStartTime: null,
      realizedEndTime: null,
      observation: { category: "ESCOLA", text: "Reuniao" },
    },
  ],
};

const generated = {
  id: "report-1",
  childId: "child-1",
  year: 2026,
  month: 10,
  version: 1,
  generatedAt: "2026-10-16T15:00:00Z",
  contentSnapshot: snapshot,
};

test("generates the month and shows that snapshot", async () => {
  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    if (url === "/api/v1/children") {
      return { ok: true, status: 200, json: async () => [child] };
    }
    if (url.startsWith("/api/v1/reports/monthly?") && !init?.method) {
      return { ok: true, status: 200, json: async () => [] };
    }
    if (url.startsWith("/api/v1/reports/monthly?") && init?.method === "POST") {
      return { ok: true, status: 201, json: async () => generated };
    }
    throw new Error(`${init?.method ?? "GET"} ${url}`);
  });
  vi.stubGlobal("fetch", fetchMock);
  const user = userEvent.setup();
  render(<ReportsPage accessToken="access-1" today="2026-10-16" />);

  expect(await screen.findByText("Nenhum relatório neste mês")).toBeTruthy();
  await user.click(screen.getByRole("button", { name: "Gerar" }));

  const sent = fetchMock.mock.calls.find((call) => call[1]?.method === "POST");
  const target = String(sent?.[0]);
  expect(target).toContain("/api/v1/reports/monthly?");
  expect(target).toContain("childId=child-1");
  expect(target).toContain("year=2026");
  expect(target).toContain("month=10");
  expect(sent?.[1]?.body).toBeUndefined();
  expect(await screen.findByText("Reuniao")).toBeTruthy();
  expect(screen.getByText("Lia")).toBeTruthy();
  expect(screen.getByText("Versão 1")).toBeTruthy();
  expect(screen.getByText("Realizado 1")).toBeTruthy();
  expect(screen.queryByText("Nenhum relatório neste mês")).toBeNull();
});

test("downloads the version that was opened", async () => {
  const older = {
    ...generated,
    contentSnapshot: {
      ...snapshot,
      days: [{ ...snapshot.days[0], observation: { category: "ESCOLA", text: "Antiga" } }],
    },
  };
  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    if (url === "/api/v1/children") {
      return { ok: true, status: 200, json: async () => [child] };
    }
    if (url.startsWith("/api/v1/reports/monthly?") && !init?.method) {
      return {
        ok: true,
        status: 200,
        json: async () => [
          { id: "report-1", version: 1, generatedAt: older.generatedAt },
          { id: "report-2", version: 2, generatedAt: older.generatedAt },
        ],
      };
    }
    if (url === "/api/v1/reports/monthly/report-1") {
      return { ok: true, status: 200, json: async () => older };
    }
    if (url === "/api/v1/reports/monthly/report-1/pdf") {
      return { ok: true, status: 200, blob: async () => new Blob(["pdf"]) };
    }
    throw new Error(`${init?.method ?? "GET"} ${url}`);
  });
  vi.stubGlobal("fetch", fetchMock);
  URL.createObjectURL = vi.fn(() => "blob:report");
  URL.revokeObjectURL = vi.fn();
  const user = userEvent.setup();
  render(<ReportsPage accessToken="access-1" today="2026-10-16" />);

  await user.click(await screen.findByRole("button", { name: "Versão 1" }));
  expect(await screen.findByText("Antiga")).toBeTruthy();
  await user.click(screen.getByRole("button", { name: "Baixar PDF" }));

  expect(fetchMock.mock.calls.some((call) => String(call[0]).endsWith("/report-1/pdf"))).toBe(true);
  expect(fetchMock.mock.calls.some((call) => String(call[0]).includes("report-2"))).toBe(false);
  expect(fetchMock.mock.calls.some((call) => call[1]?.method === "POST")).toBe(false);
});

test("clears the open report when the month changes", async () => {
  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    if (url === "/api/v1/children") {
      return { ok: true, status: 200, json: async () => [child] };
    }
    if (url.startsWith("/api/v1/reports/monthly?") && !init?.method) {
      return {
        ok: true,
        status: 200,
        json: async () => [{ id: "report-1", version: 1, generatedAt: generated.generatedAt }],
      };
    }
    if (url === "/api/v1/reports/monthly/report-1") {
      return { ok: true, status: 200, json: async () => generated };
    }
    throw new Error(`${init?.method ?? "GET"} ${url}`);
  });
  vi.stubGlobal("fetch", fetchMock);
  const user = userEvent.setup();
  render(<ReportsPage accessToken="access-1" today="2026-10-16" />);
  await user.click(await screen.findByRole("button", { name: "Versão 1" }));
  expect(await screen.findByRole("region", { name: "Resumo" })).toBeTruthy();
  fireEvent.change(screen.getByLabelText("Mês"), { target: { value: "2026-09" } });
  expect(screen.queryByRole("region", { name: "Resumo" })).toBeNull();
  expect(fetchMock.mock.calls.some((call) => String(call[0]).includes("month=9"))).toBe(true);
});
