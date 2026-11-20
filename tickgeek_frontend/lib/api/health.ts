import { apiFetch } from "@/lib/api/client";

export function checkHealth(): Promise<{ status: string }> {
  return apiFetch("/health");
}
