"use client";

import { useEffect, useState } from "react";
import { Archive, FolderPlus, LoaderCircle, Minus, Plus } from "lucide-react";
import type { OrchidGroup } from "@/entities/farm/types";
import {
  addOrchidGroupCollectionMember,
  archiveOrchidGroupCollection,
  createOrchidGroupCollection,
  getOrchidGroupCollections,
  removeOrchidGroupCollectionMember,
} from "../../api/orchidManagementApi";
import type { OrchidGroupCollection } from "../../model/types";

export default function UserCollectionPanel({
  houseNumber,
  orchidGroups,
  onSelectMembers,
}: {
  houseNumber: number;
  orchidGroups: OrchidGroup[];
  onSelectMembers: (orchidGroupIds: number[]) => void;
}) {
  const [collections, setCollections] = useState<OrchidGroupCollection[]>([]);
  const [name, setName] = useState("");
  const [loading, setLoading] = useState(true);
  const [savingId, setSavingId] = useState<number | "create" | null>(null);
  const [selectedCollectionId, setSelectedCollectionId] = useState<
    number | null
  >(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    void getOrchidGroupCollections()
      .then((result) => {
        if (!cancelled) setCollections(result);
      })
      .catch((error: unknown) => {
        if (!cancelled) setErrorMessage(toMessage(error));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  async function createCollection() {
    const normalizedName = name.trim();
    if (!normalizedName || savingId !== null) return;
    setSavingId("create");
    setErrorMessage(null);
    try {
      const created = await createOrchidGroupCollection(normalizedName);
      const withMember =
        orchidGroups.length > 0
          ? await addOrchidGroupCollectionMember(
              created.id,
              orchidGroups.map((group) => group.id),
            )
          : created;
      setCollections((current) => [withMember, ...current]);
      setSelectedCollectionId(withMember.id);
      setName("");
    } catch (error) {
      setErrorMessage(toMessage(error));
    } finally {
      setSavingId(null);
    }
  }

  async function toggleMembership(collection: OrchidGroupCollection) {
    if (savingId !== null || orchidGroups.length === 0) return;
    const joinedCount = countMembers(collection, orchidGroups);
    const allJoined = joinedCount === orchidGroups.length;
    setSavingId(collection.id);
    setErrorMessage(null);
    try {
      let updated: OrchidGroupCollection;
      if (allJoined) {
        updated = collection;
        for (const orchidGroup of orchidGroups) {
          updated = await removeOrchidGroupCollectionMember(
            collection.id,
            orchidGroup.id,
          );
        }
      } else {
        updated = await addOrchidGroupCollectionMember(
          collection.id,
          orchidGroups.map((group) => group.id),
        );
      }
      replaceCollection(updated);
    } catch (error) {
      setErrorMessage(toMessage(error));
    } finally {
      setSavingId(null);
    }
  }

  async function archiveCollection(collection: OrchidGroupCollection) {
    if (
      savingId !== null ||
      !window.confirm(`'${collection.name}' 그룹을 보관할까요?`)
    ) {
      return;
    }
    setSavingId(collection.id);
    setErrorMessage(null);
    try {
      await archiveOrchidGroupCollection(collection.id);
      setCollections((current) =>
        current.filter((item) => item.id !== collection.id),
      );
    } catch (error) {
      setErrorMessage(toMessage(error));
    } finally {
      setSavingId(null);
    }
  }

  function replaceCollection(updated: OrchidGroupCollection) {
    setCollections((current) =>
      current.map((item) => (item.id === updated.id ? updated : item)),
    );
  }

  const selectedCollection =
    collections.find((item) => item.id === selectedCollectionId) ?? null;
  const selectedHouseMembers = selectedCollection
    ? membersInHouse(selectedCollection, houseNumber)
    : [];

  return (
    <section className="flex min-h-0 flex-1 flex-col rounded-md border border-[#d7ddd4] bg-white p-3 shadow-sm">
      <div className="flex items-center justify-between gap-2">
        <div>
          <p className="text-sm font-semibold text-[#17251b]">사용자 그룹</p>
          <p className="mt-0.5 text-[11px] text-[#6a766e]">
            {orchidGroups.length > 0
              ? `선택한 ${orchidGroups.length}묶음을 그룹에 추가하거나 해제`
              : "관리 목적에 따라 직접 만든 그룹"}
          </p>
        </div>
        {loading ? (
          <LoaderCircle className="h-4 w-4 animate-spin text-[#159447]" />
        ) : null}
      </div>

      <form
        className="mt-3 flex gap-2"
        onSubmit={(event) => {
          event.preventDefault();
          void createCollection();
        }}
      >
        <input
          aria-label="새 사용자 그룹 이름"
          className="min-w-0 flex-1 rounded-md border border-[#cfd8cc] px-3 py-2 text-sm outline-none focus:border-[#159447]"
          maxLength={100}
          placeholder="새 그룹 이름"
          value={name}
          onChange={(event) => setName(event.target.value)}
        />
        <button
          className="flex items-center gap-1 rounded-md bg-[#159447] px-3 py-2 text-xs font-semibold text-white disabled:opacity-50"
          disabled={!name.trim() || savingId !== null}
          type="submit"
        >
          <FolderPlus className="h-4 w-4" />
          생성
        </button>
      </form>

      <div className="mt-3 min-h-0 flex-1 space-y-2 overflow-y-auto pr-1">
        {collections.map((collection) => {
          const joinedCount = countMembers(collection, orchidGroups);
          const allJoined =
            orchidGroups.length > 0 && joinedCount === orchidGroups.length;
          const saving = savingId === collection.id;
          const houseMembers = membersInHouse(collection, houseNumber);
          const selected = selectedCollectionId === collection.id;
          return (
            <div
              className={`flex items-center justify-between gap-2 rounded-md border px-3 py-2 ${
                selected
                  ? "border-[#246df2] bg-[#f4f8ff] ring-1 ring-[#246df2]/20"
                  : joinedCount > 0
                    ? "border-[#b9dfc2] bg-[#f2faf4]"
                    : "border-[#e1e6df] bg-white"
              }`}
              key={collection.id}
            >
              <button
                className="min-w-0 flex-1 text-left"
                type="button"
                onClick={() => {
                  setSelectedCollectionId(collection.id);
                  onSelectMembers(
                    houseMembers.map((member) => member.orchidGroupId),
                  );
                }}
              >
                <p className="truncate text-xs font-semibold text-[#26352b]">
                  {collection.name}
                </p>
                <p className="mt-0.5 text-[11px] text-[#6a766e]">
                  {collection.orchidGroupCount}묶음 · {collection.totalQuantity}
                  분 · 현재 동 {houseMembers.length}묶음
                </p>
              </button>
              <div className="flex shrink-0 gap-1">
                <button
                  aria-label={allJoined ? "그룹에서 전체 해제" : "그룹에 추가"}
                  className="flex h-8 items-center gap-1 rounded-md border border-[#cfd8cc] px-2 text-[11px] font-semibold text-[#34503b] disabled:opacity-50"
                  disabled={savingId !== null || orchidGroups.length === 0}
                  onClick={() => void toggleMembership(collection)}
                  type="button"
                >
                  {saving ? (
                    <LoaderCircle className="h-3.5 w-3.5 animate-spin" />
                  ) : allJoined ? (
                    <Minus className="h-3.5 w-3.5" />
                  ) : (
                    <Plus className="h-3.5 w-3.5" />
                  )}
                  {allJoined
                    ? `해제 ${joinedCount}`
                    : joinedCount > 0
                      ? `추가 ${orchidGroups.length - joinedCount}`
                      : orchidGroups.length > 0
                        ? `추가 ${orchidGroups.length}`
                        : "선택 필요"}
                </button>
                <button
                  aria-label="사용자 그룹 보관"
                  className="flex h-8 w-8 items-center justify-center rounded-md border border-[#e1e6df] text-[#6a766e] disabled:opacity-50"
                  disabled={savingId !== null}
                  onClick={() => void archiveCollection(collection)}
                  type="button"
                >
                  <Archive className="h-3.5 w-3.5" />
                </button>
              </div>
            </div>
          );
        })}
        {!loading && collections.length === 0 ? (
          <p className="rounded-md bg-[#f5f7f3] p-3 text-xs text-[#5c6a60]">
            사용자 그룹이 없습니다. 위에서 새 그룹을 만들 수 있습니다.
          </p>
        ) : null}
      </div>

      {selectedCollection ? (
        <div className="mt-3 shrink-0 rounded-md border border-[#dce4da] bg-[#f7faf6] p-3">
          <div className="flex items-center justify-between gap-2">
            <p className="truncate text-xs font-bold text-[#26352b]">
              {selectedCollection.name} 구성
            </p>
            <span className="shrink-0 text-[11px] font-semibold text-[#58705e]">
              현재 동 {selectedHouseMembers.length}묶음
            </span>
          </div>
          <div className="mt-2 max-h-32 space-y-1 overflow-y-auto">
            {selectedHouseMembers.map((member) => (
              <div
                className="flex items-center justify-between gap-2 rounded bg-white px-2 py-1.5 text-[11px]"
                key={member.membershipId}
              >
                <span className="min-w-0 truncate font-semibold text-[#344138]">
                  {member.varietyName} · {member.physicalBedNumber}다이 ·{" "}
                  {member.bedZoneName}
                </span>
                <span className="shrink-0 text-[#6a766e]">
                  {member.quantity}분
                </span>
              </div>
            ))}
            {selectedHouseMembers.length === 0 ? (
              <p className="text-[11px] text-[#6a766e]">
                현재 동에 포함된 난 묶음이 없습니다.
              </p>
            ) : null}
          </div>
        </div>
      ) : null}

      {errorMessage ? (
        <p className="mt-2 rounded-md border border-[#f1b0a0] bg-[#fff1ec] p-2 text-xs text-[#9b341e]">
          {errorMessage}
        </p>
      ) : null}
    </section>
  );
}

function countMembers(
  collection: OrchidGroupCollection,
  orchidGroups: OrchidGroup[],
) {
  const selectedIds = new Set(orchidGroups.map((group) => group.id));
  return collection.members.filter((member) =>
    selectedIds.has(member.orchidGroupId),
  ).length;
}

function membersInHouse(
  collection: OrchidGroupCollection,
  houseNumber: number,
) {
  return collection.members.filter(
    (member) => member.houseNumber === houseNumber,
  );
}

function toMessage(error: unknown) {
  return error instanceof Error
    ? error.message
    : "사용자 그룹을 처리하지 못했습니다.";
}
