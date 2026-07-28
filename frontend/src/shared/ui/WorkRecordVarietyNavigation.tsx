import { useRef } from "react";
import type { PointerEvent as ReactPointerEvent } from "react";
import { Check } from "lucide-react";

export type WorkRecordNavigationItem = {
  key: string;
  label: string;
  completed: boolean;
};

export function WorkRecordVarietyNavigation({
  activeKey,
  items,
  onSelect,
}: {
  activeKey: string;
  items: WorkRecordNavigationItem[];
  onSelect: (key: string) => void;
}) {
  const dragState = useRef({
    pointerId: -1,
    startScrollLeft: 0,
    startX: 0,
  });
  const suppressClick = useRef(false);

  function startDrag(event: ReactPointerEvent<HTMLDivElement>) {
    if (event.pointerType !== "mouse" || event.button !== 0) return;
    dragState.current = {
      pointerId: event.pointerId,
      startScrollLeft: event.currentTarget.scrollLeft,
      startX: event.clientX,
    };
    suppressClick.current = false;
  }

  function drag(event: ReactPointerEvent<HTMLDivElement>) {
    if (dragState.current.pointerId !== event.pointerId) return;
    const distance = event.clientX - dragState.current.startX;
    if (Math.abs(distance) > 4 && !suppressClick.current) {
      suppressClick.current = true;
      event.currentTarget.setPointerCapture(event.pointerId);
    }
    event.currentTarget.scrollLeft =
      dragState.current.startScrollLeft - distance;
  }

  function stopDrag(event: ReactPointerEvent<HTMLDivElement>) {
    if (dragState.current.pointerId !== event.pointerId) return;
    const dragged = suppressClick.current;
    dragState.current.pointerId = -1;
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
    if (dragged) {
      window.setTimeout(() => {
        suppressClick.current = false;
      }, 0);
    }
  }

  return (
    <div
      className="flex min-w-0 flex-1 cursor-grab flex-nowrap gap-2 overflow-x-auto overscroll-x-contain pb-1 select-none active:cursor-grabbing"
      onPointerCancel={stopDrag}
      onPointerDown={startDrag}
      onPointerMove={drag}
      onPointerUp={stopDrag}
    >
      {items.map((item) => {
        const active = item.key === activeKey;
        return (
          <button
            className={`inline-flex shrink-0 items-center gap-1.5 rounded-md border px-3 py-2 text-sm font-semibold focus:outline-none focus-visible:ring-2 focus-visible:ring-[#159447] focus-visible:ring-offset-1 ${
              active
                ? "border-[#159447] bg-[#edf8ef] text-[#10783a]"
                : "border-[#d4ddd3] bg-white text-[#435047] hover:bg-[#f5f8f4]"
            }`}
            key={item.key}
            style={{ WebkitTapHighlightColor: "transparent" }}
            type="button"
            onClick={() => {
              if (suppressClick.current) {
                suppressClick.current = false;
                return;
              }
              onSelect(item.key);
            }}
          >
            {item.completed ? (
              <Check className="h-4 w-4 text-[#159447]" aria-hidden="true" />
            ) : null}
            {item.label}
          </button>
        );
      })}
    </div>
  );
}
