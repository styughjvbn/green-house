"use client";

import { useId, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  businessPartnerOptionQueryOptions,
  businessPartnerOptionsQueryOptions,
} from "../../model/salesQueryOptions";

export function BusinessPartnerSelect({
  label,
  value,
  onChange,
  auctionHouse,
  activeOnly = false,
  disabled = false,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  auctionHouse?: boolean;
  activeOnly?: boolean;
  disabled?: boolean;
}) {
  const id = useId();
  const queryClient = useQueryClient();
  const [search, setSearch] = useState({ keyword: "", page: 0 });
  const optionsQuery = useQuery(
    businessPartnerOptionsQueryOptions(
      search.keyword,
      search.page,
      auctionHouse,
      activeOnly ? true : undefined,
    ),
  );
  const selectedQuery = useQuery({
    ...businessPartnerOptionQueryOptions(Number(value)),
    enabled: value !== "",
  });
  const options = optionsQuery.data?.content ?? [];
  const selected =
    selectedQuery.data ?? options.find((option) => String(option.id) === value);
  const hasError =
    optionsQuery.isError || (value !== "" && selectedQuery.isError);
  const totalPages = optionsQuery.data?.totalPages ?? 0;

  return (
    <div className="min-w-0 space-y-1">
      <label
        className="block text-sm font-semibold text-[#435047]"
        htmlFor={id}
      >
        {label}
      </label>
      <input
        aria-label={`${label} 검색`}
        className="h-9 w-full rounded-md border border-[#cfd8cc] px-2 text-sm"
        disabled={disabled}
        placeholder="이름·연락처 검색"
        type="search"
        value={search.keyword}
        onChange={(event) =>
          setSearch({ keyword: event.target.value, page: 0 })
        }
        onKeyDown={(event) => {
          if (event.key === "Enter") event.preventDefault();
        }}
      />
      <select
        id={id}
        className="h-10 w-full rounded-md border border-[#cfd8cc] bg-white px-2 text-sm"
        disabled={disabled || optionsQuery.isFetching}
        value={value}
        onChange={(event) => {
          const next = options.find(
            (option) => String(option.id) === event.target.value,
          );
          if (next)
            queryClient.setQueryData(
              businessPartnerOptionQueryOptions(next.id).queryKey,
              next,
            );
          onChange(event.target.value);
        }}
      >
        <option value="">{activeOnly ? "선택" : "전체 거래처"}</option>
        {value && !options.some((option) => String(option.id) === value) ? (
          <option
            value={value}
            disabled={activeOnly && selected?.active === false}
          >
            {selected
              ? `${selected.name}${selected.active ? "" : " (비활성)"}`
              : `거래처 #${value}`}
          </option>
        ) : null}
        {options.map((option) => (
          <option
            key={option.id}
            value={option.id}
            disabled={activeOnly && !option.active}
          >
            {option.name}
            {option.active ? "" : " (비활성)"}
          </option>
        ))}
      </select>
      <div className="flex items-center justify-between gap-1 text-xs text-[#526158]">
        <button
          type="button"
          disabled={disabled || optionsQuery.isFetching || search.page === 0}
          aria-label={`${label} 이전 선택지`}
          onClick={() =>
            setSearch((current) => ({ ...current, page: current.page - 1 }))
          }
        >
          이전
        </button>
        <span role="status">
          {optionsQuery.isFetching
            ? "조회 중"
            : optionsQuery.isError
              ? "조회 실패"
              : totalPages === 0
                ? "검색 결과 없음"
                : `${search.page + 1} / ${totalPages}`}
        </span>
        <button
          type="button"
          disabled={
            disabled || optionsQuery.isFetching || search.page + 1 >= totalPages
          }
          aria-label={`${label} 다음 선택지`}
          onClick={() =>
            setSearch((current) => ({ ...current, page: current.page + 1 }))
          }
        >
          다음
        </button>
      </div>
      {hasError ? (
        <p role="alert" className="text-xs text-[#9b341e]">
          거래처를 불러오지 못했습니다.{" "}
          <button
            type="button"
            disabled={
              disabled || optionsQuery.isFetching || selectedQuery.isFetching
            }
            onClick={() => {
              void optionsQuery.refetch();
              if (value) void selectedQuery.refetch();
            }}
          >
            다시 조회
          </button>
        </p>
      ) : null}
    </div>
  );
}
