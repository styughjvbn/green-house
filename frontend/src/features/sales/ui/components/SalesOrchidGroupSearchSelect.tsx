"use client";

import type { CSSObjectWithLabel, SingleValue } from "react-select";
import AsyncSelect from "react-select/async";
import type { SalesOrchidGroupOption } from "@/entities/farm/types";
import { searchSalesOrchidGroups } from "../../api/salesApi";

type OrchidGroupSelectOption = {
  value: number;
  label: string;
  orchidGroup: SalesOrchidGroupOption;
};

export function SalesOrchidGroupSearchSelect({
  disabled = false,
  onSelect,
}: {
  disabled?: boolean;
  onSelect: (value: SalesOrchidGroupOption) => void;
}) {
  async function loadOptions(inputValue: string) {
    const orchidGroups = await searchSalesOrchidGroups(inputValue);
    return orchidGroups.map(toOption);
  }

  function handleChange(option: SingleValue<OrchidGroupSelectOption>) {
    if (!option) return;
    onSelect(option.orchidGroup);
  }

  return (
    <label className="block">
      <span className="text-sm font-semibold text-[#435047]">난 묶음 연결</span>
      <AsyncSelect<OrchidGroupSelectOption, false>
        cacheOptions
        defaultOptions
        formatOptionLabel={(option) => (
          <div className="py-0.5">
            <div className="text-sm font-semibold text-[#17251b]">
              {option.orchidGroup.varietyName}
            </div>
            <div className="text-xs text-[#6a766e]">
              {option.orchidGroup.houseNumber}동{" "}
              {option.orchidGroup.physicalBedNumber}
              배드 {option.orchidGroup.bedZoneName} / 가용{" "}
              {option.orchidGroup.availableQuantity}분
            </div>
          </div>
        )}
        isClearable
        isDisabled={disabled}
        loadOptions={loadOptions}
        loadingMessage={() => "난 묶음 검색 중"}
        noOptionsMessage={() => "검색 결과가 없습니다."}
        placeholder="품종명 또는 위치 검색"
        styles={selectStyles}
        value={null}
        onChange={handleChange}
      />
    </label>
  );
}

function toOption(
  orchidGroup: SalesOrchidGroupOption,
): OrchidGroupSelectOption {
  return {
    value: orchidGroup.id,
    label: `${orchidGroup.varietyName} ${orchidGroup.houseNumber}동`,
    orchidGroup,
  };
}

const selectStyles = {
  control: (base: CSSObjectWithLabel, state: { isFocused: boolean }) => ({
    ...base,
    minHeight: 42,
    borderRadius: 8,
    borderColor: state.isFocused ? "#159447" : "#cfd8cc",
    boxShadow: state.isFocused ? "0 0 0 1px #159447" : "none",
    "&:hover": {
      borderColor: state.isFocused ? "#159447" : "#cfd8cc",
    },
  }),
  valueContainer: (base: CSSObjectWithLabel) => ({
    ...base,
    padding: "2px 10px",
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
  menu: (base: CSSObjectWithLabel) => ({
    ...base,
    borderRadius: 8,
    overflow: "hidden",
    zIndex: 20,
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
    padding: "8px 12px",
  }),
} satisfies Record<string, unknown>;
