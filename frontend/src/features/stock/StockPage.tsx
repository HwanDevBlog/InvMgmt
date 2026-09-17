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
import { fetchStocks } from './api';
import { StockReconciliationPanel } from './StockReconciliationPanel';
import type { Stock } from './types';

const quantityFormatter = new Intl.NumberFormat('ko-KR');
const columnHelper = createColumnHelper<Stock>();
const emptyStocks: Stock[] = [];

const columns = [
  columnHelper.accessor('sku', {
    header: '상품 코드',
    cell: (info) => <span className="sku-cell">{info.getValue()}</span>,
  }),
  columnHelper.accessor('productName', {
    header: '상품명',
  }),
  columnHelper.accessor('quantity', {
    header: '현재고',
    cell: (info) => (
      <span className="quantity-cell">{quantityFormatter.format(info.getValue())}</span>
    ),
  }),
  columnHelper.display({
    id: 'status',
    header: '재고 상태',
    cell: ({ row }) => {
      const hasStock = row.original.quantity > 0;
      return (
        <span className={`stock-badge ${hasStock ? 'available' : 'empty'}`}>
          {hasStock ? '재고 있음' : '품절'}
        </span>
      );
    },
  }),
  columnHelper.accessor('version', {
    header: '변경 버전',
    cell: (info) => <span className="version-cell">v{info.getValue()}</span>,
  }),
];

export function StockPage() {
  const [search, setSearch] = useState('');
  const [stockStatus, setStockStatus] = useState('all');
  const [sorting, setSorting] = useState<SortingState>([]);
  const stockQuery = useQuery({
    queryKey: ['stocks'],
    queryFn: ({ signal }) => fetchStocks(signal),
  });

  const stocks = stockQuery.data ?? emptyStocks;
  const totalQuantity = stocks.reduce((sum, stock) => sum + stock.quantity, 0);
  const keyword = search.trim().toLocaleLowerCase();
  const filteredStocks = useMemo(() => stocks.filter((stock) =>
    (stock.sku.toLocaleLowerCase().includes(keyword) || stock.productName.toLocaleLowerCase().includes(keyword))
    && (stockStatus === 'all' || (stockStatus === 'available' ? stock.quantity > 0 : stock.quantity === 0)),
  ), [stocks, keyword, stockStatus]);
  const table = useReactTable({
    data: filteredStocks,
    columns,
    state: { sorting },
    onSortingChange: setSorting,
    sortDescFirst: false,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
  });

  if (stockQuery.isPending) {
    return (
      <section className="content-state" aria-live="polite">
        <span className="loading-indicator" aria-hidden="true" />
        <h2>재고 정보를 불러오는 중입니다</h2>
        <p>상품별 현재 수량을 확인하고 있습니다.</p>
      </section>
    );
  }

  if (stockQuery.isError) {
    return (
      <section className="content-state error-state" role="alert">
        <span className="state-code">LOAD ERROR</span>
        <h2>재고 정보를 불러오지 못했습니다</h2>
        <p>백엔드 실행 상태를 확인한 뒤 다시 시도해 주세요.</p>
        <button type="button" className="primary-button" onClick={() => void stockQuery.refetch()}>
          다시 불러오기
        </button>
      </section>
    );
  }

  return (
    <section className="data-panel" aria-labelledby="stock-table-title">
      <div className="data-toolbar">
        <div>
          <p className="eyebrow">CURRENT STOCK</p>
          <h2 id="stock-table-title">상품별 현재고</h2>
        </div>
        <div className="summary-list" aria-label="재고 요약">
          <div>
            <span>상품</span>
            <strong>{quantityFormatter.format(stocks.length)}</strong>
          </div>
          <div>
            <span>전체 수량</span>
            <strong>{quantityFormatter.format(totalQuantity)}</strong>
          </div>
        </div>
      </div>

      <StockReconciliationPanel />

      {stocks.length > 0 ? (
        <div className="filter-toolbar">
          <label>상품 검색
            <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="상품 코드 또는 상품명" />
          </label>
          <label>재고 상태
            <select value={stockStatus} onChange={(event) => setStockStatus(event.target.value)}>
              <option value="all">전체</option>
              <option value="available">재고 있음</option>
              <option value="empty">품절</option>
            </select>
          </label>
          <span className="filter-count" role="status">조회 결과 {filteredStocks.length}건</span>
        </div>
      ) : null}

      {stocks.length === 0 ? (
        <div className="empty-state">
          <span className="state-code">NO DATA</span>
          <h3>등록된 재고가 없습니다</h3>
          <p>상품이 등록되면 이 화면에서 현재 수량을 확인할 수 있습니다.</p>
        </div>
      ) : filteredStocks.length === 0 ? (
        <div className="empty-state"><span className="state-code">NO MATCH</span><h3>검색 결과가 없습니다</h3><p>검색어나 재고 상태를 바꿔 보세요.</p></div>
      ) : (
        <div className="table-scroll">
          <table>
            <thead>
              {table.getHeaderGroups().map((headerGroup) => (
                <tr key={headerGroup.id}>
                  {headerGroup.headers.map((header) => (
                    <th key={header.id} scope="col" aria-sort={header.column.getIsSorted() === 'asc' ? 'ascending' : header.column.getIsSorted() === 'desc' ? 'descending' : undefined}>
                      {header.isPlaceholder ? null : header.column.getCanSort() ? (
                        <button type="button" className="sort-button" onClick={header.column.getToggleSortingHandler()}>
                          {flexRender(header.column.columnDef.header, header.getContext())}
                          <span aria-hidden="true">{header.column.getIsSorted() === 'asc' ? ' ↑' : header.column.getIsSorted() === 'desc' ? ' ↓' : ' ↕'}</span>
                        </button>
                      ) : flexRender(header.column.columnDef.header, header.getContext())}
                    </th>
                  ))}
                </tr>
              ))}
            </thead>
            <tbody>
              {table.getRowModel().rows.map((row) => (
                <tr key={row.id}>
                  {row.getVisibleCells().map((cell) => (
                    <td key={cell.id}>
                      {flexRender(cell.column.columnDef.cell, cell.getContext())}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {stockQuery.isFetching && !stockQuery.isPending ? (
        <p className="refresh-status" role="status">재고 정보를 새로 고치는 중입니다.</p>
      ) : null}
    </section>
  );
}
