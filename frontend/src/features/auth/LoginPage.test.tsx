import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, test, vi } from "vitest";
import { LoginPage } from "./LoginPage";

beforeEach(() => {
  sessionStorage.clear();
  vi.restoreAllMocks();
});

test("asks for email and password and has no password recovery", () => {
  render(<LoginPage />);
  expect(screen.getByLabelText("E-mail")).toBeTruthy();
  expect(screen.getByLabelText("Senha")).toBeTruthy();
  expect(screen.getByRole("button", { name: "Entrar" })).toBeTruthy();
  expect(screen.queryByText(/esqueci/i)).toBeNull();
});

test("does not call the api when the form is incomplete", async () => {
  const fetchMock = vi.fn();
  vi.stubGlobal("fetch", fetchMock);
  const user = userEvent.setup();
  render(<LoginPage />);

  await user.click(screen.getByRole("button", { name: "Entrar" }));
  expect(screen.getByText("Informe um e-mail válido")).toBeTruthy();
  expect(fetchMock).not.toHaveBeenCalled();

  await user.type(screen.getByLabelText("E-mail"), "ana@example.com");
  await user.click(screen.getByRole("button", { name: "Entrar" }));
  expect(screen.getByText("Informe a senha")).toBeTruthy();
  expect(fetchMock).not.toHaveBeenCalled();
});

test("shows the api error and keeps the form", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: false,
      status: 401,
      json: async () => ({ message: "Credenciais inválidas" }),
    }),
  );
  const user = userEvent.setup();
  render(<LoginPage />);
  await user.type(screen.getByLabelText("E-mail"), "ana@example.com");
  await user.type(screen.getByLabelText("Senha"), "errada");
  await user.click(screen.getByRole("button", { name: "Entrar" }));

  expect(await screen.findByText("Credenciais inválidas")).toBeTruthy();
  expect(screen.getByRole("button", { name: "Entrar" })).toBeTruthy();
  expect(sessionStorage.getItem("anjo.session")).toBeNull();
});

test("keeps the form when the api cannot be reached", async () => {
  vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("offline")));
  const user = userEvent.setup();
  render(<LoginPage />);
  await user.type(screen.getByLabelText("E-mail"), "ana@example.com");
  await user.type(screen.getByLabelText("Senha"), "senha-certa");
  await user.click(screen.getByRole("button", { name: "Entrar" }));

  expect(await screen.findByText("Não foi possível entrar. Tente de novo.")).toBeTruthy();
  expect(screen.getByRole("button", { name: "Entrar" })).toBeTruthy();
});

test("stores the session and can leave it", async () => {
  const fetchMock = vi.fn(async (url: string) => {
    if (String(url).endsWith("/login")) {
      return {
        ok: true,
        json: async () => ({
          accessToken: "access-1",
          refreshToken: "refresh-1",
          expiresInSeconds: 900,
        }),
      };
    }
    if (String(url) === "/api/v1/children") {
      return { ok: true, status: 200, json: async () => [] };
    }
    if (String(url).endsWith("/logout")) {
      return { ok: true, status: 204 };
    }
    return { ok: true, status: 200, json: async () => ({ days: [] }) };
  });
  vi.stubGlobal("fetch", fetchMock);
  const user = userEvent.setup();
  render(<LoginPage />);
  await user.type(screen.getByLabelText("E-mail"), "ana@example.com");
  await user.type(screen.getByLabelText("Senha"), "senha-certa");
  await user.click(screen.getByRole("button", { name: "Entrar" }));

  expect(await screen.findByRole("button", { name: "Sair" })).toBeTruthy();
  expect(JSON.parse(sessionStorage.getItem("anjo.session") ?? "{}")).toMatchObject({
    accessToken: "access-1",
    refreshToken: "refresh-1",
  });

  await user.click(screen.getByRole("button", { name: "Sair" }));
  expect(await screen.findByRole("button", { name: "Entrar" })).toBeTruthy();
  expect(sessionStorage.getItem("anjo.session")).toBeNull();
  expect(fetchMock).toHaveBeenLastCalledWith(
    "/api/v1/auth/logout",
    expect.objectContaining({ method: "POST" }),
  );
});
