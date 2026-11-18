/** Types for the backend auth API (specs/001-email-code-auth/contracts/auth-api.md). */

export type CodeRequestResponse = {
  email: string;
  codeValidMinutes: number;
  resendAvailableAt: string;
};

export type ApiErrorCode = "VALIDATION_FAILED" | "TOO_MANY_REQUESTS";

export type FieldErrors = Record<string, string[]>;

export type ApiErrorBody = {
  error: ApiErrorCode;
  message: string;
  fields?: FieldErrors;
};
