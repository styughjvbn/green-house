import {
  PHOTO_EXTRACTION_BATCH_SIZE,
  runPhotoExtractionBatches,
  pendingPhotoDocuments,
} from "../lib/photoExtractionBatches";
import { sha256 } from "@noble/hashes/sha2.js";
import { useEffect, useRef, useState, type SetStateAction } from "react";
import type { SalesSlip, SalesOrchidGroupOption } from "@/entities/farm/types";
import { ApiError } from "@/shared/api/error";
import { createUuid } from "@/shared/lib/id";
import { createPendingCreationRequestKey } from "@/shared/lib/pendingCreationRequestKey";
import {
  useRuntimeContext,
  useImagePreparationConcurrency,
} from "@/shared/runtime/RuntimeContext";
import { runConcurrentPhotoTasks } from "../lib/photoPreparationQueue";
import {
  createSalesSlip,
  openSalesSlipPhotoSession,
  closeSalesSlipPhotoSession,
  removeSalesSlipSessionPhoto,
  readSalesSlipPhotos,
  cancelSalesSlipPhotoReading,
  prepareSalesSlipPhoto,
  getSalesSlipPreparedImage,
  removeSalesSlipImagePreview,
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
import { useMutation } from "@tanstack/react-query";
import {
  preparedPhotoName,
  toPreparedSalesPhoto,
} from "../lib/preparedSalesPhoto";
import { createSalesSlipDraftActions } from "./salesSlipDraftActions";
import type { SalesSlipForm, SalesReviewField } from "./types";

import {
  cropSalesPhotoRegions,
  parsePhotoRegion,
  samePhotoRegion,
  type PhotoRegion,
  type PhotoRegionDraft,
} from "../lib/photoRegions";

type PhotoEntry = {
  id: string;
  parentId: string | null;
  region: PhotoRegion | null;
  rotationDegrees: number;
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
  error: string | null;
  pendingPayload: CreateSalesSlipPayload | null;
  requestKey: ReturnType<typeof createPendingCreationRequestKey>;
};
const MAX_ORIGINAL_PHOTOS = 10;
const message = (error: unknown) =>
  error instanceof Error ? error.message : "요청 중 문제가 발생했습니다.";

export function useSalesSlipPhotoRegistration(
  onSaved: (slip: SalesSlip) => Promise<void>,
) {
  const { businessDate } = useRuntimeContext();
  const documentDetectionConcurrency = useImagePreparationConcurrency();
  const [entries, setEntries] = useState<PhotoEntry[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [busy, setBusy] = useState<
    "upload" | "prepare" | "read" | "save" | null
  >(null);
  const [regionProcessingProgress, setRegionProcessingProgress] = useState<{
    photoIds: string[];
    completed: number;
    total: number;
  } | null>(null);
  const [extractionProgress, setExtractionProgress] = useState<{
    completed: number;
    total: number;
    batchIndex: number;
    batchCount: number;
  } | null>(null);
  const [cancelling, setCancelling] = useState(false);
  const activeRead = useRef<{
    requestId: string;
    sessionId?: string;
    controller: AbortController;
    cancelRequested: boolean;
    cancellation?: Promise<void>;
  } | null>(null);
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

  const photosWithoutDocumentRegions = entries.filter(
    (entry) =>
      !entry.parentId && !entries.some((child) => child.parentId === entry.id),
  );

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

  function createEntry(
    id: string,
    photo: SalesSlipPhotoInput,
    parentId: string | null,
    region: PhotoRegion | null = null,
    rotationDegrees = 0,
  ): PhotoEntry {
    return {
      id,
      photo,
      parentId,
      region,
      rotationDegrees,
      form: withSalesRecordingMode(createInitialSalesForm(businessDate), true),
      read: "waiting",
      save: "draft",
      notes: [],
      recognized: false,
      recognition: null,
      error: null,
      pendingPayload: null,
      requestKey: createPendingCreationRequestKey(
        `greenhouse:sales-photo-create:v1:${id}`,
        createUuid,
        () => window.sessionStorage,
      ),
    };
  }

  const documentDetection = useMutation({
    retry: false,
    mutationFn: async ({
      entry,
      signal,
      rotationDegrees,
    }: {
      entry: PhotoEntry;
      signal: AbortSignal;
      rotationDegrees: number;
    }) => {
      const preview = await prepareSalesSlipPhoto(
        entry.photo,
        rotationDegrees,
        signal,
      );
      try {
        if (!preview.id)
          throw new Error("전표 찾기 결과를 확인하지 못했습니다.");
        const documents = (preview.images ?? []).filter(
          (image) => image.kind === "DOCUMENT_COMPACTED",
        );
        if (!documents.length)
          throw new Error(
            "전표를 자동으로 찾지 못했습니다. 사진 방향과 전표가 잘리지 않았는지 확인해주세요.",
          );
        const downloads = await Promise.allSettled(
          documents.map(async (image, index) => {
            if (!image.id)
              throw new Error("잘라낸 전표 사진을 찾지 못했습니다.");
            const region = parsePhotoRegion(image.sourceRegion);
            if (!region)
              throw new Error("찾은 전표의 위치를 확인하지 못했습니다.");
            const blob = await getSalesSlipPreparedImage(
              preview.id!,
              image.id,
              signal,
            );
            return {
              photo: await toPreparedSalesPhoto(
                blob,
                preparedPhotoName(entry.photo.fileName, index + 1),
              ),
              region,
            };
          }),
        );
        const photos = downloads.map((download) => {
          if (download.status === "rejected") throw download.reason;
          return download.value;
        });
        return {
          photos,
          rotationDegrees: preview.clockwiseDegrees ?? 0,
        };
      } finally {
        if (preview.id)
          void removeSalesSlipImagePreview(preview.id).catch(() => {});
      }
    },
  });

  async function detectDocumentRegions(
    targets: PhotoEntry[],
    rotations: Record<string, number>,
  ) {
    if (
      running.current ||
      targets.some(
        (entry) =>
          entry.parentId ||
          entries.some(
            (child) =>
              child.parentId === entry.id &&
              (child.save !== "draft" || child.pendingPayload),
          ),
      )
    )
      return;
    if (!targets.length) return entries;
    const detectedDocuments = new Map<string, PhotoEntry[]>();
    running.current = true;
    setBusy("prepare");
    setUploadError(null);
    const controller = new AbortController();
    activeRequest.current = controller;
    setRegionProcessingProgress({
      photoIds: [],
      completed: 0,
      total: targets.length,
    });
    const failures: string[] = [];
    try {
      await runConcurrentPhotoTasks(
        targets,
        documentDetectionConcurrency,
        controller.signal,
        async (entry) => {
          if (!mounted.current || controller.signal.aborted) return;
          setRegionProcessingProgress((current) =>
            current
              ? { ...current, photoIds: [...current.photoIds, entry.id] }
              : current,
          );
          patch(entry.id, { error: null });
          const previous = entries.filter(
            (child) => child.parentId === entry.id,
          );
          try {
            const result = await documentDetection.mutateAsync({
              entry,
              signal: controller.signal,
              rotationDegrees: rotations[entry.id] ?? -1,
            });
            if (!mounted.current || controller.signal.aborted) return;
            const children = result.photos.map(({ photo, region }) =>
              createEntry(
                createUuid(),
                photo,
                entry.id,
                region,
                result.rotationDegrees,
              ),
            );
            for (const child of previous)
              if (sessionId.current)
                await removeSalesSlipSessionPhoto(sessionId.current, child.id);
            if (!mounted.current || controller.signal.aborted) return;
            detectedDocuments.set(entry.id, children);
            setEntries((current) => {
              const remaining = current.filter(
                (item) => item.parentId !== entry.id,
              );
              const position = remaining.findIndex(
                (item) => item.id === entry.id,
              );
              if (position < 0) return current;
              return [
                ...remaining.slice(0, position + 1),
                ...children,
                ...remaining.slice(position + 1),
              ];
            });
          } catch (error) {
            if (!mounted.current || controller.signal.aborted) return;
            patch(entry.id, { error: message(error) });
            failures.push(`${entry.photo.fileName}: ${message(error)}`);
          } finally {
            if (mounted.current)
              setRegionProcessingProgress((current) =>
                current
                  ? {
                      ...current,
                      photoIds: current.photoIds.filter(
                        (id) => id !== entry.id,
                      ),
                      completed: current.completed + 1,
                    }
                  : current,
              );
          }
        },
      );
      if (mounted.current && targets.length > 1 && failures.length)
        setUploadError(failures.join("\n"));
      return entries.flatMap((entry) => {
        if (entry.parentId)
          return detectedDocuments.has(entry.parentId) ? [] : [entry];
        return [entry, ...(detectedDocuments.get(entry.id) ?? [])];
      });
    } finally {
      documentDetection.reset();
      activeRequest.current = null;
      running.current = false;
      if (mounted.current) {
        setRegionProcessingProgress(null);
        setBusy(null);
      }
    }
  }

  function detectSelectedPhotoRegions(rotationDegrees = -1) {
    return detectDocumentRegions(
      selected ? [selected] : [],
      selected ? { [selected.id]: rotationDegrees } : {},
    );
  }

  async function applyDocumentRegions(
    ownerId: string,
    drafts: PhotoRegionDraft[],
    rotationDegrees = 0,
  ): Promise<boolean> {
    if (running.current) return false;
    const owner = entries.find(
      (entry) => entry.id === ownerId && !entry.parentId,
    );
    if (!owner) return false;
    const previous = entries.filter((entry) => entry.parentId === ownerId);
    if (
      !drafts.length ||
      drafts.some((draft) => !parsePhotoRegion(draft.region)) ||
      new Set(drafts.map((d) => d.id)).size !== drafts.length
    ) {
      patch(ownerId, { error: "원본 사진에서 전표 영역을 선택해주세요." });
      return false;
    }
    const changed = previous.filter(
      (entry) =>
        !drafts.some(
          (d) => d.id === entry.id && samePhotoRegion(entry.region, d.region),
        ),
    );
    if (
      changed.some((entry) => entry.save !== "draft" || entry.pendingPayload)
    ) {
      patch(ownerId, {
        error: "저장 완료 또는 저장 결과 확인 중인 전표는 수정할 수 없습니다.",
      });
      return false;
    }
    running.current = true;
    setBusy("prepare");
    patch(ownerId, { error: null });
    try {
      let suffix = Math.max(
        0,
        ...previous.map((entry) =>
          Number(entry.photo.fileName.match(/_(\d+)\.jpg$/)?.[1] ?? 0),
        ),
      );
      const plan = drafts.map((draft) => {
        const existing = previous.find((entry) => entry.id === draft.id);
        if (existing && samePhotoRegion(existing.region, draft.region))
          return { existing };
        return {
          request: {
            region: draft.region,
            rotationDegrees: existing?.rotationDegrees ?? rotationDegrees,
            fileName:
              existing?.photo.fileName ??
              preparedPhotoName(owner.photo.fileName, ++suffix),
          },
        };
      });
      const requests = plan.flatMap((item) =>
        item.request ? [item.request] : [],
      );
      const cropped = await cropSalesPhotoRegions(owner.photo, requests);
      let cropIndex = 0;
      const next = plan.map(
        (item) =>
          item.existing ??
          createEntry(
            createUuid(),
            cropped[cropIndex++],
            ownerId,
            item.request!.region,
            item.request!.rotationDegrees,
          ),
      );
      if (!mounted.current) return false;
      for (const entry of changed)
        if (sessionId.current)
          await removeSalesSlipSessionPhoto(sessionId.current, entry.id);
      if (!mounted.current) return false;
      setEntries((current) => {
        const rest = current.filter((entry) => entry.parentId !== ownerId);
        const index = rest.findIndex((entry) => entry.id === ownerId);
        return [...rest.slice(0, index + 1), ...next, ...rest.slice(index + 1)];
      });
      setSelectedId(ownerId);
      return true;
    } catch (error) {
      patch(ownerId, { error: message(error) });
      return false;
    } finally {
      running.current = false;
      if (mounted.current) setBusy(null);
    }
  }

  async function addFiles(files: File[]) {
    if (running.current || !files.length) return;
    running.current = true;
    setBusy("upload");
    setUploadError(null);
    const errors: string[] = [];
    const additions: PhotoEntry[] = [];
    try {
      for (const file of files) {
        if (
          entries.filter((entry) => !entry.parentId).length +
            additions.length >=
          MAX_ORIGINAL_PHOTOS
        ) {
          errors.push(
            `한 번에 최대 ${MAX_ORIGINAL_PHOTOS}장까지 등록할 수 있습니다.`,
          );
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
          additions.push(createEntry(id, photo, null));
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

  function cancelReading() {
    const run = activeRead.current;
    if (!run || run.cancellation) return run?.cancellation;
    run.cancelRequested = true;
    setCancelling(true);
    run.cancellation = (async () => {
      if (run.sessionId)
        await cancelSalesSlipPhotoReading(run.sessionId, run.requestId);
      run.controller.abort();
    })()
      .catch((error) => {
        if (mounted.current)
          setUploadError(
            `중지 요청을 확인하지 못했습니다. 다시 시도해주세요. ${message(error)}`,
          );
        run.cancelRequested = false;
        run.cancellation = undefined;
      })
      .finally(() => {
        if (mounted.current) setCancelling(false);
      });
    return run.cancellation;
  }

  async function extractDocumentValues(targets: PhotoEntry[]) {
    if (
      running.current ||
      !targets.length ||
      targets.some((entry) => !entry.parentId)
    )
      return;
    running.current = true;
    setBusy("read");
    const controller = new AbortController();
    activeRequest.current = controller;
    const run = {
      requestId: createUuid(),
      controller,
      cancelRequested: false,
    } as NonNullable<typeof activeRead.current>;
    activeRead.current = run;
    let currentBatch: PhotoEntry[] = [];
    let completed = 0;
    setExtractionProgress({
      completed: 0,
      total: targets.length,
      batchIndex: 0,
      batchCount: Math.ceil(targets.length / PHOTO_EXTRACTION_BATCH_SIZE),
    });
    try {
      const id = await ensureSession(controller.signal);
      if (controller.signal.aborted || run.cancelRequested)
        throw new DOMException("Aborted", "AbortError");
      run.sessionId = id;
      await runPhotoExtractionBatches(
        targets,
        controller.signal,
        async (batchEntries, batchIndex, batchCount) => {
          if (run.cancelRequested)
            throw new DOMException("Aborted", "AbortError");
          currentBatch = batchEntries;
          run.requestId = createUuid();
          setExtractionProgress({
            completed,
            total: targets.length,
            batchIndex,
            batchCount,
          });
          for (const entry of batchEntries)
            patch(entry.id, { read: "reading", error: null });
          const batch = await readSalesSlipPhotos(
            id,
            batchEntries.map((entry) => ({
              photoId: entry.id,
              photo: entry.photo,
            })),
            controller.signal,
            run.requestId,
          );
          if (controller.signal.aborted || run.cancelRequested)
            throw new DOMException("Aborted", "AbortError");
          const rows = batch.photos ?? [];
          if (
            rows.length !== batchEntries.length ||
            new Set(rows.map((row) => row.photoId)).size !==
              batchEntries.length ||
            rows.some(
              (row) => !batchEntries.some((entry) => entry.id === row.photoId),
            )
          )
            throw new Error("사진별 인식 결과를 확인하지 못했습니다.");
          for (const entry of batchEntries) {
            const result = rows.find((row) => row.photoId === entry.id)?.result;
            if (!result) {
              patch(entry.id, {
                read: "failed",
                error:
                  "사진 내용을 읽지 못했습니다. 다시 읽거나 다시 촬영해주세요.",
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
            });
          }
          completed += batchEntries.length;
          setExtractionProgress({
            completed,
            total: targets.length,
            batchIndex,
            batchCount,
          });
          currentBatch = [];
        },
      );
    } catch (error) {
      if (
        !currentBatch.length &&
        mounted.current &&
        !controller.signal.aborted &&
        !run.cancelRequested
      )
        setUploadError(message(error));
      for (const entry of currentBatch)
        patch(entry.id, {
          read: "failed",
          error:
            controller.signal.aborted ||
            run.cancelRequested ||
            (error instanceof ApiError && error.code === "AI_CANCELLED")
              ? "자동 입력을 중지했습니다."
              : message(error),
        });
    } finally {
      await run.cancellation;
      if (activeRead.current === run) activeRead.current = null;
      activeRequest.current = null;
      running.current = false;
      if (mounted.current) {
        setExtractionProgress(null);
        setBusy(null);
      }
    }
  }

  async function automaticInput(rotations: Record<string, number>) {
    if (running.current) return;
    setUploadError(null);
    const prepared = await detectDocumentRegions(
      photosWithoutDocumentRegions,
      rotations,
    );
    if (!mounted.current || !prepared) return;
    await extractDocumentValues(pendingPhotoDocuments(prepared));
  }

  async function saveSelected() {
    if (
      !selected ||
      !selected.parentId ||
      selected.save === "saved" ||
      running.current
    )
      return;
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
    if (!selected || running.current) return;
    const targets = entries.filter(
      (entry) => entry.id === selected.id || entry.parentId === selected.id,
    );
    if (targets.some((entry) => entry.pendingPayload || entry.save !== "draft"))
      return;
    running.current = true;
    setBusy("upload");
    try {
      if (sessionId.current) {
        for (const entry of targets.filter((entry) => entry.parentId))
          await removeSalesSlipSessionPhoto(sessionId.current, entry.id);
      }
      if (!mounted.current) return;
      const ids = new Set(targets.map((entry) => entry.id));
      setEntries((current) => current.filter((entry) => !ids.has(entry.id)));
      setSelectedId(
        selected.parentId ??
          entries.find((entry) => !ids.has(entry.id))?.id ??
          null,
      );
    } catch (error) {
      patch(selected.id, { error: message(error) });
    } finally {
      running.current = false;
      if (mounted.current) setBusy(null);
    }
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
    regionProcessingProgress,
    extractionProgress,
    photosWithoutDocumentRegionsCount: photosWithoutDocumentRegions.length,
    cancelling,
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
    detectSelectedPhotoRegions,
    automaticInput,
    applyDocumentRegions,
    saveSelected,
    removeSelected,
    extractSelectedDocument: () =>
      selected?.parentId &&
      selected.save === "draft" &&
      !selected.pendingPayload
        ? extractDocumentValues([selected])
        : Promise.resolve(),
    cancelReading,
  };
}
