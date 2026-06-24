import type { BedZone, FarmStatusMapData, House, OrchidGroup, SelectedBedZone, SelectedOrchidGroup } from "@/types/farm";

export type OrchidSelection = SelectedBedZone | SelectedOrchidGroup;

export type MutationMode = "CREATE" | "EDIT" | "MOVE" | null;

export type OrchidFormState = {
  genus: string;
  varietyName: string;
  quantity: string;
  potSize: string;
  ageYear: string;
  status: string;
  placementType: string;
  trayCount: string;
  memo: string;
};

export type MutationPayload = {
  genus: string | null;
  varietyName: string;
  quantity: number;
  potSize: string | null;
  ageYear: number | null;
  status: string;
  placementType: string | null;
  trayCount: number | null;
  memo: string | null;
};

export type OrchidManagementMapProps = {
  mapData: FarmStatusMapData;
  house: House;
};

export type OrchidMutationContext = {
  selectedOrchidGroup: OrchidGroup | null;
  selectedBedZone: BedZone | null;
  resolvedZone: BedZone | null;
};
