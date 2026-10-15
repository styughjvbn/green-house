import { sha256 } from "@noble/hashes/sha2.js";
import { useEffect, useRef, useState, type SetStateAction } from "react";
import type { SalesSlip, SalesOrchidGroupOption } from "@/entities/farm/types";
import { ApiError } from "@/shared/api/error";
import { createUuid } from "@/shared/lib/id";
import { createPendingCreationRequestKey } from "@/shared/lib/pendingCreationRequestKey";
import { useRuntimeContext } from "@/shared/runtime/RuntimeContext";
import {
  createSalesSlip,
  openSalesSlipPhotoSession,
  closeSalesSlipPhotoSession,
  removeSalesSlipSessionPhoto,
  readSalesSlipPhotos,
} from "../api/salesApi";
import type { CreateSalesSlipPayload, SalesSlipPhotoInput } from "../api/types";
import {
  createInitialSalesForm,
  distributeSalesItems,
  salesAllocationIssue,
  toCreateSalesSlipPayload,
  withSalesRecordingMode,
} from "../lib/salesForm";
import {
  withRecognizedSalesSlip,
  clearEditedSalesReviews,
  confirmSalesReview,
} from "../lib/salesPhotoForm";
import { createSalesSlipDraftActions } from "./salesSlipDraftActions";
import type { SalesSlipForm, SalesReviewField } from "./types";

type PhotoEntry = {
  id: string;
  photo: SalesSlipPhotoInput;
  form: SalesSlipForm;
  read: "waiting" | "reading" | "ready" | "failed";
  save: "draft" | "saving" | "saved";
  slipId?: number;
  notes: string[];
  recognized: boolean;
  recognition: {
    confidence: number;
    imageLongEdge?: number;
    attempts?: number;
  } | null;
  feedback: string;
  feedbackHistory: { text: string; requestId: string }[];
  error: string | null;
  pendingPayload: CreateSalesSlipPayload | null;
  requestKey: ReturnType<typeof createPendingCreationRequestKey>;
};
const MAX_PHOTOS = 10;
const message = (error: unknown) =>
  error instanceof Error ? error.message : "요청 중 문제가 발생했습니다.";

