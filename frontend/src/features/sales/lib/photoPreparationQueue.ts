export async function runConcurrentPhotoTasks<T>(
  targets: readonly T[],
  concurrency: number,
  signal: AbortSignal,
  process: (target: T) => Promise<void>,
) {
  if (!Number.isInteger(concurrency) || concurrency < 1)
    throw new Error("사진 동시 처리 수는 양의 정수여야 합니다.");
  let next = 0;
  async function worker() {
    while (!signal.aborted && next < targets.length) {
      const target = targets[next++];
      await process(target);
    }
  }
  const results = await Promise.allSettled(
    Array.from({ length: Math.min(concurrency, targets.length) }, worker),
  );
  const failure = results.find((result) => result.status === "rejected");
  if (failure?.status === "rejected") throw failure.reason;
}
