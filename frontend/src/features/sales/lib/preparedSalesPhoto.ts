import type { SalesSlipPhotoInput } from "../api/types";

export function preparedPhotoName(originalName: string, index: number) {
  return `${originalName.replace(/\.[^.]+$/, "").slice(0, 240)}_${String(index).padStart(2, "0")}.webp`;
}

/** Attach the server WebP without decoding or recompressing it. */
export async function toPreparedSalesPhoto(
  blob: Blob,
  fileName: string,
): Promise<SalesSlipPhotoInput> {
  if (blob.type !== "image/webp")
    throw new Error("전표 사진 형식을 확인해주세요.");
  if (blob.size > 8 * 1024 * 1024)
    throw new Error(
      "전표 사진이 8MB를 초과합니다. 더 작은 사진으로 시도해주세요.",
    );
  const contentBase64 = await new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result).split(",")[1]);
    reader.onerror = () => reject(new Error("전표 사진을 읽지 못했습니다."));
    reader.readAsDataURL(blob);
  });
  return { fileName, contentType: "image/webp", contentBase64 };
}
