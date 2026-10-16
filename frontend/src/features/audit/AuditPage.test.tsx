import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, test, vi } from "vitest";
import { AuditPage } from "./AuditPage";

const entries = [
  {
    id: "audit-1",
    action: "NOTE_CREATED",
    entityType: "daily_note",
    entityId: "note-1",
    createdAt: "2026-10-15T15:00:00Z",
  },
  {
    id: "audit-2",
    action: "REPORT_CREATED",
    entityType: "monthly_report",
    entityId: "report-1",
    createdAt: "2026-10-16T18:00:00Z",
  },
];

test("lists the month in the api order", async () => {
  const fetchMock = vi.fn(async (url: string) => {
    if (url.startsWith("/api/v1/audit?")) {
      return { ok: true, status: 200, json: async () => entries };
    }
    throw new Error(url);
  });
  vi.stubGlobal("fetch", fetchMock);
  render(<AuditPage accessToken="access-1" today="2026-10-16" />);

  expect(await screen.findByText("Observação criada")).toBeTruthy();
  const items = screen.getAllByRole("listitem").map((item) => item.textContent);
  expect(items[0]).toContain("Observação criada");
  expect(items[0]).toContain("Nota");
  expect(items[1]).toContain("Relatório gerado");
  expect(items[1]).toContain("Relatório mensal");
  expect(screen.queryByText("note-1")).toBeNull();
  const target = String(fetchMock.mock.calls[0]?.[0]);
  expect(target).toContain("/api/v1/audit?");
  expect(target).toContain("from=2026-10-01");
  expect(target).toContain("to=2026-10-31");
  expect(fetchMock.mock.calls.some((call) => String(call[0]).includes("/children"))).toBe(false);
});

test("does not ask for an inverted period", async () => {
  const fetchMock = vi.fn(async (url: string) => {
    if (url.startsWith("/api/v1/audit?")) {
      return { ok: true, status: 200, json: async () => [] };
    }
    throw new Error(url);
  });
  vi.stubGlobal("fetch", fetchMock);
  const user = userEvent.setup();
  render(<AuditPage accessToken="access-1" today="2026-10-16" />);
  expect(await screen.findByText("Nenhum registro no período")).toBeTruthy();
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
