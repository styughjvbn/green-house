import { useRef } from "react";
import { SalesSlipPhotoViewer } from "./SalesSlipPhotoViewer";
import { Camera, ImagePlus, LoaderCircle, X } from "lucide-react";
import type { SalesSlip } from "@/entities/farm/types";
import {
  Dialog,
  DialogContent,
  DialogTitle,
} from "@/shared/ui/primitives/dialog";
import { useSalesSlipPhotoRegistration } from "../../model/useSalesSlipPhotoRegistration";
import {
  calculateSalesTotal,
  salesAllocationIssue,
  selectedSalesGroups,
} from "../../lib/salesForm";
import { SalesSlipCreateForm } from "./SalesSlipCreateForm";
import { SalesSlipPhotoProgress } from "./SalesSlipPhotoProgress";

export function SalesSlipPhotoRegistrationDialog({
  onClose,
  onSaved,
}: {
  onClose: () => void;
  onSaved: (slip: SalesSlip) => Promise<void>;
}) {
  const photos = useSalesSlipPhotoRegistration(onSaved);
  const fileRef = useRef<HTMLInputElement>(null);
  const cameraRef = useRef<HTMLInputElement>(null);
  const selected = photos.selected;
  const remaining = photos.entries.filter(
    (entry) => entry.save !== "saved",
  ).length;
  const unreadEntries = photos.entries.filter(
    (entry) =>
      ["waiting", "failed"].includes(entry.read) &&
      entry.save === "draft" &&
      !entry.pendingPayload,
  );
  const buttonClass =
    "inline-flex h-8 items-center justify-center gap-2 rounded-md border border-[#cfd8cc] bg-white px-3 text-sm font-semibold text-[#29422e] hover:bg-[#f7faf6] focus-visible:outline-2 focus-visible:outline-[#159447] disabled:opacity-60";

  function requestClose() {
    if (photos.busy === "save" || photos.busy === "upload") return;
    if (
      remaining &&
      !window.confirm("저장하지 않은 사진과 입력 내용이 사라집니다. 닫을까요?")
    )
      return;
    photos.cancelReading();
    onClose();
  }
  function readSelected() {
    if (
      selected?.recognized &&
      !window.confirm(
        "사진을 다시 읽으면 입력한 거래처와 품목이 바뀝니다. 다시 읽을까요?",
      )
    )
      return;
    void photos.readSelected();
  }
  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open) requestClose();
      }}
    >
      <DialogContent
        showCloseButton={false}
        aria-describedby={undefined}
        className="flex h-[calc(100dvh-2rem)] max-h-[calc(100dvh-2rem)] max-w-6xl flex-col overflow-hidden rounded-md bg-[#f5fbf5]"
        style={{
          top: "1rem",
          bottom: "1rem",
          left: "1rem",
          right: "1rem",
          margin: "auto",
          transform: "none",
          translate: "none",
        }}
        onEscapeKeyDown={(event) => {
          event.preventDefault();
          requestClose();
        }}
        onInteractOutside={(event) => event.preventDefault()}
        onCloseAutoFocus={(event) => event.preventDefault()}
      >
        <header className="flex shrink-0 items-start justify-between gap-3 border-b border-[#dbe8dc] bg-white px-5 py-3">
          <div>
            <DialogTitle className="text-xl font-semibold text-[#17251b]">
              사진으로 등록
            </DialogTitle>
            <p className="mt-1 text-sm text-[#5c6a60]">
              사진 한 장당 전표 한 건을 등록합니다. 내용을 읽은 뒤
              날짜·거래처·품목을 확인하고 저장하세요.
            </p>
          </div>
          <button
            type="button"
            aria-label="닫기"
            disabled={photos.busy === "save" || photos.busy === "upload"}
            onClick={requestClose}
            className="flex h-8 w-8 shrink-0 items-center justify-center rounded border border-[#d9dfda] text-[#435047] disabled:opacity-60"
          >
            <X className="h-4 w-4" aria-hidden="true" />
          </button>
        </header>
        <div className="flex shrink-0 flex-wrap items-center justify-between gap-3 border-b border-[#dbe8dc] px-4 py-3">
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              className={buttonClass}
              disabled={photos.busy !== null || photos.entries.length >= 10}
              onClick={() => cameraRef.current?.click()}
            >
              <Camera className="h-4 w-4" aria-hidden="true" />
              사진 촬영
            </button>
            <button
              type="button"
              className={buttonClass}
              disabled={photos.busy !== null || photos.entries.length >= 10}
              onClick={() => fileRef.current?.click()}
            >
              <ImagePlus className="h-4 w-4" aria-hidden="true" />
              사진 추가
            </button>
            <button
              type="button"
              className={buttonClass}
              disabled={photos.busy !== null || !unreadEntries.length}
              onClick={() => {
                if (
                  unreadEntries.some((entry) => entry.recognized) &&
                  !window.confirm(
                    "다시 읽으면 이전에 확인한 입력 내용이 바뀝니다. 계속할까요?",
                  )
                )
                  return;
                void photos.readAll();
              }}
            >
              미처리 사진 자동 입력
            </button>
            {photos.busy === "read" ? (
              <button
                type="button"
                className={buttonClass}
                disabled={photos.cancelling}
                onClick={() => void photos.cancelReading()}
              >
                {photos.cancelling ? "중지 중…" : "읽기 중지"}
              </button>
            ) : null}
          </div>
          <p className="text-xs text-[#5c6a60]" role="status">
            사진 {photos.entries.length}장 · 저장 완료{" "}
            {photos.entries.length - remaining}건 / 최대 10장
          </p>
          <input
            ref={fileRef}
            type="file"
            multiple
            accept="image/jpeg,image/png"
            className="sr-only"
            tabIndex={-1}
            aria-label="전표 사진 추가"
            disabled={photos.busy !== null}
            onChange={(event) => {
              void photos.addFiles(Array.from(event.target.files ?? []));
              event.target.value = "";
            }}
          />
          <input
            ref={cameraRef}
            type="file"
            accept="image/jpeg,image/png"
            capture="environment"
            className="sr-only"
            tabIndex={-1}
            aria-label="전표 사진 촬영"
            disabled={photos.busy !== null}
            onChange={(event) => {
              void photos.addFiles(Array.from(event.target.files ?? []));
              event.target.value = "";
            }}
          />
          {photos.uploadError ? (
            <p
              role="alert"
              className="basis-full text-sm whitespace-pre-line text-[#9b341e]"
            >
              {photos.uploadError}
            </p>
          ) : null}
        </div>
        {photos.busy ? (
          <SalesSlipPhotoProgress
            key={photos.cancelling ? "cancel" : photos.busy}
            operation={photos.cancelling ? "cancel" : photos.busy}
            readingCount={
              photos.entries.filter((entry) => entry.read === "reading").length
            }
          />
        ) : null}
        {selected ? (
          <div className="grid min-h-0 flex-1 grid-rows-[auto_minmax(0,1fr)] lg:grid-cols-[14rem_minmax(0,1fr)] lg:grid-rows-1">
            <nav
              aria-label="등록할 전표 사진"
              className="flex gap-2 overflow-x-auto border-b border-[#dbe8dc] p-3 lg:block lg:space-y-2 lg:overflow-y-auto lg:border-r lg:border-b-0"
            >
              {photos.entries.map((entry, index) => (
                <button
                  key={entry.id}
                  type="button"
                  aria-current={entry.id === selected.id ? "true" : undefined}
                  disabled={photos.busy !== null}
                  className={`min-w-40 rounded-md border p-3 text-left lg:w-full ${entry.id === selected.id ? "border-[#159447] bg-[#e8f6eb]" : "border-[#d9e3db] bg-white"} disabled:opacity-60`}
                  onClick={() => photos.setSelectedId(entry.id)}
                >
                  <span className="block text-sm font-semibold break-all text-[#17251b]">
                    {index + 1}. {entry.photo.fileName}
                  </span>
                  <span
                    className={`mt-1 flex items-center gap-1.5 text-xs ${entry.error ? "text-[#9b341e]" : "text-[#526158]"}`}
                  >
                    {entry.save === "saving" || entry.read === "reading" ? (
                      <LoaderCircle
                        className="h-3.5 w-3.5 shrink-0 animate-spin motion-reduce:animate-none"
                        aria-hidden="true"
                      />
                    ) : null}
                    {entry.save === "saved"
                      ? "저장 완료"
                      : entry.save === "saving"
                        ? "저장 중"
                        : entry.pendingPayload
                          ? "저장 응답 확인 필요"
                          : entry.read === "ready"
                            ? "검토 후 저장"
                            : entry.read === "reading"
                              ? "내용 읽는 중"
                              : entry.read === "failed"
                                ? "자동 입력 실패"
                                : "자동 입력 대기"}
                  </span>
                </button>
              ))}
            </nav>
            <div className="min-h-0 min-w-0 overflow-y-auto">
              <section
                aria-label="선택한 전표 사진 원본"
                className="mx-5 mt-5 rounded-md border border-[#cfe0d2] bg-white p-4"
              >
                <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
                  <h3 className="min-w-0 text-sm font-bold break-all text-[#26352b]">
                    {selected.photo.fileName}
                  </h3>
                  <div className="flex flex-wrap gap-2">
                    <button
                      type="button"
                      className={buttonClass}
                      disabled={
                        photos.busy !== null ||
                        selected.save === "saved" ||
                        Boolean(selected.pendingPayload)
                      }
                      onClick={readSelected}
                    >
                      {selected.read === "reading" ? (
                        <LoaderCircle
                          className="h-4 w-4 animate-spin motion-reduce:animate-none"
                          aria-hidden="true"
                        />
                      ) : null}
                      {selected.read === "reading"
                        ? "자동 입력 중…"
                        : selected.read === "ready"
                          ? "자동 입력 재실행"
                          : "자동 입력"}
                    </button>
                    <button
                      type="button"
                      className={buttonClass}
                      disabled={
                        photos.busy !== null ||
                        selected.save === "saved" ||
                        Boolean(selected.pendingPayload)
                      }
                      onClick={photos.removeSelected}
                    >
                      사진 제거
                    </button>
                  </div>
                </div>
                <SalesSlipPhotoViewer
                  src={`data:${selected.photo.contentType};base64,${selected.photo.contentBase64}`}
                  alt="확인할 전표 사진 원본"
                  width={900}
                  height={600}
                  className="max-h-72 w-full rounded-md bg-[#f7faf6] object-contain"
                />
                <p className="mt-2 text-xs text-[#5c6a60]">
                  사진 ID: {selected.id.slice(0, 12)}
                </p>
                {selected.recognition ? (
                  <p className="mt-2 text-sm text-[#526158]" role="status">
                    인식 신뢰도 {selected.recognition.confidence}/100 · AI 자체
                    평가
                    {selected.recognition.imageLongEdge
                      ? ` · 긴 변 ${selected.recognition.imageLongEdge}px`
                      : ""}
                    {selected.recognition.attempts === 2
                      ? " · 고해상도 재인식 시도"
                      : ""}
                  </p>
                ) : null}
                {selected.notes.length ? (
                  <ul className="mt-3 list-inside list-disc space-y-1 text-sm text-[#526158]">
                    {selected.notes.map((note, index) => (
                      <li key={index}>{note}</li>
                    ))}
                  </ul>
                ) : null}
                {selected.pendingPayload ? (
                  <p role="status" className="mt-3 text-sm text-[#9b4f19]">
                    저장 응답을 확인하지 못했습니다. 같은 입력으로 다시 저장해
                    결과를 확인하세요.
                  </p>
                ) : null}
              </section>
              {selected.save === "saved" ? (
                <div
                  role="status"
                  className="m-5 rounded-md border border-[#cfe0d2] bg-white p-4 text-sm text-[#10783a]"
                >
                  전표 #{selected.slipId} 저장 완료. 다른 사진을 선택해 계속
                  등록하세요.
                  {selected.error ? (
                    <p className="mt-2 text-[#9b341e]">{selected.error}</p>
                  ) : null}
                </div>
              ) : (
                <SalesSlipCreateForm
                  key={selected.id}
                  embedded
                  readOnly={
                    photos.busy !== null || Boolean(selected.pendingPayload)
                  }
                  errorMessage={selected.error}
                  form={selected.form}
                  onConfirmReview={photos.confirmReview}
                  mode="create"
                  saving={photos.busy !== null}
                  totalAmount={calculateSalesTotal(selected.form.items)}
                  submitLabel={
                    selected.pendingPayload
                      ? "저장 결과 다시 확인"
                      : "확인한 전표 저장"
                  }
                  pendingLabel={
                    photos.busy === "read"
                      ? "내용 읽는 중"
                      : photos.busy === "upload"
                        ? "사진 추가 중"
                        : "저장 중"
                  }
                  onConnectGroups={photos.connectGroups}
                  selectedGroups={selectedSalesGroups(selected.form.items)}
                  allocationIssue={salesAllocationIssue(
                    selected.form.items,
                    selected.form.recordOnly,
                  )}
                  onSelectGroups={photos.actions.selectSalesGroups}
                  onRemoveGroup={photos.actions.removeSalesGroup}
                  onAutomaticAllocation={photos.actions.useAutomaticAllocation}
                  onResetAllocation={photos.actions.resetSalesAllocation}
                  onAddItem={photos.actions.addSalesItem}
                  onAllocationChange={photos.actions.updateAllocation}
                  onChange={photos.actions.updateSalesForm}
                  onRemoveItem={photos.actions.removeSalesItem}
                  onSalesTypeChange={photos.actions.selectSalesType}
                  onRecordingModeChange={photos.setRecordingMode}
                  onUpdateItem={photos.actions.updateItem}
                  onCancel={requestClose}
                  onSubmit={(event) => {
                    event.preventDefault();
                    void photos.saveSelected();
                  }}
                />
              )}
            </div>
          </div>
        ) : (
          <div className="flex min-h-0 flex-1 flex-col items-center justify-center gap-2 p-5 text-center text-[#5c6a60]">
            <Camera className="h-10 w-10 text-[#159447]" aria-hidden="true" />
            <p className="text-base font-semibold">전표 사진을 추가하세요.</p>
            <p className="text-sm">
              촬영하거나 여러 사진을 선택할 수 있습니다. JPEG·PNG, 사진당 최대
              8MB.
            </p>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
