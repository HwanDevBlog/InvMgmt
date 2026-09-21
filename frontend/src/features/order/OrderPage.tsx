import { useMemo, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  createColumnHelper,
  flexRender,
  getCoreRowModel,
  getExpandedRowModel,
  getSortedRowModel,
  type Row,
  type SortingState,
  useReactTable,
} from '@tanstack/react-table';
import { ApiError } from '../../api/http';
import { createOrder, executeOrderAction, fetchOrderProducts, fetchOrders, returnOrderItems,
  type CreateOrderInput, type OrderAction, type ReturnOrderLine } from './api';
import { OrderCreateForm } from './OrderCreateForm';
import { OrderReturnForm } from './OrderReturnForm';
import type { Order, OrderStatus } from './types';

const numberFormatter = new Intl.NumberFormat('ko-KR');
const dateTimeFormatter = new Intl.DateTimeFormat('ko-KR', {
  year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
});
const statusLabels: Record<OrderStatus, string> = {
  CREATED: '생성', RESERVED: '재고 예약', CONFIRMED: '확정',
  CANCELED: '취소', RETURNED: '반품 완료', EXPIRED: '만료',
};
const actionLabels: Record<OrderAction, string> = {
  reserve: '재고 예약', confirm: '주문 확정', expire: '예약 만료', cancel: '주문 취소',
};

function availableActions(order: Order): OrderAction[] {
  if (order.status === 'CREATED') return ['reserve'];
  if (order.status === 'RESERVED') return ['confirm', 'expire'];
  if (order.status === 'CONFIRMED' && order.lines.every((line) => line.returnedQuantity === 0)) return ['cancel'];
  return [];
}

type OrderActionRequest = {
  orderId: number;
  orderNumber: string;
  action: OrderAction;
  idempotencyKey: string;
};

type OrderReturnRequest = {
  orderId: number;
  orderNumber: string;
  lines: ReturnOrderLine[];
  keyId: string;
  idempotencyKey: string;
};

function sumQuantity(order: Order) {
  return order.lines.reduce((sum, line) => sum + line.quantity, 0);
}

function sumReturnedQuantity(order: Order) {
  return order.lines.reduce((sum, line) => sum + line.returnedQuantity, 0);
}

