import { createUuid } from "@/shared/lib/id";
import { createApiError, type ApiErrorResponse } from "./error";

export { ApiError, createApiError, getApiErrorMessage } from "./error";
export type { ApiErrorResponse } from "./error";

export const API_BASE_URL =
  typeof window === "undefined"
    ? (process.env.BACKEND_API_URL ??
      process.env.API_BASE_URL ??
      process.env.NEXT_PUBLIC_API_BASE_URL ??
      "http://localhost:8080/api")
    : (process.env.NEXT_PUBLIC_API_BASE_URL ?? "/api");

export type ApiResponse<T> = {
  data: T;
  message: string | null;
};

const CLIENT_INSTANCE_KEY = "greenhouse-client-instance-id";

export function buildApiHeaders(initial?: HeadersInit): Headers {
  const headers = new Headers(initial);
  if (typeof window === "undefined") return headers;

  let clientInstanceId = window.localStorage.getItem(CLIENT_INSTANCE_KEY);
  if (!clientInstanceId) {
    clientInstanceId = createUuid();
    window.localStorage.setItem(CLIENT_INSTANCE_KEY, clientInstanceId);
  }
  headers.set("X-Client-Instance-Id", clientInstanceId);
  return headers;
}

export function fetchWithClientInstance(
  input: RequestInfo | URL,
  init?: RequestInit,
): Promise<Response> {
  return globalThis.fetch(input, {
    ...init,
    headers: buildApiHeaders(init?.headers),
  });
}

async function buildRequestHeaders(): Promise<HeadersInit> {
  const headers = buildApiHeaders();
  if (typeof window !== "undefined") {
    return headers;
  }

  const { cookies } = await import("next/headers");
  const cookieHeader = (await cookies()).toString();
  if (cookieHeader) headers.set("Cookie", cookieHeader);
  return headers;
}

async function redirectToLogin() {
  if (typeof window !== "undefined") {
    window.location.assign("/login");
    return;
  }

  const { redirect } = await import("next/navigation");
  redirect("/login");
}

export async function handleAuthExpired(response: Response) {
  if (response.status === 401) {
    await redirectToLogin();
  }
}

export async function fetchApi<T>(
  path: string,
  options?: { signal?: AbortSignal },
): Promise<T> {
  return requestApi<T>(path, { method: "GET", signal: options?.signal });
}

export async function requestApi<T>(
  path: string,
  init: RequestInit = {},
  fallbackMessage = "요청을 처리하지 못했습니다.",
): Promise<T> {
  const response = await requestApiResponse(path, init, fallbackMessage);
  const payload = (await response
    .json()
    .catch(() => null)) as ApiResponse<T> | null;
  return payload?.data as T;
}

export async function requestApiBlob(
  path: string,
  init: RequestInit = {},
  fallbackMessage = "요청을 처리하지 못했습니다.",
): Promise<Blob> {
  return (await requestApiResponse(path, init, fallbackMessage)).blob();
}

/** SSE uses the same credentials, client identity and HTTP error handling as ordinary API calls. */
export async function requestApiEventStream(path: string, signal: AbortSignal) {
  const response = await requestApiResponse(
    path,
    {
      signal,
      headers: { Accept: "text/event-stream" },
    },
    "진행 상태를 연결하지 못했습니다.",
  );
  if (
    !response.body ||
    !response.headers.get("content-type")?.includes("text/event-stream")
  ) {
    await response.body?.cancel();
    throw new Error("진행 상태 응답 형식을 확인하지 못했습니다.");
  }
  return response.body;
}

async function requestApiResponse(
  path: string,
  init: RequestInit,
  fallbackMessage: string,
): Promise<Response> {
  const headers = new Headers(await buildRequestHeaders());
  new Headers(init.headers).forEach((value, key) => headers.set(key, value));
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    cache: "no-store",
    credentials: "include",
    headers,
  });
  await handleAuthExpired(response);
  if (!response.ok) {
    const payload = (await response
      .json()
      .catch(() => null)) as ApiErrorResponse | null;
    throw createApiError(response.status, payload, fallbackMessage);
  }
  return response;
}
