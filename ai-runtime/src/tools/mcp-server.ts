import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";
import { toolDescriptions, type ToolArgs } from "../contracts.js";
import { createToolClient } from "./client.js";
const server = new McpServer({
  name: "green-house-read-tools",
  version: "1.0.0",
});
const call = createToolClient(
  process.env.AI_TOOLS_URL ?? "http://127.0.0.1:8080",
  process.env.AI_TOOL_TOKEN ?? "",
  Number(process.env.AI_MAX_TOOL_CALLS ?? 20),
);
const deadline = Number(process.env.AI_DEADLINE_MS);
function signal() {
  return AbortSignal.timeout(Math.max(1, deadline - Date.now()));
}
for (const name of ["search_varieties", "search_partners"] as const) {
  server.registerTool(
    name,
    {
      annotations: {
        readOnlyHint: true,
        destructiveHint: false,
        idempotentHint: true,
        openWorldHint: false,
      },
      description: toolDescriptions[name],
      inputSchema:
        name === "search_varieties"
          ? {
              query: z.string().min(1).max(100),
              genus: z.string().max(100).nullable().optional(),
            }
          : {
              query: z.string().min(1).max(100),
              auctionHouse: z.boolean().nullable().optional(),
            },
    },
    async (args: ToolArgs) => {
      try {
        return {
          content: [
            {
              type: "text" as const,
              text: JSON.stringify({ data: await call(name, args, signal()) }),
            },
          ],
        };
      } catch {
        return {
          isError: true,
          content: [
            {
              type: "text" as const,
              text: JSON.stringify({ error: { code: "AI_TOOL_FAILED" } }),
            },
          ],
        };
      }
    },
  );
}
await server.connect(new StdioServerTransport());
