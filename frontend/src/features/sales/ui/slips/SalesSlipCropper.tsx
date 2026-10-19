"use client";

import { useEffect, useRef, useState } from "react";
import CropperCanvas from "@cropper/element-canvas";
import CropperImage from "@cropper/element-image";
import CropperHandle from "@cropper/element-handle";
import CropperSelection from "@cropper/element-selection";
import { createUuid } from "@/shared/lib/id";
import type { SalesSlipPhotoInput } from "../../api/types";
import {
  parsePhotoRegion,
  type PhotoRegion,
  type PhotoRegionDraft,
} from "../../lib/photoRegions";

[CropperCanvas, CropperImage, CropperHandle, CropperSelection].forEach(
  (Element) => Element.$define(),
);

type Input = {
  regions: PhotoRegionDraft[];
  selectedId: string | null;
  drawing: boolean;
  disabled: boolean;
  onSelect: (id?: string) => void;
  onChange: (regions: PhotoRegionDraft[]) => void;
  onCreated: (id: string) => void;
};
type Controller = { sync: (input: Input) => void; destroy: () => void };

export function SalesSlipCropper({
  photo,
  ...input
}: Input & { photo: SalesSlipPhotoInput }) {
  const host = useRef<HTMLDivElement>(null);
  const controller = useRef<Controller | null>(null);
  const [ratio, setRatio] = useState(1);
  const [failure, setFailure] = useState(false);
  useEffect(() => {
    const instance = createRegionCropper(host.current!, photo, setRatio, () =>
      setFailure(true),
    );
    controller.current = instance;
    return () => {
      instance.destroy();
      controller.current = null;
    };
  }, [photo]);
  const {
    regions,
    selectedId,
    drawing,
    disabled,
    onSelect,
    onChange,
    onCreated,
  } = input;
  useEffect(() => {
    controller.current?.sync({
      regions,
      selectedId,
      drawing,
      disabled,
      onSelect,
      onChange,
      onCreated,
    });
  }, [
    photo,
    regions,
    selectedId,
    drawing,
    disabled,
    onSelect,
    onChange,
    onCreated,
  ]);
  return (
    <>
      <div
        ref={host}
        aria-label="전표 영역 편집"
        className="relative mx-auto max-w-full overflow-hidden rounded bg-[#f4f7f3] ring-1 ring-[#d5e1d8]"
        style={{ width: `min(100%, ${620 * ratio}px)`, aspectRatio: ratio }}
      />
      {failure ? (
        <p role="alert" className="text-sm text-[#9b341e]">
          원본 사진을 불러오지 못했습니다. 창을 닫고 다시 시도해주세요.
        </p>
      ) : null}
    </>
  );
}

