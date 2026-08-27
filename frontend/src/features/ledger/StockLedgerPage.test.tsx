import { screen } from '@testing-library/react';
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
    expect(await screen.findByText('재고 예약')).toBeInTheDocument();
    expect(screen.getByText('초기 재고')).toBeInTheDocument();
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
});
