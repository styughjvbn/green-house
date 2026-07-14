"use client";

import type { ReactNode, TouchEvent } from "react";
import { useEffect, useMemo, useRef, useState } from "react";
import {
  flexRender,
  getCoreRowModel,
  useReactTable,
  type ColumnDef,
  type Column,
  type ColumnOrderState,
  type ColumnSizingState,
  type Header,
  type Row,
  type SortingState,
  type Table,
  type VisibilityState,
} from "@tanstack/react-table";
import {
  ArrowDown,
  ArrowUp,
  GripVertical,
  RotateCcw,
  Settings2,
} from "lucide-react";
import { PaginationControls } from "@/shared/ui/PaginationControls";
import type {} from "./data-table-types";

type DataTableProps<TData> = {
  actions?: ReactNode;
  columns: ColumnDef<TData, unknown>[];
  data: TData[];
  emptyMessage: string;
  errorMessage?: string | null;
  getRowId?: (row: TData) => string;
  isLoading?: boolean;
  manualSorting?: boolean;
  onRowClick?: (row: TData) => void;
  onSortingChange?: (sorting: SortingState) => void;
  pageIndex?: number;
  pageSize?: number;
  pageSizeOptions?: number[];
  selectedRowId?: string | null;
  settingsKey: string;
  sorting?: SortingState;
  title: string;
  totalLabel?: string;
  totalPages?: number;
  onPageChange?: (pageIndex: number) => void;
  onPageSizeChange?: (pageSize: number) => void;
};

type PersistedColumnSettings = {
  columnOrder?: ColumnOrderState;
  columnSizing?: ColumnSizingState;
  columnVisibility?: VisibilityState;
};

