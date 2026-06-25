"use client";

export default function MapLegend() {
  return (
    <div className="flex flex-wrap gap-4 rounded-md border border-[#d7ddd4] bg-white p-3 text-sm shadow-sm">
      <span><span className="mr-2 inline-block h-4 w-4 rounded border border-[#246df2] bg-[#dcecff] align-middle" />선택 영역</span>
      <span><span className="mr-2 inline-block h-4 w-4 rounded border border-[#82c886] bg-[#bfe2b8] align-middle" />난 묶음 있음</span>
      <span><span className="mr-2 inline-block h-4 w-4 rounded border border-[#d7ddd4] bg-[#f0f1ef] align-middle" />비어 있음</span>
      <span><span className="mr-2 inline-block h-4 w-4 rounded border border-[#159447] bg-[#eef7ec] align-middle" />드롭 대상</span>
    </div>
  );
}
