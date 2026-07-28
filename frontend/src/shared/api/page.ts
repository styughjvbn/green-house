export type Page<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export function createEmptyPage<T>(size: number, page = 0): Page<T> {
  return {
    content: [],
    page,
    size,
    totalElements: 0,
    totalPages: 0,
  };
}
