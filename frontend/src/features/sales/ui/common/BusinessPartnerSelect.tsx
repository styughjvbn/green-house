"use client";

import { useId, useState } from "react";
import {
  useInfiniteQuery,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import type { BusinessPartnerOption } from "@/entities/farm/types";
import type { CSSObjectWithLabel, SingleValue } from "react-select";
import Select from "react-select";
import {
  businessPartnerOptionQueryOptions,
  businessPartnerSearchQueryOptions,
} from "../../model/salesQueryOptions";

type PartnerSelectOption = {
  value: string;
  label: string;
  partner: BusinessPartnerOption;
};

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
  const [keyword, setKeyword] = useState("");
  const optionsQuery = useInfiniteQuery(
    businessPartnerSearchQueryOptions(
      keyword,
      auctionHouse,
      activeOnly ? true : undefined,
    ),
  );
  const selectedQuery = useQuery({
    ...businessPartnerOptionQueryOptions(Number(value)),
    enabled: value !== "",
  });
  const options = uniqueOptions(
    optionsQuery.data?.pages.flatMap((page) => page.content) ?? [],
  );
  const selected =
    selectedQuery.data ?? options.find((option) => String(option.id) === value);
  const selectedOption = selected
    ? toOption(selected)
    : value
      ? fallbackOption(value)
      : null;
  const selectOptions = options.map(toOption);
  const hasError =
    optionsQuery.isError || (value !== "" && selectedQuery.isError);

  function handleChange(option: SingleValue<PartnerSelectOption>) {
    if (option) {
      queryClient.setQueryData(
        businessPartnerOptionQueryOptions(option.partner.id).queryKey,
        option.partner,
      );
    }
    onChange(option?.value ?? "");
    setKeyword("");
  }

  return (
    <div className="min-w-0 space-y-1">
      <label
        className="block text-sm font-semibold text-[#435047]"
        htmlFor={id}
      >
        {label}
      </label>
      <Select<PartnerSelectOption, false>
        inputId={id}
        instanceId={id}
        inputValue={keyword}
        isClearable
        isDisabled={disabled}
        isLoading={optionsQuery.isFetching || selectedQuery.isFetching}
        isOptionDisabled={(option) => activeOnly && !option.partner.active}
        loadingMessage={() => `${label} 검색 중`}
        menuPosition="fixed"
        noOptionsMessage={() =>
          optionsQuery.isError
            ? `${label}를 불러오지 못했습니다.`
            : "검색 결과가 없습니다."
        }
        options={selectOptions}
        placeholder={activeOnly ? `${label} 검색` : "전체"}
        styles={selectStyles}
        value={selectedOption}
        onChange={handleChange}
        onInputChange={(nextKeyword, action) => {
          if (action.action === "input-change") setKeyword(nextKeyword);
        }}
        onMenuScrollToBottom={() => {
          if (optionsQuery.hasNextPage && !optionsQuery.isFetchingNextPage) {
            void optionsQuery.fetchNextPage();
          }
        }}
      />
      {hasError ? (
        <p role="alert" className="text-xs text-[#9b341e]">
          거래처를 불러오지 못했습니다.{" "}
          <button
            className="font-semibold underline"
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

function toOption(partner: BusinessPartnerOption): PartnerSelectOption {
  return {
    value: String(partner.id),
    label: `${partner.name}${partner.active ? "" : " (비활성)"}`,
    partner,
  };
}

function fallbackOption(value: string): PartnerSelectOption {
  return {
    value,
    label: `거래처 #${value}`,
    partner: { id: Number(value), name: `거래처 #${value}`, active: false },
  };
}

function uniqueOptions(options: BusinessPartnerOption[]) {
  return Array.from(
    new Map(options.map((option) => [option.id, option])).values(),
  );
}

const selectStyles = {
  control: (base: CSSObjectWithLabel, state: { isFocused: boolean }) => ({
    ...base,
    minHeight: 40,
    borderRadius: 6,
    borderColor: state.isFocused ? "#159447" : "#cfd8cc",
    boxShadow: state.isFocused ? "0 0 0 1px #159447" : "none",
    "&:hover": {
      borderColor: state.isFocused ? "#159447" : "#cfd8cc",
    },
  }),
  valueContainer: (base: CSSObjectWithLabel) => ({
    ...base,
    padding: "0 10px",
  }),
  placeholder: (base: CSSObjectWithLabel) => ({
    ...base,
    color: "#7d887f",
    fontSize: 14,
  }),
  input: (base: CSSObjectWithLabel) => ({
    ...base,
    fontSize: 14,
  }),
  singleValue: (base: CSSObjectWithLabel) => ({
    ...base,
    color: "#17251b",
    fontSize: 14,
    fontWeight: 600,
  }),
  menu: (base: CSSObjectWithLabel) => ({
    ...base,
    borderRadius: 8,
    overflow: "hidden",
    zIndex: 1300,
  }),
  option: (
    base: CSSObjectWithLabel,
    state: { isSelected: boolean; isFocused: boolean },
  ) => ({
    ...base,
    backgroundColor: state.isSelected
      ? "#eaf7eb"
      : state.isFocused
        ? "#f3f9f3"
        : "#ffffff",
    color: "#17251b",
    fontSize: 14,
    padding: "8px 12px",
  }),
} satisfies Record<string, unknown>;
