import { useState, type FormEvent } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import type {
  BusinessPartner,
  BusinessPartnerPage,
} from "@/entities/farm/types";
import { createEmptyPage } from "@/shared/api/page";
import { useUrlPagedListState } from "@/shared/api/useUrlPagedListState";
import { createBusinessPartner, updateBusinessPartner } from "../api/salesApi";
import {
  createEmptyBusinessPartnerForm,
  toBusinessPartnerForm,
  toCreateBusinessPartnerPayload,
} from "../lib/salesForm";
import type { SalesRouteState } from "../lib/salesRouteParams";
import {
  BUSINESS_PARTNER_FILTER_KEYS,
  createInitialBusinessPartnerFilters,
  writeBusinessPartnerFilterParams,
} from "../lib/salesUrlFilters";
import { businessPartnerPageQueryOptions } from "./salesQueryOptions";
import { salesQueryKeys } from "./salesQueryKeys";
import type { BusinessPartnerFilterState, BusinessPartnerForm } from "./types";

export function useBusinessPartners({
  routeState,
}: {
  routeState: SalesRouteState<BusinessPartnerFilterState>;
}) {
  const queryClient = useQueryClient();
  const pageQuery = useQuery(businessPartnerPageQueryOptions(routeState));
  const listState = useUrlPagedListState({
    emptyFilters: createInitialBusinessPartnerFilters,
    filterKeys: BUSINESS_PARTNER_FILTER_KEYS,
    routeFilters: routeState.filters,
    writeFilterParams: writeBusinessPartnerFilterParams,
  });
  const pageData =
    pageQuery.data ??
    createEmptyPage<BusinessPartner>(routeState.size, routeState.page);
  const [selectedPartnerId, setSelectedPartnerId] = useState<number | null>(
    null,
  );
  const [createForm, setCreateForm] = useState<BusinessPartnerForm>(
    createEmptyBusinessPartnerForm(),
  );
  const [editDraft, setEditDraft] = useState<{
    partnerId: number | null;
    form: BusinessPartnerForm;
  }>(() => ({
    partnerId: pageData.content[0]?.id ?? null,
    form: pageData.content[0]
      ? toBusinessPartnerForm(pageData.content[0])
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
      { queryKey: salesQueryKeys.partners.pages },
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
    await queryClient.invalidateQueries({
      queryKey: salesQueryKeys.partners.all,
    });
  }

  return {
    filters: listState.filters,
    partners: pageData.content,
    currentPage: routeState.page,
    pageSize: routeState.size,
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

function toMessage(error: unknown) {
  return error instanceof Error
    ? error.message
    : "요청 중 문제가 발생했습니다.";
}
