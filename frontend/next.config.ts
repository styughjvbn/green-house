import type { NextConfig } from "next";

const backendApiUrl = (
  process.env.BACKEND_API_URL ??
  process.env.API_BASE_URL ??
  "http://localhost:8080/api"
).replace(/\/$/, "");

const nextConfig: NextConfig = {
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
