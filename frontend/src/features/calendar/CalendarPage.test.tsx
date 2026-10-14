import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, test, vi } from "vitest";
import { CalendarPage } from "./CalendarPage";

const child = {
  id: "child-1",
  name: "Lia",
  birthDate: "2020-01-01",
  guardians: [
    { id: "g1", name: "Ana", relationship: "MAE", userId: null },
    { id: "g2", name: "Bruno", relationship: "PAI", userId: null },
  ],
};

function day(
  date: string,
  status: string,
  guardianId: string | null,
  exception = false,
) {
  return { date, weekday: "THURSDAY", guardianId, exception, status, realizedGuardianId: null };
}

test("places the first day on its weekday column", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string) => {
      if (url === "/api/v1/children") {
        return { ok: true, status: 200, json: async () => [child] };
      }
      if (url.includes("/calendar?")) {
        return {
          ok: true,
          status: 200,
          json: async () => ({
            days: [day("2026-10-01", "PLANEJADO", null), day("2026-10-02", "PLANEJADO", "g1")],
          }),
        };
      }
      if (url.startsWith("/api/v1/notes?")) {
        return { ok: true, status: 200, json: async () => [] };
      }
      throw new Error(url);
    }),
  );

  render(<CalendarPage accessToken="access-1" today="2026-10-14" />);

  expect(await screen.findByRole("heading", { name: "outubro de 2026" })).toBeTruthy();
  const headers = screen.getAllByRole("columnheader").map((cell) => cell.textContent);
  expect(headers).toEqual(["Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom"]);
  const cells = screen.getAllByRole("gridcell");
  expect(cells[0].textContent).toBe("");
  expect(cells[2].textContent).toBe("");
  expect(cells[3].textContent).toContain("1");
  expect(cells[4].textContent).toContain("Ana");
  expect(cells[4].textContent).toContain("PLANEJADO");
  expect(screen.getByRole("list", { name: "Legenda" }).textContent).toContain("REALIZADO");
  expect(screen.getByRole("list", { name: "Legenda" }).textContent).toContain("exceção");
});

test("moves between months and returns to today", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string) => {
      if (url === "/api/v1/children") {
        return { ok: true, status: 200, json: async () => [child] };
      }
      if (url.includes("/calendar?")) {
        const month = new URL("http://local" + url).searchParams.get("month");
        const date = month === "11" ? "2026-11-01" : "2026-10-01";
        return { ok: true, status: 200, json: async () => ({ days: [day(date, "PLANEJADO", "g1")] }) };
      }
      if (url.startsWith("/api/v1/notes?")) {
        return { ok: true, status: 200, json: async () => [] };
      }
      throw new Error(url);
    }),
  );
  const user = userEvent.setup();
  render(<CalendarPage accessToken="access-1" today="2026-10-14" />);

  expect(await screen.findByRole("heading", { name: "outubro de 2026" })).toBeTruthy();
  await user.click(screen.getByRole("button", { name: "Próximo mês" }));
  expect(await screen.findByRole("heading", { name: "novembro de 2026" })).toBeTruthy();
  await user.click(screen.getByRole("button", { name: "Hoje" }));
  expect(await screen.findByRole("heading", { name: "outubro de 2026" })).toBeTruthy();
});

test("marks an exception and opens the day with its note", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string) => {
      if (url === "/api/v1/children") {
        return { ok: true, status: 200, json: async () => [child] };
      }
      if (url.includes("/calendar?")) {
        return {
          ok: true,
          status: 200,
          json: async () => ({
            days: [day("2026-10-03", "REALIZADO", "g2", true)],
          }),
        };
      }
      if (url.startsWith("/api/v1/notes?")) {
        expect(url).toContain("from=2026-10-01");
        expect(url).toContain("to=2026-10-31");
        return {
          ok: true,
          status: 200,
          json: async () => [{ id: "n1", date: "2026-10-03", category: "SAUDE", text: "Consulta" }],
        };
      }
      throw new Error(url);
    }),
  );
  const user = userEvent.setup();
  render(<CalendarPage accessToken="access-1" today="2026-10-14" />);

  const dayButton = await screen.findByRole("button", { name: /2026-10-03|3/ });
  expect(dayButton.textContent).toContain("exceção");
  expect(dayButton.textContent).toContain("observação");
  expect(screen.getByText("Bruno 1")).toBeTruthy();
  expect(screen.getByText("exceções 1")).toBeTruthy();
  expect(screen.getByText("observações 1")).toBeTruthy();
  await user.click(dayButton);
  const detail = screen.getByRole("region", { name: "Dia selecionado" });
  expect(detail.textContent).toContain("03/10/2026");
  expect(detail.textContent).toContain("Bruno");
  expect(detail.textContent).toContain("REALIZADO");
  expect(screen.getByLabelText("Observação")).toHaveProperty("value", "Consulta");
  expect(screen.getByLabelText("Categoria")).toHaveProperty("value", "SAUDE");
});

