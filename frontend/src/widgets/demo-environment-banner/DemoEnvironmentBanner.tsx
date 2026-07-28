import { RotateCcw } from "lucide-react";

export function DemoEnvironmentBanner() {
  return (
    <div
      className="flex shrink-0 items-center justify-center gap-2 bg-[#7a3e00] py-0.5 text-center text-xs font-semibold text-white"
      role="status"
    >
      <RotateCcw className="h-4 w-4 shrink-0" aria-hidden="true" />
      <span>
        데모 환경입니다. 입력한 데이터는 예고 없이 초기화될 수 있으며 실제
        업무에는 반영되지 않습니다.
      </span>
    </div>
  );
}
