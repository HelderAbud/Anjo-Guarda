import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, test, vi } from "vitest";
import { DashboardPage } from "./DashboardPage";

const child = {
  id: "child-1",
  name: "Lia",
  birthDate: "2020-01-01",
  guardians: [{ id: "g1", name: "Ana", relationship: "MAE", userId: null }],
};

function day(date: string, status: string, guardianId: string | null) {
  return {
    date,
    weekday: "WEDNESDAY",
    guardianId,
    exception: false,
    status,
    realizedGuardianId: status === "REALIZADO" ? guardianId : null,
  };
}

test("counts the current month by status and skips days without a guardian", async () => {
  const fetchMock = vi.fn(async (url: string) => {
    if (url === "/api/v1/children") {
      return { ok: true, status: 200, json: async () => [child] };
    }
    if (url.startsWith("/api/v1/children/child-1/calendar?")) {
      if (!url.includes("month=10")) {
        return { ok: true, status: 200, json: async () => ({ days: [] }) };
      }
      return {
        ok: true,
        status: 200,
        json: async () => ({
          childId: "child-1",
          year: 2026,
          month: 10,
          timeZone: "America/Sao_Paulo",
          days: [
            day("2026-10-01", "PLANEJADO", null),
            day("2026-10-14", "PLANEJADO", "g1"),
            day("2026-10-15", "REALIZADO", "g1"),
          ],
        }),
      };
    }
    if (url.startsWith("/api/v1/notes?")) {
      return { ok: true, status: 200, json: async () => [] };
    }
    throw new Error(url);
  });
  vi.stubGlobal("fetch", fetchMock);

  render(<DashboardPage accessToken="access-1" today="2026-10-14" />);

  expect(await screen.findByRole("heading", { name: "Lia" })).toBeTruthy();
  expect(screen.getByText("PLANEJADO 1")).toBeTruthy();
  expect(screen.getByText("REALIZADO 1")).toBeTruthy();
  expect(screen.getByText("ALTERADO 0")).toBeTruthy();
  expect(screen.getByText("NAO_REALIZADO 0")).toBeTruthy();
  expect(fetchMock).toHaveBeenCalledWith(
    "/api/v1/children",
    expect.objectContaining({ headers: expect.objectContaining({ Authorization: "Bearer access-1" }) }),
  );
});

test("lists the next seven days that have a guardian", async () => {
  const lia = {
    ...child,
    guardians: [
      { id: "g1", name: "Ana", relationship: "MAE", userId: null },
      { id: "g2", name: "Bruno", relationship: "PAI", userId: null },
    ],
  };
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string) => {
      if (url === "/api/v1/children") {
        return { ok: true, status: 200, json: async () => [lia] };
      }
      if (url.includes("month=10")) {
        return {
          ok: true,
          status: 200,
          json: async () => ({
            days: [
              day("2026-10-29", "PLANEJADO", "g1"),
              day("2026-10-30", "PLANEJADO", "g1"),
              day("2026-10-31", "PLANEJADO", null),
            ],
          }),
        };
      }
      if (url.includes("month=11")) {
        return {
          ok: true,
          status: 200,
          json: async () => ({
            days: [
              day("2026-11-01", "PLANEJADO", "g2"),
              day("2026-11-02", "PLANEJADO", "g1"),
              day("2026-11-03", "PLANEJADO", "g2"),
              day("2026-11-04", "PLANEJADO", "g1"),
              day("2026-11-05", "PLANEJADO", "g2"),
              day("2026-11-06", "PLANEJADO", "g1"),
              day("2026-11-07", "PLANEJADO", "g2"),
            ],
          }),
        };
      }
      if (url.startsWith("/api/v1/notes?")) {
        return { ok: true, status: 200, json: async () => [] };
      }
      throw new Error(url);
    }),
  );

  render(<DashboardPage accessToken="access-1" today="2026-10-30" />);

  expect(await screen.findByText("30/10/2026 Ana")).toBeTruthy();
  expect(screen.getByText("01/11/2026 Bruno")).toBeTruthy();
  expect(screen.getByText("06/11/2026 Ana")).toBeTruthy();
  expect(screen.queryByText("29/10/2026 Ana")).toBeNull();
  expect(screen.queryByText(/31\/10\/2026/)).toBeNull();
  expect(screen.queryByText("07/11/2026 Bruno")).toBeNull();
});

