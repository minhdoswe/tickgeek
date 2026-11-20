"use server";

import { checkHealth } from "@/lib/api/health";

/**
 * Starts waking the backend (it sleeps when idle on the free host) and returns at once.
 * The browser runs Server Actions one at a time, so awaiting the ping here would hold up
 * a form submit queued behind it.
 */
export async function wakeBackendAction(): Promise<void> {
  void checkHealth().catch(() => {});
}
