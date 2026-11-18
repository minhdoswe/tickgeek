import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  /* config options here */
  cacheComponents: true,
  partialPrefetching: true,
  experimental: {
    // CLI path spawns tsc and parses its --showConfig output; that output can come back
    // empty in some sandboxes and fail the build. In-process check avoids the spawn.
    useTypeScriptCli: false,
  },
};

export default nextConfig;