export function useSalesSlipPhotoRegistration(
  onSaved: (slip: SalesSlip) => Promise<void>,
) {
  const { businessDate } = useRuntimeContext();
  const [entries, setEntries] = useState<PhotoEntry[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [busy, setBusy] = useState<"upload" | "read" | "save" | null>(null);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const sessionId = useRef<string | null>(null);
  const activeRequest = useRef<AbortController | null>(null);
  const running = useRef(false);
  const mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
      activeRequest.current?.abort();
      if (sessionId.current) {
        void closeSalesSlipPhotoSession(sessionId.current).catch(() => {});
        sessionId.current = null;
      }
    };
  }, []);
  const selected =
    entries.find((entry) => entry.id === selectedId) ?? entries[0] ?? null;

  async function ensureSession(signal?: AbortSignal) {
    if (sessionId.current) return sessionId.current;
    const result = await openSalesSlipPhotoSession(signal);
    if (!result.sessionId)
      throw new Error("사진 등록 대화를 시작하지 못했습니다.");
    if (!mounted.current) {
      void closeSalesSlipPhotoSession(result.sessionId).catch(() => {});
      throw new Error("사진 등록 창이 닫혔습니다.");
    }
    sessionId.current = result.sessionId;
    return result.sessionId;
  }
  function patch(id: string, update: Partial<PhotoEntry>) {
    if (mounted.current)
      setEntries((current) =>
        current.map((entry) =>
          entry.id === id ? { ...entry, ...update } : entry,
        ),
      );
  }
  function setForm(id: string, update: SetStateAction<SalesSlipForm>) {
    if (mounted.current)
      setEntries((current) =>
        current.map((entry) =>
          entry.id === id && entry.save !== "saved" && !entry.pendingPayload
            ? {
                ...entry,
                form: clearEditedSalesReviews(
                  entry.form,
                  typeof update === "function" ? update(entry.form) : update,
                ),
                error: null,
              }
            : entry,
        ),
      );
  }
  const actions = createSalesSlipDraftActions((update) => {
    if (selected) setForm(selected.id, update);
  });

  async function addFiles(files: File[]) {
    if (running.current || !files.length) return;
    running.current = true;
    setBusy("upload");
    setUploadError(null);
    const errors: string[] = [];
    const additions: PhotoEntry[] = [];
    try {
      for (const file of files) {
        if (entries.length + additions.length >= MAX_PHOTOS) {
          errors.push(`한 번에 최대 ${MAX_PHOTOS}장까지 등록할 수 있습니다.`);
          break;
        }
        try {
          if (!["image/jpeg", "image/png"].includes(file.type))
            throw new Error("JPEG 또는 PNG 사진을 선택해주세요.");
          if (file.size === 0 || file.size > 8 * 1024 * 1024)
            throw new Error("사진은 최대 8MB까지 첨부할 수 있습니다.");
          const data = await new Promise<string>((resolve, reject) => {
            const reader = new FileReader();
            reader.onload = () => resolve(String(reader.result));
            reader.onerror = () => reject(new Error("사진을 읽지 못했습니다."));
            reader.readAsDataURL(file);
          });
          if (!mounted.current) return;
          const photo: SalesSlipPhotoInput = {
            fileName: file.name.slice(0, 255),
            contentType: file.type,
            contentBase64: data.slice(data.indexOf(",") + 1),
          };
          const digest = sha256(
            new TextEncoder().encode(JSON.stringify(photo)),
          );
          const id = Array.from(new Uint8Array(digest), (byte) =>
            byte.toString(16).padStart(2, "0"),
          ).join("");
          if (
            entries.some((entry) => entry.id === id) ||
            additions.some((entry) => entry.id === id)
          )
            continue;
          additions.push({
            id,
            photo,
            form: withSalesRecordingMode(
              createInitialSalesForm(businessDate),
              true,
            ),
            read: "waiting",
            save: "draft",
            notes: [],
            recognized: false,
            recognition: null,
            feedback: "",
            feedbackHistory: [],
            error: null,
            pendingPayload: null,
            requestKey: createPendingCreationRequestKey(
              `greenhouse:sales-photo-create:v1:${id}`,
              createUuid,
              () => window.sessionStorage,
            ),
          });
        } catch (error) {
          errors.push(`${file.name}: ${message(error)}`);
        }
      }
      if (!mounted.current) return;
      setEntries((current) => [...current, ...additions]);
      if (!selectedId && additions[0]) setSelectedId(additions[0].id);
      setUploadError(errors.length ? errors.join("\n") : null);
      if (additions.length) {
        try {
          await ensureSession();
        } catch (error) {
          if (mounted.current) setUploadError(message(error));
        }
      }
    } finally {
      running.current = false;
      if (mounted.current) setBusy(null);
    }
  }

  async function readEntries(targets: PhotoEntry[]) {
    if (running.current || !targets.length) return;
    running.current = true;
    setBusy("read");
    const controller = new AbortController();
    activeRequest.current = controller;
    for (const entry of targets)
      patch(entry.id, { read: "reading", error: null });
    try {
      const id = await ensureSession(controller.signal);
      const batch = await readSalesSlipPhotos(
        id,
        targets.map((entry) => ({
          photoId: entry.id,
          photo: entry.photo,
          feedback: entry.feedback.trim() || null,
        })),
        controller.signal,
      );
      if (controller.signal.aborted)
        throw new DOMException("Aborted", "AbortError");
      const rows = batch.photos ?? [];
      if (
        rows.length !== targets.length ||
        new Set(rows.map((row) => row.photoId)).size !== targets.length ||
        rows.some((row) => !targets.some((entry) => entry.id === row.photoId))
      )
        throw new Error("사진별 인식 결과를 확인하지 못했습니다.");
      for (const entry of targets) {
        const result = rows.find((row) => row.photoId === entry.id)?.result;
        if (!result) {
          patch(entry.id, {
            read: "failed",
            error:
              "사진 내용을 읽지 못했습니다. 피드백을 추가하거나 다시 촬영해주세요.",
          });
          continue;
        }
        const salesType = result.salesType ?? entry.form.salesType;
        const notes = [...(result.warnings ?? [])];
        const partnerId =
          result.partnerId != null ? String(result.partnerId) : "";
        if (!partnerId) notes.push("거래처를 직접 선택해주세요.");
        const entryActions = createSalesSlipDraftActions((update) =>
          setForm(entry.id, update),
        );
        entryActions.selectSalesType(salesType);
        setForm(entry.id, (current) =>
          withRecognizedSalesSlip(current, result, partnerId),
        );
        if (!result.saleDate) notes.push("판매일·출하일을 확인해주세요.");
        patch(entry.id, {
          read: "ready",
          recognized: true,
          recognition:
            result.confidence != null
              ? {
                  confidence: result.confidence,
                  imageLongEdge: result.imageLongEdge,
                  attempts: result.attempts,
                }
              : null,
          notes,
          error: null,
          feedback: "",
          feedbackHistory: entry.feedback.trim()
            ? [
                ...entry.feedbackHistory,
                {
                  text: entry.feedback.trim(),
                  requestId: batch.requestId ?? "",
                },
              ]
            : entry.feedbackHistory,
        });
      }
    } catch (error) {
      for (const entry of targets)
        patch(entry.id, {
          read: "failed",
          error: controller.signal.aborted
            ? "내용 읽기를 취소했습니다."
            : message(error),
        });
    } finally {
      activeRequest.current = null;
      running.current = false;
      if (mounted.current) setBusy(null);
    }
  }

  async function saveSelected() {
    if (!selected || selected.save === "saved" || running.current) return;
    let payload: CreateSalesSlipPayload;
    try {
      const issue = salesAllocationIssue(
        selected.form.items,
        selected.form.recordOnly,
      );
      if (issue) throw new Error(issue);
      payload = selected.pendingPayload ?? {
        ...toCreateSalesSlipPayload(selected.form),
        sourcePhoto: selected.photo,
      };
    } catch (error) {
      patch(selected.id, { error: message(error) });
      return;
    }
    running.current = true;
    setBusy("save");
    patch(selected.id, {
      save: "saving",
      error: null,
      pendingPayload: payload,
    });
    const key = selected.requestKey.get();
    try {
      const slip = await createSalesSlip(payload, key);
      selected.requestKey.complete(key);
      patch(selected.id, {
        save: "saved",
        slipId: slip.id,
        pendingPayload: null,
        error: null,
      });
      try {
        await onSaved(slip);
      } catch {
        patch(selected.id, {
          error:
            "전표는 저장됐지만 목록을 갱신하지 못했습니다. 목록을 다시 조회해주세요.",
        });
      }
    } catch (error) {
      // An explicit rejection can be corrected. An unknown outcome retries the exact payload and key.
      const rejected =
        error instanceof ApiError &&
        error.status >= 400 &&
        error.status < 500 &&
        error.status !== 408 &&
        error.code !== "SALES_CREATE_REQUEST_KEY_CONFLICT";
      patch(selected.id, {
        save: "draft",
        error: message(error),
        pendingPayload: rejected ? null : payload,
      });
    } finally {
      running.current = false;
      if (mounted.current) setBusy(null);
    }
  }

  async function removeSelected() {
    if (
      !selected ||
      busy ||
      selected.pendingPayload ||
      selected.save === "saved"
    )
      return;
    running.current = true;
    setBusy("upload");
    try {
      if (sessionId.current)
        await removeSalesSlipSessionPhoto(sessionId.current, selected.id);
    } catch (error) {
      patch(selected.id, { error: message(error) });
      return;
    } finally {
      running.current = false;
      if (mounted.current) setBusy(null);
    }
    if (!mounted.current) return;
    setEntries((current) =>
      current.filter((entry) => entry.id !== selected.id),
    );
    setSelectedId(
      entries.find((entry) => entry.id !== selected.id)?.id ?? null,
    );
  }
  function setRecordingMode(recordOnly: boolean) {
    if (!selected) return;
    setForm(selected.id, (current) =>
      current.recordOnly === recordOnly
        ? current
        : {
            ...withSalesRecordingMode(current, recordOnly),
            items: current.items.map((item) => ({
              ...item,
              allocations: [],
              allocationMode: "AUTO" as const,
            })),
          },
    );
  }

  function connectGroups(indices: number[], groups: SalesOrchidGroupOption[]) {
    if (!selected || !groups.length || busy) return;
    const first = groups[0];
    if (
      groups.some(
        (group) =>
          group.genus !== first.genus ||
          group.varietyName !== first.varietyName,
      )
    ) {
      patch(selected.id, {
        error: "한 품목에는 같은 품종·속의 난 묶음을 선택해주세요.",
      });
      return;
    }
    setForm(selected.id, (current) =>
      confirmSalesReview(
        confirmSalesReview(
          {
            ...current,
            items: distributeSalesItems(
              current.items.map((item, index) =>
                indices.includes(index)
                  ? {
                      ...item,
                      itemName: first.varietyName,
                      genus: first.genus,
                      allocationMode: "AUTO" as const,
                      allocations: groups.map((group) => ({
                        orchidGroupId: String(group.id),
                        varietyName: group.varietyName,
                        genus: group.genus,
                        locationLabel: `${group.houseNumber}동 ${group.physicalBedNumber}배드 ${group.bedZoneName}`,
                        availableQuantity: group.availableQuantity,
                        quantity: "0",
                      })),
                    }
                  : item,
              ),
            ),
          },
          "ITEM_NAME",
          indices,
        ),
        "GENUS",
        indices,
      ),
    );
  }
  return {
    entries,
    selected,
    selectedId: selected?.id,
    setSelectedId,
    busy,
    uploadError,
    actions,
    confirmReview: (field: SalesReviewField, indices?: number[]) => {
      if (selected)
        setForm(selected.id, (form) =>
          confirmSalesReview(form, field, indices),
        );
    },
    setRecordingMode,
    connectGroups,
    addFiles,
    saveSelected,
    removeSelected,
    readSelected: () =>
      selected && selected.save === "draft" && !selected.pendingPayload
        ? readEntries([selected])
        : Promise.resolve(),
    readAll: () =>
      readEntries(
        entries.filter(
          (entry) =>
            ["waiting", "failed"].includes(entry.read) &&
            entry.save === "draft" &&
            !entry.pendingPayload,
        ),
      ),
    setFeedback: (value: string) => {
      if (
        selected &&
        !busy &&
        selected.save === "draft" &&
        !selected.pendingPayload
      )
        patch(selected.id, { feedback: value });
    },
    cancelReading: () => activeRequest.current?.abort(),
  };
}
