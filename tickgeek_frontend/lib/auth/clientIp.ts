import { isIP } from "node:net";
import { headers } from "next/headers";

export const UNKNOWN_IP = "unknown";

/**
 * The client IP, read only from the header the hosting proxy overwrites (CLIENT_IP_HEADER).
 * The incoming X-Forwarded-For is never trusted, because clients can set it
 * (specs/001-request-code/research.md R6).
 */
function clientIpFrom(requestHeaders: Headers): string {
  const headerName = process.env.CLIENT_IP_HEADER;
  const value = headerName ? requestHeaders.get(headerName)?.trim() : undefined;
  return value && isIP(value) !== 0 ? value : UNKNOWN_IP;
}

export async function clientIp(): Promise<string> {
  return clientIpFrom(await headers());
}
