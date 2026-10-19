import { useRef, useState } from "react";
import { pendingPhotoDocuments } from "../../lib/photoExtractionBatches";
import dynamic from "next/dynamic";
import { SalesSlipSourceRegions } from "./SalesSlipSourceRegions";
import { samePhotoRegion, type PhotoRegionDraft } from "../../lib/photoRegions";
import { SalesSlipFloatingPhoto } from "./SalesSlipFloatingPhoto";
import { SalesSlipPhotoViewer } from "./SalesSlipPhotoViewer";
import {
  Camera,
  ImagePlus,
  LoaderCircle,
  X,
  ScanLine,
  Check,
  CheckCircle2,
  CircleAlert,
  Clock3,
} from "lucide-react";
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

const photoStatuses = {
  regionsNeeded: { label: "영역 선택 필요", tone: "waiting", icon: ScanLine },
  regionsReady: { label: "영역 선택 완료", tone: "complete", icon: Check },
  regionProcessing: {
    label: "전표 찾는 중",
    tone: "processing",
    icon: LoaderCircle,
  },
  regionFailed: { label: "영역 처리 실패", tone: "error", icon: CircleAlert },
  waiting: { label: "사진 읽기 대기", tone: "waiting", icon: Clock3 },
  reading: { label: "읽어 입력 중", tone: "processing", icon: LoaderCircle },
  readFailed: { label: "사진 읽기 실패", tone: "error", icon: CircleAlert },
  review: { label: "확인 필요", tone: "review", icon: CircleAlert },
  needsAttention: { label: "확인 필요", tone: "error", icon: CircleAlert },
  saving: { label: "저장 중", tone: "processing", icon: LoaderCircle },
  saved: { label: "저장 완료", tone: "complete", icon: CheckCircle2 },
  saveUncertain: {
    label: "저장 결과 확인 필요",
    tone: "review",
    icon: CircleAlert,
  },
} as const;

const photoStatusTones = {
  waiting: "border-[#dce3df] bg-[#f3f6f4] text-[#526158]",
  processing: "border-[#c9dcf4] bg-[#edf4ff] text-[#245b94]",
  review: "border-[#efd7a8] bg-[#fff7e7] text-[#835719]",
  complete: "border-[#bedfc7] bg-[#eaf6ed] text-[#176638]",
  error: "border-[#efc8bf] bg-[#fff0eb] text-[#9b341e]",
};

const SalesSlipRegionEditor = dynamic(
  () =>
    import("./SalesSlipRegionEditor").then(
      (module) => module.SalesSlipRegionEditor,
    ),
  {
    ssr: false,
    loading: () => (
      <div
        role="status"
        className="fixed inset-0 z-[1051] flex items-center justify-center bg-black/30 text-white"
      >
        전표 영역 편집기 여는 중…
      </div>
    ),
  },
);

