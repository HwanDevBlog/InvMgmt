import { ApiError, getJson } from '../../api/http';
import type { Order } from './types';

export type OrderAction = 'reserve' | 'confirm' | 'cancel';
export type ReturnOrderLine = { orderLineId: number; quantity: number };

export function fetchOrders(signal?: AbortSignal): Promise<Order[]> {
  return getJson<Order[]>('/api/orders', signal);
}

async function postOrder(url: string, idempotencyKey: string, body?: unknown): Promise<Order> {
  const response = await fetch(url, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Idempotency-Key': idempotencyKey,
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!response.ok) {
    throw new ApiError('주문을 처리하지 못했습니다.', response.status);
  }
  return response.json() as Promise<Order>;
}

export function executeOrderAction(orderId: number, action: OrderAction, idempotencyKey: string): Promise<Order> {
  return postOrder(`/api/orders/${orderId}/${action}`, idempotencyKey);
}

export function returnOrderItems(orderId: number, lines: ReturnOrderLine[], idempotencyKey: string): Promise<Order> {
  return postOrder(`/api/orders/${orderId}/returns`, idempotencyKey, { lines });
}
