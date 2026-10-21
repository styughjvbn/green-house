import { Camera, PenLine } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogTitle,
  DialogDescription,
} from "@/shared/ui/primitives/dialog";

export function SalesSlipRegistrationMethodDialog({
  onSelect,
  onClose,
}: {
  onSelect: (method: "manual" | "photo") => void;
  onClose: () => void;
}) {
  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open) onClose();
      }}
    >
      <DialogContent
        className="max-w-xl rounded-md p-5"
        onCloseAutoFocus={(event) => event.preventDefault()}
      >
        <DialogTitle className="pr-10 text-xl font-semibold text-[#17251b]">
          전표 등록
        </DialogTitle>
        <DialogDescription className="mt-2 text-sm text-[#5c6a60]">
          등록 방법을 선택하세요.
        </DialogDescription>
        <div className="mt-5 grid gap-3 sm:grid-cols-2">
          <button
            type="button"
            onClick={() => onSelect("manual")}
            className="rounded-md border border-[#cfe0d2] p-4 text-left hover:bg-[#f5fbf5] focus-visible:outline-2 focus-visible:outline-[#159447]"
          >
            <PenLine
              className="mb-3 h-6 w-6 text-[#10783a]"
              aria-hidden="true"
            />
            <span className="block text-base font-bold text-[#17251b]">
              직접 입력
            </span>
            <span className="mt-2 block text-sm text-[#5c6a60]">
              거래처와 판매 품목을 직접 입력합니다.
            </span>
          </button>
          <button
            type="button"
            onClick={() => onSelect("photo")}
            className="rounded-md border border-[#cfe0d2] p-4 text-left hover:bg-[#f5fbf5] focus-visible:outline-2 focus-visible:outline-[#159447]"
          >
            <Camera
              className="mb-3 h-6 w-6 text-[#10783a]"
              aria-hidden="true"
            />
            <span className="block text-base font-bold text-[#17251b]">
              사진으로 등록
            </span>
            <span className="mt-2 block text-sm text-[#5c6a60]">
              여러 전표 사진을 읽고 사진별로 확인해 저장합니다.
            </span>
          </button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
