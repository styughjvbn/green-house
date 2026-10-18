import test from "node:test";
import assert from "node:assert/strict";
import {
  preparedPhotoName,
  toPreparedSalesPhoto,
} from "../src/features/sales/lib/preparedSalesPhoto.ts";

test("prepared documents retain their source name with a bounded numbered JPEG suffix", () => {
  assert.equal(
    preparedPhotoName("20260816_091644.jpg", 1),
    "20260816_091644_01.jpg",
  );
  assert.equal(
    preparedPhotoName("receipt.scan.PNG", 12),
    "receipt.scan_12.jpg",
  );
  assert.equal(preparedPhotoName("receipt", 2), "receipt_02.jpg");
  assert.ok(preparedPhotoName("x".repeat(255) + ".jpg", 1).length <= 255);
});

test("prepared documents flatten transparency to white and release image resources", async (context) => {
  const calls = [];
  const previousBitmap = globalThis.createImageBitmap;
  const previousDocument = globalThis.document;
  context.after(() => {
    if (previousBitmap === undefined) delete globalThis.createImageBitmap;
    else globalThis.createImageBitmap = previousBitmap;
    if (previousDocument === undefined) delete globalThis.document;
    else globalThis.document = previousDocument;
  });
  globalThis.createImageBitmap = async () => ({
    width: 100,
    height: 200,
    close: () => calls.push("close"),
  });
  const canvas = {
    width: 0,
    height: 0,
    getContext: () => ({
      set fillStyle(value) {
        calls.push(value);
      },
      fillRect: (...args) => calls.push(args),
      drawImage: () => calls.push("draw"),
    }),
    toDataURL: (type) => {
      assert.equal(type, "image/jpeg");
      return "data:image/jpeg;base64,YQ==";
    },
  };
  globalThis.document = { createElement: () => canvas };
  const photo = await toPreparedSalesPhoto(new Blob(), "document_01.jpg");
  assert.deepEqual(photo, {
    fileName: "document_01.jpg",
    contentType: "image/jpeg",
    contentBase64: "YQ==",
  });
  assert.deepEqual(calls, ["#ffffff", [0, 0, 100, 200], "draw", "close"]);
  assert.deepEqual([canvas.width, canvas.height], [100, 200]);
});
