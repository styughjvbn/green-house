import type { Customer } from "@/entities/farm/types";

export function CustomerList({
  customers,
  selectedCustomerId,
  onSelectCustomer,
}: {
  customers: Customer[];
  selectedCustomerId: string;
  onSelectCustomer: (customerId: number) => void;
}) {
  return (
    <section className="rounded-md border border-[#d7ddd4] bg-white p-4 shadow-sm">
      <p className="text-sm font-semibold text-[#3d6f91]">거래처 목록</p>
      <div className="mt-3 space-y-2">
        {customers.map((customer) => (
          <button
            key={customer.id}
            className={`w-full rounded-md border px-3 py-2 text-left text-sm ${
              selectedCustomerId === String(customer.id)
                ? "border-[#159447] bg-[#eef7ec]"
                : "border-[#d7ddd4] bg-white"
            }`}
            onClick={() => onSelectCustomer(customer.id)}
            type="button"
          >
            <span className="font-semibold">{customer.name}</span>
            {customer.phone ? <span className="ml-2 text-[#5c6a60]">{customer.phone}</span> : null}
          </button>
        ))}
        {customers.length === 0 ? <p className="text-sm text-[#5c6a60]">등록된 거래처가 없습니다.</p> : null}
      </div>
    </section>
  );
}

