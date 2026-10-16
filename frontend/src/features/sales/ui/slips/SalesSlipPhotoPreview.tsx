"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { SalesSlipPhotoViewer } from "./SalesSlipPhotoViewer";
import { getSalesSlipPhoto, getSalesSlipPhotoInfo } from "../../api/salesApi";

export function SalesSlipPhotoPreview({
  salesSlipId,
}: {
  salesSlipId: number;
}) {
  const [open, setOpen] = useState(false);
  const info = useQuery({
    queryKey: ["sales", "slipPhotoInfo", salesSlipId],
    queryFn: () => getSalesSlipPhotoInfo(salesSlipId),
  });
  const photo = useQuery({
    queryKey: ["sales", "slipPhoto", salesSlipId],
    queryFn: ({ signal }) => getSalesSlipPhoto(salesSlipId, signal),
    enabled: open && info.data != null,
  });
  if (info.isError)
    return (
      <p role="alert" className="px-4 py-3 text-sm text-[#9b341e]">
        첨부 사진을 확인하지 못했습니다.{" "}
        <button
          type="button"
          className="underline"
          onClick={() => void info.refetch()}
        >
          다시 조회
        </button>
      </p>
    );
  if (!info.data) return null;
  return (
    <section className="border-b border-[#e7ebe5] p-4">
      <button
        type="button"
        aria-expanded={open}
        className="min-h-10 rounded-md border border-[#cfd8cc] px-3 text-sm font-semibold"
        onClick={() => setOpen((value) => !value)}
      >
        {open ? "첨부 사진 접기" : "첨부 사진 보기"}
      </button>
      {open ? (
        <div className="mt-3">
          {photo.isPending ? (
            <p role="status">사진을 불러오는 중입니다.</p>
          ) : photo.isError ? (
            <p role="alert">
              사진을 불러오지 못했습니다.{" "}
              <button
                type="button"
                className="underline"
                onClick={() => void photo.refetch()}
              >
                다시 조회
              </button>
            </p>
          ) : photo.data ? (
            <SalesSlipPhotoViewer
              src={`data:${photo.data.contentType};base64,${photo.data.contentBase64}`}
              width={900}
              height={1200}
              alt="전표에 첨부된 사진"
              className="max-h-[70dvh] w-full object-contain"
            />
          ) : null}
        </div>
      ) : null}
    </section>
  );
}
