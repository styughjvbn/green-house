import type { NextConfig } from "next";

const backendApiUrl = (
  process.env.BACKEND_API_URL ??
  process.env.API_BASE_URL ??
  "http://localhost:8080/api"
).replace(/\/$/, "");

const nextConfig: NextConfig = {
  typedRoutes: true,

  experimental: { proxyClientMaxBodySize: "82mb", proxyTimeout: 610_000 },

  allowedDevOrigins: ["192.168.0.2", "192.168.0.2:3000"],

  logging: {
    fetches: {
      fullUrl: true,
    },
  },

  output: "standalone",

  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${backendApiUrl}/:path*`,
      },
    ];
  },
};

export default nextConfig;
