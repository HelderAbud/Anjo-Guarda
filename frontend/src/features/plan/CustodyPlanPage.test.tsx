import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, test, vi } from "vitest";
import { CustodyPlanPage } from "./CustodyPlanPage";

const child = {
  id: "child-1",
  name: "Lia",
  guardians: [
    { id: "g1", name: "Ana" },
    { id: "g2", name: "Bruno" },
  ],
};

test("saves alternate days when there is no plan", async () => {
  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    if (url === "/api/v1/children") {
      return { ok: true, status: 200, json: async () => [child] };
    }
    if (url === "/api/v1/children/child-1/custody-plan" && init?.method !== "POST") {
      return { ok: false, status: 404, json: async () => ({ detail: "Plano não encontrado" }) };
    }
    if (url === "/api/v1/children/child-1/custody-plan" && init?.method === "POST") {
      const body = JSON.parse(String(init.body));
      return {
        ok: true,
        status: 201,
        json: async () => ({
          id: "plan-1",
          childId: "child-1",
          name: body.name,
          startDate: body.startDate,
          endDate: body.endDate,
          active: true,
          rule: {
            id: "rule-1",
            ruleType: body.ruleType,
            startDate: body.startDate,
            endDate: body.endDate,
            configuration: body.configuration,
          },
        }),
      };
    }
    throw new Error(`${init?.method ?? "GET"} ${url}`);
  });
  vi.stubGlobal("fetch", fetchMock);
  const user = userEvent.setup();
  render(<CustodyPlanPage accessToken="access-1" today="2026-10-14" />);

  await user.type(await screen.findByLabelText("Nome"), "Dias da Lia");
  await user.selectOptions(screen.getByLabelText("Alterna com"), "g2");
  await user.click(screen.getByRole("button", { name: "Salvar plano" }));

  const call = fetchMock.mock.calls.find((item) => item[1]?.method === "POST");
  expect(JSON.parse(String(call?.[1]?.body))).toEqual({
    name: "Dias da Lia",
    startDate: "2026-10-14",
    endDate: null,
    ruleType: "ALTERNATE_DAYS",
    configuration: { start: "g1", alternate: "g2" },
  });
  expect(await screen.findByRole("heading", { name: "Dias da Lia" })).toBeTruthy();
  expect(screen.getByText("Dias alternados")).toBeTruthy();
  expect(screen.getByText("Começa com: Ana")).toBeTruthy();
  expect(screen.getByText("Alterna com: Bruno")).toBeTruthy();
  expect(screen.queryByRole("button", { name: "Salvar plano" })).toBeNull();
});

test("shows the active plan and does not offer another save", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string) => {
      if (url === "/api/v1/children") {
        return { ok: true, status: 200, json: async () => [child] };
      }
      if (url === "/api/v1/children/child-1/custody-plan") {
        return {
          ok: true,
          status: 200,
          json: async () => ({
            id: "plan-1",
            name: "Fins de semana",
            startDate: "2026-03-02",
            endDate: "2026-12-31",
            rule: {
              ruleType: "WEEKENDS",
              configuration: { weekday: "g1", weekend: "g2" },
            },
          }),
        };
      }
      throw new Error(url);
    }),
  );
  render(<CustodyPlanPage accessToken="access-1" today="2026-10-14" />);
  expect(await screen.findByRole("heading", { name: "Fins de semana" })).toBeTruthy();
  expect(screen.getByText("02/03/2026 a 31/12/2026")).toBeTruthy();
  expect(screen.getByText("Dias de semana: Ana")).toBeTruthy();
  expect(screen.getByText("Fim de semana: Bruno")).toBeTruthy();
  expect(screen.queryByRole("button", { name: "Salvar plano" })).toBeNull();
});

test("does not send a plan that ends before it starts", async () => {
  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    if (url === "/api/v1/children") {
      return { ok: true, status: 200, json: async () => [child] };
    }
    if (url === "/api/v1/children/child-1/custody-plan") {
      return { ok: false, status: 404, json: async () => ({}) };
    }
    throw new Error(url);
  });
  vi.stubGlobal("fetch", fetchMock);
  const user = userEvent.setup();
  render(<CustodyPlanPage accessToken="access-1" today="2026-10-14" />);
  await user.type(await screen.findByLabelText("Nome"), "Curto");
  await user.clear(screen.getByLabelText("Início"));
  await user.type(screen.getByLabelText("Início"), "2026-10-20");
  await user.type(screen.getByLabelText("Fim"), "2026-10-01");
  await user.click(screen.getByRole("button", { name: "Salvar plano" }));
  expect(screen.getByText("Data final anterior à inicial")).toBeTruthy();
  expect(fetchMock.mock.calls.some((call) => call[1]?.method === "POST")).toBe(false);
});

test("sends weekend guardians when that rule is chosen", async () => {
  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    if (url === "/api/v1/children") {
      return { ok: true, status: 200, json: async () => [child] };
    }
    if (url === "/api/v1/children/child-1/custody-plan" && init?.method !== "POST") {
      return { ok: false, status: 404, json: async () => ({}) };
    }
    if (init?.method === "POST") {
      const body = JSON.parse(String(init.body));
      return {
        ok: true,
        status: 201,
        json: async () => ({
          id: "plan-2",
          name: body.name,
          startDate: body.startDate,
          endDate: body.endDate,
          rule: { ruleType: body.ruleType, configuration: body.configuration },
        }),
      };
    }
    throw new Error(url);
  });
  vi.stubGlobal("fetch", fetchMock);
  const user = userEvent.setup();
  render(<CustodyPlanPage accessToken="access-1" today="2026-10-14" />);
  await user.type(await screen.findByLabelText("Nome"), "Fim de semana");
  await user.selectOptions(screen.getByLabelText("Regra"), "WEEKENDS");
  await user.selectOptions(screen.getByLabelText("Fim de semana"), "g2");
  await user.click(screen.getByRole("button", { name: "Salvar plano" }));
  const call = fetchMock.mock.calls.find((item) => item[1]?.method === "POST");
  expect(JSON.parse(String(call?.[1]?.body)).configuration).toEqual({
    weekday: "g1",
    weekend: "g2",
  });
});
