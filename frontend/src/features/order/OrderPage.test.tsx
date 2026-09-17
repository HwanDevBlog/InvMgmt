import { StrictMode } from 'react';
import { fireEvent, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderWithQueryClient } from '../../test/renderWithQueryClient';
import { OrderPage } from './OrderPage';

describe('OrderPage', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('주문 상태와 펼친 상품 내역을 표시한다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => [{
      id: 1, orderNumber: 'ORD-20260828-001', status: 'CONFIRMED',
      lines: [
        { id: 10, productId: 1, sku: 'SKU-001', quantity: 3, returnedQuantity: 1 },
        { id: 11, productId: 2, sku: 'SKU-002', quantity: 2, returnedQuantity: 0 },
      ],
      createdAt: '2026-08-28T01:00:00Z', updatedAt: '2026-08-28T02:00:00Z',
    }] } as Response));

    renderWithQueryClient(<OrderPage />);
    expect(await screen.findByText('ORD-20260828-001')).toBeInTheDocument();
    expect(within(screen.getByRole('table')).getByText('확정')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'ORD-20260828-001 상품 내역 펼치기' }));
    expect(screen.getByLabelText('ORD-20260828-001 상품 내역')).toBeInTheDocument();
    expect(screen.getByText('SKU-001')).toBeInTheDocument();
    expect(screen.getByText('SKU-002')).toBeInTheDocument();
    expect(fetch).toHaveBeenCalledWith('/api/orders', expect.objectContaining({ headers: { Accept: 'application/json' } }));
  });

  it('주문이 없으면 빈 결과를 안내한다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => [] } as Response));
    renderWithQueryClient(<OrderPage />);
    expect(await screen.findByText('등록된 주문이 없습니다')).toBeInTheDocument();
  });

  it('상품 코드 검색·주문 상태 필터·주문 일시 정렬을 적용한다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => [
      { id: 2, orderNumber: 'ORD-002', status: 'CONFIRMED', lines: [{ id: 20, productId: 1, sku: 'SKU-002', quantity: 2, returnedQuantity: 0 }], createdAt: '2026-08-28T02:00:00Z', updatedAt: '2026-08-28T02:00:00Z' },
      { id: 1, orderNumber: 'ORD-001', status: 'CANCELED', lines: [{ id: 10, productId: 2, sku: 'SKU-001', quantity: 1, returnedQuantity: 0 }], createdAt: '2026-08-28T01:00:00Z', updatedAt: '2026-08-28T01:00:00Z' },
    ] } as Response));
    renderWithQueryClient(<StrictMode><OrderPage /></StrictMode>);
    expect(await screen.findByText('ORD-002')).toBeInTheDocument();

    fireEvent.change(screen.getByRole('textbox', { name: '주문 검색' }), { target: { value: 'SKU-001' } });
    expect(screen.getByRole('status')).toHaveTextContent('조회 결과 1건');
    expect(screen.queryByText('ORD-002')).not.toBeInTheDocument();
    fireEvent.change(screen.getByRole('textbox', { name: '주문 검색' }), { target: { value: '' } });
    fireEvent.change(screen.getByRole('combobox', { name: '주문 상태' }), { target: { value: 'CONFIRMED' } });
    expect(screen.queryByText('ORD-001')).not.toBeInTheDocument();

    fireEvent.change(screen.getByRole('combobox', { name: '주문 상태' }), { target: { value: 'all' } });
    fireEvent.click(screen.getByRole('button', { name: '주문 일시' }));
    expect(within(screen.getAllByRole('row')[1]).getByText('ORD-001')).toBeInTheDocument();
  });

  it('생성 주문을 예약하고 목록을 새로 불러온다', async () => {
    let status = 'CREATED';
    const order = () => ({
      id: 1, orderNumber: 'ORD-001', status,
      lines: [{ id: 10, productId: 1, sku: 'SKU-001', quantity: 2, returnedQuantity: 0 }],
      createdAt: '2026-08-28T01:00:00Z', updatedAt: '2026-08-28T01:00:00Z',
    });
    const fetchMock = vi.fn(async (url: string, _init?: RequestInit) => {
      if (url === '/api/orders') return { ok: true, json: async () => [order()] } as Response;
      if (url === '/api/orders/1/reserve') {
        status = 'RESERVED';
        return { ok: true, json: async () => order() } as Response;
      }
      if (url === '/api/orders/1/confirm') {
        status = 'CONFIRMED';
        return { ok: true, json: async () => order() } as Response;
      }
      throw new Error(`Unexpected URL: ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);
    renderWithQueryClient(<OrderPage />);

    fireEvent.click(await screen.findByRole('button', { name: 'ORD-001 재고 예약' }));
    expect(await screen.findByRole('button', { name: 'ORD-001 주문 확정' })).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith('/api/orders/1/reserve', expect.objectContaining({
      method: 'POST', headers: expect.objectContaining({ 'Idempotency-Key': expect.any(String) }),
    }));
    fireEvent.click(screen.getByRole('button', { name: 'ORD-001 주문 확정' }));
    expect(await screen.findByText('ORD-001: 주문 확정 완료')).toBeInTheDocument();
    expect(within(screen.getByRole('table')).getByText('확정')).toBeInTheDocument();
    expect(fetchMock.mock.calls.filter(([url]) => url === '/api/orders')).toHaveLength(3);
  });

  it('요청 실패 후 재시도할 때 같은 멱등키를 사용한다', async () => {
    const order = {
      id: 1, orderNumber: 'ORD-001', status: 'CREATED',
      lines: [{ id: 10, productId: 1, sku: 'SKU-001', quantity: 2, returnedQuantity: 0 }],
      createdAt: '2026-08-28T01:00:00Z', updatedAt: '2026-08-28T01:00:00Z',
    };
    let attempts = 0;
    const fetchMock = vi.fn(async (url: string, _init?: RequestInit) => {
      if (url === '/api/orders') return { ok: true, json: async () => [order] } as Response;
      if (url === '/api/orders/1/reserve') {
        attempts += 1;
        return attempts === 1
          ? { ok: false, status: 500 } as Response
          : { ok: true, json: async () => ({ ...order, status: 'RESERVED' }) } as Response;
      }
      throw new Error(`Unexpected URL: ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);
    renderWithQueryClient(<OrderPage />);

    fireEvent.click(await screen.findByRole('button', { name: 'ORD-001 재고 예약' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('처리에 실패했습니다');
    fireEvent.click(screen.getByRole('button', { name: 'ORD-001 재고 예약' }));
    expect(await screen.findByText('ORD-001: 재고 예약 완료')).toBeInTheDocument();
    const postCalls = fetchMock.mock.calls.filter(([url]) => url === '/api/orders/1/reserve');
    expect(postCalls).toHaveLength(2);
    expect((postCalls[0][1] as RequestInit).headers).toEqual((postCalls[1][1] as RequestInit).headers);
  });
});
