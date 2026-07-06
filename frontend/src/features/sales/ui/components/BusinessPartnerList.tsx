import { Plus } from "lucide-react";
import type { BusinessPartner } from "@/entities/farm/types";

export function BusinessPartnerList({
  partners,
  selectedBusinessPartnerId,
  onSelectBusinessPartner,
  onCreateBusinessPartner,
}: {
  partners: BusinessPartner[];
  selectedBusinessPartnerId: number | null;
  onSelectBusinessPartner: (partnerId: number) => void;
  onCreateBusinessPartner: () => void;
}) {
  return (
    <section className="rounded-md border border-[#d7ddd4] bg-white p-4 shadow-sm">
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-center gap-2">
          <h2 className="text-xl font-bold text-[#17251b]">거래처 목록</h2>
          <span className="text-sm font-semibold text-[#159447]">
            총 {partners.length}건
          </span>
        </div>
        <button
          className="inline-flex h-10 items-center gap-2 rounded-md bg-[#159447] px-4 text-sm font-semibold text-white shadow-sm"
          type="button"
          onClick={onCreateBusinessPartner}
        >
          <Plus className="h-4 w-4" strokeWidth={1.8} aria-hidden="true" />
          거래처 등록
        </button>
      </div>
      <div className="mt-3 space-y-2">
        {partners.map((partner) => (
          <button
            key={partner.id}
            className={`w-full rounded-md border px-3 py-2 text-left text-sm ${
              selectedBusinessPartnerId === partner.id
                ? "border-[#159447] bg-[#eef7ec]"
                : "border-[#d7ddd4] bg-white"
            }`}
            onClick={() => onSelectBusinessPartner(partner.id)}
            type="button"
          >
            <span className="font-semibold">{partner.name}</span>
            <span className="ml-2 text-xs text-[#66746a]">
              {partnerTypeLabel(partner.partnerType)}
            </span>
            {partner.phone ? (
              <span className="ml-2 text-[#5c6a60]">{partner.phone}</span>
            ) : null}
          </button>
        ))}
        {partners.length === 0 ? (
          <p className="text-sm text-[#5c6a60]">등록된 거래처가 없습니다.</p>
        ) : null}
      </div>
    </section>
  );
}

function partnerTypeLabel(type: BusinessPartner["partnerType"]) {
  if (type === "AUCTION_HOUSE") return "경매장";
  if (type === "RETAIL") return "소매";
  return "도매";
}