const columnHelper = createColumnHelper<Order>();
const emptyOrders: Order[] = [];
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
  const queryClient = useQueryClient();
  const retryKeys = useRef(new Map<string, string>());
  const [search, setSearch] = useState('');
  const [orderStatus, setOrderStatus] = useState('all');
  const [sorting, setSorting] = useState<SortingState>([]);
  const [actionMessage, setActionMessage] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [returnOrderId, setReturnOrderId] = useState<number | null>(null);
  const [isCreateOpen, setIsCreateOpen] = useState(false);

  function invalidateOrderViews() {
    return Promise.all([
      queryClient.invalidateQueries({ queryKey: ['orders'] }),
      queryClient.invalidateQueries({ queryKey: ['stocks'] }),
      queryClient.invalidateQueries({ queryKey: ['stock-ledgers'] }),
      queryClient.invalidateQueries({ queryKey: ['stock-reconciliations'] }),
    ]);
  }

  const actionMutation = useMutation({
    mutationFn: ({ orderId, action, idempotencyKey }: OrderActionRequest) =>
      executeOrderAction(orderId, action, idempotencyKey),
    onSuccess: async (_order, request) => {
      retryKeys.current.delete(`${request.orderId}:${request.action}`);
      setActionError(null);
      await invalidateOrderViews();
      setActionMessage(`${request.orderNumber}: ${actionLabels[request.action]} 완료`);
    },
    onError: (error, request) => {
      setActionMessage(null);
      if (error instanceof ApiError && error.status === 409) {
        retryKeys.current.delete(`${request.orderId}:${request.action}`);
        void queryClient.invalidateQueries({ queryKey: ['orders'] });
        setActionError(`${request.orderNumber}: 현재 주문 상태에서 처리할 수 없습니다. 목록을 확인해 주세요.`);
      } else {
        setActionError(`${request.orderNumber}: 처리에 실패했습니다. 다시 시도해 주세요.`);
      }
    },
  });

  const returnMutation = useMutation({
    mutationFn: ({ orderId, lines, idempotencyKey }: OrderReturnRequest) =>
      returnOrderItems(orderId, lines, idempotencyKey),
    onSuccess: async (_order, request) => {
      retryKeys.current.delete(request.keyId);
      setActionError(null);
      await invalidateOrderViews();
      setReturnOrderId(null);
      setActionMessage(`${request.orderNumber}: 반품 처리 완료`);
    },
    onError: (error, request) => {
      setActionMessage(null);
      if (error instanceof ApiError && error.status === 409) {
        retryKeys.current.delete(request.keyId);
        void queryClient.invalidateQueries({ queryKey: ['orders'] });
        setActionError(`${request.orderNumber}: 상태 또는 반품 가능 수량이 변경되었습니다. 주문을 다시 확인해 주세요.`);
      } else {
        setActionError(`${request.orderNumber}: 반품 처리에 실패했습니다. 다시 시도해 주세요.`);
      }
    },
  });

  const createMutation = useMutation({
    mutationFn: createOrder,
    onSuccess: async (order) => {
      setActionError(null);
      await queryClient.invalidateQueries({ queryKey: ['orders'] });
      setIsCreateOpen(false);
      setActionMessage(`${order.orderNumber}: 주문 생성 완료`);
    },
    onError: (error, input) => {
      setActionMessage(null);
      if (error instanceof ApiError && error.status === 409) {
        void queryClient.invalidateQueries({ queryKey: ['orders'] });
        setActionError(`${input.orderNumber}: 이미 등록된 주문 번호입니다. 목록을 확인해 주세요.`);
      } else {
        setActionError(`${input.orderNumber}: 주문 생성에 실패했습니다. 목록에서 생성 여부를 확인해 주세요.`);
      }
    },
  });

  function handleCreate(input: CreateOrderInput) {
    if (createMutation.isPending || actionMutation.isPending || returnMutation.isPending) return;
    setActionMessage(null);
    setActionError(null);
    createMutation.mutate(input);
  }

  function handleAction(order: Order, action: OrderAction) {
    if (createMutation.isPending || actionMutation.isPending || returnMutation.isPending) return;
    setActionMessage(null);
    setActionError(null);
    const keyId = `${order.id}:${action}`;
    let idempotencyKey = retryKeys.current.get(keyId);
    if (!idempotencyKey) {
      idempotencyKey = crypto.randomUUID();
      retryKeys.current.set(keyId, idempotencyKey);
    }
    actionMutation.mutate({ orderId: order.id, orderNumber: order.orderNumber, action, idempotencyKey });
  }

  function handleReturn(order: Order, lines: ReturnOrderLine[]) {
    if (createMutation.isPending || actionMutation.isPending || returnMutation.isPending) return;
    setActionMessage(null);
    setActionError(null);
    const identity = [...lines].sort((left, right) => left.orderLineId - right.orderLineId)
      .map((line) => `${line.orderLineId}:${line.quantity}`).join(',');
    const keyId = `${order.id}:return:${identity}`;
    let idempotencyKey = retryKeys.current.get(keyId);
    if (!idempotencyKey) {
      idempotencyKey = crypto.randomUUID();
      retryKeys.current.set(keyId, idempotencyKey);
    }
    returnMutation.mutate({ orderId: order.id, orderNumber: order.orderNumber, lines, keyId, idempotencyKey });
  }
  const orderQuery = useQuery({
    queryKey: ['orders'],
    queryFn: ({ signal }) => fetchOrders(signal),
  });
  const productsQuery = useQuery({
    queryKey: ['products'],
    queryFn: ({ signal }) => fetchOrderProducts(signal),
    enabled: isCreateOpen,
  });
  const activeProducts = productsQuery.data?.filter((product) => product.active) ?? [];
  const isProcessing = createMutation.isPending || actionMutation.isPending || returnMutation.isPending;
  const orders = orderQuery.data ?? emptyOrders;
  const totalLineCount = orders.reduce((sum, order) => sum + order.lines.length, 0);
  const totalReturnedQuantity = orders.reduce((sum, order) => sum + sumReturnedQuantity(order), 0);
  const keyword = search.trim().toLocaleLowerCase();
  const filteredOrders = useMemo(() => orders.filter((order) =>
    (order.orderNumber.toLocaleLowerCase().includes(keyword)
      || order.lines.some((line) => line.sku.toLocaleLowerCase().includes(keyword)))
    && (orderStatus === 'all' || order.status === orderStatus),
  ), [orders, keyword, orderStatus]);
  const table = useReactTable({
    data: filteredOrders,
    columns,
    state: { sorting },
    onSortingChange: setSorting,
    sortDescFirst: false,
    getRowCanExpand: () => true,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
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
      <div className="filter-toolbar">
        {orders.length > 0 ? <>
          <label>주문 검색
            <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="주문 번호 또는 상품 코드" />
          </label>
          <label>주문 상태
            <select value={orderStatus} onChange={(event) => setOrderStatus(event.target.value)}>
              <option value="all">전체</option>
              {Object.entries(statusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
            </select>
          </label>
        </> : null}
        <div className="order-list-actions">
          {orders.length > 0 ? <span className="filter-count" role="status">조회 결과 {filteredOrders.length}건</span> : null}
          <button type="button" className={isCreateOpen
            ? 'order-action-button order-action-button-secondary order-create-toggle'
            : 'primary-button order-create-toggle'} disabled={isProcessing}
            aria-expanded={isCreateOpen} onClick={() => {
              setActionError(null); setActionMessage(null); setIsCreateOpen((open) => !open);
            }}>{isCreateOpen ? '주문 입력 닫기' : '새 주문'}</button>
        </div>
      </div>
      {isCreateOpen ? <div className="order-create-panel">
        {productsQuery.isPending ? <p role="status">상품을 불러오는 중입니다.</p>
          : productsQuery.isError ? <p role="alert">상품을 불러오지 못했습니다.
            <button type="button" className="order-action-button" onClick={() => void productsQuery.refetch()}>다시 불러오기</button>
          </p> : activeProducts.length === 0 ? <p>주문할 수 있는 상품이 없습니다.</p>
            : <OrderCreateForm products={activeProducts} isProcessing={isProcessing}
              onSubmit={handleCreate} onClose={() => setIsCreateOpen(false)} />}
      </div> : null}
      {actionMessage ? <p className="order-action-message" role="status">{actionMessage}</p> : null}
      {actionError ? <p className="order-action-message order-action-error" role="alert">{actionError}</p> : null}
      {orders.length === 0 ? (
        <div className="empty-state"><span className="state-code">NO DATA</span><h3>등록된 주문이 없습니다</h3><p>주문이 생성되면 처리 상태와 상품 내역이 표시됩니다.</p></div>
      ) : filteredOrders.length === 0 ? (
        <div className="empty-state"><span className="state-code">NO MATCH</span><h3>검색 결과가 없습니다</h3><p>검색어나 주문 상태를 바꿔 보세요.</p></div>
      ) : (
        <div className="table-scroll"><table><thead>
          {table.getHeaderGroups().map((headerGroup) => <tr key={headerGroup.id}>{headerGroup.headers.map((header) => (
            <th key={header.id} scope="col" aria-sort={header.column.getIsSorted() === 'asc' ? 'ascending' : header.column.getIsSorted() === 'desc' ? 'descending' : undefined}>
              {header.isPlaceholder ? null : header.column.getCanSort() ? (
                <button type="button" className="sort-button" onClick={header.column.getToggleSortingHandler()}>
                  {flexRender(header.column.columnDef.header, header.getContext())}
                  <span aria-hidden="true">{header.column.getIsSorted() === 'asc' ? ' ↑' : header.column.getIsSorted() === 'desc' ? ' ↓' : ' ↕'}</span>
                </button>
              ) : flexRender(header.column.columnDef.header, header.getContext())}
            </th>
          ))}<th scope="col">처리</th></tr>)}
        </thead><tbody>
          {table.getRowModel().rows.map((row) => (
            <OrderRow key={row.id} row={row} visibleColumnCount={row.getVisibleCells().length + 1}
              isProcessing={isProcessing}
              isReturnOpen={returnOrderId === row.original.id}
              onAction={handleAction}
              onOpenReturn={(order) => { setActionError(null); setActionMessage(null); setReturnOrderId(order.id); }}
              onSubmitReturn={handleReturn}
              onCloseReturn={() => setReturnOrderId(null)} />
          ))}
        </tbody></table></div>
      )}
    </section>
  );
}

type OrderRowProps = {
  row: Row<Order>;
  visibleColumnCount: number;
  isProcessing: boolean;
  isReturnOpen: boolean;
  onAction: (order: Order, action: OrderAction) => void;
  onOpenReturn: (order: Order) => void;
  onSubmitReturn: (order: Order, lines: ReturnOrderLine[]) => void;
  onCloseReturn: () => void;
};

function OrderRow({ row, visibleColumnCount, isProcessing, isReturnOpen, onAction,
  onOpenReturn, onSubmitReturn, onCloseReturn }: OrderRowProps) {
  const actions = availableActions(row.original);
  const canReturn = row.original.status === 'CONFIRMED'
    && row.original.lines.some((line) => line.returnedQuantity < line.quantity);
  return (
    <>
      <tr>{row.getVisibleCells().map((cell) => <td key={cell.id}>{flexRender(cell.column.columnDef.cell, cell.getContext())}</td>)}
        <td><div className="order-actions">
          {actions.map((action) => (
            <button type="button" className={action === 'expire'
              ? 'order-action-button order-action-button-secondary' : 'order-action-button'}
              key={action} disabled={isProcessing}
              aria-label={`${row.original.orderNumber} ${actionLabels[action]}`}
              onClick={() => {
                const message = action === 'cancel'
                  ? `${row.original.orderNumber} 주문을 취소하고 재고를 복원할까요?`
                  : `${row.original.orderNumber} 예약을 만료하고 재고를 복원할까요?`;
                if ((action !== 'cancel' && action !== 'expire') || window.confirm(message)) {
                  onAction(row.original, action);
                }
              }}>
              {actionLabels[action]}
            </button>
          ))}
          {canReturn ? (
            <button type="button" className="order-action-button" disabled={isProcessing}
              aria-label={`${row.original.orderNumber} 반품 처리`}
              onClick={() => { row.toggleExpanded(true); onOpenReturn(row.original); }}>
              반품 처리
            </button>
          ) : null}
          {actions.length === 0 && !canReturn ? '-' : null}
        </div></td>
      </tr>
      {row.getIsExpanded() ? (
        <tr className="order-detail-row"><td colSpan={visibleColumnCount}>
          <div className="order-lines" aria-label={`${row.original.orderNumber} 상품 내역`}>
            <div className="order-lines-heading"><strong>주문 상품</strong><span>{row.original.lines.length}개 항목</span></div>
            <table><thead><tr><th scope="col">SKU</th><th scope="col">주문 수량</th><th scope="col">반품 수량</th></tr></thead>
              <tbody>{row.original.lines.map((line) => <tr key={line.id}><td><span className="sku-cell">{line.sku}</span></td><td>{numberFormatter.format(line.quantity)}</td><td>{numberFormatter.format(line.returnedQuantity)}</td></tr>)}</tbody>
            </table>
            {isReturnOpen ? (
              <OrderReturnForm order={row.original} isProcessing={isProcessing}
                onSubmit={(lines) => onSubmitReturn(row.original, lines)} onClose={onCloseReturn} />
            ) : null}
          </div>
        </td></tr>
      ) : null}
    </>
  );
}
