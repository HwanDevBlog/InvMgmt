import { screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderWithQueryClient } from '../../test/renderWithQueryClient';
import { StockPage } from './StockPage';

describe('StockPage', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('API에서 받은 현재고를 표와 요약에 표시한다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => [
          {
            productId: 1,
            sku: 'SKU-001',
            productName: '기계식 키보드',
            quantity: 25,
            version: 3,
          },
          {
            productId: 2,
            sku: 'SKU-002',
            productName: '무선 마우스',
            quantity: 0,
            version: 1,
          },
        ],
      } as Response),
    );

    renderWithQueryClient(<StockPage />);

    expect(await screen.findByText('기계식 키보드')).toBeInTheDocument();
    expect(screen.getByText('무선 마우스')).toBeInTheDocument();
    expect(screen.getByText('재고 있음')).toBeInTheDocument();
    expect(screen.getByText('품절')).toBeInTheDocument();
    expect(screen.getByText('v3')).toBeInTheDocument();
    expect(fetch).toHaveBeenCalledWith(
      '/api/stocks',
      expect.objectContaining({ headers: { Accept: 'application/json' } }),
    );
  });

  it('API 오류가 발생하면 다시 불러오기 안내를 표시한다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 500,
      } as Response),
    );

    renderWithQueryClient(<StockPage />);

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '재고 정보를 불러오지 못했습니다',
    );
    expect(screen.getByRole('button', { name: '다시 불러오기' })).toBeInTheDocument();
  });
});
