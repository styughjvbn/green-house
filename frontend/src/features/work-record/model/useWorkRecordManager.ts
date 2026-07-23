"use client";

import { useEffect, useState } from "react";
import type { BedZone, OrchidGroup } from "@/entities/farm/types";
import { getWorkTargetSelectionOptions } from "../api/workRecordApi";

export function useWorkRecordManager() {
  const [orchidGroups, setOrchidGroups] = useState<OrchidGroup[]>([]);
  const [bedZones, setBedZones] = useState<BedZone[]>([]);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [operationCreatedVersion, setOperationCreatedVersion] = useState(0);

  useEffect(() => {
    let cancelled = false;
    void getWorkTargetSelectionOptions()
      .then((options) => {
        if (!cancelled) {
          setOrchidGroups(options.orchidGroups);
          setBedZones(options.bedZones);
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setErrorMessage(
            error instanceof Error
              ? error.message
              : "대상 목록을 불러오지 못했습니다.",
          );
        }
      });

    return () => {
      cancelled = true;
    };
  }, []);

  return {
    errorMessage,
    orchidGroups,
    bedZones,
    operationCreatedVersion,
    setOperationCreatedVersion,
  };
}
