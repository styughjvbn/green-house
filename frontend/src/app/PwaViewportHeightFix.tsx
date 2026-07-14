"use client";

import { useEffect } from "react";

const REMEASURE_DELAYS_MS = [0, 50, 150, 300, 600, 1000];

function isInstalledApp() {
  return (
    window.matchMedia("(display-mode: standalone)").matches ||
    window.matchMedia("(display-mode: fullscreen)").matches ||
    Boolean((navigator as Navigator & { standalone?: boolean }).standalone)
  );
}

function isMobileChrome() {
  const userAgent = navigator.userAgent;
  const isChrome = /Chrome|CriOS/.test(userAgent);
  const isExcludedChromium = /Edg|OPR|SamsungBrowser/.test(userAgent);
  const isMobile = /Android|Mobile/.test(userAgent);

  return isChrome && !isExcludedChromium && isMobile;
}

function readViewportHeight() {
  return Math.floor(window.visualViewport?.height ?? window.innerHeight);
}

export default function PwaViewportHeightFix() {
  useEffect(() => {
    if (!isInstalledApp() || !isMobileChrome()) {
      return;
    }

    const root = document.documentElement;
    const setMeasuredHeight = () => {
      root.style.setProperty(
        "--app-viewport-height",
        `${readViewportHeight()}px`,
      );
    };
    const scheduleMeasure = () => {
      window.requestAnimationFrame(setMeasuredHeight);
    };

    root.classList.add("pwa-fixed-viewport");
    const timeoutIds = REMEASURE_DELAYS_MS.map((delay) =>
      window.setTimeout(scheduleMeasure, delay),
    );

    window.visualViewport?.addEventListener("resize", scheduleMeasure);
    window.addEventListener("resize", scheduleMeasure);
    window.addEventListener("orientationchange", scheduleMeasure);
    document.addEventListener("visibilitychange", scheduleMeasure);

    return () => {
      timeoutIds.forEach(window.clearTimeout);
      window.visualViewport?.removeEventListener("resize", scheduleMeasure);
      window.removeEventListener("resize", scheduleMeasure);
      window.removeEventListener("orientationchange", scheduleMeasure);
      document.removeEventListener("visibilitychange", scheduleMeasure);
      root.classList.remove("pwa-fixed-viewport");
      root.style.removeProperty("--app-viewport-height");
    };
  }, []);

  return null;
}
