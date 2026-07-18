export const POT_SIZE_OPTIONS = [
  { value: "", label: "미지정" },
  { value: '2"', label: "2인치" },
  { value: '2.5"', label: "2.5인치" },
  { value: '3"', label: "3인치" },
  { value: '3.5"', label: "3.5인치" },
  { value: '4"', label: "4인치" },
  { value: '4.5"', label: "4.5인치" },
  { value: '5"', label: "5인치" },
  { value: '6"', label: "6인치" },
  { value: "행잉", label: "행잉" },
  { value: "기타", label: "기타" },
] as const;

export function isStandardPotSize(value: string) {
  return POT_SIZE_OPTIONS.some((option) => option.value === value);
}

export function formatPotSize(code: string, value: string | null) {
  if (code === "UNSPECIFIED") return "화분 미지정";
  if (code === "UNMAPPED") return value ? `${value} (검수 필요)` : "검수 필요";
  const option = POT_SIZE_OPTIONS.find((item) => item.value === value);
  return option?.label ?? value ?? code;
}
