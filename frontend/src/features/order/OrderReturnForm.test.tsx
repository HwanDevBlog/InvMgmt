import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { OrderReturnForm } from './OrderReturnForm';
import type { Order } from './types';

const order: Order = {
  id: 1, orderNumber: 'ORD-001', status: 'CONFIRMED',
  lines: [{ id: 10, productId: 1, sku: 'SKU-001', quantity: 5, returnedQuantity: 2 }],
  createdAt: '2026-08-28T01:00:00Z', updatedAt: '2026-08-28T01:00:00Z',
};

describe('OrderReturnForm', () => {
  it('남은 수량을 넘거나 아무것도 선택하지 않으면 제출하지 않는다', () => {
    const onSubmit = vi.fn();
    render(<OrderReturnForm order={order} isProcessing={false} onSubmit={onSubmit} onClose={vi.fn()} />);

    fireEvent.click(screen.getByRole('button', { name: '반품 적용' }));
    expect(screen.getByRole('alert')).toHaveTextContent('반품할 상품의 수량');
    fireEvent.change(screen.getByRole('spinbutton', { name: 'SKU-001 반품 수량' }), { target: { value: '4' } });
    fireEvent.click(screen.getByRole('button', { name: '반품 적용' }));
    expect(screen.getByRole('alert')).toHaveTextContent('남은 수량 이내');
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('남은 수량 이내의 양의 정수만 전송한다', () => {
    const onSubmit = vi.fn();
    render(<OrderReturnForm order={order} isProcessing={false} onSubmit={onSubmit} onClose={vi.fn()} />);
    fireEvent.change(screen.getByRole('spinbutton', { name: 'SKU-001 반품 수량' }), { target: { value: '2' } });
    fireEvent.click(screen.getByRole('button', { name: '반품 적용' }));
    expect(onSubmit).toHaveBeenCalledWith([{ orderLineId: 10, quantity: 2 }]);
  });
});
