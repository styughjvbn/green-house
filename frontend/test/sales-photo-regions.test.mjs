import test from "node:test";
import assert from "node:assert/strict";
import {
  parsePhotoRegion,
  samePhotoRegion,
  photoRegionPixels,
} from "../src/features/sales/lib/photoRegions.ts";

test("region validation rejects nonfinite, empty and out-of-source rectangles", () => {
  for (const region of [
    null,
    {},
    { x: 0, y: 0, width: NaN, height: 1 },
    { x: 0, y: 0, width: 1, height: Infinity },
    { x: -0.1, y: 0, width: 1, height: 1 },
    { x: 0.8, y: 0, width: 0.3, height: 1 },
    { x: 0, y: 0, width: 0, height: 1 },
  ])
    assert.equal(parsePhotoRegion(region), null);
  assert.deepEqual(
    photoRegionPixels({ x: 0.1, y: 0.2, width: 0.3, height: 0.4 }, 4000, 3000),
    { x: 400, y: 600, width: 1200, height: 1200 },
  );
});

test("unchanged regions preserve existing extracted image identities", () => {
  const region = { x: 0.1, y: 0.2, width: 0.3, height: 0.4 };
  assert.equal(samePhotoRegion(region, { ...region, x: 0.10000000001 }), true);
  assert.equal(samePhotoRegion(region, { ...region, width: 0.31 }), false);
  assert.equal(samePhotoRegion(null, region), false);
});

test("multiple crops decode the source once, encode sequentially and release resources", async (context) => {
  const { cropSalesPhotoRegions } =
    await import("../src/features/sales/lib/photoRegions.ts");
  const originals = new Map(
    ["createImageBitmap", "document", "FileReader"].map((key) => [
      key,
      globalThis[key],
    ]),
  );
  context.after(() => {
    for (const [key, value] of originals) {
      if (value === undefined) delete globalThis[key];
      else globalThis[key] = value;
    }
  });
  let decoded = 0,
    closed = 0,
    created = 0,
    encoding = 0,
    maxEncoding = 0;
  const sizes = [],
    draws = [];
  const bitmap = { width: 4000, height: 3000, close: () => closed++ };
  globalThis.createImageBitmap = async () => {
    decoded++;
    return bitmap;
  };
  const canvas = {
    width: 0,
    height: 0,
    getContext: () => ({
      fillStyle: "",
      fillRect() {},
      translate() {},
      rotate() {},
      drawImage(...args) {
        draws.push(args.slice(1, 5));
      },
    }),
    toBlob(callback, type) {
      assert.equal(type, "image/webp");
      sizes.push([canvas.width, canvas.height]);
      encoding++;
      maxEncoding = Math.max(maxEncoding, encoding);
      queueMicrotask(() => {
        encoding--;
        callback(new Blob(["webp"], { type: "image/webp" }));
      });
    },
  };
  globalThis.document = {
    createElement: () => {
      created++;
      return canvas;
    },
  };
  globalThis.FileReader = class {
    readAsDataURL() {
      this.result = "data:image/webp;base64,d2VicA==";
      this.onload();
    }
  };
  const photo = {
    fileName: "original.jpg",
    contentType: "image/jpeg",
    contentBase64: "YQ==",
  };
  assert.deepEqual(await cropSalesPhotoRegions(photo, []), []);
  assert.equal(decoded, 0);
  const result = await cropSalesPhotoRegions(photo, [
    {
      fileName: "original_01.webp",
      region: { x: 0.1, y: 0.2, width: 0.3, height: 0.4 },
      rotationDegrees: 0,
    },
    {
      fileName: "original_02.webp",
      region: { x: 0, y: 0, width: 0.5, height: 0.25 },
      rotationDegrees: 90,
    },
  ]);
  assert.deepEqual(
    result.map((r) => r.fileName),
    ["original_01.webp", "original_02.webp"],
  );
  assert.deepEqual(sizes, [
    [1200, 1200],
    [750, 2000],
  ]);
  assert.deepEqual(draws, [
    [400, 600, 1200, 1200],
    [0, 0, 2000, 750],
  ]);
  assert.deepEqual(
    { decoded, created, closed, maxEncoding },
    { decoded: 1, created: 1, closed: 1, maxEncoding: 1 },
  );
  assert.deepEqual([canvas.width, canvas.height], [0, 0]);
  canvas.toBlob = (callback) => callback(null);
  await assert.rejects(
    cropSalesPhotoRegions(photo, [
      {
        fileName: "failed.jpg",
        region: { x: 0, y: 0, width: 1, height: 1 },
        rotationDegrees: 0,
      },
    ]),
    /전표 사진을 만들지 못했습니다/,
  );
  assert.equal(closed, 2);
  assert.deepEqual([canvas.width, canvas.height], [0, 0]);
});
