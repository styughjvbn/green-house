"use client";

import { useId, useState } from "react";
import { useInfiniteQuery } from "@tanstack/react-query";
import Select from "react-select";
import {
  ReviewBadge,
  ReviewField,
  ReviewSelectInput,
  type InputReview,
} from "../common/InputReview";
import type { VarietyOption } from "@/entities/farm/types";
import { getSalesRecordedVarieties } from "../../api/salesApi";

type Option = {
  id: number;
  value: string;
  label: string;
  name: string;
  genus: string;
};

export function SalesRecordedVarietySelect({
  name,
  genus,
  varietyId,
  disabled,
  onSelect,
  review,
}: {
  name: string;
  genus: string;
  varietyId: string;
  disabled: boolean;
  onSelect: (variety: Pick<VarietyOption, "id" | "name" | "genus">) => void;
  review?: InputReview;
}) {
  const id = useId();
  const [keyword, setKeyword] = useState("");
  const normalized = keyword.trim();
  const query = useInfiniteQuery({
    queryKey: ["sales", "recordVarieties", normalized, 20],
    queryFn: ({ pageParam, signal }) =>
      getSalesRecordedVarieties(normalized, pageParam, signal),
    initialPageParam: 0,
    getNextPageParam: (lastPage) =>
      lastPage.page + 1 < lastPage.totalPages ? lastPage.page + 1 : undefined,
  });
  const options: Option[] = (
    query.data?.pages.flatMap((page) => page.content) ?? []
  ).map((variety) => ({
    id: variety.id,
    value: String(variety.id),
    label: `${variety.name} / ${variety.genus}${variety.active ? "" : " (비활성)"}`,
    name: variety.name,
    genus: variety.genus,
  }));
  const selectedValue = varietyId || JSON.stringify([genus, name]);
  const selected = name
    ? (options.find(
        (option) =>
          option.value === selectedValue &&
          option.name === name &&
          option.genus === genus,
      ) ?? {
        id: Number(varietyId),
        value: selectedValue,
        label: `${name} / ${genus}`,
        name,
        genus,
      })
    : null;

  return (
    <ReviewField
      id={`${id}-review`}
      review={review}
      disabled={disabled}
      className="w-1/2 min-w-0 shrink-0 space-y-1"
    >
      <label
        htmlFor={id}
        className="block text-sm font-semibold text-[#435047]"
      >
        품종명 / 속 <span className="text-red-600">*</span>
        {review ? <ReviewBadge /> : null}
      </label>
      <Select<Option, false>
        components={{ Input: ReviewSelectInput }}
        inputId={id}
        aria-describedby={`${id}-review`}
        instanceId={id}
        inputValue={keyword}
        value={selected}
        options={options}
        filterOption={null}
        isDisabled={disabled}
        isLoading={query.isFetching}
        menuPosition="fixed"
        placeholder="품종명 또는 속명 검색"
        loadingMessage={() => "품종 검색 중"}
        noOptionsMessage={() =>
          query.isError
            ? "품종을 불러오지 못했습니다."
            : "검색 결과가 없습니다."
        }
        styles={{
          control: (base) => ({
            ...base,
            minHeight: 40,
            borderColor: review ? "#d97706" : "#cfd8cc",
            backgroundColor: review ? "#fffbeb" : base.backgroundColor,
            borderRadius: 6,
          }),
          menu: (base) => ({ ...base, zIndex: 1300 }),
          option: (base, state) => ({
            ...base,
            fontSize: 14,
            color: "#17251b",
            backgroundColor: state.isSelected
              ? "#eaf7eb"
              : state.isFocused
                ? "#f3f9f3"
                : "#fff",
          }),
        }}
        onInputChange={(value, action) => {
          if (action.action === "input-change") setKeyword(value);
        }}
        onChange={(option) => {
          if (option) {
            onSelect({ id: option.id, name: option.name, genus: option.genus });
            review?.onConfirm();
          }
          setKeyword("");
        }}
        onMenuScrollToBottom={() => {
          if (query.hasNextPage && !query.isFetchingNextPage)
            void query.fetchNextPage();
        }}
      />
      {query.isError ? (
        <p role="alert" className="text-xs text-[#9b341e]">
          품종을 불러오지 못했습니다.{" "}
          <button
            type="button"
            disabled={disabled || query.isFetching}
            className="font-semibold underline"
            onClick={() => void query.refetch()}
          >
            다시 조회
          </button>
        </p>
      ) : null}
    </ReviewField>
  );
}
