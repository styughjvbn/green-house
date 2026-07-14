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
  orchidGroup,
}: {
  orchidGroup: OrchidGroup;
}) {
  const [collections, setCollections] = useState<OrchidGroupCollection[]>([]);
  const [name, setName] = useState("");
  const [loading, setLoading] = useState(true);
  const [savingId, setSavingId] = useState<number | "create" | null>(null);
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
  }, [orchidGroup.id]);

  async function createCollection() {
    const normalizedName = name.trim();
    if (!normalizedName || savingId !== null) return;
    setSavingId("create");
    setErrorMessage(null);
    try {
      const created = await createOrchidGroupCollection(normalizedName);
      const withMember = await addOrchidGroupCollectionMember(
        created.id,
        orchidGroup.id,
      );
      setCollections((current) => [withMember, ...current]);
      setName("");
    } catch (error) {
      setErrorMessage(toMessage(error));
    } finally {
      setSavingId(null);
    }
  }

  async function toggleMembership(collection: OrchidGroupCollection) {
    if (savingId !== null) return;
    const joined = hasMember(collection, orchidGroup.id);
    setSavingId(collection.id);
    setErrorMessage(null);
    try {
      const updated = joined
        ? await removeOrchidGroupCollectionMember(collection.id, orchidGroup.id)
        : await addOrchidGroupCollectionMember(collection.id, orchidGroup.id);
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

  return (
    <section className="shrink-0 rounded-md border border-[#d7ddd4] bg-white p-3 shadow-sm">
      <div className="flex items-center justify-between gap-2">
        <div>
          <p className="text-sm font-semibold text-[#17251b]">사용자 그룹</p>
          <p className="mt-0.5 text-[11px] text-[#6a766e]">
            {orchidGroup.varietyName} 묶음의 별도 관리 그룹
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

      <div className="mt-3 max-h-48 space-y-2 overflow-y-auto pr-1">
        {collections.map((collection) => {
          const joined = hasMember(collection, orchidGroup.id);
          const saving = savingId === collection.id;
          return (
            <div
              className={`flex items-center justify-between gap-2 rounded-md border px-3 py-2 ${
                joined
                  ? "border-[#b9dfc2] bg-[#f2faf4]"
                  : "border-[#e1e6df] bg-white"
              }`}
              key={collection.id}
            >
              <div className="min-w-0">
                <p className="truncate text-xs font-semibold text-[#26352b]">
                  {collection.name}
                </p>
                <p className="mt-0.5 text-[11px] text-[#6a766e]">
                  {collection.orchidGroupCount}묶음 · {collection.totalQuantity}
                  분
                </p>
              </div>
              <div className="flex shrink-0 gap-1">
                <button
                  aria-label={joined ? "그룹에서 해제" : "그룹에 추가"}
                  className="flex h-8 items-center gap-1 rounded-md border border-[#cfd8cc] px-2 text-[11px] font-semibold text-[#34503b] disabled:opacity-50"
                  disabled={savingId !== null}
                  onClick={() => void toggleMembership(collection)}
                  type="button"
                >
                  {saving ? (
                    <LoaderCircle className="h-3.5 w-3.5 animate-spin" />
                  ) : joined ? (
                    <Minus className="h-3.5 w-3.5" />
                  ) : (
                    <Plus className="h-3.5 w-3.5" />
                  )}
                  {joined ? "해제" : "추가"}
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

      {errorMessage ? (
        <p className="mt-2 rounded-md border border-[#f1b0a0] bg-[#fff1ec] p-2 text-xs text-[#9b341e]">
          {errorMessage}
        </p>
      ) : null}
    </section>
  );
}

function hasMember(collection: OrchidGroupCollection, orchidGroupId: number) {
  return collection.members.some(
    (member) => member.orchidGroupId === orchidGroupId,
  );
}

function toMessage(error: unknown) {
  return error instanceof Error
    ? error.message
    : "사용자 그룹을 처리하지 못했습니다.";
}
