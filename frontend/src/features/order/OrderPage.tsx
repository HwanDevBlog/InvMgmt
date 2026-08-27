import { useQuery } from '@tanstack/react-query';
import {
  createColumnHelper,
  flexRender,
  getCoreRowModel,
  getExpandedRowModel,
  type Row,
  useReactTable,
} from '@tanstack/react-table';
import { fetchOrders } from './api';
import type { Order, OrderStatus } from './types';

const numberFormatter = new Intl.NumberFormat('ko-KR');
const dateTimeFormatter = new Intl.DateTimeFormat('ko-KR', {
  year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
});
const statusLabels: Record<OrderStatus, string> = {
  CREATED: '생성', RESERVED: '재고 예약', CONFIRMED: '확정',
  CANCELED: '취소', RETURNED: '반품 완료', EXPIRED: '만료',
};

function sumQuantity(order: Order) {
  return order.lines.reduce((sum, line) => sum + line.quantity, 0);
}

function sumReturnedQuantity(order: Order) {
  return order.lines.reduce((sum, line) => sum + line.returnedQuantity, 0);
}

const columnHelper = createColumnHelper<Order>();
const columns = [
  columnHelper.display({
    id: 'expand',
    header: '상세',
    cell: ({ row }) => (
      <button
        type="button"
        className="expand-button"
        aria-label={`${row.original.orderNumber} 상품 내역 ${row.getIsExpanded() ? '접기' : '펼치기'}`}
        aria-expanded={row.getIsExpanded()}
        onClick={row.getToggleExpandedHandler()}
      >
        {row.getIsExpanded() ? '−' : '+'}
      </button>
    ),
  }),
  columnHelper.accessor('orderNumber', {
    header: '주문 번호',
    cell: (info) => <span className="order-number-cell">{info.getValue()}</span>,
  }),
  columnHelper.accessor('status', {
    header: '주문 상태',
    cell: (info) => <span className={`order-status status-${info.getValue().toLowerCase()}`}>{statusLabels[info.getValue()]}</span>,
  }),
  columnHelper.display({
    id: 'lineCount', header: '상품 종류',
    cell: ({ row }) => numberFormatter.format(row.original.lines.length),
  }),
  columnHelper.display({
    id: 'orderedQuantity', header: '주문 수량',
    cell: ({ row }) => <span className="quantity-cell">{numberFormatter.format(sumQuantity(row.original))}</span>,
  }),
  columnHelper.display({
    id: 'returnedQuantity', header: '반품 수량',
    cell: ({ row }) => numberFormatter.format(sumReturnedQuantity(row.original)),
  }),
  columnHelper.accessor('createdAt', {
    header: '주문 일시',
    cell: (info) => <time className="date-cell" dateTime={info.getValue()}>{dateTimeFormatter.format(new Date(info.getValue()))}</time>,
  }),
];

export function OrderPage() {
  const orderQuery = useQuery({
    queryKey: ['orders'],
    queryFn: ({ signal }) => fetchOrders(signal),
  });
  const orders = orderQuery.data ?? [];
  const totalLineCount = orders.reduce((sum, order) => sum + order.lines.length, 0);
  const totalReturnedQuantity = orders.reduce((sum, order) => sum + sumReturnedQuantity(order), 0);
  const table = useReactTable({
    data: orders,
    columns,
    getRowCanExpand: () => true,
    getCoreRowModel: getCoreRowModel(),
    getExpandedRowModel: getExpandedRowModel(),
  });

  if (orderQuery.isPending) {
    return <section className="content-state" aria-live="polite"><span className="loading-indicator" aria-hidden="true" /><h2>주문 목록을 불러오는 중입니다</h2><p>주문 상태와 상품 수량을 확인하고 있습니다.</p></section>;
  }
  if (orderQuery.isError) {
    return <section className="content-state error-state" role="alert"><span className="state-code">LOAD ERROR</span><h2>주문 목록을 불러오지 못했습니다</h2><p>백엔드 실행 상태를 확인한 뒤 다시 시도해 주세요.</p><button type="button" className="primary-button" onClick={() => void orderQuery.refetch()}>다시 불러오기</button></section>;
  }

  return (
    <section className="data-panel" aria-labelledby="order-table-title">
      <div className="data-toolbar">
        <div><p className="eyebrow">ORDER TRACKING</p><h2 id="order-table-title">주문 처리 현황</h2></div>
        <div className="summary-list" aria-label="주문 요약">
          <div><span>전체 주문</span><strong>{numberFormatter.format(orders.length)}</strong></div>
          <div><span>주문 항목</span><strong>{numberFormatter.format(totalLineCount)}</strong></div>
          <div><span>반품 수량</span><strong>{numberFormatter.format(totalReturnedQuantity)}</strong></div>
        </div>
      </div>
      {orders.length === 0 ? (
        <div className="empty-state"><span className="state-code">NO DATA</span><h3>등록된 주문이 없습니다</h3><p>주문이 생성되면 처리 상태와 상품 내역이 표시됩니다.</p></div>
      ) : (
        <div className="table-scroll"><table><thead>
          {table.getHeaderGroups().map((headerGroup) => <tr key={headerGroup.id}>{headerGroup.headers.map((header) => <th key={header.id} scope="col">{header.isPlaceholder ? null : flexRender(header.column.columnDef.header, header.getContext())}</th>)}</tr>)}
        </thead><tbody>
          {table.getRowModel().rows.map((row) => (
            <OrderRow key={row.id} row={row} visibleColumnCount={row.getVisibleCells().length} />
          ))}
        </tbody></table></div>
      )}
    </section>
  );
}

type OrderRowProps = {
  row: Row<Order>;
  visibleColumnCount: number;
};

function OrderRow({ row, visibleColumnCount }: OrderRowProps) {
  return (
    <>
      <tr>{row.getVisibleCells().map((cell) => <td key={cell.id}>{flexRender(cell.column.columnDef.cell, cell.getContext())}</td>)}</tr>
      {row.getIsExpanded() ? (
        <tr className="order-detail-row"><td colSpan={visibleColumnCount}>
          <div className="order-lines" aria-label={`${row.original.orderNumber} 상품 내역`}>
            <div className="order-lines-heading"><strong>주문 상품</strong><span>{row.original.lines.length}개 항목</span></div>
            <table><thead><tr><th scope="col">SKU</th><th scope="col">주문 수량</th><th scope="col">반품 수량</th></tr></thead>
              <tbody>{row.original.lines.map((line) => <tr key={line.id}><td><span className="sku-cell">{line.sku}</span></td><td>{numberFormatter.format(line.quantity)}</td><td>{numberFormatter.format(line.returnedQuantity)}</td></tr>)}</tbody>
            </table>
          </div>
        </td></tr>
      ) : null}
    </>
  );
}
