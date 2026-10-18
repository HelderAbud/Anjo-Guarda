import { render, screen } from "@testing-library/react";
import { expect, test, vi } from "vitest";
import { SettingsPage } from "./SettingsPage";

test("shows the fixed limits and does not call the api", () => {
  const fetchMock = vi.fn();
  vi.stubGlobal("fetch", fetchMock);
  render(<SettingsPage />);

  expect(screen.getByRole("heading", { name: "Configurações" })).toBeTruthy();
  expect(screen.getByText("America/Sao_Paulo")).toBeTruthy();
  expect(screen.getByText("Não há recuperação de senha por e-mail.")).toBeTruthy();
  expect(screen.queryByLabelText("Senha")).toBeNull();
  expect(screen.queryByRole("button", { name: "Convidar" })).toBeNull();
  expect(fetchMock).not.toHaveBeenCalled();
});
