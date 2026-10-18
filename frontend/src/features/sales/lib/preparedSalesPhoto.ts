import type { SalesSlipPhotoInput } from "../api/types";

export function preparedPhotoName(originalName: string, index: number) {
  return `${originalName.replace(/\.[^.]+$/, "").slice(0, 245)}_${String(index).padStart(2, "0")}.jpg`;
}

/** Flatten transparent paper backgrounds before attaching the processed JPEG. */
export async function toPreparedSalesPhoto(
  blob: Blob,
  fileName: string,
): Promise<SalesSlipPhotoInput> {
  const bitmap = await createImageBitmap(blob);
  try {
    const canvas = document.createElement("canvas");
    canvas.width = bitmap.width;
    canvas.height = bitmap.height;
    const context = canvas.getContext("2d");
    if (!context) throw new Error("문서 사진을 만들지 못했습니다.");
    context.fillStyle = "#ffffff";
    context.fillRect(0, 0, canvas.width, canvas.height);
    context.drawImage(bitmap, 0, 0);
    const data = canvas.toDataURL("image/jpeg", 0.92);
    const contentBase64 = data.slice(data.indexOf(",") + 1);
    if (Math.ceil((contentBase64.length * 3) / 4) > 8 * 1024 * 1024)
      throw new Error(
        "문서 사진이 8MB를 초과합니다. 더 작은 사진으로 시도해주세요.",
      );
    return { fileName, contentType: "image/jpeg", contentBase64 };
  } finally {
    bitmap.close();
  }
}
