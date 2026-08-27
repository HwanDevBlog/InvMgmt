import { useQuery } from '@tanstack/react-query';
import {
  createColumnHelper,
  flexRender,
  getCoreRowModel,
  useReactTable,
} from '@tanstack/react-table';
import { fetchStocks } from './api';
import type { Stock } from './types';

const quantityFormatter = new Intl.NumberFormat('ko-KR');
const columnHelper = createColumnHelper<Stock>();

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
  const stockQuery = useQuery({
    queryKey: ['stocks'],
    queryFn: ({ signal }) => fetchStocks(signal),
  });

  const stocks = stockQuery.data ?? [];
  const totalQuantity = stocks.reduce((sum, stock) => sum + stock.quantity, 0);
  const table = useReactTable({
    data: stocks,
    columns,
    getCoreRowModel: getCoreRowModel(),
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

      {stocks.length === 0 ? (
        <div className="empty-state">
          <span className="state-code">NO DATA</span>
          <h3>등록된 재고가 없습니다</h3>
          <p>상품이 등록되면 이 화면에서 현재 수량을 확인할 수 있습니다.</p>
        </div>
      ) : (
        <div className="table-scroll">
          <table>
            <thead>
              {table.getHeaderGroups().map((headerGroup) => (
                <tr key={headerGroup.id}>
                  {headerGroup.headers.map((header) => (
                    <th key={header.id} scope="col">
                      {header.isPlaceholder
                        ? null
                        : flexRender(header.column.columnDef.header, header.getContext())}
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
