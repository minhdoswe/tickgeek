"use client";

import { useEffect } from "react";
import { wakeBackendAction } from "@/action/healthAction";

/** Wakes the backend as soon as a page opens, so it is usually awake by the first real request. */
export default function BackendWakeUp() {
  useEffect(() => {
    wakeBackendAction().catch(() => {});
  }, []);
  return null;
}
