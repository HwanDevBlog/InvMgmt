import { ApiError, getJson } from '../../api/http';
import type { Order } from './types';

export type OrderAction = 'reserve' | 'confirm';

export function fetchOrders(signal?: AbortSignal): Promise<Order[]> {
  return getJson<Order[]>('/api/orders', signal);
}

export async function executeOrderAction(orderId: number, action: OrderAction, idempotencyKey: string): Promise<Order> {
  const response = await fetch(`/api/orders/${orderId}/${action}`, {
    method: 'POST',
    headers: { Accept: 'application/json', 'Idempotency-Key': idempotencyKey },
  });
  if (!response.ok) {
    throw new ApiError('주문을 처리하지 못했습니다.', response.status);
  }
  return response.json() as Promise<Order>;
}
