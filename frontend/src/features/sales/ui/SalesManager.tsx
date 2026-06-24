"use client";

import { FormEvent, useMemo, useState } from "react";
import Link from "next/link";
import { API_BASE_URL } from "@/shared/api/client";
import type { Customer, SalesSlip } from "@/entities/farm/types";

type SalesManagerProps = {
  initialCustomers: Customer[];
  initialSalesSlips: SalesSlip[];
};

type CustomerForm = {
  name: string;
  ownerName: string;
  phone: string;
  address: string;
  memo: string;
};

type SalesItemForm = {
  itemName: string;
  genus: string;
  spec: string;
  quantity: string;
  unitPrice: string;
  memo: string;
};

type SalesSlipForm = {
  saleDate: string;
  customerId: string;
  paymentStatus: string;
  salesStatus: string;
  paymentMethod: string;
  memo: string;
  items: SalesItemForm[];
};

export function SalesManager({ initialCustomers, initialSalesSlips }: SalesManagerProps) {
  const today = new Date().toISOString().slice(0, 10);
  const [customers, setCustomers] = useState(initialCustomers);
  const [salesSlips, setSalesSlips] = useState(initialSalesSlips);
  const [customerForm, setCustomerForm] = useState<CustomerForm>({ name: "", ownerName: "", phone: "", address: "", memo: "" });
  const [salesForm, setSalesForm] = useState<SalesSlipForm>({
    saleDate: today,
    customerId: initialCustomers[0] ? String(initialCustomers[0].id) : "",
    paymentStatus: "미입금",
    salesStatus: "작성중",
    paymentMethod: "",
    memo: "",
    items: [emptyItem()],
  });
  const [savingCustomer, setSavingCustomer] = useState(false);
  const [savingSlip, setSavingSlip] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const totalAmount = useMemo(
    () => salesForm.items.reduce((sum, item) => sum + Number(item.quantity || 0) * Number(item.unitPrice || 0), 0),
    [salesForm.items],
  );

  function updateCustomerForm<K extends keyof CustomerForm>(field: K, value: CustomerForm[K]) {
    setCustomerForm((current) => ({ ...current, [field]: value }));
  }

  function updateSalesForm<K extends keyof SalesSlipForm>(field: K, value: SalesSlipForm[K]) {
    setSalesForm((current) => ({ ...current, [field]: value }));
  }

  function updateItem(index: number, field: keyof SalesItemForm, value: string) {
    setSalesForm((current) => ({
      ...current,
      items: current.items.map((item, itemIndex) => (itemIndex === index ? { ...item, [field]: value } : item)),
    }));
  }

  async function handleCreateCustomer(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSavingCustomer(true);
    setErrorMessage(null);
    try {
      const response = await fetch(`${API_BASE_URL}/customers`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          name: customerForm.name,
          ownerName: nullableText(customerForm.ownerName),
          phone: nullableText(customerForm.phone),
          address: nullableText(customerForm.address),
          memo: nullableText(customerForm.memo),
        }),
      });
      const payload = await response.json();
      if (!response.ok) {
        throw new Error(payload?.error?.message ?? "거래처를 저장하지 못했습니다.");
      }
      const customer = payload.data as Customer;
      setCustomers((current) => [...current, customer]);
      setSalesForm((current) => ({ ...current, customerId: String(customer.id) }));
      setCustomerForm({ name: "", ownerName: "", phone: "", address: "", memo: "" });
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "요청 중 문제가 발생했습니다.");
    } finally {
      setSavingCustomer(false);
    }
  }

  async function handleCreateSalesSlip(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSavingSlip(true);
    setErrorMessage(null);
    try {
      const response = await fetch(`${API_BASE_URL}/sales-slips`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          saleDate: salesForm.saleDate,
          customerId: Number(salesForm.customerId),
          paymentStatus: salesForm.paymentStatus,
          salesStatus: salesForm.salesStatus,
          paymentMethod: nullableText(salesForm.paymentMethod),
          memo: nullableText(salesForm.memo),
          items: salesForm.items.map((item) => ({
            itemName: item.itemName,
            genus: nullableText(item.genus),
            spec: nullableText(item.spec),
            quantity: Number(item.quantity),
            unitPrice: Number(item.unitPrice),
            memo: nullableText(item.memo),
          })),
        }),
      });
      const payload = await response.json();
      if (!response.ok) {
        throw new Error(payload?.error?.message ?? "판매 전표를 저장하지 못했습니다.");
      }
      setSalesSlips((current) => [payload.data as SalesSlip, ...current]);
      setSalesForm((current) => ({ ...current, memo: "", items: [emptyItem()] }));
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "요청 중 문제가 발생했습니다.");
    } finally {
      setSavingSlip(false);
    }
  }

  return (
    <div className="grid gap-5 xl:grid-cols-[360px_minmax(0,1fr)]">
      <section className="space-y-4">
        <form className="rounded-md border border-[#d7ddd4] bg-white p-4 shadow-sm" onSubmit={handleCreateCustomer}>
          <p className="text-sm font-semibold text-[#3d6f91]">거래처</p>
          <h2 className="mt-1 text-xl font-semibold">거래처 등록</h2>
          <div className="mt-4 space-y-3">
            <TextField label="거래처명" required value={customerForm.name} onChange={(value) => updateCustomerForm("name", value)} />
            <div className="grid grid-cols-2 gap-3">
              <TextField label="대표자" value={customerForm.ownerName} onChange={(value) => updateCustomerForm("ownerName", value)} />
              <TextField label="전화번호" value={customerForm.phone} onChange={(value) => updateCustomerForm("phone", value)} />
            </div>
            <TextField label="주소" value={customerForm.address} onChange={(value) => updateCustomerForm("address", value)} />
            <TextField label="메모" value={customerForm.memo} onChange={(value) => updateCustomerForm("memo", value)} />
            <button className="w-full rounded-md bg-[#159447] px-4 py-2.5 text-sm font-semibold text-white disabled:opacity-60" disabled={savingCustomer} type="submit">
              {savingCustomer ? "저장 중" : "거래처 저장"}
            </button>
          </div>
        </form>
        <section className="rounded-md border border-[#d7ddd4] bg-white p-4 shadow-sm">
          <p className="text-sm font-semibold text-[#3d6f91]">거래처 목록</p>
          <div className="mt-3 space-y-2">
            {customers.map((customer) => (
              <button
                key={customer.id}
                className={`w-full rounded-md border px-3 py-2 text-left text-sm ${
                  salesForm.customerId === String(customer.id) ? "border-[#159447] bg-[#eef7ec]" : "border-[#d7ddd4] bg-white"
                }`}
                onClick={() => updateSalesForm("customerId", String(customer.id))}
                type="button"
              >
                <span className="font-semibold">{customer.name}</span>
                {customer.phone ? <span className="ml-2 text-[#5c6a60]">{customer.phone}</span> : null}
              </button>
            ))}
            {customers.length === 0 ? <p className="text-sm text-[#5c6a60]">등록된 거래처가 없습니다.</p> : null}
          </div>
        </section>
      </section>

      <section className="space-y-4">
        <form className="rounded-md border border-[#d7ddd4] bg-white p-4 shadow-sm" onSubmit={handleCreateSalesSlip}>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <p className="text-sm font-semibold text-[#3d6f91]">판매 전표</p>
              <h2 className="mt-1 text-xl font-semibold">새 판매 전표</h2>
            </div>
            <div className="rounded-md bg-[#eef7ec] px-3 py-2 text-sm font-semibold text-[#246b38]">
              합계 {totalAmount.toLocaleString()}원
            </div>
          </div>
          <div className="mt-4 grid gap-3 md:grid-cols-4">
            <TextField label="판매일" required type="date" value={salesForm.saleDate} onChange={(value) => updateSalesForm("saleDate", value)} />
            <SelectField label="거래처" value={salesForm.customerId} onChange={(value) => updateSalesForm("customerId", value)}>
              <option value="">선택</option>
              {customers.map((customer) => (
                <option key={customer.id} value={customer.id}>
                  {customer.name}
                </option>
              ))}
            </SelectField>
            <SelectField label="입금 상태" value={salesForm.paymentStatus} onChange={(value) => updateSalesForm("paymentStatus", value)}>
              <option value="미입금">미입금</option>
              <option value="입금 완료">입금 완료</option>
            </SelectField>
            <SelectField label="판매 상태" value={salesForm.salesStatus} onChange={(value) => updateSalesForm("salesStatus", value)}>
              <option value="작성중">작성중</option>
              <option value="출고 완료">출고 완료</option>
            </SelectField>
          </div>
          <div className="mt-3 grid gap-3 md:grid-cols-2">
            <TextField label="결제 방법" value={salesForm.paymentMethod} onChange={(value) => updateSalesForm("paymentMethod", value)} />
            <TextField label="메모" value={salesForm.memo} onChange={(value) => updateSalesForm("memo", value)} />
          </div>
          <div className="mt-4 space-y-3">
            {salesForm.items.map((item, index) => {
              const amount = Number(item.quantity || 0) * Number(item.unitPrice || 0);
              return (
                <div key={index} className="rounded-md border border-[#d7ddd4] bg-[#f8faf7] p-3">
                  <div className="grid gap-3 md:grid-cols-[minmax(0,1.2fr)_1fr_1fr_90px_110px_110px]">
                    <TextField label="품목명" required value={item.itemName} onChange={(value) => updateItem(index, "itemName", value)} />
                    <TextField label="속명" value={item.genus} onChange={(value) => updateItem(index, "genus", value)} />
                    <TextField label="규격" value={item.spec} onChange={(value) => updateItem(index, "spec", value)} />
                    <TextField label="수량" required type="number" value={item.quantity} onChange={(value) => updateItem(index, "quantity", value)} />
                    <TextField label="단가" required type="number" value={item.unitPrice} onChange={(value) => updateItem(index, "unitPrice", value)} />
                    <div>
                      <p className="text-sm font-semibold text-[#435047]">금액</p>
                      <p className="mt-2 text-sm font-semibold">{amount.toLocaleString()}원</p>
                    </div>
                  </div>
                  <div className="mt-2 flex gap-2">
                    <TextField label="품목 메모" value={item.memo} onChange={(value) => updateItem(index, "memo", value)} />
                    {salesForm.items.length > 1 ? (
                      <button
                        className="mt-6 rounded-md border border-[#e0b3aa] px-3 py-2 text-sm font-semibold text-[#b43b24]"
                        onClick={() => updateSalesForm("items", salesForm.items.filter((_, itemIndex) => itemIndex !== index))}
                        type="button"
                      >
                        삭제
                      </button>
                    ) : null}
                  </div>
                </div>
              );
            })}
          </div>
          <div className="mt-4 flex flex-wrap gap-2">
            <button className="rounded-md border border-[#d7ddd4] px-4 py-2 text-sm font-semibold" onClick={() => updateSalesForm("items", [...salesForm.items, emptyItem()])} type="button">
              품목 추가
            </button>
            <button className="rounded-md bg-[#159447] px-4 py-2 text-sm font-semibold text-white disabled:opacity-60" disabled={savingSlip || !salesForm.customerId} type="submit">
              {savingSlip ? "저장 중" : "판매 전표 저장"}
            </button>
          </div>
          {errorMessage ? <p className="mt-3 rounded-md bg-[#fff1ec] p-3 text-sm text-[#9b341e]">{errorMessage}</p> : null}
        </form>

        <section className="rounded-md border border-[#d7ddd4] bg-white p-4 shadow-sm">
          <div className="flex items-center justify-between">
            <h2 className="text-xl font-semibold">판매 전표 목록</h2>
            <span className="rounded-full bg-[#eef7ec] px-3 py-1 text-sm font-semibold text-[#246b38]">{salesSlips.length}건</span>
          </div>
          <div className="mt-3 overflow-x-auto">
            <table className="w-full min-w-[760px] border-separate border-spacing-y-2 text-left text-sm">
              <thead className="text-[#637063]">
                <tr>
                  <th className="px-3 font-semibold">전표번호</th>
                  <th className="px-3 font-semibold">판매일</th>
                  <th className="px-3 font-semibold">거래처</th>
                  <th className="px-3 text-right font-semibold">합계</th>
                  <th className="px-3 font-semibold">상태</th>
                  <th className="px-3 font-semibold">메모</th>
                  <th className="px-3 font-semibold">출력</th>
                </tr>
              </thead>
              <tbody>
                {salesSlips.map((slip) => (
                  <tr key={slip.id} className="bg-[#f8faf7]">
                    <td className="rounded-l-md px-3 py-3 font-semibold">{slip.slipNumber}</td>
                    <td className="px-3 py-3">{slip.saleDate}</td>
                    <td className="px-3 py-3">{slip.customer.name}</td>
                    <td className="px-3 py-3 text-right">{slip.totalAmount.toLocaleString()}원</td>
                    <td className="px-3 py-3">{slip.paymentStatus} / {slip.salesStatus}</td>
                    <td className="px-3 py-3">{slip.memo ?? "-"}</td>
                    <td className="rounded-r-md px-3 py-3">
                      <Link className="inline-flex min-h-0 items-center rounded-md border border-[#9db59a] px-3 py-1.5 text-xs font-semibold text-[#214f31]" href={`/print/sales-slips/${slip.id}`}>
                        A5 출력
                      </Link>
                    </td>
                  </tr>
                ))}
                {salesSlips.length === 0 ? (
                  <tr>
                    <td className="rounded-md bg-[#f8faf7] px-3 py-8 text-center text-[#5c6a60]" colSpan={7}>
                      아직 판매 전표가 없습니다.
                    </td>
                  </tr>
                ) : null}
              </tbody>
            </table>
          </div>
        </section>
      </section>
    </div>
  );
}

function emptyItem(): SalesItemForm {
  return { itemName: "", genus: "", spec: "", quantity: "1", unitPrice: "0", memo: "" };
}

function TextField({
  label,
  onChange,
  required = false,
  type = "text",
  value,
}: {
  label: string;
  onChange: (value: string) => void;
  required?: boolean;
  type?: "date" | "number" | "text";
  value: string;
}) {
  return (
    <label className="block w-full">
      <span className="text-sm font-semibold text-[#435047]">{label}</span>
      <input
        className="mt-1 w-full rounded-md border border-[#cfd8cc] px-3 py-2 text-sm"
        min={type === "number" ? 0 : undefined}
        required={required}
        type={type}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  );
}

function SelectField({
  children,
  label,
  onChange,
  value,
}: {
  children: React.ReactNode;
  label: string;
  onChange: (value: string) => void;
  value: string;
}) {
  return (
    <label className="block">
      <span className="text-sm font-semibold text-[#435047]">{label}</span>
      <select className="mt-1 w-full rounded-md border border-[#cfd8cc] px-3 py-2 text-sm" value={value} onChange={(event) => onChange(event.target.value)}>
        {children}
      </select>
    </label>
  );
}

function nullableText(value: string) {
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}
