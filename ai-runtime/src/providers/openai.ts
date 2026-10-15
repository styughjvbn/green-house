import {
  RuntimeError,
  instructions,
  schema,
  batchSchema,
  inputPhotos,
  turnPrompt,
  sessionReferencePrompt,
  validateResult,
  type AgentProvider,
  type SlipInput,
  type RunContext,
  type ToolName,
  type ToolArgs,
} from "../contracts.js";
export const tools = [
  {
    type: "function",
    name: "search_varieties",
    description:
      "품종명·별명으로 후보를 검색하며 검색어와 이름의 내부 공백을 무시합니다. 같은 일치도에서는 원문 표기가 일치하는 후보가 우선입니다. 코드 검색은 하지 않습니다. 품종명 정확 일치, 별명 정확 일치, 품종명 부분 일치, 별명 부분 일치 순이며 속은 같은 일치도의 후보 정렬 힌트입니다. 속이 달라도 제외하지 않습니다. 모호하면 ID를 선택하지 마세요. 비활성 후보도 과거 기록 검토를 위해 반환됩니다.",
    strict: true,
    parameters: {
      type: "object",
      properties: {
        query: { type: "string", minLength: 1, maxLength: 100 },
        genus: { type: ["string", "null"], maxLength: 100 },
      },
      required: ["query", "genus"],
      additionalProperties: false,
    },
  },
  {
    type: "function",
    name: "search_partners",
    description:
      "활성 구매 거래처 또는 경매장 후보를 검색합니다. 경매장이 확실하면 auctionHouse=true, 일반 거래처면 false, 불명확하면 null.",
    strict: true,
    parameters: {
      type: "object",
      properties: {
        query: { type: "string", minLength: 1, maxLength: 100 },
        auctionHouse: { type: ["boolean", "null"] },
      },
      required: ["query", "auctionHouse"],
      additionalProperties: false,
    },
  },
];
type Item = {
  type: string;
  name?: string;
  arguments?: string;
  call_id?: string;
  content?: { type: string; text?: string }[];
  [key: string]: unknown;
};
export function openAiProvider(
  apiKey: string,
  model: string,
  endpoint = "https://api.openai.com/v1/responses",
): AgentProvider {
  return {
    name: "openai",
    model,
    async run(input: SlipInput, context: RunContext) {
      if (!apiKey) throw new RuntimeError(503, "AI_NOT_CONFIGURED");
      const reference = !context.session?.history.length
        ? sessionReferencePrompt(context.session)
        : "";
      const conversation: unknown[] = [
        ...(context.session?.history ?? []),
        ...(reference
          ? [
              {
                role: "user",
                content: [{ type: "input_text", text: reference }],
              },
            ]
          : []),
        {
          role: "user",
          content: [
            { type: "input_text", text: turnPrompt(input) },
            ...inputPhotos(input).flatMap((photo) => [
              ...(input.photos
                ? [{ type: "input_text", text: `photoId: ${photo.photoId}` }]
                : []),
              {
                type: "input_image",
                image_url: `data:${photo.contentType};base64,${photo.contentBase64}`,
                detail: "high",
              },
            ]),
          ],
        },
      ];
      let calls = 0;
      for (let turn = 0; turn <= context.maxToolCalls; turn++) {
        context.diagnostic?.("model_request", { turn: turn + 1, model });
        const response = await fetch(endpoint, {
          method: "POST",
          headers: {
            Authorization: `Bearer ${apiKey}`,
            "Content-Type": "application/json",
          },
          signal: context.signal,
          body: JSON.stringify({
            model,
            store: false,
            instructions,
            input: conversation,
            tools,
            parallel_tool_calls: false,
            max_output_tokens: input.photos ? 24000 : 7000,
            text: {
              format: {
                type: "json_schema",
                name: "slip_extraction",
                strict: true,
                schema: input.photos ? batchSchema : schema,
              },
            },
          }),
        });
        context.diagnostic?.("model_response", {
          turn: turn + 1,
          status: response.status,
        });
        if (!response.ok)
          throw new RuntimeError(
            response.status === 429 ? 429 : 502,
            "AI_PROVIDER_FAILED",
          );
        const result = (await response.json()) as {
          status: string;
          output: Item[];
        };
        if (result.status !== "completed" || !Array.isArray(result.output))
          throw new RuntimeError(502, "AI_INCOMPLETE");
        const requested = result.output.filter(
          (item) => item.type === "function_call",
        );
        if (!requested.length) {
          const text = result.output
            .flatMap((item) => item.content ?? [])
            .filter((part) => part.type === "output_text")
            .map((part) => part.text ?? "")
            .join("");
          try {
            const output = validateResult(JSON.parse(text), input);
            if (context.session) {
              // Keep dialogue and extraction results, not historical photo binaries.
              context.session.history = [...conversation, ...result.output].map(
                (entry) => {
                  const item = entry as { content?: { type: string }[] };
                  return item.content
                    ? {
                        ...item,
                        content: item.content.filter(
                          (part) => part.type !== "input_image",
                        ),
                      }
                    : entry;
                },
              );
            }
            return output;
          } catch {
            throw new RuntimeError(502, "AI_INVALID_RESULT");
          }
        }
        conversation.push(...result.output);
        for (const call of requested) {
          calls++;
          const totalCalls = context.toolBudget
            ? ++context.toolBudget.used
            : calls;
          if (totalCalls > context.maxToolCalls)
            throw new RuntimeError(429, "AI_TOOL_LIMIT");
          if (
            !["search_varieties", "search_partners"].includes(
              call.name ?? "",
            ) ||
            !call.call_id
          )
            throw new RuntimeError(502, "AI_TOOL_NOT_ALLOWED");
          let args: ToolArgs;
          try {
            args = JSON.parse(call.arguments ?? "");
          } catch {
            throw new RuntimeError(502, "AI_TOOL_ARGUMENT_INVALID");
          }
          let output: unknown;
          try {
            output = {
              data: await context.callTool(
                call.name as ToolName,
                args,
                context.signal,
              ),
            };
          } catch (error) {
            if (context.signal.aborted) throw error;
            if (error instanceof RuntimeError && error.code === "AI_TOOL_LIMIT")
              throw error;
            output = { error: { code: "AI_TOOL_FAILED" } };
          }
          conversation.push({
            type: "function_call_output",
            call_id: call.call_id,
            output: JSON.stringify(output),
          });
        }
      }
      throw new RuntimeError(429, "AI_TOOL_LIMIT");
    },
  };
}
