export type Stock = {
  productId: number;
  sku: string;
  productName: string;
  quantity: number;
  version: number;
};

export type StockReconciliation = {
  productId: number;
  sku: string;
  productName: string;
  currentQuantity: number;
  ledgerQuantity: number;
  difference: number;
  consistent: boolean;
};
