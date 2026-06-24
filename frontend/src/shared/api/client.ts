export const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api";

export type ApiResponse<T> = {
  data: T;
  message: string | null;
};

export type ApiErrorResponse = {
  error: {
    code: string;
    message: string;
    details: string[];
  };
};

export async function fetchApi<T>(path: string): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    cache: "no-store",
  });
  const payload = (await response.json()) as ApiResponse<T> | ApiErrorResponse;

  if (!response.ok) {
    const message = "error" in payload ? payload.error.message : "요청을 처리하지 못했습니다.";
    throw new Error(message);
  }

  return (payload as ApiResponse<T>).data;
}
