export type OrderStatus =
  | 'CREATED'
  | 'RESERVED'
  | 'CONFIRMED'
  | 'CANCELED'
  | 'RETURNED'
  | 'EXPIRED';

export type OrderLine = {
  id: number;
  productId: number;
  sku: string;
  quantity: number;
  returnedQuantity: number;
};

export type Order = {
  id: number;
  orderNumber: string;
  status: OrderStatus;
  lines: OrderLine[];
  createdAt: string;
  updatedAt: string;
};
