import type { RowData } from "@tanstack/react-table";

declare module "@tanstack/react-table" {
  interface ColumnMeta<TData extends RowData, TValue> {
    align?: "left" | "center" | "right";
    headerClassName?: string;
    cellClassName?: string;
    hideable?: boolean;
    reorderable?: boolean;
    resizable?: boolean;
  }
}
