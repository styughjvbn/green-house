"use client";

import type { CSSObjectWithLabel, SingleValue } from "react-select";
import AsyncSelect from "react-select/async";
import type { VarietyOption } from "@/entities/farm/types";
import { searchOrchidVarieties } from "../../api/orchidManagementApi";

type VarietySelectOption = {
  value: number;
  label: string;
  variety: VarietyOption;
};

export default function VarietySearchSelect({
  disabled = false,
  selectedVariety,
  onSelect,
}: {
  disabled?: boolean;
  selectedVariety: VarietyOption | null;
  onSelect: (value: VarietyOption) => void;
}) {
  const selectedOption = selectedVariety ? toOption(selectedVariety) : null;

  async function loadOptions(inputValue: string) {
    const varieties = await searchOrchidVarieties(inputValue);
    const options = varieties.map(toOption);

    if (
      selectedOption &&
      !options.some((option) => option.value === selectedOption.value)
    ) {
      return [selectedOption, ...options];
    }

    return options;
  }

  function handleChange(option: SingleValue<VarietySelectOption>) {
    if (!option) {
      return;
    }
    onSelect(option.variety);
  }

  return (
    <label className="block">
      <span className="text-sm font-semibold text-[#435047]">품종</span>
      <AsyncSelect<VarietySelectOption, false>
        cacheOptions
        defaultOptions={selectedOption ? [selectedOption] : true}
        formatOptionLabel={(option) => (
          <div className="py-0.5">
            <div className="text-sm font-semibold text-[#17251b]">
              {option.variety.name}
            </div>
            <div className="text-xs text-[#6a766e]">
              {option.variety.genus}
              {option.variety.defaultPotSize
                ? ` · 기본 ${option.variety.defaultPotSize}`
                : ""}
            </div>
          </div>
        )}
        isClearable={false}
        isDisabled={disabled}
        loadOptions={loadOptions}
        loadingMessage={() => "품종 검색 중"}
        noOptionsMessage={() => "검색 결과가 없습니다."}
        placeholder="품종명 또는 속명 검색"
        styles={selectStyles}
        value={selectedOption}
        onChange={handleChange}
      />
    </label>
  );
}

function toOption(variety: VarietyOption): VarietySelectOption {
  return {
    value: variety.id,
    label: `${variety.name} ${variety.genus}`,
    variety,
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
