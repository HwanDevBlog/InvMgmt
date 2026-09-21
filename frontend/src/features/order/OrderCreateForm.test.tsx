import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { OrderCreateForm } from './OrderCreateForm';

const products = [
  { id: 1, sku: 'SKU-001', name: '키보드', active: true, stockQuantity: 10 },
  { id: 2, sku: 'SKU-002', name: '마우스', active: true, stockQuantity: 5 },
];

describe('OrderCreateForm', () => {
  it('빈 주문 번호와 중복 상품을 막는다', () => {
    const onSubmit = vi.fn();
    render(<OrderCreateForm products={products} isProcessing={false} onSubmit={onSubmit} onClose={vi.fn()} />);
    fireEvent.click(screen.getByRole('button', { name: '주문 생성' }));
    expect(screen.getByRole('alert')).toHaveTextContent('주문 번호');

    fireEvent.change(screen.getByRole('textbox', { name: '주문 번호' }), { target: { value: 'ORD-001' } });
    fireEvent.change(screen.getByRole('combobox', { name: '상품 1' }), { target: { value: '1' } });
    fireEvent.click(screen.getByRole('button', { name: '상품 추가' }));
    fireEvent.change(screen.getByRole('combobox', { name: '상품 2' }), { target: { value: '1' } });
    fireEvent.click(screen.getByRole('button', { name: '주문 생성' }));
    expect(screen.getByRole('alert')).toHaveTextContent('같은 상품');
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('서로 다른 상품과 양의 정수 수량을 전송한다', () => {
    const onSubmit = vi.fn();
    render(<OrderCreateForm products={products} isProcessing={false} onSubmit={onSubmit} onClose={vi.fn()} />);
    fireEvent.change(screen.getByRole('textbox', { name: '주문 번호' }), { target: { value: ' ORD-002 ' } });
    fireEvent.change(screen.getByRole('combobox', { name: '상품 1' }), { target: { value: '1' } });
    fireEvent.change(screen.getByRole('spinbutton', { name: '수량 1' }), { target: { value: '0' } });
    fireEvent.click(screen.getByRole('button', { name: '주문 생성' }));
    expect(screen.getByRole('alert')).toHaveTextContent('1 이상의 정수');

    fireEvent.change(screen.getByRole('spinbutton', { name: '수량 1' }), { target: { value: '2' } });
    fireEvent.click(screen.getByRole('button', { name: '상품 추가' }));
    fireEvent.change(screen.getByRole('combobox', { name: '상품 2' }), { target: { value: '2' } });
    fireEvent.click(screen.getByRole('button', { name: '주문 생성' }));
    expect(onSubmit).toHaveBeenCalledWith({
      orderNumber: 'ORD-002',
      lines: [{ productId: 1, quantity: 2 }, { productId: 2, quantity: 1 }],
    });
  });
});
