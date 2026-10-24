import { beforeEach, expect, test, vi } from "vitest";
import { authorizedFetch, readSession } from "./session";

beforeEach(() => {
  sessionStorage.clear();
  vi.restoreAllMocks();
});

test("renews the session once and retries the request", async () => {
  sessionStorage.setItem(
    "anjo.session",
    JSON.stringify({ accessToken: "access-1", refreshToken: "refresh-1", expiresInSeconds: 900 }),
  );
  let childrenCalls = 0;
  let refreshCalls = 0;
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string, init?: RequestInit) => {
      if (String(url).endsWith("/auth/refresh")) {
        refreshCalls += 1;
        return {
          ok: true,
          status: 200,
          json: async () => ({ accessToken: "access-2", refreshToken: "refresh-2", expiresInSeconds: 900 }),
        };
      }
      childrenCalls += 1;
      const authorization = new Headers(init?.headers).get("Authorization");
      if (authorization === "Bearer access-2") {
        return { ok: true, status: 200, json: async () => [] };
      }
      return { ok: false, status: 401, json: async () => ({}) };
    }),
  );

  const [first, second] = await Promise.all([
    authorizedFetch("/api/v1/children", { headers: { Authorization: "Bearer access-1" } }),
    authorizedFetch("/api/v1/children", { headers: { Authorization: "Bearer access-1" } }),
  ]);

  expect(first.status).toBe(200);
  expect(second.status).toBe(200);
  expect(refreshCalls).toBe(1);
  expect(childrenCalls).toBe(4);
  expect(readSession()).toMatchObject({ accessToken: "access-2", refreshToken: "refresh-2" });
});

test("clears the session when the refresh fails", async () => {
  sessionStorage.setItem(
    "anjo.session",
    JSON.stringify({ accessToken: "access-1", refreshToken: "refresh-1", expiresInSeconds: 900 }),
  );
  const fetchMock = vi.fn(async () => ({ ok: false, status: 401, json: async () => ({}) }));
  vi.stubGlobal("fetch", fetchMock);

  const response = await authorizedFetch("/api/v1/children", {
    headers: { Authorization: "Bearer access-1" },
  });

  expect(response.status).toBe(401);
  expect(readSession()).toBeNull();
  expect(fetchMock).toHaveBeenCalledTimes(2);
});

test("leaves the login failure alone", async () => {
  const fetchMock = vi.fn(async () => ({ ok: false, status: 401, json: async () => ({}) }));
  vi.stubGlobal("fetch", fetchMock);

  const response = await authorizedFetch("/api/v1/auth/login", { method: "POST" });

  expect(response.status).toBe(401);
  expect(fetchMock).toHaveBeenCalledTimes(1);
});
