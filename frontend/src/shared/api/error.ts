export type ApiErrorResponse = {
  error: {
    code: string;
    message: string;
    details: string[];
  };
};

export function getApiErrorMessage(
  payload: unknown,
  fallbackMessage: string,
): string {
  const error = (payload as Partial<ApiErrorResponse> | null)?.error;
  const message = error?.message?.trim() || fallbackMessage;
  const details = Array.isArray(error?.details)
    ? error.details
        .filter((detail): detail is string => typeof detail === "string")
        .map((detail) => detail.trim())
        .filter((detail) => detail && detail !== message)
    : [];

  return details.length > 0 ? `${message}\n${details.join("\n")}` : message;
}