export function SalesSlipPhotoRegistrationDialog({
  onClose,
  onSaved,
}: {
  onClose: () => void;
  onSaved: (slip: SalesSlip) => Promise<void>;
}) {
  const photos = useSalesSlipPhotoRegistration(onSaved);
  const [regionEditor, setRegionEditor] = useState<{
    ownerId: string;
    selectedId?: string;
  } | null>(null);
  const [rotations, setRotations] = useState<Record<string, number>>({});
  const detailScrollRef = useRef<HTMLDivElement>(null);
  const photoAreaRef = useRef<HTMLDivElement>(null);
  const fileRef = useRef<HTMLInputElement>(null);
  const cameraRef = useRef<HTMLInputElement>(null);
  const selected = photos.selected;
  const remaining = photos.entries.filter(
    (entry) => entry.parentId && entry.save !== "saved",
  ).length;
  const documentsPendingExtraction = pendingPhotoDocuments(photos.entries);
  const originals = photos.entries.filter((entry) => !entry.parentId);
  const documents = photos.entries.filter((entry) => entry.parentId);
  const children = photos.entries.filter(
    (entry) => entry.parentId === selected?.id,
  );
  const parent = photos.entries.find(
    (entry) => entry.id === selected?.parentId,
  );
  const selectedFamily = selected
    ? photos.entries.filter(
        (entry) => entry.id === selected.id || entry.parentId === selected.id,
      )
    : [];
  const cannotRemove = selectedFamily.some(
    (entry) => entry.save !== "draft" || entry.pendingPayload,
  );
  const blocking = photos.busy !== null && photos.busy !== "read";
  const regionOwner = photos.entries.find(
    (entry) => entry.id === regionEditor?.ownerId,
  );
  const regionEntries = regionOwner
    ? photos.entries.filter((entry) => entry.parentId === regionOwner.id)
    : [];
  const editableRegions: PhotoRegionDraft[] = regionEntries.flatMap((entry) =>
    entry.region
      ? [
          {
            id: entry.id,
            region: entry.region,
            locked: entry.save !== "draft" || Boolean(entry.pendingPayload),
          },
        ]
      : [],
  );
  function openRegionEditor(id?: string) {
    if (!selected || photos.busy) return;
    const ownerId = selected.parentId ?? selected.id;
    setRegionEditor({
      ownerId,
      selectedId: id ?? (selected.parentId ? selected.id : children[0]?.id),
    });
  }
  async function applyDocumentRegions(drafts: PhotoRegionDraft[]) {
    if (!regionOwner) return;
    const changes = regionEntries.filter(
      (entry) =>
        !drafts.some(
          (draft) =>
            draft.id === entry.id &&
            samePhotoRegion(entry.region, draft.region),
        ),
    );
    if (
      changes.length &&
      !window.confirm(
        "영역을 변경하거나 제거한 전표의 입력 내용이 초기화됩니다. 적용할까요?",
      )
    )
      return;
    if (
      await photos.applyDocumentRegions(
        regionOwner.id,
        drafts,
        Math.max(0, rotations[regionOwner.id] ?? 0),
      )
    )
      setRegionEditor(null);
  }
  const regionProcessingMode = regionEditor ? "manual" : "automatic";
  const regionProcessingLabel =
    regionProcessingMode === "manual" ? "전표 사진 만드는 중" : "전표 찾는 중";
  const processingPhotoIds =
    photos.busy === "prepare"
      ? (photos.regionProcessingProgress?.photoIds ??
        (regionEditor ? [regionEditor.ownerId] : []))
      : [];
  const buttonBaseClass =
    "inline-flex h-8 items-center justify-center gap-2 rounded-md border px-3 text-sm font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#159447] disabled:opacity-60";
  const buttonClass = `${buttonBaseClass} border-[#cfd8cc] bg-white text-[#29422e] hover:bg-[#f7faf6]`;
  const primaryButtonClass = `${buttonBaseClass} border-[#159447] bg-[#159447] text-white hover:bg-[#10783a]`;

  function requestClose() {
    if (blocking) return;
    if (
      (remaining ||
        originals.some(
          (entry) =>
            !photos.entries.some((child) => child.parentId === entry.id),
        )) &&
      !window.confirm("저장하지 않은 사진과 입력 내용이 사라집니다. 닫을까요?")
    )
      return;
    photos.cancelReading();
    onClose();
  }
  function confirmAndExtractSelectedDocument() {
    if (
      selected?.recognized &&
      !window.confirm(
        "사진을 다시 읽으면 입력한 거래처와 품목이 바뀝니다. 다시 읽을까요?",
      )
    )
      return;
    void photos.extractSelectedDocument();
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
              자동 입력을 누르면 전표 찾기부터 내용 입력까지 진행합니다. 입력
              내용을 확인하고 저장해주세요.
            </p>
          </div>
          <button
            type="button"
            aria-label="닫기"
            disabled={blocking}
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
              disabled={photos.busy !== null || originals.length >= 10}
              onClick={() => cameraRef.current?.click()}
            >
              <Camera className="h-4 w-4" aria-hidden="true" />
              사진 촬영
            </button>
            <button
              type="button"
              className={originals.length ? buttonClass : primaryButtonClass}
              disabled={photos.busy !== null || originals.length >= 10}
              onClick={() => fileRef.current?.click()}
            >
              <ImagePlus className="h-4 w-4" aria-hidden="true" />
              사진 추가
            </button>
            <button
              type="button"
              className={primaryButtonClass}
              disabled={
                photos.busy !== null ||
                (!photos.photosWithoutDocumentRegionsCount &&
                  !documentsPendingExtraction.length)
              }
              onClick={() => {
                if (
                  documentsPendingExtraction.some(
                    (entry) => entry.recognized,
                  ) &&
                  !window.confirm(
                    "다시 읽으면 이전에 확인한 입력 내용이 바뀝니다. 계속할까요?",
                  )
                )
                  return;
                void photos.automaticInput(rotations);
              }}
            >
              {photos.busy === "prepare" || photos.busy === "read" ? (
                <LoaderCircle
                  className="h-4 w-4 animate-spin motion-reduce:animate-none"
                  aria-hidden="true"
                />
              ) : null}
              자동 입력
            </button>
            {photos.busy === "read" ? (
              <button
                type="button"
                className={buttonClass}
                disabled={photos.cancelling}
                onClick={() => void photos.cancelReading()}
              >
                {photos.cancelling ? "중지 중…" : "자동 입력 중지"}
              </button>
            ) : null}
          </div>
          <p className="text-xs text-[#5c6a60]" role="status">
            원본 사진 {originals.length}/10장 · 전표 {documents.length}개 · 저장
            완료 {documents.length - remaining}건
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
            regionProcessingMode={regionProcessingMode}
            regionProcessingProgress={
              photos.regionProcessingProgress ?? undefined
            }
            extractionProgress={photos.extractionProgress ?? undefined}
            readingCount={
              photos.entries.filter((entry) => entry.read === "reading").length
            }
          />
        ) : null}
        {photos.busy === "read" ? (
          <p
            role="note"
            className="shrink-0 border-b border-[#dbe8dc] bg-white px-4 py-2 text-xs text-[#526158]"
          >
            내용 입력 중에도 목록에서 원본과 잘라낸 전표를 확인할 수 있습니다.
            입력값 편집은 자동 입력이 끝나면 가능합니다.
          </p>
        ) : null}
        {selected ? (
          <div className="grid min-h-0 flex-1 grid-rows-[auto_minmax(0,1fr)] lg:grid-cols-[14rem_minmax(0,1fr)] lg:grid-rows-1">
            <nav
              aria-label="등록할 전표 사진"
              className="flex gap-2 overflow-x-auto border-b border-[#dbe8dc] p-3 lg:block lg:space-y-2 lg:overflow-y-auto lg:border-r lg:border-b-0"
            >
              {photos.entries.map((entry) => {
                const status = !entry.parentId
                  ? processingPhotoIds.includes(entry.id)
                    ? "regionProcessing"
                    : entry.error
                      ? "regionFailed"
                      : photos.entries.some(
                            (child) => child.parentId === entry.id,
                          )
                        ? "regionsReady"
                        : "regionsNeeded"
                  : entry.save === "saved"
                    ? "saved"
                    : entry.save === "saving"
                      ? "saving"
                      : entry.pendingPayload
                        ? "saveUncertain"
                        : entry.read === "reading"
                          ? "reading"
                          : entry.read === "failed"
                            ? "readFailed"
                            : entry.error
                              ? "needsAttention"
                              : entry.read === "ready"
                                ? "review"
                                : "waiting";
                const reviewCount =
                  Object.keys(entry.form.fieldReviews ?? {}).length +
                  entry.form.items.reduce(
                    (sum, item) =>
                      sum + Object.keys(item.fieldReviews ?? {}).length,
                    0,
                  );
                const badge = photoStatuses[status];
                const StatusIcon = badge.icon;
                return (
                  <button
                    key={entry.id}
                    type="button"
                    aria-current={entry.id === selected.id ? "true" : undefined}
                    disabled={blocking}
                    className={`w-48 min-w-40 shrink-0 rounded-md border p-3 text-left ${entry.parentId ? "ml-4 lg:w-[calc(100%-1rem)]" : "lg:w-full"} ${entry.id === selected.id ? "border-[#159447] bg-[#e8f6eb]" : "border-[#d9e3db] bg-white"} disabled:opacity-60`}
                    onClick={() => photos.setSelectedId(entry.id)}
                  >
                    <span
                      title={entry.photo.fileName}
                      className="block truncate text-sm font-semibold text-[#17251b]"
                    >
                      {entry.parentId
                        ? "└ "
                        : `${originals.findIndex((original) => original.id === entry.id) + 1}. `}
                      {entry.photo.fileName}
                    </span>
                    <span className="mt-2 flex flex-wrap items-center gap-1.5">
                      {!entry.parentId ? (
                        <span className="text-xs text-[#526158]">원본</span>
                      ) : null}
                      <span
                        className={`inline-flex max-w-full items-center gap-1 rounded border px-1.5 py-0.5 text-xs font-medium ${photoStatusTones[badge.tone]}`}
                      >
                        <StatusIcon
                          className={`h-3.5 w-3.5 shrink-0 ${badge.tone === "processing" ? "animate-spin motion-reduce:animate-none" : ""}`}
                          aria-hidden="true"
                        />
                        {status === "regionProcessing"
                          ? regionProcessingLabel
                          : status === "review"
                            ? reviewCount > 0
                              ? `확인 필요 ${reviewCount}건`
                              : "저장 대기"
                            : badge.label}
                      </span>
                    </span>
                  </button>
                );
              })}
            </nav>
            <div className="relative min-h-0 min-w-0">
              <div ref={detailScrollRef} className="h-full overflow-y-auto">
                <section
                  aria-label="선택한 전표 사진"
                  className="mx-5 mt-5 rounded-md border border-[#cfe0d2] bg-white p-4"
                >
                  <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
                    <h3 className="min-w-0 text-sm font-bold break-all text-[#26352b]">
                      {selected.photo.fileName}
                    </h3>
                    <div className="flex flex-wrap gap-2">
                      {!selected.parentId ? (
                        <label className="flex items-center gap-2 text-xs text-[#526158]">
                          회전
                          <select
                            aria-label="전표 자동 찾기 시 사진 회전"
                            value={rotations[selected.id] ?? -1}
                            disabled={photos.busy !== null}
                            onChange={(event) =>
                              setRotations((current) => ({
                                ...current,
                                [selected.id]: Number(event.target.value),
                              }))
                            }
                            className="h-8 rounded-md border border-[#cfd8cc] bg-white px-2"
                          >
                            <option value={-1}>자동</option>
                            {[0, 90, 180, 270].map((degrees) => (
                              <option key={degrees} value={degrees}>
                                {degrees}° 시계 방향
                              </option>
                            ))}
                          </select>
                        </label>
                      ) : null}
                      {!selected.parentId ? (
                        <>
                          <button
                            type="button"
                            className={
                              children.length ? buttonClass : primaryButtonClass
                            }
                            disabled={photos.busy !== null || cannotRemove}
                            onClick={() => {
                              if (
                                children.length &&
                                !window.confirm(
                                  "전표를 다시 찾으면 기존 전표 사진과 입력 내용이 바뀝니다. 계속할까요?",
                                )
                              )
                                return;
                              void photos.detectSelectedPhotoRegions(
                                rotations[selected.id] ?? -1,
                              );
                            }}
                          >
                            {processingPhotoIds.includes(selected.id) &&
                            !regionEditor
                              ? "전표 찾는 중…"
                              : "전표 자동 찾기"}
                          </button>
                          <button
                            type="button"
                            className={buttonClass}
                            disabled={photos.busy !== null}
                            onClick={() => openRegionEditor()}
                          >
                            {children.length
                              ? "전표 영역 수정"
                              : "직접 영역 선택"}
                          </button>
                        </>
                      ) : (
                        <button
                          type="button"
                          className={buttonClass}
                          disabled={photos.busy !== null}
                          onClick={() => openRegionEditor(selected.id)}
                        >
                          전표 영역 수정
                        </button>
                      )}
                      {selected.parentId ? (
                        <button
                          type="button"
                          className={
                            selected.read === "ready"
                              ? buttonClass
                              : primaryButtonClass
                          }
                          disabled={photos.busy !== null || cannotRemove}
                          onClick={confirmAndExtractSelectedDocument}
                        >
                          {selected.read === "reading" ? (
                            <LoaderCircle
                              className="h-4 w-4 animate-spin motion-reduce:animate-none"
                              aria-hidden="true"
                            />
                          ) : null}
                          {selected.read === "reading"
                            ? "전표 읽어 입력 중…"
                            : selected.read === "ready"
                              ? "다시 읽어 입력"
                              : "사진 읽어 입력"}
                        </button>
                      ) : null}
                      <button
                        type="button"
                        className={buttonClass}
                        disabled={photos.busy !== null || cannotRemove}
                        onClick={photos.removeSelected}
                      >
                        사진 제거
                      </button>
                    </div>
                  </div>
                  {!selected.parentId ? (
                    <p className="mb-3 text-sm text-[#526158]">
                      {children.length
                        ? "전표 영역을 선택했습니다. 자동 입력을 누르면 내용을 읽어 입력합니다. 영역이 맞지 않으면 전표 영역 수정으로 조정하세요."
                        : "자동 입력을 누르면 전표를 찾아 내용을 입력합니다. 직접 영역 선택으로 전표 영역을 먼저 지정할 수도 있습니다."}
                    </p>
                  ) : null}
                  <div
                    ref={photoAreaRef}
                    className={`grid gap-4 ${selected.parentId ? "grid-cols-2" : "sm:grid-cols-2"}`}
                  >
                    {(selected.parentId
                      ? [parent, selected]
                      : [selected, ...children]
                    )
                      .filter((entry) => entry != null)
                      .map((entry) => (
                        <figure key={entry.id} className="min-w-0">
                          <figcaption className="mb-2 text-xs font-semibold break-all text-[#526158]">
                            {entry.parentId ? "잘라낸 전표" : "원본"} ·{" "}
                            {entry.photo.fileName}
                          </figcaption>
                          {!entry.parentId ? (
                            <SalesSlipSourceRegions
                              photo={entry.photo}
                              regions={photos.entries
                                .filter(
                                  (child) =>
                                    child.parentId === entry.id && child.region,
                                )
                                .map((child) => ({
                                  id: child.id,
                                  region: child.region!,
                                  locked:
                                    child.save !== "draft" ||
                                    Boolean(child.pendingPayload),
                                }))}
                              disabled={photos.busy !== null}
                              onSelect={(id) => openRegionEditor(id)}
                            />
                          ) : (
                            <SalesSlipPhotoViewer
                              src={`data:${entry.photo.contentType};base64,${entry.photo.contentBase64}`}
                              alt={
                                entry.parentId
                                  ? "잘라낸 전표 사진"
                                  : "전표 사진 원본"
                              }
                              width={900}
                              height={600}
                              className="max-h-80 w-full rounded-md bg-[#f7faf6] object-contain"
                            />
                          )}
                        </figure>
                      ))}
                  </div>

                  {!selected.parentId && selected.error ? (
                    <p role="alert" className="mt-3 text-sm text-[#9b341e]">
                      {selected.error}
                    </p>
                  ) : null}
                  <p className="mt-2 text-xs text-[#5c6a60]">
                    사진 ID: {selected.id.slice(0, 12)}
                  </p>
                  {selected.recognition ? (
                    <p className="mt-2 text-sm text-[#526158]" role="status">
                      인식 신뢰도 {selected.recognition.confidence}/100 · AI
                      자체 평가
                      {selected.recognition.imageLongEdge
                        ? ` · 긴 변 ${selected.recognition.imageLongEdge}px`
                        : ""}
                      {selected.recognition.attempts === 2
                        ? " · 고해상도 재인식 시도"
                        : ""}
                    </p>
                  ) : null}
                  {selected.parentId && selected.notes.length ? (
                    <ul className="mt-3 list-inside list-disc space-y-1 text-sm text-[#526158]">
                      {selected.notes.map((note, index) => (
                        <li key={index}>{note}</li>
                      ))}
                    </ul>
                  ) : null}
                  {selected.pendingPayload ? (
                    <p role="status" className="mt-3 text-sm text-[#9b4f19]">
                      저장 여부를 확인하지 못했습니다. 다시 저장을 눌러 결과를
                      확인하세요.
                    </p>
                  ) : null}
                </section>
                {!selected.parentId ? null : selected.save === "saved" ? (
                  <div
                    role="status"
                    className="m-5 rounded-md border border-[#cfe0d2] bg-white p-4 text-sm text-[#10783a]"
                  >
                    전표 #{selected.slipId} 저장 완료. 다른 전표를 선택해 계속
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
                        : "전표 저장"
                    }
                    pendingLabel={
                      photos.busy === "read"
                        ? "전표 읽어 입력 중"
                        : photos.busy === "prepare"
                          ? regionProcessingLabel
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
                    onAutomaticAllocation={
                      photos.actions.useAutomaticAllocation
                    }
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
              {selected.parentId ? (
                <SalesSlipFloatingPhoto
                  key={selected.id}
                  src={`data:${selected.photo.contentType};base64,${selected.photo.contentBase64}`}
                  scrollRoot={detailScrollRef}
                  photoArea={photoAreaRef}
                />
              ) : null}
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
        {regionOwner && regionEditor ? (
          <SalesSlipRegionEditor
            key={regionOwner.id}
            photo={regionOwner.photo}
            regions={editableRegions}
            selectedId={regionEditor.selectedId}
            busy={photos.busy !== null}
            error={regionOwner.error}
            onApply={applyDocumentRegions}
            onClose={() => setRegionEditor(null)}
          />
        ) : null}
      </DialogContent>
    </Dialog>
  );
}
