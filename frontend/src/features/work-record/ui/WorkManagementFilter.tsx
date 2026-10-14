import type { WorkWorkspaceScope } from "../lib/workRecordUrlState";
import { FilterCheck } from "@/shared/ui/FilterControls";

export function WorkManagementFilter({
  scope,
  onChange,
}: {
  scope: WorkWorkspaceScope;
  onChange: (scope: WorkWorkspaceScope) => void;
}) {
  return (
    <FilterCheck
      checked={scope === "MANAGEMENT"}
      className="min-h-8 cursor-pointer text-sm text-[#344138]"
      label="미완료 또는 오늘 업데이트 된 작업"
      onChange={(checked) => onChange(checked ? "MANAGEMENT" : "ALL")}
    />
  );
}