export function DataTable<TData>({
  actions,
  columns,
  data,
  emptyMessage,
  errorMessage = null,
  getRowId,
  isLoading = false,
  manualSorting = true,
  onRowClick,
  onSortingChange,
  pageIndex,
  pageSize,
  pageSizeOptions = [10, 20, 50],
  selectedRowId,
  settingsKey,
  sorting = [],
  title,
  totalLabel,
  totalPages,
  onPageChange,
  onPageSizeChange,
}: DataTableProps<TData>) {
  const tableBodyRef = useRef<HTMLDivElement | null>(null);
  const resizeTouchRef = useRef<{ columnId: string; time: number } | null>(
    null,
  );
  const initialSettings = useMemo(
    () => readColumnSettings(settingsKey),
    [settingsKey],
  );
  const [columnVisibility, setColumnVisibility] = useState<VisibilityState>(
    initialSettings.columnVisibility ?? {},
  );
  const [columnOrder, setColumnOrder] = useState<ColumnOrderState>(
    initialSettings.columnOrder ?? [],
  );
  const [columnSizing, setColumnSizing] = useState<ColumnSizingState>(
    initialSettings.columnSizing ?? {},
  );

  const table = useReactTable({
    data,
    enableSorting: Boolean(onSortingChange),
    columns,
    getCoreRowModel: getCoreRowModel(),
    getRowId,
    manualSorting,
    columnResizeMode: "onChange",
    state: {
      columnOrder,
      columnSizing,
      columnVisibility,
      sorting,
    },
    onColumnOrderChange: setColumnOrder,
    onColumnSizingChange: setColumnSizing,
    onColumnVisibilityChange: setColumnVisibility,
    onSortingChange: (updater) => {
      if (!onSortingChange) {
        return;
      }
      onSortingChange(
        typeof updater === "function" ? updater(sorting) : updater,
      );
    },
  });

  useEffect(() => {
    writeColumnSettings(settingsKey, {
      columnOrder,
      columnSizing,
      columnVisibility,
    });
  }, [columnOrder, columnSizing, columnVisibility, settingsKey]);

  function handleResizeTouchStart(
    event: TouchEvent<HTMLButtonElement>,
    header: Header<TData, unknown>,
  ) {
    const now = Date.now();
    const lastTouch = resizeTouchRef.current;
    const isDoubleTap =
      lastTouch?.columnId === header.column.id && now - lastTouch.time < 320;

    if (isDoubleTap) {
      event.preventDefault();
      event.stopPropagation();
      resizeTouchRef.current = null;
      fitColumnToContent(tableBodyRef.current, table, header.column);
      return;
    }

    resizeTouchRef.current = {
      columnId: header.column.id,
      time: now,
    };
    header.getResizeHandler()(event);
  }

  const hasPagination =
    pageIndex != null &&
    pageSize != null &&
    totalPages != null &&
    onPageChange &&
    onPageSizeChange;

  return (
    <section className="min-w-0 rounded-md border border-[#dfe5dc] bg-white shadow-sm">
      <div className="flex items-center justify-between gap-3 border-b border-[#e7ebe5] px-4 py-3">
        <h2 className="text-base font-bold text-[#17251b]">{title}</h2>
        <div className="flex shrink-0 items-center gap-3">
          {totalLabel ? (
            <span className="text-xs font-semibold whitespace-nowrap text-[#159447]">
              {totalLabel}
            </span>
          ) : null}
          {actions}
          <DataTableColumnMenu table={table} />
        </div>
      </div>

      <div ref={tableBodyRef} className="max-h-[590px] overflow-auto bg-white">
        <table
          className="w-full table-fixed border-collapse text-left text-xs"
          style={{
            minWidth: `${table.getCenterTotalSize()}px`,
          }}
        >
          <colgroup>
            {table.getVisibleLeafColumns().map((column) => (
              <col
                key={column.id}
                style={{
                  maxWidth: `${column.getSize()}px`,
                  width: `${column.getSize()}px`,
                }}
              />
            ))}
            <col />
          </colgroup>
          <thead className="sticky top-0 z-10 bg-[#f7f9f6] text-[#4b584f]">
            {table.getHeaderGroups().map((headerGroup) => (
              <tr key={headerGroup.id}>
                {headerGroup.headers.map((header) => {
                  const meta = header.column.columnDef.meta;
                  const sorted = header.column.getIsSorted();
                  const resizing = header.column.getIsResizing();
                  return (
                    <th
                      key={header.id}
                      data-column-id={header.column.id}
                      className={[
                        "relative border-r border-b border-[#e6ebe3] px-2.5 py-2.5 font-semibold whitespace-nowrap last:border-r-0",
                        alignClass(meta?.align),
                        meta?.headerClassName,
                      ]
                        .filter(Boolean)
                        .join(" ")}
                      style={{
                        maxWidth: `${header.getSize()}px`,
                        width: `${header.getSize()}px`,
                      }}
                    >
                      <span className="block max-w-full overflow-hidden text-ellipsis whitespace-nowrap">
                        <button
                          className="inline-flex min-h-0 w-max max-w-none items-center gap-1 whitespace-nowrap"
                          data-column-content={header.column.id}
                          type="button"
                          disabled={!header.column.getCanSort()}
                          onClick={header.column.getToggleSortingHandler()}
                        >
                          {flexRender(
                            header.column.columnDef.header,
                            header.getContext(),
                          )}
                          {sorted === "asc" ? (
                            <ArrowUp className="h-3.5 w-3.5" />
                          ) : sorted === "desc" ? (
                            <ArrowDown className="h-3.5 w-3.5" />
                          ) : null}
                        </button>
                      </span>
                      {header.column.getCanResize() ? (
                        <button
                          className={`group absolute top-0 right-0 h-full w-2 cursor-col-resize touch-none ${
                            resizing ? "bg-[#159447]/10" : ""
                          }`}
                          type="button"
                          aria-label="컬럼 너비 조절"
                          onDoubleClick={(event) => {
                            event.preventDefault();
                            event.stopPropagation();
                            fitColumnToContent(
                              tableBodyRef.current,
                              table,
                              header.column,
                            );
                          }}
                          onMouseDown={header.getResizeHandler()}
                          onTouchStart={(event) =>
                            handleResizeTouchStart(event, header)
                          }
                        >
                          <span
                            className={`absolute top-1/2 left-1/2 h-6 -translate-x-1/2 -translate-y-1/2 rounded-full transition ${
                              resizing
                                ? "w-1 bg-[#159447]"
                                : "w-[2px] bg-[#aebbac] group-hover:w-1 group-hover:bg-[#159447]"
                            }`}
                          />
                        </button>
                      ) : null}
                    </th>
                  );
                })}
                <th className="border-b border-[#e6ebe3] bg-[#f7f9f6]" />
              </tr>
            ))}
          </thead>
          <tbody>
            {isLoading ? (
              <StateRow colSpan={table.getVisibleLeafColumns().length + 1}>
                불러오는 중입니다.
              </StateRow>
            ) : errorMessage ? (
              <StateRow colSpan={table.getVisibleLeafColumns().length + 1}>
                {errorMessage}
              </StateRow>
            ) : table.getRowModel().rows.length === 0 ? (
              <StateRow colSpan={table.getVisibleLeafColumns().length + 1}>
                {emptyMessage}
              </StateRow>
            ) : (
              table
                .getRowModel()
                .rows.map((row) => (
                  <DataTableRow
                    key={row.id}
                    row={row}
                    selected={selectedRowId === row.id}
                    onClick={onRowClick}
                  />
                ))
            )}
          </tbody>
        </table>
      </div>

      {hasPagination ? (
        <div className="flex flex-wrap items-center justify-between gap-2 border-t border-[#e7ebe5] px-3 py-2.5">
          <PaginationControls
            nextLabel="다음"
            pageCount={totalPages}
            pageIndex={pageIndex}
            pageSize={pageSize}
            pageSizeOptions={pageSizeOptions}
            previousLabel="이전"
            onPageChange={onPageChange}
            onPageSizeChange={onPageSizeChange}
          />
        </div>
      ) : null}
    </section>
  );
}

