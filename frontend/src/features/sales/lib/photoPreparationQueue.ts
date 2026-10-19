export const PHOTO_DOCUMENT_LIMIT_MESSAGE =
  "잘라낸 전표는 최대 10개까지 등록할 수 있습니다. 기존 전표 사진을 제거한 뒤 다시 시도해주세요.";

export function createPhotoSlotBudget(maximum: number, existing: number) {
  let used = existing;
  return (count: number, replaced = 0) => {
    // Keep old slots reserved until replacement succeeds; rollback must not overbook them.
    const delta = Math.max(0, count - replaced);
    if (used + delta > maximum) throw new Error(PHOTO_DOCUMENT_LIMIT_MESSAGE);
    used += delta;
    let released = false;
    return () => {
      if (released) return;
      used -= delta;
      released = true;
    };
  };
}

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
