import { useRef, useState, type FormEvent } from 'react';
import type { CreateOrderInput, OrderProduct } from './api';

type OrderCreateFormProps = {
  products: OrderProduct[];
  isProcessing: boolean;
  onSubmit: (input: CreateOrderInput) => void;
  onClose: () => void;
};

type DraftLine = { key: number; productId: string; quantity: string };

export function OrderCreateForm({ products, isProcessing, onSubmit, onClose }: OrderCreateFormProps) {
  const [orderNumber, setOrderNumber] = useState('');
  const [lines, setLines] = useState<DraftLine[]>([{ key: 0, productId: '', quantity: '1' }]);
  const [validationError, setValidationError] = useState<string | null>(null);
  const nextLineKey = useRef(1);

  function updateLine(key: number, changes: Partial<DraftLine>) {
    setLines((current) => current.map((line) => line.key === key ? { ...line, ...changes } : line));
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const trimmedOrderNumber = orderNumber.trim();
    if (!trimmedOrderNumber || trimmedOrderNumber.length > 50) {
      setValidationError('주문 번호를 1~50자로 입력해 주세요.');
      return;
    }
    const selected = lines.map((line) => ({
      productId: Number(line.productId), quantity: Number(line.quantity),
    }));
    if (selected.some((line) => !products.some((product) => product.id === line.productId)
      || !Number.isSafeInteger(line.quantity) || line.quantity < 1)) {
      setValidationError('상품과 1 이상의 정수 수량을 입력해 주세요.');
      return;
    }
    if (new Set(selected.map((line) => line.productId)).size !== selected.length) {
      setValidationError('같은 상품을 한 주문에 두 번 넣을 수 없습니다.');
      return;
    }
    setValidationError(null);
    onSubmit({ orderNumber: trimmedOrderNumber, lines: selected });
  }

  return (
    <form className="order-create-form" aria-label="새 주문 입력" onSubmit={handleSubmit} noValidate>
      <div className="order-create-heading"><strong>새 주문</strong><span>주문 생성 후 재고 예약을 진행할 수 있습니다.</span></div>
      <label className="order-create-number">주문 번호
        <input value={orderNumber} maxLength={50} disabled={isProcessing}
          onChange={(event) => setOrderNumber(event.target.value)} placeholder="예: ORDER-001" />
      </label>
      <div className="order-create-lines">
        {lines.map((line, index) => (
          <div className="order-create-line" key={line.key}>
            <label>상품 {index + 1}
              <select value={line.productId} disabled={isProcessing}
                onChange={(event) => updateLine(line.key, { productId: event.target.value })}>
                <option value="">상품 선택</option>
                {products.map((product) => <option key={product.id} value={product.id}>
                  {product.sku} · {product.name}
                </option>)}
              </select>
            </label>
            <label>수량 {index + 1}
              <input type="number" min="1" step="1" inputMode="numeric" value={line.quantity}
                disabled={isProcessing} onChange={(event) => updateLine(line.key, { quantity: event.target.value })} />
            </label>
            {lines.length > 1 ? <button type="button" className="order-action-button order-action-button-secondary"
              disabled={isProcessing} onClick={() => setLines((current) => current.filter((item) => item.key !== line.key))}>
              상품 {index + 1} 삭제
            </button> : null}
          </div>
        ))}
      </div>
      {validationError ? <p className="order-return-error" role="alert">{validationError}</p> : null}
      <div className="order-create-actions">
        <button type="button" className="order-action-button order-action-button-secondary"
          disabled={isProcessing || lines.length >= products.length}
          onClick={() => setLines((current) => [...current, { key: nextLineKey.current++, productId: '', quantity: '1' }])}>
          상품 추가
        </button>
        <button type="button" className="order-action-button order-action-button-secondary" onClick={onClose} disabled={isProcessing}>닫기</button>
        <button type="submit" className="order-action-button" disabled={isProcessing}>주문 생성</button>
      </div>
    </form>
  );
}