function DataTableRow<TData>({
  row,
  selected,
  onClick,
}: {
  row: Row<TData>;
  selected: boolean;
  onClick?: (row: TData) => void;
}) {
  return (
    <tr
      className={`border-b border-[#edf0ec] hover:bg-[#f0f8ef] ${
        selected ? "bg-[#eaf7eb]" : ""
      } ${onClick ? "cursor-pointer" : ""}`}
      onClick={() => onClick?.(row.original)}
    >
      {row.getVisibleCells().map((cell) => {
        const meta = cell.column.columnDef.meta;
        return (
          <td
            key={cell.id}
            data-column-id={cell.column.id}
            className={[
              "min-w-0 overflow-hidden border-r border-[#eef2ec] px-2.5 py-2.5 last:border-r-0",
              alignClass(meta?.align),
              meta?.cellClassName,
            ]
              .filter(Boolean)
              .join(" ")}
            style={{
              maxWidth: `${cell.column.getSize()}px`,
              width: `${cell.column.getSize()}px`,
            }}
          >
            <span className="block max-w-full overflow-hidden text-ellipsis whitespace-nowrap">
              <span
                className="inline-block w-max max-w-none"
                data-column-content={cell.column.id}
              >
                {flexRender(cell.column.columnDef.cell, cell.getContext())}
              </span>
            </span>
          </td>
        );
      })}
      <td className="border-b border-[#edf0ec]" />
    </tr>
  );
}

function StateRow({
  children,
  colSpan,
}: {
  children: ReactNode;
  colSpan: number;
}) {
  return (
    <tr>
      <td
        className="py-14 text-center text-sm text-[#6c786f]"
        colSpan={colSpan}
      >
        {children}
      </td>
    </tr>
  );
}