/** Cropper owns live geometry. React receives selection changes and completed gestures only. */
function createRegionCropper(
  host: HTMLDivElement,
  photo: SalesSlipPhotoInput,
  onRatio: (ratio: number) => void,
  onError: () => void,
): Controller {
  const canvas = new CropperCanvas();
  canvas.style.cssText = "width:100%;height:100%;touch-action:none;";
  canvas.themeColor = "#159447";
  const image = new CropperImage();
  image.src = `data:${photo.contentType};base64,${photo.contentBase64}`;
  image.alt = "전표 영역을 선택할 원본 사진";
  const background = new CropperHandle();
  background.plain = true;
  canvas.append(image, background);
  const boxes = new Map<string, CropperSelection>();
  let input: Input | null = null;
  let gesture: { id: string; created: boolean } | null = null;
  let syncing = false,
    ready = false,
    disposed = false;
  let resizeFrame = 0;

  function fitImage() {
    image.translatable = true;
    image.scalable = true;
    image.$center("contain");
    image.translatable = false;
    image.scalable = false;
  }
  function makeBox(id: string) {
    const box = new CropperSelection();
    box.dataset.regionId = id;
    box.multiple = true;
    box.precise = true;
    box.minInset = "0";
    box.style.position = "absolute";
    canvas.append(box);
    box.setAttribute("action", "move");
    box.$addStyles(
      ":host{outline:2px solid #159447!important;background:rgb(21 148 71 / 7%)}:host([active]){background:rgb(21 148 71 / 15%)}",
    );
    const move = new CropperHandle();
    move.action = "move";
    move.plain = true;
    box.append(move);
    for (const action of ["n", "e", "s", "w", "nw", "ne", "sw", "se"]) {
      const handle = new CropperHandle();
      handle.action = `${action}-resize`;
      // Screen-pixel hit areas remain usable as the image changes size.
      box.append(handle);
      handle.$addStyles(
        ":host([action$=-resize]){width:28px;height:28px}:host([action=n-resize]),:host([action=s-resize]){width:100%}:host([action=e-resize]),:host([action=w-resize]){height:100%}:host([action^=n]){top:-14px}:host([action^=s]){bottom:-14px}:host([action^=e]),:host([action$=e-resize]){right:-14px}:host([action^=w]),:host([action$=w-resize]){left:-14px}:host([action$=-resize]):after{width:8px;height:8px;border-radius:2px}",
      );
    }
    const label = document.createElement("span");
    label.style.cssText =
      "position:absolute;left:5px;top:3px;color:#075c2b;background:white;border-radius:3px;padding:0 4px;font-size:12px;font-weight:700;pointer-events:none;";
    box.append(label);
    boxes.set(id, box);
    return box;
  }
  function normalized(box: CropperSelection): PhotoRegion | null {
    const width = canvas.clientWidth,
      height = canvas.clientHeight;
    return parsePhotoRegion({
      x: box.x / width,
      y: box.y / height,
      width: box.width / width,
      height: box.height / height,
    });
  }
  function render() {
    if (!input || !ready || gesture) return;
    syncing = true;
    try {
      canvas.disabled = input.disabled;
      background.action = input.drawing ? "select" : "none";
      const ids = new Set(input.regions.map((r) => r.id));
      for (const [id, box] of boxes)
        if (!ids.has(id)) {
          box.remove();
          boxes.delete(id);
        }
      input.regions.forEach((draft, index) => {
        const box = boxes.get(draft.id) ?? makeBox(draft.id);
        box.active = draft.id === input!.selectedId;
        box.movable = !draft.locked;
        box.resizable = !draft.locked;
        box.$change(
          draft.region.x * canvas.clientWidth,
          draft.region.y * canvas.clientHeight,
          draft.region.width * canvas.clientWidth,
          draft.region.height * canvas.clientHeight,
        );
        box.querySelector("span")!.textContent = String(index + 1);
      });
    } finally {
      syncing = false;
    }
  }
  function start(event: Event) {
    if (!input || !ready || input.disabled) {
      event.preventDefault();
      return;
    }
    const detail = (
      event as CustomEvent<{ action: string; relatedEvent: PointerEvent }>
    ).detail;
    if (
      gesture ||
      ![
        "select",
        "move",
        "none",
        "n-resize",
        "e-resize",
        "s-resize",
        "w-resize",
        "nw-resize",
        "ne-resize",
        "sw-resize",
        "se-resize",
      ].includes(detail.action)
    ) {
      event.preventDefault();
      return;
    }
    const target = (
      detail.relatedEvent.target as Element
    ).closest<CropperSelection>("cropper-selection");
    let id = target?.dataset.regionId;
    if (!id && detail.action === "select" && input.drawing) {
      id = `new-${createUuid()}`;
      const box = makeBox(id);
      box.resizable = true;
      box.movable = true;
      box.hidden = true;
      // Initial property reflection may unhide an empty selection. Keep it empty
      // until Cropper starts drawing, so its native multiple mode does not clone it.
      void box.$nextTick(() => {
        if (!box.width && !box.height) box.hidden = true;
      });
      gesture = { id, created: true };
    } else if (id) {
      if (input.regions.find((r) => r.id === id)?.locked) {
        input.onSelect(id);
        event.preventDefault();
        return;
      }
      gesture = { id, created: false };
      input.onSelect(id);
    } else {
      event.preventDefault();
      return;
    }
    for (const [boxId, box] of boxes) box.active = boxId === id;
  }
  function end(event: Event) {
    const active = gesture;
    if (!active || !input) return;
    gesture = null;
    const box = boxes.get(active.id)!;
    const region = normalized(box);
    const cancelled =
      (event as CustomEvent<{ relatedEvent?: Event }>).detail?.relatedEvent
        ?.type === "pointercancel";
    if (
      !cancelled &&
      region &&
      region.width >= 0.005 &&
      region.height >= 0.005
    ) {
      const drafts = active.created
        ? [...input.regions, { id: active.id, region }]
        : input.regions.map((r) => (r.id === active.id ? { ...r, region } : r));
      input.onChange(drafts);
      if (active.created) input.onCreated(active.id);
    } else render();
  }
  function change(event: Event) {
    if (syncing) return;
    const box = event.target as CropperSelection;
    if (input?.regions.find((r) => r.id === box.dataset.regionId)?.locked)
      event.preventDefault();
  }
  // Editing moves selections, not the image; keep wheel scrolling available.
  const allowScroll = (event: Event) => event.stopImmediatePropagation();
  canvas.addEventListener("wheel", allowScroll, { capture: true });
  canvas.addEventListener("actionstart", start, true);
  canvas.addEventListener("actionend", end);
  canvas.addEventListener("change", change);
  host.append(canvas);
  const resize = new ResizeObserver(() => {
    cancelAnimationFrame(resizeFrame);
    resizeFrame = requestAnimationFrame(() => {
      if (!disposed && ready) {
        fitImage();
        render();
      }
    });
  });
  resize.observe(host);
  void image
    .$ready()
    .then((img) => {
      if (disposed) return;
      onRatio(img.naturalWidth / img.naturalHeight);
      ready = true;
      fitImage();
      render();
    })
    .catch(() => {
      if (!disposed) onError();
    });
  return {
    sync(next) {
      input = next;
      render();
    },
    destroy() {
      disposed = true;
      resize.disconnect();
      cancelAnimationFrame(resizeFrame);
      canvas.removeEventListener("wheel", allowScroll, true);
      canvas.removeEventListener("actionstart", start, true);
      canvas.removeEventListener("actionend", end);
      canvas.removeEventListener("change", change);
      canvas.remove();
      boxes.clear();
    },
  };
}
