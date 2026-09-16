import { StrictMode } from 'react';
import { fireEvent, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderWithQueryClient } from '../../test/renderWithQueryClient';
import { StockLedgerPage } from './StockLedgerPage';

describe('StockLedgerPage', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('재고 증감과 업무 참조를 표시한다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => [
      { id: 2, productId: 1, sku: 'SKU-001', productName: '기계식 키보드', movementType: 'RESERVE', quantityDelta: -3, balanceAfter: 22, referenceType: 'ORDER', referenceId: '100', createdAt: '2026-08-27T07:00:00Z' },
      { id: 1, productId: 1, sku: 'SKU-001', productName: '기계식 키보드', movementType: 'INITIAL', quantityDelta: 25, balanceAfter: 25, referenceType: 'PRODUCT', referenceId: null, createdAt: '2026-08-27T06:00:00Z' },
    ] } as Response));
    renderWithQueryClient(<StockLedgerPage />);
    expect(await screen.findByText('ORDER · 100')).toBeInTheDocument();
    expect(within(screen.getByRole('table')).getByText('재고 예약')).toBeInTheDocument();
    expect(within(screen.getByRole('table')).getByText('초기 재고')).toBeInTheDocument();
    expect(screen.getByText('-3')).toBeInTheDocument();
    expect(screen.getByText('+25')).toBeInTheDocument();
    expect(screen.getByText('ORDER · 100')).toBeInTheDocument();
    expect(fetch).toHaveBeenCalledWith('/api/stock-ledgers', expect.objectContaining({ headers: { Accept: 'application/json' } }));
  });

  it('원장이 없으면 빈 결과를 안내한다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => [] } as Response));
    renderWithQueryClient(<StockLedgerPage />);
    expect(await screen.findByText('재고 거래 이력이 없습니다')).toBeInTheDocument();
  });

  it('참조 ID 검색·거래 유형 필터·증감 수량 정렬을 적용한다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => [
      { id: 2, productId: 1, sku: 'SKU-001', productName: '키보드', movementType: 'RESERVE', quantityDelta: -3, balanceAfter: 22, referenceType: 'ORDER', referenceId: '200', createdAt: '2026-08-27T07:00:00Z' },
      { id: 1, productId: 2, sku: 'SKU-002', productName: '마우스', movementType: 'INITIAL', quantityDelta: 25, balanceAfter: 25, referenceType: 'PRODUCT', referenceId: '100', createdAt: '2026-08-27T06:00:00Z' },
    ] } as Response));
    renderWithQueryClient(<StrictMode><StockLedgerPage /></StrictMode>);
    expect(await screen.findByText('키보드')).toBeInTheDocument();

    fireEvent.change(screen.getByRole('textbox', { name: '거래 검색' }), { target: { value: '100' } });
    expect(screen.getByRole('status')).toHaveTextContent('조회 결과 1건');
    expect(screen.queryByText('키보드')).not.toBeInTheDocument();
    fireEvent.change(screen.getByRole('textbox', { name: '거래 검색' }), { target: { value: '' } });
    fireEvent.change(screen.getByRole('combobox', { name: '거래 유형' }), { target: { value: 'INITIAL' } });
    expect(screen.queryByText('키보드')).not.toBeInTheDocument();

    fireEvent.change(screen.getByRole('combobox', { name: '거래 유형' }), { target: { value: 'all' } });
    fireEvent.click(screen.getByRole('button', { name: '증감 수량' }));
    fireEvent.click(screen.getByRole('button', { name: '증감 수량' }));
    expect(within(screen.getAllByRole('row')[1]).getByText('마우스')).toBeInTheDocument();
  });
});
