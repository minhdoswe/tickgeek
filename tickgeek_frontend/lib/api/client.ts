import { clientIp } from "@/lib/auth/clientIp";
import type { ApiErrorBody, ApiErrorCode, FieldErrors } from "@/lib/api/types";

const NO_CONTENT = 204;
const GENERIC_MESSAGE = "Something went wrong. Please try again.";

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: ApiErrorCode | "UNKNOWN",
    message: string,
    readonly fields?: FieldErrors,
    readonly retryAfterSeconds?: number,
  ) {
    super(message);
  }
}

export type ApiRequest = {
  method?: "GET" | "POST";
  body?: unknown;
};

/** Calls the backend from server code. Throws ApiError for any non-2xx response. */
export async function apiFetch<T>(path: string, request: ApiRequest = {}): Promise<T> {
  const response = await fetch(`${process.env.API_URL}${path}`, {
    method: request.method ?? "GET",
    headers: await requestHeaders(),
    body: request.body === undefined ? undefined : JSON.stringify(request.body),
    cache: "no-store",
  });
  if (!response.ok) {
    throw await toApiError(response);
  }
  return response.status === NO_CONTENT ? (undefined as T) : ((await response.json()) as T);
}

async function requestHeaders(): Promise<Headers> {
  const secret = process.env.FRONTEND_SECRET;
  if (!secret) {
    throw new Error("FRONTEND_SECRET is not set");
  }
  return new Headers({
    "Content-Type": "application/json",
    "X-Tickgeek-Client-Ip": await clientIp(),
    "X-Tickgeek-Frontend-Secret": secret,
  });
}

async function toApiError(response: Response): Promise<ApiError> {
  const retryAfter = Number(response.headers.get("Retry-After")) || undefined;
  const body = await readErrorBody(response);
  if (!body) {
    return new ApiError(response.status, "UNKNOWN", GENERIC_MESSAGE, undefined, retryAfter);
  }
  return new ApiError(response.status, body.error, body.message, body.fields, retryAfter);
}

async function readErrorBody(response: Response): Promise<ApiErrorBody | undefined> {
  try {
    const body: unknown = await response.json();
    return isErrorBody(body) ? body : undefined;
  } catch {
    return undefined;
  }
}

function isErrorBody(value: unknown): value is ApiErrorBody {
  return (
    typeof value === "object" &&
    value !== null &&
    typeof (value as Record<string, unknown>).error === "string" &&
    typeof (value as Record<string, unknown>).message === "string"
  );
}
