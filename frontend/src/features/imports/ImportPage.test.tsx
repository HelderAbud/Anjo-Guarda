import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, test, vi } from "vitest";
import { ImportPage } from "./ImportPage";

const child = {
  id: "child-1",
  name: "Lia",
  guardians: [{ id: "g1", name: "Ana" }],
};

const extracted = {
  id: "imp-1",
  childId: "child-1",
  filename: "revisao.docx",
  sourceType: "DOCX",
  status: "EXTRAIDO",
  extractedText: "texto solto\nOBSERVACAO 2026-03-04 ESCOLA Reuniao da escola",
  items: [
    {
      id: "item-1",
      type: "OBSERVACAO",
      proposedData: { date: "2026-03-04", category: "ESCOLA", text: "Reuniao da escola" },
      confidence: 1,
      conflict: false,
      approved: null,
      resolution: null,
      resultingEntityType: null,
      resultingEntityId: null,
    },
  ],
};

test("shows the extracted item and approves it without a resolution", async () => {
  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    if (url === "/api/v1/children") {
      return { ok: true, status: 200, json: async () => [child] };
    }
    if (url === "/api/v1/imports" && init?.method === "POST") {
      return { ok: true, status: 201, json: async () => extracted };
    }
    if (url === "/api/v1/imports/imp-1/approve" && init?.method === "POST") {
      return {
        ok: true,
        status: 200,
        json: async () => ({
          ...extracted,
          status: "REVISADO",
          items: [{ ...extracted.items[0], approved: true, resultingEntityType: "daily_note" }],
        }),
      };
    }
    throw new Error(`${init?.method ?? "GET"} ${url}`);
  });
  vi.stubGlobal("fetch", fetchMock);
  const user = userEvent.setup();
  render(<ImportPage accessToken="access-1" />);

  await user.upload(
    await screen.findByLabelText("Arquivo"),
    new File(["docx"], "revisao.docx", {
      type: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    }),
  );
  await user.click(screen.getByRole("button", { name: "Enviar" }));

  expect(await screen.findByText("texto solto")).toBeTruthy();
  expect(screen.getByText("Reuniao da escola")).toBeTruthy();
  await user.click(screen.getByRole("radio", { name: "Aprovar" }));
  await user.click(screen.getByRole("button", { name: "Concluir revisão" }));

  const sent = fetchMock.mock.calls.find((call) => String(call[0]).endsWith("/approve"));
  expect(JSON.parse(String(sent?.[1]?.body))).toEqual({
    decisions: [{ itemId: "item-1", approved: true }],
  });
  expect(await screen.findByText("Revisado")).toBeTruthy();
  expect(screen.queryByRole("button", { name: "Concluir revisão" })).toBeNull();
});

test("requires a resolution before approving a conflict", async () => {
  const conflicted = {
    ...extracted,
    id: "imp-2",
    items: [{ ...extracted.items[0], conflict: true }],
  };
  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    if (url === "/api/v1/children") {
      return { ok: true, status: 200, json: async () => [child] };
    }
    if (url === "/api/v1/imports" && init?.method === "POST") {
      return { ok: true, status: 201, json: async () => conflicted };
    }
    if (url === "/api/v1/imports/imp-2/approve" && init?.method === "POST") {
      return { ok: true, status: 200, json: async () => ({ ...conflicted, status: "REVISADO" }) };
    }
    throw new Error(`${init?.method ?? "GET"} ${url}`);
  });
  vi.stubGlobal("fetch", fetchMock);
  const user = userEvent.setup();
  render(<ImportPage accessToken="access-1" />);
  await user.upload(
    await screen.findByLabelText("Arquivo"),
    new File(["docx"], "conflito.docx", {
      type: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    }),
  );
  await user.click(screen.getByRole("button", { name: "Enviar" }));
  await user.click(await screen.findByRole("radio", { name: "Aprovar" }));
  expect(screen.getByRole("option", { name: "Mesclar" })).toBeTruthy();
  await user.click(screen.getByRole("button", { name: "Concluir revisão" }));
  expect(screen.getByText("Conflito exige resolução")).toBeTruthy();
  expect(fetchMock.mock.calls.some((call) => String(call[0]).endsWith("/approve"))).toBe(false);

  await user.selectOptions(screen.getByLabelText("Resolução"), "MESCLAR");
  await user.click(screen.getByRole("button", { name: "Concluir revisão" }));
  const sent = fetchMock.mock.calls.find((call) => String(call[0]).endsWith("/approve"));
  expect(JSON.parse(String(sent?.[1]?.body))).toEqual({
    decisions: [{ itemId: "item-1", approved: true, resolution: "MESCLAR" }],
  });
});

test("drops the open review when another child is chosen", async () => {
  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    if (url === "/api/v1/children") {
      return {
        ok: true,
        status: 200,
        json: async () => [child, { id: "child-2", name: "Noah", guardians: [] }],
      };
    }
    if (url === "/api/v1/imports" && init?.method === "POST") {
      return { ok: true, status: 201, json: async () => extracted };
    }
    throw new Error(`${init?.method ?? "GET"} ${url}`);
  });
  vi.stubGlobal("fetch", fetchMock);
  const user = userEvent.setup();
  render(<ImportPage accessToken="access-1" />);
  await user.upload(
    await screen.findByLabelText("Arquivo"),
    new File(["docx"], "revisao.docx", {
      type: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    }),
  );
  await user.click(screen.getByRole("button", { name: "Enviar" }));
  expect(await screen.findByRole("region", { name: "Revisão" })).toBeTruthy();
  await user.selectOptions(screen.getByLabelText("Criança"), "child-2");
  expect(screen.getByRole("heading", { name: "Noah" })).toBeTruthy();
  expect(screen.queryByRole("region", { name: "Revisão" })).toBeNull();
});
