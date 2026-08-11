import assert from "node:assert/strict";
import test from "node:test";
import { getApiErrorMessage } from "../src/shared/api/error.ts";

test("API error message includes every non-empty detail", () => {
  assert.equal(
    getApiErrorMessage(
      {
        error: {
          message: "요청 값이 올바르지 않습니다.",
          details: ["수량은 1 이상이어야 합니다.", "작업일을 입력해주세요."],
        },
      },
      "요청을 처리하지 못했습니다.",
    ),
    "요청 값이 올바르지 않습니다.\n수량은 1 이상이어야 합니다.\n작업일을 입력해주세요.",
  );
});

test("API error message falls back when the response has no error message", () => {
  assert.equal(
    getApiErrorMessage(null, "요청을 처리하지 못했습니다."),
    "요청을 처리하지 못했습니다.",
  );
});
