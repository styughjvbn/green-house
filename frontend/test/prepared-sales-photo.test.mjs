import test from "node:test";
import assert from "node:assert/strict";
import {
  preparedPhotoName,
  toPreparedSalesPhoto,
} from "../src/features/sales/lib/preparedSalesPhoto.ts";

test("prepared documents retain their source name with a bounded numbered WebP suffix", () => {
  assert.equal(
    preparedPhotoName("20260816_091644.jpg", 1),
    "20260816_091644_01.webp",
  );
  assert.equal(
    preparedPhotoName("receipt.scan.PNG", 12),
    "receipt.scan_12.webp",
  );
  assert.equal(preparedPhotoName("receipt", 2), "receipt_02.webp");
  assert.ok(preparedPhotoName("x".repeat(255) + ".webp", 1).length <= 255);
});

test("prepared documents preserve server WebP bytes without image conversion", async (context) => {
  const previousReader = globalThis.FileReader;
  context.after(() => {
    if (previousReader === undefined) delete globalThis.FileReader;
    else globalThis.FileReader = previousReader;
  });
  globalThis.FileReader = class {
    async readAsDataURL(blob) {
      this.result = `data:${blob.type};base64,${Buffer.from(await blob.arrayBuffer()).toString("base64")}`;
      this.onload();
    }
  };
  const bytes = Buffer.from("RIFFtestWEBPbytes");
  const photo = await toPreparedSalesPhoto(
    new Blob([bytes], { type: "image/webp" }),
    "document_01.webp",
  );
  assert.deepEqual(photo, {
    fileName: "document_01.webp",
    contentType: "image/webp",
    contentBase64: bytes.toString("base64"),
  });
});

test("prepared documents reject non-WebP and oversized responses", async () => {
  await assert.rejects(
    toPreparedSalesPhoto(new Blob(["png"], { type: "image/png" }), "a.webp"),
    /형식/,
  );
  await assert.rejects(
    toPreparedSalesPhoto(
      new Blob([new Uint8Array(8 * 1024 * 1024 + 1)], { type: "image/webp" }),
      "a.webp",
    ),
    /8MB/,
  );
});
