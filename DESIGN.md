# InvMgmt — 재고 차감·반품 정합성 토이 프로젝트

## 1. 목적

ERP 실무에서 발생하는 재고 차감·취소·반품의 상태 불일치를 작은 도메인으로 재현한다. Spring Boot, JPA, 테스트 코드와 React를 사용하되 기술 시연보다 재고와 원장의 정합성을 지키는 설계 판단에 집중한다.

## 2. 핵심 범위

- 상품과 현재고 관리
- 주문에 따른 재고 차감과 취소·반품 복원
- 현재고와 재고 원장의 합계 일치
- 동일 요청의 중복 차감을 막는 멱등성
- 낙관적 락과 비관적 락의 동시성 제어 비교
- 부분 반품과 허용된 주문 상태 전이
- 정합성 대사 결과 조회

인증, 회원가입, 마이크로서비스 분리와 전체 관리자 CRUD는 범위에서 제외한다.

## 3. 기술 스택

### 백엔드

- Java 21 + Spring Boot 3.x
- JPA/Hibernate 주력
- 복잡한 집계 조회에만 MyBatis 사용
- PostgreSQL 16 + Flyway

### 프론트엔드

- React 18 + TypeScript + Vite
- TanStack Table v8 + TanStack Query
- 재고 현황, 거래 이력, 주문 상태 추적 화면

### 테스트와 실행 환경

- JUnit 5 + AssertJ
- 로컬 테스트와 개발 실행은 Zonky Embedded PostgreSQL 16 사용
- 컨테이너 런타임은 초기 범위에서 제외하고 허용된 환경에서 후속 검증
- GitHub Actions CI는 후속 마일스톤에서 구성

Zonky는 테스트 및 로컬 실행 전용 의존성으로 격리하며 배포용 애플리케이션 코드에는 포함하지 않는다.

## 4. 도메인 모델

| 엔티티 | 역할 |
|---|---|
| `Product` | 상품 식별 정보와 사용 상태 |
| `Stock` | 상품별 현재고와 낙관적 락 버전 |
| `StockLedger` | 재고 증감 원장과 처리 후 잔량 |
| `PurchaseOrder` / `OrderLine` | 주문과 주문 상품 |
| `IdempotencyKey` | 중복 요청 처리 결과 |

현재고는 음수가 될 수 없고, 현재고는 해당 상품 원장 증감량의 합과 일치해야 한다. 재고 수량을 일반 수정 API로 직접 덮어쓰지 않고 도메인 명령을 통해서만 변경한다.

주문 상태 전이는 다음 표로 강제한다.

```text
CREATED → RESERVED → CONFIRMED → CANCELED 또는 RETURNED
                  ↘ EXPIRED
```

정확한 `EXPIRED` 전이 출발 상태는 상태 머신 구현 전에 테스트 사례와 함께 확정한다.

## 5. 마일스톤

| 주차 | 산출물 |
|---|---|
| 1주 | 도메인 모델, JPA 매핑, Flyway 스키마, 기본 API, Zonky PostgreSQL 테스트 환경 |
| 2주 | 낙관적·비관적 락 구현, 동시성 실패 재현 테스트, 측정 결과 기록 |
| 3주 | 멱등키, 상태 머신, 부분 반품, 정합성 대사, MyBatis 집계 |
| 4주 | React 그리드 3종, OpenAPI, GitHub Actions, README |

## 6. 검증 기준

- Windows에서 `./gradlew` 대신 `.\\gradlew.bat` 명령을 사용한다.
- `.\\gradlew.bat test`로 PostgreSQL 16 기반 전체 테스트가 통과한다.
- `.\\gradlew.bat localRun`으로 임베디드 PostgreSQL과 애플리케이션이 함께 실행된다.
- Flyway 적용 후 Hibernate `validate`가 스키마와 매핑의 불일치를 잡는다.
- 동시성 테스트는 락을 제거하면 실패해야 한다.
- README에는 실제로 측정한 결과만 기록한다.

## 7. 공개 저장소 원칙

- 회사 코드, 설정, 명칭, 데이터와 개인 문서를 포함하지 않는다.
- 비밀값과 로컬 경로를 커밋하지 않는다.
- 커밋 전 `git diff --staged`로 공개 내용을 검토한다.
- 측정하지 않은 수치나 `완벽`, `무중단`, `버그 0건` 같은 표현을 사용하지 않는다.
