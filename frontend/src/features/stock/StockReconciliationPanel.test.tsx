import { fireEvent, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderWithQueryClient } from '../../test/renderWithQueryClient';
import { StockReconciliationPanel } from './StockReconciliationPanel';

describe('StockReconciliationPanel', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('버튼을 누를 때만 대사를 요청하고 일치 건수를 표시한다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => [
      { productId: 1, sku: 'SKU-001', productName: '키보드', currentQuantity: 7, ledgerQuantity: 7, difference: 0, consistent: true },
    ] } as Response));
    renderWithQueryClient(<StockReconciliationPanel />);

    expect(fetch).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: '정합성 확인' }));
    expect(await screen.findByText(/대사 1건 · 일치 1건 · 불일치 0건/)).toBeInTheDocument();
    expect(fetch).toHaveBeenCalledWith('/api/reconciliations/stocks', expect.objectContaining({ headers: { Accept: 'application/json' } }));
  });

  it('불일치 상품의 현재고와 원장 합계 및 차이를 보여준다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => [
      { productId: 1, sku: 'SKU-001', productName: '키보드', currentQuantity: 7, ledgerQuantity: 5, difference: 2, consistent: false },
      { productId: 2, sku: 'SKU-002', productName: '마우스', currentQuantity: 3, ledgerQuantity: 3, difference: 0, consistent: true },
    ] } as Response));
    renderWithQueryClient(<StockReconciliationPanel />);
    fireEvent.click(screen.getByRole('button', { name: '정합성 확인' }));

    expect(await screen.findByText(/대사 2건 · 일치 1건 · 불일치 1건/)).toBeInTheDocument();
    const table = screen.getByRole('table');
    expect(within(table).getByText('SKU-001')).toBeInTheDocument();
    expect(within(table).getByText('+2')).toBeInTheDocument();
    expect(within(table).queryByText('SKU-002')).not.toBeInTheDocument();
  });

  it('조회 실패 시 안내하고 다시 확인할 수 있다', async () => {
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce({ ok: false, status: 500 } as Response)
      .mockResolvedValueOnce({ ok: true, json: async () => [] } as Response));
    renderWithQueryClient(<StockReconciliationPanel />);
    fireEvent.click(screen.getByRole('button', { name: '정합성 확인' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('대사 결과를 불러오지 못했습니다');

    fireEvent.click(screen.getByRole('button', { name: '정합성 확인' }));
    expect(await screen.findByText(/대사할 상품이 없습니다/)).toBeInTheDocument();
  });
});
