import { useQuery } from '@tanstack/react-query';
import { fetchStockReconciliations } from './api';

const numberFormatter = new Intl.NumberFormat('ko-KR');
const timeFormatter = new Intl.DateTimeFormat('ko-KR', {
  hour: '2-digit', minute: '2-digit', second: '2-digit',
});

export function StockReconciliationPanel() {
  const reconciliationQuery = useQuery({
    queryKey: ['stock-reconciliations'],
    queryFn: ({ signal }) => fetchStockReconciliations(signal),
    enabled: false,
  });
  const results = reconciliationQuery.data;
  const mismatches = results?.filter((result) => !result.consistent) ?? [];

  return (
    <div className="reconciliation-panel" aria-label="재고 정합성 대사">
      <div className="reconciliation-heading">
        <div>
          <p className="eyebrow">STOCK RECONCILIATION</p>
          <h3>현재고·원장 대사</h3>
          <p>서버에서 상품별 현재고와 재고 원장의 증감 합계를 비교합니다.</p>
        </div>
        <button
          type="button"
          className="reconciliation-button"
          disabled={reconciliationQuery.isFetching}
          onClick={() => void reconciliationQuery.refetch()}
        >
          {reconciliationQuery.isFetching ? '확인 중...' : results ? '다시 확인' : '정합성 확인'}
        </button>
      </div>

      {reconciliationQuery.isFetching ? <p className="reconciliation-message" role="status">정합성을 확인하고 있습니다.</p> : null}
      {reconciliationQuery.isError ? (
        <p className="reconciliation-message reconciliation-error" role="alert">대사 결과를 불러오지 못했습니다. 다시 확인해 주세요.</p>
      ) : results ? (
        <div className="reconciliation-result">
          <p className={`reconciliation-message ${mismatches.length > 0 ? 'reconciliation-error' : 'reconciliation-ok'}`} role="status">
            {results.length === 0
              ? '대사할 상품이 없습니다.'
              : `대사 ${numberFormatter.format(results.length)}건 · 일치 ${numberFormatter.format(results.length - mismatches.length)}건 · 불일치 ${numberFormatter.format(mismatches.length)}건`}
            {' · '}<time dateTime={new Date(reconciliationQuery.dataUpdatedAt).toISOString()}>{timeFormatter.format(reconciliationQuery.dataUpdatedAt)} 기준</time>
          </p>
          {mismatches.length > 0 ? (
            <div className="table-scroll">
              <table>
                <thead><tr><th scope="col">상품 코드</th><th scope="col">상품명</th><th scope="col">현재고</th><th scope="col">원장 합계</th><th scope="col">차이</th></tr></thead>
                <tbody>{mismatches.map((item) => (
                  <tr key={item.productId}>
                    <td><span className="sku-cell">{item.sku}</span></td>
                    <td>{item.productName}</td>
                    <td>{numberFormatter.format(item.currentQuantity)}</td>
                    <td>{numberFormatter.format(item.ledgerQuantity)}</td>
                    <td className="reconciliation-difference">{item.difference > 0 ? '+' : ''}{numberFormatter.format(item.difference)}</td>
                  </tr>
                ))}</tbody>
              </table>
            </div>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
