export type ApiErrorResponse = {
  error: {
    code: string;
    message: string;
    details: string[];
  };
};

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly details: string[];

  constructor({
    status,
    code,
    message,
    details,
  }: {
    status: number;
    code: string;
    message: string;
    details: string[];
  }) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

export function createApiError(
  status: number,
  payload: unknown,
  fallbackMessage: string,
): ApiError {
  const error = readApiError(payload, fallbackMessage);
  return new ApiError({
    status,
    code: error.code || `HTTP_${status}`,
    message: formatApiErrorMessage(error.message, error.details),
    details: error.details,
  });
}

export function getApiErrorMessage(
  payload: unknown,
  fallbackMessage: string,
): string {
  const error = readApiError(payload, fallbackMessage);
  return formatApiErrorMessage(error.message, error.details);
}

function readApiError(payload: unknown, fallbackMessage: string) {
  const error = (payload as Partial<ApiErrorResponse> | null)?.error;
  const message = error?.message?.trim() || fallbackMessage;
  const details = Array.isArray(error?.details)
    ? error.details
        .filter((detail): detail is string => typeof detail === "string")
        .map((detail) => detail.trim())
        .filter((detail) => detail && detail !== message)
    : [];
  return {
    code: typeof error?.code === "string" ? error.code.trim() : "",
    message,
    details,
  };
}

function formatApiErrorMessage(message: string, details: string[]) {
  return details.length > 0 ? `${message}\n${details.join("\n")}` : message;
}
