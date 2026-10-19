"use client";

import {
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
  type RefObject,
} from "react";
import { SalesSlipPhotoViewer } from "./SalesSlipPhotoViewer";

type Position = { x: number; y: number };

function clampPosition(
  next: Position,
  area: HTMLDivElement | null,
  photo: HTMLDivElement | null,
): Position {
  if (!area || !photo) return next;
  return {
    x: Math.max(8, Math.min(next.x, area.clientWidth - photo.offsetWidth - 8)),
    y: Math.max(
      8,
      Math.min(next.y, area.clientHeight - photo.offsetHeight - 8),
    ),
  };
}

export function SalesSlipFloatingPhoto({
  src,
  scrollRoot,
  photoArea,
}: {
  src: string;
  scrollRoot: RefObject<HTMLDivElement | null>;
  photoArea: RefObject<HTMLDivElement | null>;
}) {
  const element = useRef<HTMLDivElement>(null);
  const [floating, setFloating] = useState(false);
  const [position, setPosition] = useState<Position | null>(null);
  const [dragging, setDragging] = useState(false);
  const gesture = useRef<{
    pointerId: number;
    start: Position;
    origin: Position;
    dragging: boolean;
    target: HTMLButtonElement;
  } | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const frame = useRef<number | null>(null);
  const suppressClick = useRef(false);

  function clearTimer() {
    if (timer.current !== null) clearTimeout(timer.current);
    timer.current = null;
  }
  function finishGesture() {
    clearTimer();
    const current = gesture.current;
    gesture.current = null;
    if (current?.target.hasPointerCapture(current.pointerId))
      current.target.releasePointerCapture(current.pointerId);
    setDragging(false);
  }

  useEffect(() => {
    const root = scrollRoot.current;
    const target = photoArea.current;
    if (!root || !target) return;
    const observer = new IntersectionObserver(
      ([entry]) => {
        const top = entry.rootBounds?.top ?? root.getBoundingClientRect().top;
        setFloating(
          !entry.isIntersecting && entry.boundingClientRect.bottom <= top,
        );
      },
      { root, threshold: 0 },
    );
    observer.observe(target);
    return () => observer.disconnect();
  }, [scrollRoot, photoArea]);

  useLayoutEffect(() => {
    const root = scrollRoot.current;
    const photo = element.current;
    if (!root || !photo) return;
    const resize = () =>
      setPosition((current) =>
        clampPosition(
          current ?? {
            x: root.clientWidth - photo.offsetWidth - 12,
            y: root.clientHeight - photo.offsetHeight - 12,
          },
          root,
          photo,
        ),
      );
    resize();
    const observer = new ResizeObserver(resize);
    observer.observe(root);
    observer.observe(photo);
    return () => observer.disconnect();
  }, [scrollRoot]);

  useEffect(
    () => () => {
      clearTimer();
      if (frame.current !== null) cancelAnimationFrame(frame.current);
    },
    [],
  );

  return (
    <div
      ref={element}
      data-floating-document
      aria-hidden={!floating}
      className={`absolute top-0 left-0 z-20 w-44 max-w-[calc(100%-1rem)] touch-none rounded-md border bg-white p-1.5 shadow-xl select-none ${dragging ? "cursor-grabbing border-[#159447] ring-2 ring-[#159447]/30" : "border-[#cfe0d2]"}`}
      style={{
        visibility: floating && position ? "visible" : "hidden",
        transform: `translate3d(${position?.x ?? 0}px, ${position?.y ?? 0}px, 0)`,
      }}
      onContextMenu={(event) => event.preventDefault()}
      onDragStart={(event) => event.preventDefault()}
      onPointerDown={(event) => {
        if (
          !event.isPrimary ||
          event.button !== 0 ||
          !position ||
          !event.currentTarget.contains(event.target as Node)
        )
          return;
        const target = (event.target as HTMLElement).closest("button");
        if (!target) return;
        clearTimer();
        suppressClick.current = false;
        gesture.current = {
          pointerId: event.pointerId,
          start: { x: event.clientX, y: event.clientY },
          origin: position,
          dragging: false,
          target,
        };
        target.setPointerCapture(event.pointerId);
        timer.current = setTimeout(() => {
          if (!gesture.current) return;
          gesture.current.dragging = true;
          suppressClick.current = true;
          setDragging(true);
        }, 450);
      }}
      onPointerMove={(event) => {
        const current = gesture.current;
        if (!current || current.pointerId !== event.pointerId) return;
        const dx = event.clientX - current.start.x;
        const dy = event.clientY - current.start.y;
        if (!current.dragging) {
          if (Math.hypot(dx, dy) > 8) {
            clearTimer();
            suppressClick.current = true;
          }
          return;
        }
        const next = clampPosition(
          {
            x: current.origin.x + dx,
            y: current.origin.y + dy,
          },
          scrollRoot.current,
          element.current,
        );
        if (frame.current !== null) cancelAnimationFrame(frame.current);
        frame.current = requestAnimationFrame(() => {
          setPosition(next);
          frame.current = null;
        });
      }}
      onPointerUp={finishGesture}
      onPointerCancel={() => {
        suppressClick.current = true;
        finishGesture();
      }}
      onLostPointerCapture={finishGesture}
      onClickCapture={(event) => {
        if (
          !event.currentTarget.contains(event.target as Node) ||
          !event.detail ||
          !suppressClick.current
        )
          return;
        event.preventDefault();
        event.stopPropagation();
        suppressClick.current = false;
      }}
      onKeyDown={(event) => {
        if (!position || !event.currentTarget.contains(event.target as Node))
          return;
        const movement: Record<string, Position> = {
          ArrowLeft: { x: -16, y: 0 },
          ArrowRight: { x: 16, y: 0 },
          ArrowUp: { x: 0, y: -16 },
          ArrowDown: { x: 0, y: 16 },
        };
        const step = movement[event.key];
        if (!step) return;
        event.preventDefault();
        setPosition(
          clampPosition(
            { x: position.x + step.x, y: position.y + step.y },
            scrollRoot.current,
            element.current,
          ),
        );
      }}
    >
      <SalesSlipPhotoViewer
        src={src}
        alt="지정된 문서 사진 미리보기"
        width={600}
        height={800}
        className="h-44 w-full rounded bg-[#f7faf6] object-contain"
      />
      <p className="mt-1 text-center text-[11px] text-[#526158]">
        클릭하여 확대 · 길게 눌러 이동
        <span className="sr-only">키보드 방향키로 위치 이동</span>
      </p>
    </div>
  );
}
