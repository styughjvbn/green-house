export const PHOTO_EXTRACTION_BATCH_SIZE = 10;

/** Preserve list order and finish each conversation turn before starting the next. */
export async function runPhotoExtractionBatches<T>(
  targets: readonly T[],
  signal: AbortSignal,
  extract: (
    batch: T[],
    batchIndex: number,
    batchCount: number,
  ) => Promise<void>,
) {
  const batchCount = Math.ceil(targets.length / PHOTO_EXTRACTION_BATCH_SIZE);
  for (let index = 0; index < batchCount; index++) {
    if (signal.aborted) return;
    const start = index * PHOTO_EXTRACTION_BATCH_SIZE;
    await extract(
      targets.slice(start, start + PHOTO_EXTRACTION_BATCH_SIZE),
      index + 1,
      batchCount,
    );
  }
}
