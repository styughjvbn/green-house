export function formatShortDate(date: string | null | undefined) {
  if (!date) {
    return "-";
  }

  if (/^\d{4}-\d{2}-\d{2}$/.test(date)) {
    return date.slice(2);
  }

  const parsed = new Date(date);
  if (Number.isNaN(parsed.getTime())) {
    return date;
  }

  const year = String(parsed.getFullYear()).slice(2);
  const month = String(parsed.getMonth() + 1).padStart(2, "0");
  const day = String(parsed.getDate()).padStart(2, "0");

  return `${year}-${month}-${day}`;
}
