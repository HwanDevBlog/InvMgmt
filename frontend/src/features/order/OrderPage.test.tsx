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
    expect(screen.queryByRole('button', { name: 'ORD-20260828-001 주문 취소' })).not.toBeInTheDocument();
    expect(fetch).toHaveBeenCalledWith('/api/orders', expect.objectContaining({ headers: { Accept: 'application/json' } }));
  });

  it('주문이 없으면 빈 결과를 안내한다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => [] } as Response));
    renderWithQueryClient(<OrderPage />);
    expect(await screen.findByText('등록된 주문이 없습니다')).toBeInTheDocument();
  });

  it('새 주문을 생성하고 빈 목록을 갱신한다', async () => {
    let orders: object[] = [];
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      if (url === '/api/orders' && !init?.method) return { ok: true, json: async () => orders } as Response;
      if (url === '/api/products') return { ok: true, json: async () => [
        { id: 1, sku: 'SKU-001', name: '키보드', active: true, stockQuantity: 10 },
        { id: 2, sku: 'SKU-002', name: '비활성', active: false, stockQuantity: 5 },
      ] } as Response;
      if (url === '/api/orders' && init?.method === 'POST') {
        orders = [{ id: 1, orderNumber: 'ORD-NEW', status: 'CREATED',
          lines: [{ id: 10, productId: 1, sku: 'SKU-001', quantity: 2, returnedQuantity: 0 }],
          createdAt: '2026-08-28T01:00:00Z', updatedAt: '2026-08-28T01:00:00Z' }];
        return { ok: true, json: async () => orders[0] } as Response;
      }
      throw new Error(`Unexpected URL: ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);
    renderWithQueryClient(<OrderPage />);

    expect(await screen.findByText('등록된 주문이 없습니다')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '새 주문' }).closest('.filter-toolbar')).not.toBeNull();
    fireEvent.click(screen.getByRole('button', { name: '새 주문' }));
    expect(await screen.findByRole('combobox', { name: '상품 1' })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: /SKU-002/ })).not.toBeInTheDocument();
    fireEvent.change(screen.getByRole('textbox', { name: '주문 번호' }), { target: { value: 'ORD-NEW' } });
    fireEvent.change(screen.getByRole('combobox', { name: '상품 1' }), { target: { value: '1' } });
    fireEvent.change(screen.getByRole('spinbutton', { name: '수량 1' }), { target: { value: '2' } });
    fireEvent.click(screen.getByRole('button', { name: '주문 생성' }));

    expect(await screen.findByText('ORD-NEW: 주문 생성 완료')).toBeInTheDocument();
    expect(screen.getByText('ORD-NEW')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith('/api/orders', expect.objectContaining({
      method: 'POST', body: JSON.stringify({ orderNumber: 'ORD-NEW', lines: [{ productId: 1, quantity: 2 }] }),
    }));
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

  it('반품하지 않은 확정 주문만 확인 후 취소한다', async () => {
    let status = 'CONFIRMED';
    const order = () => ({
      id: 1, orderNumber: 'ORD-001', status,
      lines: [{ id: 10, productId: 1, sku: 'SKU-001', quantity: 2, returnedQuantity: 0 }],
      createdAt: '2026-08-28T01:00:00Z', updatedAt: '2026-08-28T01:00:00Z',
    });
    const fetchMock = vi.fn(async (url: string) => {
      if (url === '/api/orders') return { ok: true, json: async () => [order()] } as Response;
      if (url === '/api/orders/1/cancel') {
        status = 'CANCELED';
        return { ok: true, json: async () => order() } as Response;
      }
      throw new Error(`Unexpected URL: ${url}`);
    });
    const confirmMock = vi.fn().mockReturnValueOnce(false).mockReturnValueOnce(true);
    vi.stubGlobal('fetch', fetchMock);
    vi.stubGlobal('confirm', confirmMock);
    renderWithQueryClient(<OrderPage />);

    const cancelButton = await screen.findByRole('button', { name: 'ORD-001 주문 취소' });
    fireEvent.click(cancelButton);
    expect(fetchMock.mock.calls.filter(([url]) => url === '/api/orders/1/cancel')).toHaveLength(0);
    fireEvent.click(cancelButton);
    expect(await screen.findByText('ORD-001: 주문 취소 완료')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'ORD-001 주문 취소' })).not.toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith('/api/orders/1/cancel', expect.objectContaining({
      method: 'POST', headers: expect.objectContaining({ 'Idempotency-Key': expect.any(String) }),
    }));
    expect(confirmMock).toHaveBeenCalledTimes(2);
  });

  it('재고 예약 주문을 확인 후 만료하고 재고 관련 목록을 갱신한다', async () => {
    let status = 'RESERVED';
    const order = () => ({
      id: 1, orderNumber: 'ORD-001', status,
      lines: [{ id: 10, productId: 1, sku: 'SKU-001', quantity: 2, returnedQuantity: 0 }],
      createdAt: '2026-08-28T01:00:00Z', updatedAt: '2026-08-28T01:00:00Z',
    });
    const fetchMock = vi.fn(async (url: string) => {
      if (url === '/api/orders') return { ok: true, json: async () => [order()] } as Response;
      if (url === '/api/orders/1/expire') {
        status = 'EXPIRED';
        return { ok: true, json: async () => order() } as Response;
      }
      throw new Error(`Unexpected URL: ${url}`);
    });
    const confirmMock = vi.fn().mockReturnValue(true);
    vi.stubGlobal('fetch', fetchMock);
    vi.stubGlobal('confirm', confirmMock);
    renderWithQueryClient(<OrderPage />);

    expect(await screen.findByRole('button', { name: 'ORD-001 주문 확정' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'ORD-001 예약 만료' }));

    expect(confirmMock).toHaveBeenCalledWith('ORD-001 예약을 만료하고 재고를 복원할까요?');
    expect(await screen.findByText('ORD-001: 예약 만료 완료')).toBeInTheDocument();
    expect(within(screen.getByRole('table')).getByText('만료')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'ORD-001 주문 확정' })).not.toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith('/api/orders/1/expire', expect.objectContaining({
      method: 'POST', headers: expect.objectContaining({ 'Idempotency-Key': expect.any(String) }),
    }));
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

  it('확정 주문의 일부 반품 수량을 전송하고 주문 목록을 갱신한다', async () => {
    let returnedQuantity = 0;
    const order = () => ({
      id: 1, orderNumber: 'ORD-001', status: 'CONFIRMED',
      lines: [{ id: 10, productId: 1, sku: 'SKU-001', quantity: 3, returnedQuantity }],
      createdAt: '2026-08-28T01:00:00Z', updatedAt: '2026-08-28T01:00:00Z',
    });
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      if (url === '/api/orders') return { ok: true, json: async () => [order()] } as Response;
      if (url === '/api/orders/1/returns') {
        returnedQuantity = 1;
        return { ok: true, json: async () => order() } as Response;
      }
      throw new Error(`Unexpected URL: ${url} ${init?.method}`);
    });
    vi.stubGlobal('fetch', fetchMock);
    renderWithQueryClient(<OrderPage />);

    fireEvent.click(await screen.findByRole('button', { name: 'ORD-001 반품 처리' }));
    fireEvent.change(screen.getByRole('spinbutton', { name: 'SKU-001 반품 수량' }), { target: { value: '1' } });
    fireEvent.click(screen.getByRole('button', { name: '반품 적용' }));
    expect(await screen.findByText('ORD-001: 반품 처리 완료')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'ORD-001 반품 처리' })).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith('/api/orders/1/returns', expect.objectContaining({
      method: 'POST', body: JSON.stringify({ lines: [{ orderLineId: 10, quantity: 1 }] }),
      headers: expect.objectContaining({ 'Content-Type': 'application/json', 'Idempotency-Key': expect.any(String) }),
    }));
  });

  it('반품 요청 실패 후 같은 수량으로 재시도하면 멱등키를 재사용한다', async () => {
    const order = {
      id: 1, orderNumber: 'ORD-001', status: 'CONFIRMED',
      lines: [{ id: 10, productId: 1, sku: 'SKU-001', quantity: 3, returnedQuantity: 0 }],
      createdAt: '2026-08-28T01:00:00Z', updatedAt: '2026-08-28T01:00:00Z',
    };
    let attempts = 0;
    const fetchMock = vi.fn(async (url: string, _init?: RequestInit) => {
      if (url === '/api/orders') return { ok: true, json: async () => [order] } as Response;
      if (url === '/api/orders/1/returns') {
        attempts += 1;
        return attempts === 1
          ? { ok: false, status: 500 } as Response
          : { ok: true, json: async () => ({ ...order, status: 'RETURNED' }) } as Response;
      }
      throw new Error(`Unexpected URL: ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);
    renderWithQueryClient(<OrderPage />);

    fireEvent.click(await screen.findByRole('button', { name: 'ORD-001 반품 처리' }));
    fireEvent.change(screen.getByRole('spinbutton', { name: 'SKU-001 반품 수량' }), { target: { value: '3' } });
    fireEvent.click(screen.getByRole('button', { name: '반품 적용' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('반품 처리에 실패했습니다');
    fireEvent.click(screen.getByRole('button', { name: '반품 적용' }));
    expect(await screen.findByText('ORD-001: 반품 처리 완료')).toBeInTheDocument();
    const postCalls = fetchMock.mock.calls.filter(([url]) => url === '/api/orders/1/returns');
    expect(postCalls).toHaveLength(2);
    expect((postCalls[0][1] as RequestInit).headers).toEqual((postCalls[1][1] as RequestInit).headers);
  });
});
