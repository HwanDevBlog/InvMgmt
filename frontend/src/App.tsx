const modules = [
  {
    number: '01',
    title: '재고 현황',
    description: '상품별 현재 수량과 재고 상태를 한눈에 확인합니다.',
    path: '/api/stocks',
  },
  {
    number: '02',
    title: '재고 거래 이력',
    description: '예약, 취소, 반품으로 발생한 재고 변동을 추적합니다.',
    path: '/api/stock-ledgers',
  },
  {
    number: '03',
    title: '주문 상태 추적',
    description: '주문별 처리 상태와 상품 수량 변화를 확인합니다.',
    path: '/api/orders',
  },
];

function App() {
  return (
    <div className="app-shell">
      <header className="top-bar">
        <a className="brand" href="/" aria-label="InvMgmt 홈">
          <span className="brand-mark">IM</span>
          <span>InvMgmt</span>
        </a>
        <span className="environment-badge">LOCAL</span>
      </header>

      <main>
        <section className="hero" aria-labelledby="page-title">
          <p className="eyebrow">INVENTORY MANAGEMENT</p>
          <h1 id="page-title">재고 흐름 관리</h1>
          <p className="hero-description">
            주문에서 시작된 재고 변화를 현재고와 원장 기준으로 확인합니다.
          </p>
          <div className="connection-status" role="status">
            <span className="status-dot" aria-hidden="true" />
            백엔드 API 연결 준비
          </div>
        </section>

        <section className="module-section" aria-labelledby="module-title">
          <div className="section-heading">
            <div>
              <p className="eyebrow">WORKSPACE</p>
              <h2 id="module-title">업무 화면</h2>
            </div>
            <p>조회 화면부터 순서대로 연결합니다.</p>
          </div>

          <div className="module-grid">
            {modules.map((module) => (
              <article className="module-card" key={module.number}>
                <span className="module-number">{module.number}</span>
                <h3>{module.title}</h3>
                <p>{module.description}</p>
                <code>{module.path}</code>
              </article>
            ))}
          </div>
        </section>
      </main>

      <footer>
        <span>InvMgmt</span>
        <span>React 18 · Spring Boot 3</span>
      </footer>
    </div>
  );
}

export default App;
