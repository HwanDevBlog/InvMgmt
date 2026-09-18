import { useState, type FormEvent } from 'react';
import type { ReturnOrderLine } from './api';
import type { Order } from './types';

type OrderReturnFormProps = {
  order: Order;
  isProcessing: boolean;
  onSubmit: (lines: ReturnOrderLine[]) => void;
  onClose: () => void;
};

export function OrderReturnForm({ order, isProcessing, onSubmit, onClose }: OrderReturnFormProps) {
  const [quantities, setQuantities] = useState<Record<number, string>>({});
  const [validationError, setValidationError] = useState<string | null>(null);
  const returnableLines = order.lines.filter((line) => line.quantity > line.returnedQuantity);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const lines = returnableLines.map((line) => ({
      orderLineId: line.id,
      quantity: Number(quantities[line.id] ?? 0),
    }));
    const invalid = lines.some((item) => {
      const orderLine = returnableLines.find((line) => line.id === item.orderLineId)!;
      return !Number.isInteger(item.quantity)
        || item.quantity < 0
        || item.quantity > orderLine.quantity - orderLine.returnedQuantity;
    });
    if (invalid) {
      setValidationError('반품 수량은 남은 수량 이내의 정수로 입력해 주세요.');
      return;
    }
    const selected = lines.filter((item) => item.quantity > 0);
    if (selected.length === 0) {
      setValidationError('반품할 상품의 수량을 입력해 주세요.');
      return;
    }
    setValidationError(null);
    onSubmit(selected);
  }

  return (
    <form className="order-return-form" aria-label={`${order.orderNumber} 반품 입력`} onSubmit={handleSubmit} noValidate>
      <div className="order-lines-heading">
        <strong>반품 수량 입력</strong>
        <span>0은 반품하지 않는 상품입니다.</span>
      </div>
      <table>
        <thead><tr><th scope="col">상품 코드</th><th scope="col">주문 수량</th><th scope="col">기반품</th><th scope="col">반품 가능</th><th scope="col">이번 반품</th></tr></thead>
        <tbody>{returnableLines.map((line) => {
          const remaining = line.quantity - line.returnedQuantity;
          return (
            <tr key={line.id}>
              <td><span className="sku-cell">{line.sku}</span></td>
              <td>{line.quantity}</td>
              <td>{line.returnedQuantity}</td>
              <td>{remaining}</td>
              <td><input type="number" min="0" max={remaining} step="1" inputMode="numeric"
                aria-label={`${line.sku} 반품 수량`} disabled={isProcessing}
                value={quantities[line.id] ?? ''}
                onChange={(event) => setQuantities((current) => ({ ...current, [line.id]: event.target.value }))} /></td>
            </tr>
          );
        })}</tbody>
      </table>
      {validationError ? <p className="order-return-error" role="alert">{validationError}</p> : null}
      <div className="order-return-actions">
        <button type="button" className="order-action-button order-action-button-secondary"
          onClick={onClose} disabled={isProcessing}>닫기</button>
        <button type="submit" className="order-action-button" disabled={isProcessing}>반품 적용</button>
      </div>
    </form>
  );
}
