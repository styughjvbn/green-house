export function workOperationGraphEdgeLabel(
  type: string,
  relation?: string | null,
) {
  if (type === "ORIGINATED") return "생성";
  if (type === "PRECEDES" && relation === "MOVEMENT_DISCARD") {
    return "이동 후 폐기";
  }
  if (type === "PRECEDES") return "선행";
  if (type === "EFFECT") return "실행 효과";
  if (relation === "CORRECTS") return "보정";
  if (relation === "COMPENSATES") return "보상";
  if (relation === "SUPERSEDES") return "대체";
  if (relation === "SOURCE") return "원본";
  if (relation === "RESULT") return "결과";
  return undefined;
}
