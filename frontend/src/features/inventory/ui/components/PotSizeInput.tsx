"use client";

export function PotSizeInput({
  label,
  value,
  onChange,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
}) {
  const { amount, unit } = parsePotSize(value);

  return (
    <label className="space-y-1 text-xs font-semibold text-[#425047]">
      <span>{label}</span>
      <div className="flex overflow-hidden rounded-md border border-[#d7ddd8] bg-white">
        <input
          className="min-w-0 flex-1 px-3 py-2 text-sm outline-none focus:ring-1 focus:ring-[#159447]"
          inputMode="decimal"
          value={amount}
          onChange={(event) =>
            onChange(formatPotSize(toNumericInput(event.target.value), unit))
          }
        />
        <select
          aria-label={`${label} 단위`}
          className="border-l border-[#d7ddd8] bg-[#f8faf7] px-2 py-2 text-sm font-semibold text-[#26352c] outline-none"
          value={unit}
          onChange={(event) =>
            onChange(formatPotSize(amount, event.target.value))
          }
        >
          <option value={'"'}>&quot;</option>
          <option value="cm">cm</option>
        </select>
      </div>
    </label>
  );
}

function parsePotSize(value: string) {
  const trimmed = value.trim();

  if (trimmed.toLowerCase().endsWith("cm")) {
    return {
      amount: toNumericInput(trimmed.slice(0, -2).trim()),
      unit: "cm",
    };
  }

  if (trimmed.endsWith('"')) {
    return {
      amount: toNumericInput(trimmed.slice(0, -1).trim()),
      unit: '"',
    };
  }

  return {
    amount: toNumericInput(trimmed),
    unit: '"',
  };
}

function formatPotSize(amount: string, unit: string) {
  const trimmed = amount.trim();
  return trimmed ? `${trimmed}${unit}` : "";
}

function toNumericInput(value: string) {
  const numeric = value.replace(/[^\d.]/g, "");
  const [integerPart, ...decimalParts] = numeric.split(".");
  return decimalParts.length
    ? `${integerPart}.${decimalParts.join("")}`
    : integerPart;
}
