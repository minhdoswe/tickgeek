"use client";

import { useEffect } from "react";
import { wakeBackendAction } from "@/action/healthAction";

export default function BackendWakeUp() {
  useEffect(() => {
    wakeBackendAction().catch(() => {});
  }, []);
  return null;
}
