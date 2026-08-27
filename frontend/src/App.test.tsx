import { screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App';
import { renderWithQueryClient } from './test/renderWithQueryClient';

describe('App', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => [],
      } as Response),
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('세 가지 업무 메뉴와 재고 현황을 표시한다', async () => {
    renderWithQueryClient(<App />);

    expect(screen.getByRole('button', { name: '재고 현황' })).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(screen.getByRole('button', { name: '재고 거래 이력' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '주문 상태 추적' })).toBeInTheDocument();
    expect(await screen.findByText('등록된 재고가 없습니다')).toBeInTheDocument();
  });
});
