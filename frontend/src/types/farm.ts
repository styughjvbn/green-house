export type BedZoneSide = "LEFT" | "RIGHT" | "CUSTOM" | "HANGING";

export type BedZoneType = "DEFAULT" | "CUSTOM" | "HANGING" | "TRAY" | "GRID";

export type OrchidGroup = {
  id: number;
  bedZoneId: number;
  genus: string | null;
  varietyName: string;
  quantity: number;
  potSize: string | null;
  ageYear: number | null;
  status: string;
  placementType: string | null;
  trayCount: number | null;
  sortOrder: number;
  memo: string | null;
  houseNumber: number;
  physicalBedNumber: number;
  bedZoneName: string;
};

export type BedZone = {
  id: number;
  physicalBedId: number;
  physicalBedNumber: number;
  houseId: number;
  houseNumber: number;
  name: string;
  side: BedZoneSide;
  zoneType: BedZoneType;
  sortOrder: number;
  active: boolean;
  memo: string | null;
  orchidGroups: OrchidGroup[];
};

export type PhysicalBed = {
  id: number;
  houseId: number;
  houseNumber: number;
  number: number;
  displayOrder: number;
  lengthCm: number | null;
  widthCm: number | null;
  wireCount: number | null;
  supportIntervalCm: number | null;
  memo: string | null;
  bedZones: BedZone[];
};

export type House = {
  id: number;
  number: number;
  name: string;
  memo: string | null;
  physicalBeds: PhysicalBed[];
};
