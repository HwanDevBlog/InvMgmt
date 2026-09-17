import { getJson } from '../../api/http';
import type { Stock, StockReconciliation } from './types';

export function fetchStocks(signal?: AbortSignal): Promise<Stock[]> {
  return getJson<Stock[]>('/api/stocks', signal);
}

export function fetchStockReconciliations(signal?: AbortSignal): Promise<StockReconciliation[]> {
  return getJson<StockReconciliation[]>('/api/reconciliations/stocks', signal);
}
