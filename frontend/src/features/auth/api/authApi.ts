import {
  API_BASE_URL,
  type ApiErrorResponse,
  type ApiResponse,
} from "@/shared/api/client";

export type AuthRole = "ADMIN" | "WORKER";

export type AuthenticatedUser = {
  username: string;
  role: AuthRole;
};

export async function login(username: string, password: string) {
  const response = await fetch(`${API_BASE_URL}/auth/login`, {
    method: "POST",
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ username, password }),
  });
  const payload = (await response.json()) as
    | ApiResponse<AuthenticatedUser>
    | ApiErrorResponse;

  if (!response.ok) {
    throw new Error(
      "error" in payload ? payload.error.message : "로그인에 실패했습니다.",
    );
  }

  return (payload as ApiResponse<AuthenticatedUser>).data;
}

export async function logout() {
  const response = await fetch(`${API_BASE_URL}/auth/logout`, {
    method: "POST",
    credentials: "include",
  });

  if (!response.ok && response.status !== 401 && response.status !== 403) {
    throw new Error("로그아웃에 실패했습니다.");
  }
}

export async function getCurrentUser() {
  const response = await fetch(`${API_BASE_URL}/auth/me`, {
    cache: "no-store",
    credentials: "include",
  });

  if (!response.ok) {
    return null;
  }

  const payload =
    (await response.json()) as ApiResponse<AuthenticatedUser | null>;
  return payload.data;
}
