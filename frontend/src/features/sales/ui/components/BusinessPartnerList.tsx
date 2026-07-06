import type { BusinessPartner } from "@/entities/farm/types";

export function BusinessPartnerList({
  partners,
  selectedBusinessPartnerId,
  onSelectBusinessPartner,
}: {
  partners: BusinessPartner[];
  selectedBusinessPartnerId: number | null;
  onSelectBusinessPartner: (partnerId: number) => void;
}) {
  return (
    <section className="rounded-md border border-[#d7ddd4] bg-white p-4 shadow-sm">
      <p className="text-sm font-semibold text-[#3d6f91]">거래처 목록</p>
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
