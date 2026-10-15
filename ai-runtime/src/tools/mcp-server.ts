import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";
import type { ToolArgs } from "../contracts.js";
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
      description:
        name === "search_varieties"
          ? "품종명·별명으로 후보를 검색하며 검색어와 이름의 내부 공백을 무시합니다. 같은 일치도에서는 원문 표기가 일치하는 후보가 우선입니다. 코드 검색은 하지 않습니다. 품종명 정확 일치, 별명 정확 일치, 품종명 부분 일치, 별명 부분 일치 순이며 속은 같은 일치도의 후보 정렬 힌트입니다. 속이 달라도 제외하지 않습니다. 모호하면 ID를 선택하지 마세요."
          : "활성 거래처 이름으로 후보 검색",
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
