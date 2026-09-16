import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  createColumnHelper,
  flexRender,
  getCoreRowModel,
  getSortedRowModel,
  type SortingState,
  useReactTable,
} from '@tanstack/react-table';
import { fetchStockLedgers } from './api';
import type { StockLedger, StockMovementType } from './types';

const quantityFormatter = new Intl.NumberFormat('ko-KR');
const dateTimeFormatter = new Intl.DateTimeFormat('ko-KR', {
  year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
});
const movementLabels: Record<StockMovementType, string> = {
  INITIAL: '초기 재고', RESERVE: '재고 예약', CANCEL: '예약 취소',
  RETURN: '반품', ADJUSTMENT: '재고 조정',
};

function formatDelta(value: number) {
  const formatted = quantityFormatter.format(value);
  return value > 0 ? `+${formatted}` : formatted;
}

function formatReference(referenceType: string | null, referenceId: string | null) {
  if (!referenceType) return '-';
  return referenceId ? `${referenceType} · ${referenceId}` : referenceType;
}

const columnHelper = createColumnHelper<StockLedger>();
const emptyLedgers: StockLedger[] = [];
const columns = [
  columnHelper.accessor('createdAt', {
    header: '발생 일시',
    cell: (info) => <time className="date-cell" dateTime={info.getValue()}>{dateTimeFormatter.format(new Date(info.getValue()))}</time>,
  }),
  columnHelper.accessor('sku', {
    header: '상품 코드', cell: (info) => <span className="sku-cell">{info.getValue()}</span>,
  }),
  columnHelper.accessor('productName', { header: '상품명' }),
  columnHelper.accessor('movementType', {
    header: '거래 유형',
    cell: (info) => <span className={`movement-badge movement-${info.getValue().toLowerCase()}`}>{movementLabels[info.getValue()]}</span>,
  }),
  columnHelper.accessor('quantityDelta', {
    header: '증감 수량',
    cell: (info) => {
      const value = info.getValue();
      const direction = value > 0 ? 'positive' : value < 0 ? 'negative' : 'neutral';
      return <span className={`delta-cell ${direction}`}>{formatDelta(value)}</span>;
    },
  }),
  columnHelper.accessor('balanceAfter', {
    header: '변경 후 재고',
    cell: (info) => <span className="quantity-cell">{quantityFormatter.format(info.getValue())}</span>,
  }),
  columnHelper.display({
    id: 'reference', header: '업무 참조',
    cell: ({ row }) => <span className="reference-cell">{formatReference(row.original.referenceType, row.original.referenceId)}</span>,
  }),
];

export function StockLedgerPage() {
  const [search, setSearch] = useState('');
  const [movementType, setMovementType] = useState('all');
  const [sorting, setSorting] = useState<SortingState>([]);
  const ledgerQuery = useQuery({
    queryKey: ['stock-ledgers'],
    queryFn: ({ signal }) => fetchStockLedgers(signal),
  });
  const ledgers = ledgerQuery.data ?? emptyLedgers;
  const increaseCount = ledgers.filter((ledger) => ledger.quantityDelta > 0).length;
  const decreaseCount = ledgers.filter((ledger) => ledger.quantityDelta < 0).length;
  const keyword = search.trim().toLocaleLowerCase();
  const filteredLedgers = useMemo(() => ledgers.filter((ledger) =>
    (ledger.sku.toLocaleLowerCase().includes(keyword)
      || ledger.productName.toLocaleLowerCase().includes(keyword)
      || (ledger.referenceId ?? '').toLocaleLowerCase().includes(keyword))
    && (movementType === 'all' || ledger.movementType === movementType),
  ), [ledgers, keyword, movementType]);
  const table = useReactTable({
    data: filteredLedgers, columns, state: { sorting }, onSortingChange: setSorting,
    sortDescFirst: false,
    getCoreRowModel: getCoreRowModel(), getSortedRowModel: getSortedRowModel(),
  });

  if (ledgerQuery.isPending) {
    return <section className="content-state" aria-live="polite"><span className="loading-indicator" aria-hidden="true" /><h2>재고 거래 이력을 불러오는 중입니다</h2><p>최근 재고 변동 기록을 확인하고 있습니다.</p></section>;
  }
  if (ledgerQuery.isError) {
    return <section className="content-state error-state" role="alert"><span className="state-code">LOAD ERROR</span><h2>재고 거래 이력을 불러오지 못했습니다</h2><p>백엔드 실행 상태를 확인한 뒤 다시 시도해 주세요.</p><button type="button" className="primary-button" onClick={() => void ledgerQuery.refetch()}>다시 불러오기</button></section>;
  }

  return (
    <section className="data-panel" aria-labelledby="ledger-table-title">
      <div className="data-toolbar">
        <div><p className="eyebrow">STOCK LEDGER</p><h2 id="ledger-table-title">재고 변동 원장</h2></div>
        <div className="summary-list" aria-label="재고 거래 요약">
          <div><span>전체 거래</span><strong>{quantityFormatter.format(ledgers.length)}</strong></div>
          <div><span>증가 거래</span><strong>{quantityFormatter.format(increaseCount)}</strong></div>
          <div><span>감소 거래</span><strong>{quantityFormatter.format(decreaseCount)}</strong></div>
        </div>
      </div>
      {ledgers.length > 0 ? (
        <div className="filter-toolbar">
          <label>거래 검색
            <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="상품 코드·상품명·참조 ID" />
          </label>
          <label>거래 유형
            <select value={movementType} onChange={(event) => setMovementType(event.target.value)}>
              <option value="all">전체</option>
              {Object.entries(movementLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
            </select>
          </label>
          <span className="filter-count" role="status">조회 결과 {filteredLedgers.length}건</span>
        </div>
      ) : null}
      {ledgers.length === 0 ? (
        <div className="empty-state"><span className="state-code">NO DATA</span><h3>재고 거래 이력이 없습니다</h3><p>상품 등록이나 주문 처리가 발생하면 재고 변동 기록이 표시됩니다.</p></div>
      ) : filteredLedgers.length === 0 ? (
        <div className="empty-state"><span className="state-code">NO MATCH</span><h3>검색 결과가 없습니다</h3><p>검색어나 거래 유형을 바꿔 보세요.</p></div>
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
          ))}</tr>)}
        </thead><tbody>
          {table.getRowModel().rows.map((row) => <tr key={row.id}>{row.getVisibleCells().map((cell) => <td key={cell.id}>{flexRender(cell.column.columnDef.cell, cell.getContext())}</td>)}</tr>)}
        </tbody></table></div>
      )}
    </section>
  );
}
