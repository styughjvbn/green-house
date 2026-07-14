export type StatusBadgeSize = "default" | "compact";
export type StatusBadgeTone = "blue" | "green" | "orange" | "red" | "gray";

export function StatusBadge({
  children,
  size = "default",
  tone,
}: {
  children: string;
  size?: StatusBadgeSize;
  tone: StatusBadgeTone;
}) {
  const sizeClass =
    size === "compact"
      ? "rounded px-1.5 py-0.5 text-[10px]"
      : "rounded px-2 py-1 text-[11px]";
  const toneClass = {
    blue: "bg-[#e9f1fb] text-[#286aa6]",
    green: "bg-[#e7f7e8] text-[#16853b]",
    orange: "bg-[#fff1d6] text-[#c66f00]",
    red: "bg-[#fff0ed] text-[#c4473c]",
    gray: "bg-[#eef1ee] text-[#657169]",
  }[tone];

  return (
    <span
      className={`inline-flex font-bold whitespace-nowrap ${sizeClass} ${toneClass}`}
    >
      {children}
    </span>
  );
}
