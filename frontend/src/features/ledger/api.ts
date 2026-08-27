import { getJson } from '../../api/http';
import type { StockLedger } from './types';

export function fetchStockLedgers(signal?: AbortSignal): Promise<StockLedger[]> {
  return getJson<StockLedger[]>('/api/stock-ledgers', signal);
}
