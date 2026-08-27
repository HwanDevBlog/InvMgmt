import { getJson } from '../../api/http';
import type { Order } from './types';

export function fetchOrders(signal?: AbortSignal): Promise<Order[]> {
  return getJson<Order[]>('/api/orders', signal);
}
