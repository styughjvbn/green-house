import type { SalesSlipPhotoInput } from "../api/types";

export type PhotoRegion = {
  x: number;
  y: number;
  width: number;
  height: number;
};
export type PhotoRegionDraft = {
  id: string;
  region: PhotoRegion;
  locked?: boolean;
};
export function parsePhotoRegion(input: unknown): PhotoRegion | null {
  if (!input || typeof input !== "object") return null;
  const r = input as Record<string, unknown>;
  if (
    ![r.x, r.y, r.width, r.height].every(
      (value) => typeof value === "number" && Number.isFinite(value),
    )
  )
    return null;
  const region = r as PhotoRegion;
  if (
    region.x < 0 ||
    region.y < 0 ||
    region.width <= 0 ||
    region.height <= 0 ||
    region.x + region.width > 1.000001 ||
    region.y + region.height > 1.000001
  )
    return null;
  return region;
}

export function samePhotoRegion(a: PhotoRegion | null, b: PhotoRegion) {
  return (
    a !== null &&
    (["x", "y", "width", "height"] as const).every(
      (key) => Math.abs(a[key] - b[key]) < 0.000001,
    )
  );
}

export function photoRegionPixels(
  region: PhotoRegion,
  width: number,
  height: number,
) {
  if (!parsePhotoRegion(region))
    throw new Error("전표 영역을 원본 사진 안에 지정해주세요.");
  const x = Math.max(0, Math.floor(region.x * width)),
    y = Math.max(0, Math.floor(region.y * height));
  const right = Math.min(
      width,
      Math.ceil((region.x + region.width) * width - 1e-8),
    ),
    bottom = Math.min(
      height,
      Math.ceil((region.y + region.height) * height - 1e-8),
    );
  return { x, y, width: right - x, height: bottom - y };
}

export async function cropSalesPhotoRegions(
  photo: SalesSlipPhotoInput,
  regions: { region: PhotoRegion; fileName: string; rotationDegrees: number }[],
): Promise<SalesSlipPhotoInput[]> {
  if (!regions.length) return [];
  const bytes = Uint8Array.from(atob(photo.contentBase64), (char) =>
    char.charCodeAt(0),
  );
  const bitmap = await createImageBitmap(
    new Blob([bytes], { type: photo.contentType }),
  );
  const canvas = document.createElement("canvas");
  try {
    const results: SalesSlipPhotoInput[] = [];
    for (const { region, fileName, rotationDegrees } of regions) {
      const crop = photoRegionPixels(region, bitmap.width, bitmap.height);
      const turns = (((rotationDegrees / 90) % 4) + 4) % 4;
      canvas.width = turns % 2 ? crop.height : crop.width;
      canvas.height = turns % 2 ? crop.width : crop.height;
      const context = canvas.getContext("2d");
      if (!context) throw new Error("문서 사진을 만들지 못했습니다.");
      context.fillStyle = "#ffffff";
      context.fillRect(0, 0, canvas.width, canvas.height);
      if (turns === 1) context.translate(crop.height, 0);
      else if (turns === 2) context.translate(crop.width, crop.height);
      else if (turns === 3) context.translate(0, crop.width);
      context.rotate((turns * Math.PI) / 2);
      context.drawImage(
        bitmap,
        crop.x,
        crop.y,
        crop.width,
        crop.height,
        0,
        0,
        crop.width,
        crop.height,
      );
      const blob = await new Promise<Blob>((resolve, reject) =>
        canvas.toBlob(
          (result) =>
            result
              ? resolve(result)
              : reject(
                  new Error("지정한 영역으로 문서 사진을 만들지 못했습니다."),
                ),
          "image/jpeg",
          0.92,
        ),
      );
      if (blob.size > 8 * 1024 * 1024)
        throw new Error("문서 사진이 8MB를 초과합니다. 영역을 줄여주세요.");
      const base64 = await new Promise<string>((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(String(reader.result).split(",")[1]);
        reader.onerror = () =>
          reject(new Error("문서 사진을 읽지 못했습니다."));
        reader.readAsDataURL(blob);
      });
      results.push({
        fileName,
        contentType: "image/jpeg",
        contentBase64: base64,
      });
    }
    return results;
  } finally {
    canvas.width = 0;
    canvas.height = 0;
    bitmap.close();
  }
}
