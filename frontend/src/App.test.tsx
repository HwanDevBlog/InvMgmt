import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import App from './App';

describe('App', () => {
  it('설계한 세 가지 업무 화면을 안내한다', () => {
    render(<App />);

    expect(screen.getByRole('heading', { name: '재고 흐름 관리' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '재고 현황' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '재고 거래 이력' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '주문 상태 추적' })).toBeInTheDocument();
  });
});
