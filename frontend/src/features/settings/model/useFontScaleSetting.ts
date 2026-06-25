"use client";

import { useEffect, useState } from "react";
import {
  DEFAULT_FONT_SCALE,
  FONT_SCALE_STORAGE_KEY,
  normalizeFontScale,
} from "../lib/fontScale";

export function useFontScaleSetting() {
  const [fontScale, setFontScale] = useState(() => {
    if (typeof window === "undefined") {
      return DEFAULT_FONT_SCALE;
    }
    return normalizeFontScale(
      Number(window.localStorage.getItem(FONT_SCALE_STORAGE_KEY)),
    );
  });

  useEffect(() => {
    const normalizedScale = normalizeFontScale(fontScale);
    document.documentElement.style.setProperty(
      "--font-scale",
      String(normalizedScale),
    );
    window.localStorage.setItem(
      FONT_SCALE_STORAGE_KEY,
      String(normalizedScale),
    );
  }, [fontScale]);

  function updateFontScale(value: number) {
    setFontScale(normalizeFontScale(value));
  }

  function resetFontScale() {
    setFontScale(DEFAULT_FONT_SCALE);
  }

  return {
    fontScale,
    updateFontScale,
    resetFontScale,
  };
}
