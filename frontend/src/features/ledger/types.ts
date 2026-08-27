export type StockMovementType = 'INITIAL' | 'RESERVE' | 'CANCEL' | 'RETURN' | 'ADJUSTMENT';

export type StockLedger = {
  id: number;
  productId: number;
  sku: string;
  productName: string;
  movementType: StockMovementType;
  quantityDelta: number;
  balanceAfter: number;
  referenceType: string | null;
  referenceId: string | null;
  createdAt: string;
};