test("shows the five latest observations from the last 90 days", async () => {
  let notesUrl = "";
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string) => {
      if (url === "/api/v1/children") {
        return { ok: true, status: 200, json: async () => [child] };
      }
      if (url.includes("/calendar?")) {
        if (!url.includes("month=10")) {
          return { ok: true, status: 200, json: async () => ({ days: [] }) };
        }
        return { ok: true, status: 200, json: async () => ({ days: [day("2026-10-14", "PLANEJADO", "g1")] }) };
      }
      if (url.startsWith("/api/v1/notes?")) {
        notesUrl = url;
        return {
          ok: true,
          status: 200,
          json: async () => [
            { id: "n1", date: "2026-07-20", category: "ROTINA", text: "Antiga" },
            { id: "n2", date: "2026-08-01", category: "ESCOLA", text: "Uma" },
            { id: "n3", date: "2026-08-02", category: "ATIVIDADE", text: "Duas" },
            { id: "n4", date: "2026-09-01", category: "SAUDE", text: "Tres" },
            { id: "n5", date: "2026-09-02", category: "VIAGEM", text: "Quatro" },
            { id: "n6", date: "2026-10-10", category: "OUTRO", text: "Recente" },
          ],
        };
      }
      throw new Error(url);
    }),
  );

  render(<DashboardPage accessToken="access-1" today="2026-10-14" />);

  expect(await screen.findByText("10/10/2026 OUTRO Recente")).toBeTruthy();
  expect(screen.getByText("01/08/2026 ESCOLA Uma")).toBeTruthy();
  expect(screen.queryByText("20/07/2026 ROTINA Antiga")).toBeNull();
  expect(notesUrl).toContain("childId=child-1");
  expect(notesUrl).toContain("from=2026-07-16");
  expect(notesUrl).toContain("to=2026-10-14");
});

test("does not load the calendar when there is no child", async () => {
  const fetchMock = vi.fn(async (url: string) => {
    if (url === "/api/v1/children") {
      return { ok: true, status: 200, json: async () => [] };
    }
    throw new Error(url);
  });
  vi.stubGlobal("fetch", fetchMock);

  render(<DashboardPage accessToken="access-1" today="2026-10-14" />);

  expect(await screen.findByText("Nenhuma criança cadastrada")).toBeTruthy();
  expect(fetchMock).toHaveBeenCalledTimes(1);
});

test("switches the month summary to the chosen child", async () => {
  const noah = {
    id: "child-2",
    name: "Noah",
    birthDate: "2021-02-02",
    guardians: [{ id: "g9", name: "Caio", relationship: "PAI", userId: null }],
  };
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string) => {
      if (url === "/api/v1/children") {
        return { ok: true, status: 200, json: async () => [child, noah] };
      }
      if (url.includes("/calendar?")) {
        if (!url.includes("month=10")) {
          return { ok: true, status: 200, json: async () => ({ days: [] }) };
        }
        const guardianId = url.includes("child-2") ? "g9" : "g1";
        return {
          ok: true,
          status: 200,
          json: async () => ({ days: [day("2026-10-14", "PLANEJADO", guardianId)] }),
        };
      }
      if (url.startsWith("/api/v1/notes?")) {
        return { ok: true, status: 200, json: async () => [] };
      }
      throw new Error(url);
    }),
  );
  const user = userEvent.setup();
  render(<DashboardPage accessToken="access-1" today="2026-10-14" />);

  expect(await screen.findByRole("heading", { name: "Lia" })).toBeTruthy();
  await user.selectOptions(screen.getByLabelText("Criança"), "child-2");
  expect(await screen.findByRole("heading", { name: "Noah" })).toBeTruthy();
  expect(screen.getByText("14/10/2026 Caio")).toBeTruthy();
});

test("leaves the session when a read is rejected", async () => {
  const onUnauthorized = vi.fn();
  vi.stubGlobal(
    "fetch",
    vi.fn(async () => ({ ok: false, status: 401, json: async () => ({}) })),
  );

  render(<DashboardPage accessToken="access-1" today="2026-10-14" onUnauthorized={onUnauthorized} />);

  await waitFor(() => expect(onUnauthorized).toHaveBeenCalled());
});
