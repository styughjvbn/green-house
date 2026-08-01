export function ColorField({
  color,
  onChange,
}: {
  color: string | null;
  onChange: (color: string | null) => void;
}) {
  return (
    <label className="flex items-center gap-2 text-sm font-semibold text-[#425047]">
      <input
        checked={color !== null}
        type="checkbox"
        onChange={(event) => onChange(event.target.checked ? "#4f8f86" : null)}
      />
      품종 색상 지정
      {color ? (
        <input
          aria-label="품종 색상"
          className="h-8 w-10 cursor-pointer rounded border border-[#d7ddd8] bg-white p-1"
          type="color"
          value={color}
          onChange={(event) => onChange(event.target.value.toUpperCase())}
        />
      ) : null}
    </label>
  );
}
