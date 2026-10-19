export function resolveImagePreparationConcurrency(value: string | undefined) {
  if (value === undefined) return 3;
  const configured = value.trim();
  const concurrency = Number(configured);
  if (!/^[1-9]\d*$/.test(configured) || concurrency > 2147483647)
    throw new Error("SALES_DOCUMENT_IMAGE_WORKERS는 양의 정수여야 합니다.");
  return concurrency;
}
