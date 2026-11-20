import { ApiError } from "@/lib/api/client";
import type { ApiErrorCode, FieldErrors } from "@/lib/api/types";

/** What a Server Action hands back to its form through useActionState. */
export type FormState = {
  error?: { code: ApiErrorCode | "UNKNOWN"; message: string };
  fields?: FieldErrors;
  notice?: string;
};

const GENERIC_MESSAGE = "Something went wrong. Please try again.";

export function toFormState(error: unknown): FormState {
  if (error instanceof ApiError) {
    return { error: { code: error.code, message: error.message }, fields: error.fields };
  }
  return { error: { code: "UNKNOWN", message: GENERIC_MESSAGE } };
}

export function formText(formData: FormData, name: string): string {
  const value = formData.get(name);
  return typeof value === "string" ? value : "";
}
