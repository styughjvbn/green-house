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