test("does not load the calendar when there is no child", async () => {
  const fetchMock = vi.fn(async (url: string) => {
    if (url === "/api/v1/children") {
      return { ok: true, status: 200, json: async () => [] };
    }
    throw new Error(url);
  });
  vi.stubGlobal("fetch", fetchMock);
  render(<CalendarPage accessToken="access-1" today="2026-10-14" />);
  expect(await screen.findByText("Nenhuma criança cadastrada")).toBeTruthy();
  expect(fetchMock).toHaveBeenCalledTimes(1);
});

test("saves a confirmation and a new observation", async () => {
  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    if (url === "/api/v1/children") {
      return { ok: true, status: 200, json: async () => [child] };
    }
    if (String(url).includes("/calendar?")) {
      return {
        ok: true,
        status: 200,
        json: async () => ({ days: [day("2026-10-02", "PLANEJADO", "g1")] }),
      };
    }
    if (String(url).startsWith("/api/v1/notes?")) {
      return { ok: true, status: 200, json: async () => [] };
    }
    if (String(url) === "/api/v1/calendar/confirmations" && init?.method === "POST") {
      return { ok: true, status: 201, json: async () => ({}) };
    }
    if (String(url) === "/api/v1/notes" && init?.method === "POST") {
      return { ok: true, status: 201, json: async () => ({ id: "n9" }) };
    }
    throw new Error(`${init?.method ?? "GET"} ${url}`);
  });
  vi.stubGlobal("fetch", fetchMock);
  const user = userEvent.setup();
  render(<CalendarPage accessToken="access-1" today="2026-10-14" />);
  await user.click(await screen.findByRole("button", { name: /Ana/ }));
  await user.click(screen.getByRole("button", { name: "Salvar confirmação" }));
  await user.selectOptions(screen.getByLabelText("Categoria"), "ESCOLA");
  await user.type(screen.getByLabelText("Observação"), "Lanche");
  await user.click(screen.getByRole("button", { name: "Salvar observação" }));

  const confirmation = fetchMock.mock.calls.find((call) => String(call[0]) === "/api/v1/calendar/confirmations");
  expect(JSON.parse(String(confirmation?.[1]?.body))).toMatchObject({
    childId: "child-1",
    date: "2026-10-02",
    status: "REALIZADO",
  });
  expect(JSON.parse(String(confirmation?.[1]?.body)).realizedStartTime).toBeUndefined();
  const note = fetchMock.mock.calls.find((call) => call[0] === "/api/v1/notes" && call[1]?.method === "POST");
  expect(JSON.parse(String(note?.[1]?.body))).toMatchObject({
    childId: "child-1",
    date: "2026-10-02",
    category: "ESCOLA",
    text: "Lanche",
  });
});

test("does not send an alteration that matches the plan", async () => {
  const fetchMock = vi.fn(async (url: string) => {
    if (url === "/api/v1/children") {
      return { ok: true, status: 200, json: async () => [child] };
    }
    if (url.includes("/calendar?")) {
      return { ok: true, status: 200, json: async () => ({ days: [day("2026-10-02", "PLANEJADO", "g1")] }) };
    }
    if (url.startsWith("/api/v1/notes?")) {
      return { ok: true, status: 200, json: async () => [] };
    }
    throw new Error(url);
  });
  vi.stubGlobal("fetch", fetchMock);
  const user = userEvent.setup();
  render(<CalendarPage accessToken="access-1" today="2026-10-14" />);
  await user.click(await screen.findByRole("button", { name: /Ana/ }));
  await user.selectOptions(screen.getByLabelText("Status"), "ALTERADO");
  await user.click(screen.getByRole("button", { name: "Salvar confirmação" }));
  expect(screen.getByText("Aconteceu como planejado")).toBeTruthy();
  expect(fetchMock.mock.calls.some((call) => String(call[0]).includes("confirmations"))).toBe(false);
});
