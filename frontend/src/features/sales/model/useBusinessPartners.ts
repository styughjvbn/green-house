import { useState, type FormEvent } from "react";
import {
  keepPreviousData,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import type {
  BusinessPartner,
  BusinessPartnerPage,
} from "@/entities/farm/types";
import {
  createBusinessPartner,
  getBusinessPartnerPage,
  updateBusinessPartner,
} from "../api/salesApi";
import {
  createEmptyBusinessPartnerForm,
  toBusinessPartnerForm,
  toCreateBusinessPartnerPayload,
} from "../lib/salesForm";
import { createInitialBusinessPartnerFilters } from "../lib/salesUrlFilters";
import type { BusinessPartnerFilterState, BusinessPartnerForm } from "./types";
import { usePagedListQueryState } from "./usePagedListQueryState";

const businessPartnerKeys = {
  all: ["sales", "businessPartners"] as const,
  page: (filters: BusinessPartnerFilterState, page: number, size: number) =>
    [...businessPartnerKeys.all, filters, page, size] as const,
};

export function useBusinessPartners({
  initialFilters,
  initialPage,
}: {
  initialFilters: BusinessPartnerFilterState;
  initialPage: BusinessPartnerPage;
}) {
  const queryClient = useQueryClient();
  const listState = usePagedListQueryState({
    createEmptyFilters: createInitialBusinessPartnerFilters,
    initialFilters,
    initialPage: initialPage.page,
    initialSize: initialPage.size,
  });
  const { filters, page, size } = listState.queryState;
  const isInitialQuery =
    filters === initialFilters &&
    page === initialPage.page &&
    size === initialPage.size;
  const pageQuery = useQuery({
    queryKey: businessPartnerKeys.page(filters, page, size),
    queryFn: () => getBusinessPartnerPage(filters, page, size),
    initialData: isInitialQuery ? initialPage : undefined,
    placeholderData: keepPreviousData,
  });
  const pageData = pageQuery.data ?? emptyBusinessPartnerPage(size);
  const [selectedPartnerId, setSelectedPartnerId] = useState<number | null>(
    initialPage.content[0]?.id ?? null,
  );
  const [createForm, setCreateForm] = useState<BusinessPartnerForm>(
    createEmptyBusinessPartnerForm(),
  );
  const [editDraft, setEditDraft] = useState<{
    partnerId: number | null;
    form: BusinessPartnerForm;
  }>(() => ({
    partnerId: initialPage.content[0]?.id ?? null,
    form: initialPage.content[0]
      ? toBusinessPartnerForm(initialPage.content[0])
      : createEmptyBusinessPartnerForm(),
  }));
  const [savingCreate, setSavingCreate] = useState(false);
  const [savingEdit, setSavingEdit] = useState(false);
  const [mutationError, setMutationError] = useState<string | null>(null);

  const selectedPartner =
    pageData.content.find((partner) => partner.id === selectedPartnerId) ??
    pageData.content[0] ??
    null;
  const visibleSelectedPartnerId = selectedPartner?.id ?? null;
  const editForm =
    selectedPartner == null
      ? createEmptyBusinessPartnerForm()
      : editDraft.partnerId === selectedPartner.id
        ? editDraft.form
        : toBusinessPartnerForm(selectedPartner);

  function updateCreateForm<K extends keyof BusinessPartnerForm>(
    field: K,
    value: BusinessPartnerForm[K],
  ) {
    setCreateForm((current) => ({ ...current, [field]: value }));
  }

  function updateEditForm<K extends keyof BusinessPartnerForm>(
    field: K,
    value: BusinessPartnerForm[K],
  ) {
    if (!selectedPartner) return;
    setEditDraft((current) => ({
      partnerId: selectedPartner.id,
      form: {
        ...(current.partnerId === selectedPartner.id
          ? current.form
          : toBusinessPartnerForm(selectedPartner)),
        [field]: value,
      },
    }));
  }

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSavingCreate(true);
    setMutationError(null);
    try {
      const created = await createBusinessPartner(
        toCreateBusinessPartnerPayload(createForm),
      );
      setSelectedPartnerId(created.id);
      setCreateForm(createEmptyBusinessPartnerForm());
      listState.changePage(0);
      await invalidate();
      return true;
    } catch (error) {
      setMutationError(toMessage(error));
      return false;
    } finally {
      setSavingCreate(false);
    }
  }

  async function handleUpdate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedPartner) return false;
    setSavingEdit(true);
    setMutationError(null);
    try {
      const updated = await updateBusinessPartner(
        selectedPartner.id,
        toCreateBusinessPartnerPayload(editForm),
      );
      setSelectedPartnerId(updated.id);
      setEditDraft({
        partnerId: updated.id,
        form: toBusinessPartnerForm(updated),
      });
      updateCachedPartner(updated);
      await invalidate();
      return true;
    } catch (error) {
      setMutationError(toMessage(error));
      return false;
    } finally {
      setSavingEdit(false);
    }
  }

  function updateCachedPartner(updated: BusinessPartner) {
    queryClient.setQueriesData<BusinessPartnerPage>(
      { queryKey: businessPartnerKeys.all },
      (current) =>
        current == null
          ? current
          : {
              ...current,
              content: current.content.map((partner) =>
                partner.id === updated.id ? updated : partner,
              ),
            },
    );
  }

  async function invalidate() {
    await queryClient.invalidateQueries({ queryKey: businessPartnerKeys.all });
  }

  return {
    filters: listState.filters,
    partners: pageData.content,
    currentPage: page,
    pageSize: size,
    totalElements: pageData.totalElements,
    totalPages: Math.max(1, pageData.totalPages),
    selectedPartnerId: visibleSelectedPartnerId,
    selectedPartner,
    createForm,
    editForm,
    savingCreate,
    savingEdit,
    loading: pageQuery.isFetching,
    errorMessage:
      mutationError ??
      (pageQuery.error == null ? null : toMessage(pageQuery.error)),
    updateFilter: listState.updateFilter,
    search: listState.search,
    resetFilters: listState.reset,
    setPage: listState.changePage,
    setPageSize: listState.changePageSize,
    selectPartner: setSelectedPartnerId,
    updateCreateForm,
    updateEditForm,
    handleCreate,
    handleUpdate,
  };
}

function emptyBusinessPartnerPage(size: number): BusinessPartnerPage {
  return {
    content: [],
    page: 0,
    size,
    totalElements: 0,
    totalPages: 0,
  };
}

function toMessage(error: unknown) {
  return error instanceof Error
    ? error.message
    : "요청 중 문제가 발생했습니다.";
}
