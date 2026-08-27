import { getJson } from '../../api/http';
import type { Stock } from './types';

export function fetchStocks(signal?: AbortSignal): Promise<Stock[]> {
  return getJson<Stock[]>('/api/stocks', signal);
}