function DataTableColumnMenu<TData>({ table }: { table: Table<TData> }) {
  const [open, setOpen] = useState(false);
  const [draggingColumnId, setDraggingColumnId] = useState<string | null>(null);
  const [dragOverColumnId, setDragOverColumnId] = useState<string | null>(null);
  const rootRef = useRef<HTMLDivElement | null>(null);
  const configurableColumns = table
    .getAllLeafColumns()
    .filter((column) => column.columnDef.meta?.hideable !== false);

  useEffect(() => {
    if (!open) {
      return;
    }

    function handlePointerDown(event: PointerEvent) {
      const target = event.target;
      if (
        target instanceof Node &&
        rootRef.current &&
        !rootRef.current.contains(target)
      ) {
        setOpen(false);
      }
    }

    document.addEventListener("pointerdown", handlePointerDown);

    return () => document.removeEventListener("pointerdown", handlePointerDown);
  }, [open]);

  function moveColumn(columnId: string, direction: -1 | 1) {
    const order = table.getState().columnOrder;
    const visibleOrder =
      order.length > 0
        ? order
        : table.getAllLeafColumns().map((column) => column.id);
    const index = visibleOrder.indexOf(columnId);
    const nextIndex = index + direction;
    if (index < 0 || nextIndex < 0 || nextIndex >= visibleOrder.length) {
      return;
    }
    const nextOrder = [...visibleOrder];
    nextOrder.splice(index, 1);
    nextOrder.splice(nextIndex, 0, columnId);
    table.setColumnOrder(nextOrder);
  }

  function reorderColumn(sourceId: string, targetId: string) {
    if (sourceId === targetId) {
      return;
    }
    const order = table.getState().columnOrder;
    const visibleOrder =
      order.length > 0
        ? order
        : table.getAllLeafColumns().map((column) => column.id);
    const sourceIndex = visibleOrder.indexOf(sourceId);
    const targetIndex = visibleOrder.indexOf(targetId);
    if (sourceIndex < 0 || targetIndex < 0) {
      return;
    }
    const nextOrder = [...visibleOrder];
    nextOrder.splice(sourceIndex, 1);
    nextOrder.splice(targetIndex, 0, sourceId);
    table.setColumnOrder(nextOrder);
  }

  return (
    <div
      ref={rootRef}
      className="relative"
      onBlur={(event) => {
        if (draggingColumnId) {
          return;
        }
        if (!event.currentTarget.contains(event.relatedTarget)) {
          setOpen(false);
        }
      }}
    >
      <button
        className="flex h-8 w-8 items-center justify-center rounded-md border border-[#dfe5dc] text-[#435047] hover:bg-[#f6f8f5]"
        type="button"
        aria-label="컬럼 설정"
        title="컬럼 설정"
        onClick={() => setOpen((value) => !value)}
      >
        <Settings2 className="h-4 w-4" strokeWidth={1.8} aria-hidden="true" />
      </button>
      {open ? (
        <div className="absolute top-10 right-0 z-30 w-72 rounded-md border border-[#dfe5dc] bg-white p-3 shadow-xl">
          <div className="mb-2 flex items-center justify-between">
            <p className="text-sm font-bold text-[#17251b]">컬럼 설정</p>
            <div className="flex gap-1">
              <button
                className="inline-flex h-7 items-center rounded border border-[#dfe5dc] px-2 text-xs font-semibold"
                type="button"
                onClick={() => table.resetColumnSizing()}
              >
                너비 초기화
              </button>
              <button
                className="inline-flex h-7 items-center gap-1 rounded border border-[#dfe5dc] px-2 text-xs font-semibold"
                type="button"
                onClick={() => {
                  table.resetColumnVisibility();
                  table.resetColumnOrder();
                  table.resetColumnSizing();
                }}
              >
                <RotateCcw className="h-3.5 w-3.5" />
                전체
              </button>
            </div>
          </div>
          <div className="max-h-80 space-y-1 overflow-y-auto">
            {configurableColumns.map((column) => {
              const isDragging = draggingColumnId === column.id;
              const showDropPreview =
                draggingColumnId != null &&
                dragOverColumnId === column.id &&
                draggingColumnId !== column.id;

              return (
                <div
                  key={column.id}
                  className={`relative grid grid-cols-[auto_minmax(0,1fr)_auto] items-center gap-2 rounded border border-[#edf0ec] px-2 py-1.5 transition ${
                    isDragging ? "bg-[#eef7ec] opacity-70" : ""
                  } ${showDropPreview ? "border-[#159447] bg-[#f4fbf3]" : ""}`}
                  draggable={column.columnDef.meta?.reorderable !== false}
                  onDragStart={() => setDraggingColumnId(column.id)}
                  onDragEnd={() => {
                    setDraggingColumnId(null);
                    setDragOverColumnId(null);
                  }}
                  onDragEnter={() => setDragOverColumnId(column.id)}
                  onDragOver={(event) => {
                    event.preventDefault();
                    setDragOverColumnId(column.id);
                  }}
                  onDrop={() => {
                    if (draggingColumnId) {
                      reorderColumn(draggingColumnId, column.id);
                    }
                    setDraggingColumnId(null);
                    setDragOverColumnId(null);
                  }}
                >
                  {showDropPreview ? (
                    <span className="absolute -top-1.5 right-2 left-2 h-1 rounded-full bg-[#159447]" />
                  ) : null}
                  <GripVertical className="h-4 w-4 text-[#8a968d]" />
                  <label className="flex min-w-0 items-center gap-2 text-sm">
                    <input
                      className="h-4 w-4 accent-[#159447]"
                      type="checkbox"
                      checked={column.getIsVisible()}
                      disabled={!column.getCanHide()}
                      onChange={column.getToggleVisibilityHandler()}
                    />
                    <span className="truncate">{columnLabel(column)}</span>
                  </label>
                  <div className="flex gap-1">
                    <button
                      className="h-6 w-6 rounded border border-[#dfe5dc] text-xs"
                      type="button"
                      aria-label="위로 이동"
                      onClick={() => moveColumn(column.id, -1)}
                    >
                      ↑
                    </button>
                    <button
                      className="h-6 w-6 rounded border border-[#dfe5dc] text-xs"
                      type="button"
                      aria-label="아래로 이동"
                      onClick={() => moveColumn(column.id, 1)}
                    >
                      ↓
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      ) : null}
    </div>
  );
}

function columnLabel<TData>(column: Column<TData, unknown>) {
  const header = column.columnDef.header;
  return typeof header === "string" ? header : column.id;
}

function fitColumnToContent<TData>(
  root: HTMLElement | null,
  table: Table<TData>,
  column: Column<TData, unknown>,
) {
  if (!root) {
    return;
  }

  const columnId = escapeSelector(column.id);
  const elements = root.querySelectorAll<HTMLElement>(
    `[data-column-content="${columnId}"]`,
  );
  const contentWidth = Array.from(elements).reduce(
    (maxWidth, element) => Math.max(maxWidth, Math.ceil(element.scrollWidth)),
    0,
  );
  const resizeHandleAllowance = 20;
  const maxSize = column.columnDef.maxSize ?? 600;
  const nextSize = Math.min(
    Math.max(contentWidth + resizeHandleAllowance, 40),
    maxSize,
  );

  table.setColumnSizing((current) => ({
    ...current,
    [column.id]: nextSize,
  }));
}

function escapeSelector(value: string) {
  return typeof CSS !== "undefined" && CSS.escape
    ? CSS.escape(value)
    : value.replace(/["\\]/g, "\\$&");
}

function alignClass(align?: "left" | "center" | "right") {
  if (align === "right") {
    return "text-right";
  }
  if (align === "center") {
    return "text-center";
  }
  return "";
}

function readColumnSettings(settingsKey: string): PersistedColumnSettings {
  if (typeof window === "undefined") {
    return {};
  }
  try {
    const raw = window.localStorage.getItem(storageKey(settingsKey));
    return raw ? (JSON.parse(raw) as PersistedColumnSettings) : {};
  } catch {
    return {};
  }
}

function writeColumnSettings(
  settingsKey: string,
  settings: PersistedColumnSettings,
) {
  if (typeof window === "undefined") {
    return;
  }
  window.localStorage.setItem(
    storageKey(settingsKey),
    JSON.stringify(settings),
  );
}

function storageKey(settingsKey: string) {
  return `green-house:data-table:${settingsKey}`;
}
