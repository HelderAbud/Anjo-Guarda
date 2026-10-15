import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, test, vi } from "vitest";
import { DocumentsPage } from "./DocumentsPage";

const child = {
  id: "child-1",
  name: "Lia",
  guardians: [{ id: "g1", name: "Ana" }],
};

test("lists documents and uploads a file", async () => {
  const listed = [
    {
      id: "doc-1",
      childId: "child-1",
      category: "escola",
      filename: "acordo.pdf",
      mimeType: "application/pdf",
      size: 4,
    },
  ];
  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    if (url === "/api/v1/children") {
      return { ok: true, status: 200, json: async () => [child] };
    }
    if (url === "/api/v1/documents?childId=child-1" && init?.method !== "POST") {
      return { ok: true, status: 200, json: async () => listed.map((item) => ({ ...item })) };
    }
    if (url === "/api/v1/documents" && init?.method === "POST") {
      const body = init.body as FormData;
      const file = body.get("file") as File;
      listed.push({
        id: "doc-2",
        childId: String(body.get("childId")),
        category: String(body.get("category")),
        filename: file.name,
        mimeType: file.type,
        size: file.size,
      });
      return { ok: true, status: 201, json: async () => listed[1] };
    }
    throw new Error(`${init?.method ?? "GET"} ${url}`);
  });
  vi.stubGlobal("fetch", fetchMock);
  const user = userEvent.setup();
  render(<DocumentsPage accessToken="access-1" />);

  expect(await screen.findByText("acordo.pdf")).toBeTruthy();
  expect(screen.getByText("escola")).toBeTruthy();
  expect(screen.queryByText("storageKey")).toBeNull();

  await user.type(screen.getByLabelText("Categoria"), "saude");
  await user.upload(
    screen.getByLabelText("Arquivo"),
    new File(["%PDF"], "laudo.pdf", { type: "application/pdf" }),
  );
  await user.click(screen.getByRole("button", { name: "Enviar" }));

  expect(await screen.findByText("laudo.pdf")).toBeTruthy();
  const sent = fetchMock.mock.calls.find((call) => call[1]?.method === "POST");
  const body = sent?.[1]?.body as FormData;
  expect(body.get("childId")).toBe("child-1");
  expect(body.get("category")).toBe("saude");
  expect((body.get("file") as File).name).toBe("laudo.pdf");
  expect(String(sent?.[1]?.headers && new Headers(sent[1].headers).get("Authorization"))).toBe(
    "Bearer access-1",
  );
});

test("does not send an empty category", async () => {
  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    if (url === "/api/v1/children") {
      return { ok: true, status: 200, json: async () => [child] };
    }
    if (url.startsWith("/api/v1/documents?")) {
      return { ok: true, status: 200, json: async () => [] };
    }
    throw new Error(url);
  });
  vi.stubGlobal("fetch", fetchMock);
  const user = userEvent.setup();
  render(<DocumentsPage accessToken="access-1" />);
  expect(await screen.findByText("Nenhum documento")).toBeTruthy();
  await user.upload(
    screen.getByLabelText("Arquivo"),
    new File(["%PDF"], "acordo.pdf", { type: "application/pdf" }),
  );
  await user.click(screen.getByRole("button", { name: "Enviar" }));
  expect(screen.getByText("Categoria inválida")).toBeTruthy();
  expect(fetchMock.mock.calls.some((call) => call[1]?.method === "POST")).toBe(false);
});

test("filters the list by name", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string) => {
      if (url === "/api/v1/children") {
        return { ok: true, status: 200, json: async () => [child] };
      }
      if (url.startsWith("/api/v1/documents?")) {
        return {
          ok: true,
          status: 200,
          json: async () => [
            { id: "doc-1", childId: "child-1", category: "escola", filename: "acordo.pdf", mimeType: "application/pdf", size: 4 },
            { id: "doc-2", childId: "child-1", category: "saude", filename: "laudo.pdf", mimeType: "application/pdf", size: 8 },
          ],
        };
      }
      throw new Error(url);
    }),
  );
  const user = userEvent.setup();
  render(<DocumentsPage accessToken="access-1" />);
  expect(await screen.findByText("acordo.pdf")).toBeTruthy();
  await user.type(screen.getByLabelText("Busca"), "laudo");
  expect(screen.queryByText("acordo.pdf")).toBeNull();
  expect(screen.getByText("laudo.pdf")).toBeTruthy();
});
