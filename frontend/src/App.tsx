import { useState } from 'react';
import { StockLedgerPage } from './features/ledger/StockLedgerPage';
import { StockPage } from './features/stock/StockPage';

type PageKey = 'stocks' | 'ledgers' | 'orders';

const navigation: Array<{ key: PageKey; label: string }> = [
  { key: 'stocks', label: '재고 현황' },
  { key: 'ledgers', label: '재고 거래 이력' },
  { key: 'orders', label: '주문 상태 추적' },
];

const pageDescriptions: Record<PageKey, string> = {
  stocks: '상품별 현재 수량과 재고 상태를 확인합니다.',
  ledgers: '예약, 취소, 반품으로 발생한 재고 변동을 추적합니다.',
  orders: '주문별 처리 상태와 상품 수량 변화를 확인합니다.',
};

function App() {
  const [activePage, setActivePage] = useState<PageKey>('stocks');
  const activeLabel = navigation.find((item) => item.key === activePage)?.label ?? '';

  return (
    <div className="app-shell">
      <header className="top-bar">
        <a className="brand" href="/" aria-label="InvMgmt 홈">
          <span className="brand-mark">IM</span>
          <span>InvMgmt</span>
        </a>
        <nav aria-label="주요 업무">
          {navigation.map((item) => (
            <button
              type="button"
              key={item.key}
              className={activePage === item.key ? 'active' : ''}
              aria-current={activePage === item.key ? 'page' : undefined}
              onClick={() => setActivePage(item.key)}
            >
              {item.label}
            </button>
          ))}
        </nav>
        <span className="environment-badge">LOCAL</span>
      </header>

      <main>
        <section className="page-heading" aria-labelledby="page-title">
          <div>
            <p className="eyebrow">INVENTORY MANAGEMENT</p>
            <h1 id="page-title">{activeLabel}</h1>
            <p>{pageDescriptions[activePage]}</p>
          </div>
          <div className="connection-status" role="status">
            <span className="status-dot" aria-hidden="true" />
            Spring Boot API
          </div>
        </section>

        <div className="page-content">
          {activePage === 'stocks' && <StockPage />}
          {activePage === 'ledgers' && <StockLedgerPage />}
          {activePage === 'orders' && (
            <section className="content-state pending-state">
              <span className="state-code">NEXT STEP</span>
              <h2>{activeLabel}</h2>
              <p>이 화면은 다음 구현 단계에서 API와 연결합니다.</p>
            </section>
          )}
        </div>
      </main>

      <footer>
        <span>InvMgmt</span>
        <span>React 18 · Spring Boot 3</span>
      </footer>
    </div>
  );
}

export default App;
