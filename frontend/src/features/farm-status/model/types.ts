import type { FarmStatusTargetType } from "@/entities/farm/types";

export type SelectedTarget = {
  type: FarmStatusTargetType;
  id: number;
};
