import type { Customer, SalesSlip } from "@/entities/farm/types";

export type SalesManagerProps = {
  initialCustomers: Customer[];
  initialSalesSlips: SalesSlip[];
};

export type CustomerForm = {
  name: string;
  ownerName: string;
  phone: string;
  address: string;
  memo: string;
};

export type SalesItemForm = {
  itemName: string;
  genus: string;
  spec: string;
  quantity: string;
  unitPrice: string;
  memo: string;
};

export type SalesSlipForm = {
  saleDate: string;
  customerId: string;
  paymentStatus: string;
  salesStatus: string;
  paymentMethod: string;
  memo: string;
  items: SalesItemForm[];
};

export type CreateCustomerPayload = {
  name: string;
  ownerName: string | null;
  phone: string | null;
  address: string | null;
  memo: string | null;
};

export type CreateSalesSlipPayload = {
  saleDate: string;
  customerId: number;
  paymentStatus: string;
  salesStatus: string;
  paymentMethod: string | null;
  memo: string | null;
  items: Array<{
    itemName: string;
    genus: string | null;
    spec: string | null;
    quantity: number;
    unitPrice: number;
    memo: string | null;
  }>;
};
