"use client";

import type { CSSObjectWithLabel, SingleValue } from "react-select";
import CreatableSelect from "react-select/creatable";
import type { Variety } from "../../model/types";
import { Field } from "./InventoryPrimitives";

type SelectOption = {
  label: string;
  value: string;
};

export function VarietyCreatableFields({
  genus,
  genusLabel = "속",
  name,
  nameLabel = "품종명",
  varieties,
  onGenusChange,
  onNameChange,
}: {
  genus: string;
  genusLabel?: string;
  name: string;
  nameLabel?: string;
  varieties: Variety[];
  onGenusChange: (value: string) => void;
  onNameChange: (value: string) => void;
}) {
  const genusOptions = toOptions(varieties.map((variety) => variety.genus));
  const nameOptions = toOptions(
    varieties
      .filter((variety) => !genus || variety.genus === genus)
      .map((variety) => variety.name),
  );

  return (
    <>
      <Field label={genusLabel}>
        <CreatableSelect<SelectOption, false>
          formatCreateLabel={(value) => `"${value}" 추가`}
          isClearable
          noOptionsMessage={() => "검색 결과가 없습니다."}
          options={genusOptions}
          placeholder="속을 선택하거나 입력"
          styles={selectStyles}
          value={toSelectedOption(genus)}
          onChange={(option) => onGenusChange(readOptionValue(option))}
          onCreateOption={(value) => onGenusChange(value.trim())}
        />
      </Field>
      <Field label={nameLabel}>
        <CreatableSelect<SelectOption, false>
          formatCreateLabel={(value) => `"${value}" 추가`}
          isClearable
          noOptionsMessage={() => "검색 결과가 없습니다."}
          options={nameOptions}
          placeholder="품종명을 선택하거나 입력"
          styles={selectStyles}
          value={toSelectedOption(name)}
          onChange={(option) => onNameChange(readOptionValue(option))}
          onCreateOption={(value) => onNameChange(value.trim())}
        />
      </Field>
    </>
  );
}

function toOptions(values: string[]) {
  return [...new Set(values.filter(Boolean))]
    .sort((left, right) => left.localeCompare(right, "ko"))
    .map((value) => ({ label: value, value }));
}

function toSelectedOption(value: string) {
  const trimmed = value.trim();
  return trimmed ? { label: trimmed, value: trimmed } : null;
}

function readOptionValue(option: SingleValue<SelectOption>) {
  return option?.value.trim() ?? "";
}

const selectStyles = {
  control: (base: CSSObjectWithLabel, state: { isFocused: boolean }) => ({
    ...base,
    minHeight: 36,
    borderRadius: 6,
    borderColor: state.isFocused ? "#159447" : "#d7ddd8",
    boxShadow: state.isFocused ? "0 0 0 1px #159447" : "none",
    "&:hover": {
      borderColor: state.isFocused ? "#159447" : "#d7ddd8",
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
    zIndex: 50,
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
