import { apiFetch } from "@/lib/api/client";
import type { CodeRequestResponse } from "@/lib/api/types";

const AUTH = "/auth";

export function requestCode(email: string): Promise<CodeRequestResponse> {
  return apiFetch(`${AUTH}/code-requests`, { method: "POST", body: { email } });
}
