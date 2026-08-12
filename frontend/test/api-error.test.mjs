import assert from "node:assert/strict";
import test from "node:test";
import {
  ApiError,
  createApiError,
  getApiErrorMessage,
} from "../src/shared/api/error.ts";

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

test("structured API error preserves status, code, and details", () => {
  const error = createApiError(
    409,
    {
      error: {
        code: "CAPACITY_CONFLICT",
        message: "배치할 수 없습니다.",
        details: ["남은 자리가 부족합니다."],
      },
    },
    "요청을 처리하지 못했습니다.",
  );

  assert.ok(error instanceof ApiError);
  assert.equal(error.status, 409);
  assert.equal(error.code, "CAPACITY_CONFLICT");
  assert.deepEqual(error.details, ["남은 자리가 부족합니다."]);
  assert.equal(error.message, "배치할 수 없습니다.\n남은 자리가 부족합니다.");
});
