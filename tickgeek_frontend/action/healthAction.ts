"use server";

import { after } from "next/server";
import { checkHealth } from "@/lib/api/health";

export async function wakeBackendAction(): Promise<void> {
  after(() => checkHealth().catch(() => {}));
}
