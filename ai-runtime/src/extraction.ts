import sharp from "sharp";
import {
  inputPhotos,
  validateResult,
  RuntimeError,
  type AgentProvider,
  type PhotoInput,
  type RunContext,
  type SlipInput,
} from "./contracts.js";

type ModelResult = {
  confidence: number;
  warnings: string[];
  [key: string]: unknown;
};
type Row = { photoId: string; result: ModelResult | null };
type Prepared = { photo: PhotoInput; longEdge: number; sourceLongEdge: number };
export async function resizePhoto(
  photo: PhotoInput,
  maximum: 1024 | 2048,
): Promise<Prepared> {
  try {
    const original = Buffer.from(photo.contentBase64, "base64");
    const image = sharp(original, {
      limitInputPixels: 25_000_000,
      failOn: "error",
    });
    const metadata = await image.metadata();
    if (
      !["jpeg", "png"].includes(metadata.format ?? "") ||
      !metadata.width ||
      !metadata.height
    )
      throw new Error("Unsupported image");
    const sourceLongEdge = Math.max(metadata.width, metadata.height);
    if (
      sourceLongEdge <= maximum &&
      (!metadata.orientation || metadata.orientation === 1)
    )
      return { photo, longEdge: sourceLongEdge, sourceLongEdge };
    const { data, info } = await image
      .rotate()
      .resize({
        width: maximum,
        height: maximum,
        fit: "inside",
        withoutEnlargement: true,
      })
      .flatten({ background: "#ffffff" })
      .jpeg({ quality: 90, chromaSubsampling: "4:4:4" })
      .toBuffer({ resolveWithObject: true });
    return {
      photo: {
        ...photo,
        contentType: "image/jpeg",
        contentBase64: data.toString("base64"),
      },
      longEdge: Math.max(info.width, info.height),
      sourceLongEdge,
    };
  } catch {
    throw new RuntimeError(400, "AI_INVALID_IMAGE");
  }
}
function withPhotos(input: SlipInput, photos: PhotoInput[]): SlipInput {
  return input.photos
    ? { ...input, photos }
    : {
        ...input,
        contentType: photos[0].contentType,
        contentBase64: photos[0].contentBase64,
      };
}
export async function extractWithResolutionFallback(
  provider: AgentProvider,
  input: SlipInput,
  context: RunContext,
  threshold = 80,
) {
  if (!Number.isInteger(threshold) || threshold < 0 || threshold > 100)
    throw new Error("AI_MIN_CONFIDENCE must be an integer from 0 to 100");
  const prepared: Prepared[] = [];
  for (const photo of inputPhotos(input)) {
    context.signal.throwIfAborted();
    const resized = await resizePhoto(photo, 1024);
    prepared.push(resized);
    context.diagnostic?.("image_prepared", {
      photoId: photo.photoId,
      longEdge: resized.longEdge,
      attempt: 1,
    });
  }
  const shared = {
    ...context,
    toolBudget: { used: 0 },
    deadlineMs: context.deadlineMs ?? Date.now() + input.timeoutSeconds * 1000,
    session: context.session ?? {
      history: [],
      turns: 0,
      expiresAt: Date.now() + input.timeoutSeconds * 1000,
    },
  };
  async function infer(request: SlipInput): Promise<Row[]> {
    context.signal.throwIfAborted();
    const value = validateResult(await provider.run(request, shared), request);
    return request.photos
      ? (value as { photos: Row[] }).photos
      : [{ photoId: "single", result: value as ModelResult }];
  }
  const first = await infer(
    withPhotos(
      input,
      prepared.map((p) => p.photo),
    ),
  );
  const rows = new Map(first.map((row) => [row.photoId, row]));
  const attempts = new Map(
    prepared.map((p) => [
      p.photo.photoId,
      { longEdge: p.longEdge, attempts: 1 },
    ]),
  );
  const retry = prepared.filter(
    (p) =>
      (rows.get(p.photo.photoId)!.result?.confidence ?? 0) < threshold &&
      p.sourceLongEdge > p.longEdge,
  );
  const retryWarnings = new Set<string>();
  if (retry.length) {
    const originals = new Map(
      inputPhotos(input).map((photo) => [photo.photoId, photo]),
    );
    const larger: Prepared[] = [];
    for (const first of retry) {
      context.signal.throwIfAborted();
      // Always regenerate from the original, never upscale the first-pass image.
      const resized = await resizePhoto(
        originals.get(first.photo.photoId)!,
        2048,
      );
      larger.push(resized);
      context.diagnostic?.("image_prepared", {
        photoId: first.photo.photoId,
        longEdge: resized.longEdge,
        attempt: 2,
      });
    }
    try {
      context.diagnostic?.("resolution_retry_started", {
        photoCount: larger.length,
        threshold,
      });
      const second = await infer(
        withPhotos(
          input,
          larger.map((p) => p.photo),
        ),
      );
      for (const row of second) {
        const image = larger.find((p) => p.photo.photoId === row.photoId)!;
        const first = rows.get(row.photoId)!;
        if (row.result || !first.result) rows.set(row.photoId, row);
        else retryWarnings.add(row.photoId);
        // The returned values may be the first-pass result if the retry was unreadable.
        attempts.set(row.photoId, {
          longEdge: row.result
            ? image.longEdge
            : prepared.find((p) => p.photo.photoId === row.photoId)!.longEdge,
          attempts: 2,
        });
      }
    } catch (error) {
      if (context.signal.aborted) throw error;
      context.diagnostic?.("resolution_retry_failed", {
        code: error instanceof RuntimeError ? error.code : "AI_PROVIDER_FAILED",
      });
      for (const photo of larger) {
        retryWarnings.add(photo.photo.photoId);
        attempts.set(photo.photo.photoId, {
          longEdge: prepared.find(
            (p) => p.photo.photoId === photo.photo.photoId,
          )!.longEdge,
          attempts: 2,
        });
      }
    }
  }
  const photos = prepared.map((photo) => {
    const row = rows.get(photo.photo.photoId)!;
    if (!row.result) return row;
    const additionalWarnings: string[] = [];
    if (retryWarnings.has(row.photoId))
      additionalWarnings.push(
        "고해상도 재인식을 완료하지 못해 첫 인식값을 유지했습니다. 원본과 비교해주세요.",
      );
    if (row.result.confidence < threshold)
      additionalWarnings.push(
        "인식 신뢰도가 낮습니다. 사진을 더 선명하게 다시 촬영하거나 입력값을 직접 확인해주세요.",
      );
    const attempt = attempts.get(row.photoId)!;
    return {
      photoId: row.photoId,
      result: {
        ...row.result,
        imageLongEdge: attempt.longEdge,
        attempts: attempt.attempts,
        warnings: [
          ...row.result.warnings.slice(0, 30 - additionalWarnings.length),
          ...additionalWarnings,
        ],
      },
    };
  });
  return input.photos ? { photos } : photos[0].result;
}
