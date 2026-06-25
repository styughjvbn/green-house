import Script from "next/script";

import {
  DEFAULT_FONT_SCALE,
  FONT_SCALE_STORAGE_KEY,
  MAX_FONT_SCALE,
  MIN_FONT_SCALE,
} from "../lib/fontScale";

export function FontScaleInitializer() {
  return (
    <Script
      id="font-scale-initializer"
      strategy="beforeInteractive"
      dangerouslySetInnerHTML={{
        __html: `
          (function () {
            try {
              var saved = window.localStorage.getItem("${FONT_SCALE_STORAGE_KEY}");
              var scale = Number(saved || "${DEFAULT_FONT_SCALE}");
              if (!Number.isFinite(scale)) scale = ${DEFAULT_FONT_SCALE};
              scale = Math.min(${MAX_FONT_SCALE}, Math.max(${MIN_FONT_SCALE}, scale));
              document.documentElement.style.setProperty("--font-scale", String(scale));
            } catch (error) {}
          })();
        `,
      }}
    />
  );
}
