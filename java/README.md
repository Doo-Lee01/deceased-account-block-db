# Java 구현 가이드

`schema.sql` 로 만든 MySQL 데이터베이스 위에, **사망자 명의 거래 차단 판정**을 수행하는
Java 애플리케이션을 붙인다. 프레임워크(Spring/JPA) 없이 **순수 JDBC + 객체지향 설계**로만 만든다.
목적이 OOP 학습이므로, 편의 도구가 숨겨 버리는 부분(트랜잭션 경계, 락, 타입 변환)을
직접 눈으로 보게 하는 것이 이 구성의 이유다.

> **동작을 먼저 보고 싶으면** `docs/simulator.html` 을 브라우저로 열면 된다.
> 이 가이드에 나오는 처리 단계와 SQL 이 그대로 화면에 재생되고,
> 시스템 일시를 바꿔 차단개시 경계값을 직접 실험할 수 있다.

---

## 0. 최종 구성

외부 서비스를 쓰지 않는다. 필요한 것이 전부 로컬과 GitHub 안에 있다.

| 역할 | 선택 | 이유 |
|---|---|---|
| 데이터베이스 | **로컬 MySQL 8.0** | `concurrency/` 락 실습 결과가 MySQL 기준으로 측정되어 있다. DB를 바꾸면 그 보고서를 다시 측정해야 한다 |
| DB 접근 | **순수 JDBC** | 트랜잭션 경계, 락, 타입 변환을 직접 다룬다. 프레임워크가 숨기는 부분이 바로 배우려는 부분이다 |
| 커넥션 관리 | **HikariCP** | 커넥션을 미리 만들어 두고 빌려 쓴다 → 9절 |
| 공개 | **GitHub Pages** | 저장소의 `docs/` 폴더가 그대로 웹에 올라간다. 서버도, 요금도, 잠드는 일도 없다 → 부록 B |

### 검토했지만 쓰지 않은 것

과제 보고서에 "왜 안 썼는지"를 쓸 수 있으니 근거만 남겨 둔다.

| 후보 | 판단 | 근거 |
|---|---|---|
| **Vercel** | 불가능 | 런타임이 Node.js / Python / Go / Ruby 뿐이다. JVM이 없어 `.java`를 올려도 실행되지 않는다 |
| **Render** | 가능하지만 불필요 | Java 네이티브 런타임이 없어 Dockerfile이 필요하고, 관리형 MySQL이 없어 DB를 외부에 또 둬야 한다. 무료 플랜은 15분 무요청 시 잠들어 다음 첫 요청이 50초쯤 걸린다 — 발표 시연에 쓰기 나쁘다 |
| **Supabase** | 가능하지만 손해 | PostgreSQL이라 기본 격리수준이 READ COMMITTED(MySQL은 REPEATABLE READ)이고 데드락 코드도 `40001`→`40P01`로 바뀐다. 생성 컬럼 35개도 `DEFAULT + CHECK`로 고쳐야 한다. 락 실습 보고서가 그 DB에서는 재현되지 않는다 |

콘솔 애플리케이션은 배포할 대상이 아니라는 점도 분명히 해 둘 만하다.
Render나 Vercel의 Web Service는 "HTTP 포트를 열고 응답하는 프로그램"을 전제로 한다.
`Scanner`로 키보드를 읽는 프로그램은 올려도 접속할 창구가 없다.
배포하려면 Spring Boot로 감싸는 작업이 먼저인데, 그건 이번 학습 목표(OOP와 JDBC)와 다른 주제다.

> 시연은 `docs/simulator.html`로 한다. DB도 서버도 필요 없고, 처리 단계와 SQL이
> 그대로 재생되므로 라이브 시연보다 안정적이다.

---

## 1. 무엇을 만드는가

```
 [ 콘솔 화면 ]  Main.java
        │  메뉴 선택 / 입력값 읽기만 한다. 업무 규칙이 여기 들어오면 안 된다.
        ▼
 [ 서비스 계층 ]  BlockDecisionService / TransactionService
        │  판정 순서, 트랜잭션 경계, 재시도. "조합과 분기"가 여기 있다.
        ▼
 [ 저장소 계층 ]  AccountRepository (인터페이스)  ←  JdbcAccountRepository (구현)
        │  SQL 문자열은 전부 여기에만 있다. 서비스는 SQL 을 한 줄도 모른다.
        ▼
 [ MySQL ]  차단개시일 CHECK, block_rule 테이블, FK, UNIQUE
            "규칙"은 여기 있다. 자바가 다시 계산하지 않는다.
```

이 프로젝트의 설계 원칙 **"규칙은 테이블에, 조합과 분기는 서비스 계층에"** 를
그대로 코드 구조로 옮긴 것이다.

| DB 가 책임지는 것 | 자바가 책임지는 것 |
|---|---|
| 차단개시일시 = 신고일 + 1일 (`ck_block_start` CHECK) | 지금 시각이 차단개시일시를 넘었는지 **비교** |
| 거래유형별 허용/차단 (`block_rule` 행) | 어떤 순서로 무엇을 먼저 볼지 **판정 흐름** |
| 잔액 ≥ 0 (`ck_acct_balance` CHECK) | 잔액 부족 시 어떤 응답코드를 줄지 |
| 코드값 유효성 (복합 FK) | 코드값을 enum 으로 감싸 오타를 컴파일 단계에서 차단 |

---

## 2. 준비물

| 항목 | 버전 | 비고 |
|---|---|---|
| JDK | 17 이상 | Eclipse 2026 에 번들된 JDK 21 사용 가능 |
| Eclipse | 2026-xx (Eclipse IDE for Java Developers) | |
| MySQL | 8.0 이상 | `schema.sql` + `seed_data.sql` 이 이미 적재된 상태 |
| MySQL Connector/J | 8.4.0 | Maven 이 자동으로 받아온다 |
| HikariCP | 5.1.0 | 커넥션 풀. 같이 받아온다 |
| slf4j-simple | 2.0.13 | HikariCP 의 로그를 콘솔에 보여 주는 구현체 |

---

## 3. Eclipse 에서 프로젝트 만들기 (순서대로)

### 3-1. Maven 프로젝트 생성 (권장)

Maven 을 쓰면 JDBC 드라이버 jar 을 손으로 내려받아 Build Path 에 추가하는 과정이 통째로 사라진다.

1. `File` → `New` → `Other...` → `Maven` → **`Maven Project`** → `Next`
2. **`Create a simple project (skip archetype selection)`** 체크 → `Next`
3. 아래처럼 입력하고 `Finish`
   - Group Id: `kr.doolee`
   - Artifact Id: `deceased-block-app`
   - Version: `1.0.0`
   - Packaging: `jar`
4. 생성된 `pom.xml` 을 열고, 이 폴더의 `deceased-block-app/pom.xml` 내용으로 **통째로 교체**한다.
5. 저장하면 Eclipse 가 자동으로 드라이버를 내려받는다.
   안 되면 프로젝트 우클릭 → `Maven` → `Update Project...` → `Force Update of Snapshots/Releases` 체크 → OK
6. `Package Explorer` 에서 `Maven Dependencies` 안에 `mysql-connector-j-8.4.0.jar` 이 보이면 성공

### 3-2. Maven 을 쓰지 않는 경우

1. `File` → `New` → `Java Project` → 이름 `deceased-block-app` → `Finish`
2. https://dev.mysql.com/downloads/connector/j/ → `Platform Independent` → zip 다운로드 → 압축 해제
3. 프로젝트 우클릭 → `Build Path` → `Configure Build Path...` → `Libraries` 탭
   → `Classpath` 선택 → `Add External JARs...` → `mysql-connector-j-8.4.0.jar` 선택 → `Apply and Close`
4. 소스는 `src/` 아래에 `kr/doolee/dab/...` 구조로 그대로 넣는다.
   `db.properties` 는 `src/` 바로 아래에 둔다(클래스패스 루트여야 `getResourceAsStream("/db.properties")` 가 찾는다).

### 3-3. 소스 넣기

이 폴더의 `deceased-block-app/src` 전체를 만든 프로젝트의 같은 위치에 복사한다.
Eclipse 에서는 `Package Explorer` 에 드래그 앤 드롭해도 된다.

### 3-4. DB 접속 정보 설정

`src/main/resources/db.properties.example` 를 복사해 **`db.properties`** 로 이름을 바꾸고 비밀번호를 채운다.

```properties
db.url=jdbc:mysql://localhost:3306/core_bank?serverTimezone=Asia/Seoul&characterEncoding=utf8&allowPublicKeyRetrieval=true&useSSL=false
db.user=root
db.password=여기에_비밀번호
```

> `db.properties` 는 `.gitignore` 에 이미 들어 있다. **절대 커밋하지 않는다.**
> 이 저장소는 공개 저장소이므로 비밀번호가 한 번이라도 올라가면 커밋 기록에 영구히 남는다.
> 커밋하는 것은 비밀번호가 없는 `db.properties.example` 쪽이다.

접속 URL 의 데이터베이스는 `core_bank` 하나만 적는다.
나머지 스키마(`comm_code`, `channel`, `ext_link`, `info_mart`)는
쿼리에서 `comm_code.rsp_code` 처럼 **스키마명을 붙인 완전한 이름**으로 쓴다.
DAO 의 SQL 이 전부 그렇게 되어 있다.

### 3-5. 한글 깨짐 방지 (윈도우 필수)

Eclipse 콘솔의 기본 인코딩이 MS949 면 한글이 `???` 로 나온다.

- `Run` → `Run Configurations...` → 왼쪽에서 `Main` 선택 → `Common` 탭
  → `Encoding` → `Other` → **`UTF-8`** 선택 → `Apply`
- 추가로 `Window` → `Preferences` → `General` → `Workspace` → `Text file encoding` 도 `UTF-8` 로 맞춘다.

### 3-6. 실행

`Main.java` 우클릭 → `Run As` → `Java Application`

```
==========================================
 사망자 명의 금융거래 신속차단 - 콘솔
==========================================
 1. 계좌 조회
 2. 차단 판정 시뮬레이션 (잔액 변경 없음)
 3. 입금
 4. 출금
 5. 동시 출금 테스트 (스레드 2개)
 0. 종료
```

---

## 4. 클래스 구성과 OOP 개념의 대응

패키지 이름은 블로그에 정리한 계층 구분
([service 와 repository 패키지](https://lxvxxu.tistory.com/233))을 그대로 따랐다.

```
kr.doolee.dab
├─ Main.java                         조립(Composition Root) + 콘솔 + 예외 최종 처리
├─ domain/                           테이블 한 행 = 객체 하나
│   ├─ Account, DeathBlock, BlockRule, AccountTxn
│   ├─ TxnType, DrCr, BlockStatus    enum — 코드값을 타입으로
│   └─ Decision                      판정 결과 + 근거를 한 덩어리로
├─ repository/                       저장소 계층 = 계약(인터페이스)
│   ├─ AccountRepository, AccountTxnRepository, DeathBlockRepository,
│   │  BlockRuleRepository, ChannelLogRepository
│   └─ jdbc/                         구현체. SQL 은 전부 여기에만 있다
│       └─ JdbcAccountRepository, JdbcDeathBlockRepository, ...
├─ service/                          비즈니스 로직
│   ├─ BlockDecisionService          판정 흐름 — 이 프로젝트의 핵심
│   ├─ TransactionService            트랜잭션 경계 + 채널 로그
│   └─ TxnResult                     성공/거절 결과 객체
├─ support/                          공통 기반
│   ├─ Db                            커넥션 풀(HikariCP) 창구
│   ├─ TxTemplate / TxWork           트랜잭션 + 데드락 재시도 템플릿
│   ├─ DataAccessException           시스템 장애
│   ├─ BusinessException             업무 거절
│   ├─ BlockedException              차단 거절 (판정 근거를 들고 다닌다)
│   ├─ MySqlError                    오류번호 → 한글 설명 사전
│   └─ AppEnv                        개발/운영 환경 구분
└─ demo/
    └─ PoolBenchmark                 커넥션 풀 효과를 숫자로 측정
```

`repository` 를 실무에서는 **DAO(Data Access Object)** 라고도 부른다. 이름만 다르고 역할은 같다.

| 코드에서 확인할 OOP 개념 | 어디에 있는지 |
|---|---|
| **캡슐화** | `Account.canAfford()` — 서비스가 잔액을 꺼내 비교하지 않고, 계좌 스스로 판단한다 |
| **불변 객체** | `domain/` 의 모든 필드가 `final` — DB 스냅샷을 자바에서 몰래 고치지 못한다 |
| **인터페이스와 구현 분리** | `AccountRepository` ↔ `JdbcAccountRepository` — PostgreSQL 로 바꿔도 서비스 코드는 그대로 |
| **의존성 주입** | 서비스가 저장소를 `new` 하지 않고 생성자로 받는다 → 가짜 저장소로 DB 없이 테스트 가능 |
| **다형성** | `Main` 에서 구현체를 고르는 줄만 바꾸면 전체 동작이 바뀐다 |
| **정적 팩토리 메서드** | `Decision.allow()` / `Decision.block()` — 생성자 4개 대신 이름으로 의도를 표현 |
| **상속** | `BlockedException extends BusinessException` — 더 구체적인 거절을 따로 잡을 수 있다 |
| **예외 계층** | `BusinessException`(업무 거절) 과 `DataAccessException`(시스템 장애)을 구분 |
| **함수형 인터페이스 / 템플릿** | `TxWork` + `TxTemplate` — 트랜잭션 보일러플레이트를 한 곳으로 |
| **enum 의 행위** | `TxnType.WDR.drCr()` — 거래유형이 입금인지 출금인지를 유형 자신이 안다 |
| **문서화 주석** | 모든 public 클래스/메서드에 `@param` `@return` `@throws` — Eclipse 에서 마우스를 올리면 설명이 뜬다 |

---

## 5. MySQL ↔ Java 타입 매핑

`JdbcAccountRepository.map()`, `JdbcDeathBlockRepository` 에 실제로 쓰인 방식이다.

| MySQL 컬럼 타입 | Java 타입 | 읽기 | 쓰기 |
|---|---|---|---|
| `VARCHAR`, `CHAR` | `String` | `rs.getString("cust_no")` | `ps.setString(1, s)` |
| `DECIMAL(18,2)` | **`BigDecimal`** | `rs.getBigDecimal("balance_amt")` | `ps.setBigDecimal(1, v)` |
| `BIGINT` | `long` | `rs.getLong("block_id")` | `ps.setLong(1, v)` |
| `INT` | `int` | `rs.getInt(...)` | `ps.setInt(...)` |
| `DATE` | `LocalDate` | `rs.getObject("death_dt", LocalDate.class)` | `ps.setObject(1, d)` |
| `DATETIME(3)` | `LocalDateTime` | `rs.getObject("block_start_dtm", LocalDateTime.class)` | `ps.setObject(1, t)` |
| `VARBINARY` | `byte[]` | `rs.getBytes(...)` | `ps.setBytes(...)` |
| NULL 가능 컬럼 | 래퍼 타입(`Long`, `Integer`) | `rs.getObject(c, Long.class)` | `ps.setNull(i, Types.BIGINT)` |

주의할 점 두 가지.

1. **금액에 `double` 을 쓰면 안 된다.** `0.1 + 0.2 != 0.3` 이라 잔액이 1원씩 어긋난다.
   `DECIMAL` 의 정확한 짝은 `BigDecimal` 뿐이다. 비교도 `equals` 가 아니라 `compareTo` 로 한다
   (`1000` 과 `1000.00` 은 `equals` 로는 다르고 `compareTo` 로는 같다).
2. **`getDate()` / `getTimestamp()` 는 쓰지 않는다.** `java.util.Date` 기반이라 타임존 문제가 생긴다.
   JDBC 4.2 의 `getObject(컬럼, 타입.class)` 가 정식 방법이다.

---

## 6. 판정 로직: SQL 을 자바로 옮기기

`validation_queries.sql` S4 의 `CASE` 식이 원본이다.

```sql
CASE
 WHEN b.cust_no IS NULL OR b.block_status_cd <> '20' THEN '허용 (차단 없음)'
 WHEN x.txn_dtm < b.block_start_dtm                  THEN '허용 (차단개시 전)'
 WHEN r.allow_yn = 'Y'                               THEN '허용 (규칙상 필수거래)'
 ELSE CONCAT('차단 (', r.reject_rsp_cd, ')')
END
```

이것을 그대로 `BlockDecisionService.judge()` 로 옮긴다.

```java
public Decision judge(Connection conn, String custNo, TxnType type, LocalDateTime at) {

    // 1) 차단 정보가 아예 없다
    DeathBlock block = deathBlockRepository.findByCustNo(conn, custNo);
    if (block == null) return Decision.allow("허용 (차단 정보 없음)");

    // 2) 상태가 '차단중'이 아니다 (차단예정 / 해제)
    if (block.status() != BlockStatus.BLOCKED)
        return Decision.allow("허용 (차단상태 아님: " + block.status().label() + ")");

    // 2-1) 차단개시일시 이전 → 아직 효력 없음
    //      신고일 23:59:59.999 는 허용, 다음날 00:00:00.000 은 차단
    if (!block.isEffectiveAt(at))
        return Decision.allow("허용 (차단개시 전, 개시=" + block.blockStartDtm() + ")");

    // 3) 규칙 조회 — 그 시점에 유효한 규칙만
    BlockRule rule = blockRuleRepository.findEffective(conn, type, type.drCr(), at.toLocalDate());
    if (rule == null) { /* 규칙 행이 없을 때의 기본값 — 실제 코드 참조 */ }
    if (rule.isAllow()) return Decision.allowByRule(rule, block.blockStartDtm());
    return Decision.block(rule, block.blockStartDtm());
}
```

규칙 행이 아예 없는 경우(`rule == null`)의 기본값은 **정책 결정**이다.
실제 코드에서는 입금은 허용하고 출금은 안전측으로 차단한다.
"규칙이 없으면 통과"로 두면 코드값이 하나 빠졌을 때 사망자 계좌에서 돈이 나간다.

여기서 **하지 않은 일**이 중요하다.

- `blockStartDtm = deathReportDt.plusDays(1)` 을 자바에서 계산하지 않는다.
  DB 의 `ck_block_start` CHECK 가 이미 보장하므로, 자바가 또 계산하면
  두 곳이 언젠가 어긋난다. 읽어서 비교만 한다.
- `if (txnType.equals("WDR")) 차단;` 같은 분기를 쓰지 않는다.
  정책이 바뀌면 자바를 고쳐 재배포해야 하기 때문이다.
  `block_rule` 행을 갈아 끼우면 재배포 없이 정책이 바뀐다.

---

## 7. 예외 처리 설계

예외 처리는 "try-catch 를 어디에 쓰느냐"가 아니라 **"누가 책임지느냐"**를 정하는 일이다.
이 프로젝트는 세 가지로 나눴다.

| 예외 | 성격 | 누가 잡는가 | 응답 |
|---|---|---|---|
| `BusinessException` | 규칙대로 거절한 것 (잔액부족, 금액 오류) | `Main` | E030 등 안내 |
| `BlockedException` | 차단 규칙에 걸린 것 — `BusinessException` 의 자식 | `TransactionService` | D001~D004 |
| `DataAccessException` | 시스템 장애 (접속 끊김, 제약 위반) | `Main` | X999 + 운영자 알림 |

### 7-1. 체크 예외를 언체크 예외로 바꾸는 자리

`SQLException` 은 **체크 예외**다. 컴파일러가 처리하라고 강제한다.
그 말을 그대로 따르면 저장소를 호출하는 모든 코드가 이렇게 된다.

```java
try {
    accountRepository.findByNo(conn, acctNo);
} catch (SQLException e) {
    // 서비스가 여기서 할 수 있는 일이 없다. 다시 던지는 것 말고는.
}
```

서비스 계층은 JDBC 를 쓰는지도 알 필요가 없고, `SQLException` 을 받아도 복구할 방법이 없다.
그래서 `DataAccessException`(= `RuntimeException` 상속)으로 **한 번 감싸** 중간 계층을 비워 둔다.
처리할 수 있는 맨 위 — 화면을 가진 `Main` — 에서만 잡는다.

### 7-2. 원인 예외를 반드시 넘긴다

```java
throw new DataAccessException("계좌 조회 실패: " + acctNo, e);   //  ← e 가 핵심
```

두 번째 인자로 원래 예외를 주면 스택 트레이스에 `Caused by:` 가 붙는다.

```
kr.doolee.dab.support.DataAccessException: 계좌 조회 실패: 10200000000002 — ...
  at kr.doolee.dab.repository.jdbc.JdbcAccountRepository.select(JdbcAccountRepository.java:62)
  at kr.doolee.dab.service.TransactionService.execute(TransactionService.java:118)
  at kr.doolee.dab.Main.run(Main.java:146)
Caused by: java.sql.SQLSyntaxErrorException: Unknown column 'balance_amnt'   ← 진짜 원인
```

[스택 트레이스는 위에서부터 읽는다](https://lxvxxu.tistory.com/232).
맨 윗줄이 터진 자리이고, 아래로 갈수록 호출한 순서가 거꾸로 나온다.
`e` 를 빼먹으면 `Caused by` 가 안 붙어서 "계좌 조회 실패"라는 말만 남고 원인이 사라진다.

### 7-3. 환경에 따라 다르게 보여 준다

스택 트레이스에는 패키지 구조와 때로는 SQL 까지 그대로 찍힌다.
개발 중에는 봐야 하지만 운영 중에 고객 화면에 띄우면 시스템 내부를 알려 주는 셈이 된다.
그래서 `AppEnv` 로 갈라 둔다.

```java
private static void printTrace(Throwable e) {
    if (AppEnv.isDev()) {
        e.printStackTrace(System.out);          // 개발: 전부 출력
    } else {
        System.out.println("  자세한 내용은 서버 로그를 확인해 주세요.");
    }
}
```

```bash
java -jar app.jar                 # DEV (기본) — 스택 트레이스 출력
java -Dapp.env=PROD -jar app.jar  # PROD       — 안내 문구만
```

### 7-4. 자원은 try-with-resources 로 닫는다

```java
try (PreparedStatement ps = conn.prepareStatement(sql)) {
    ...
    try (ResultSet rs = ps.executeQuery()) { ... }
}
```

`finally` 에 직접 `close()` 를 쓰면, close 자체가 예외를 던질 때 **원래 예외가 묻힌다**.
try-with-resources 는 원래 예외를 살리고 close 예외를 `suppressed` 로 붙여 준다.
`Connection` 은 저장소에서 닫지 않는다 — 트랜잭션을 시작한 쪽의 소유물이기 때문이다.

### 7-5. 삼키지 않는다

고쳐야 했던 부분이 하나 있었다. 처음 버전의 `readCustNo()` 는 이랬다.

```java
try (Connection conn = Db.getConnection()) {
    Account a = accountRepository.findByNo(conn, acctNo);
    return a == null ? null : a.custNo();
} catch (SQLException e) {
    return null;                 // ← 삼켜 버렸다
}
```

이러면 DB 가 죽었을 때도 "고객번호를 모르는 계좌"로 처리되어 넘어간다.
`catch` 블록에서 아무 것도 안 하는 것(= 예외를 삼키는 것)은
**장애를 정상으로 위장하는** 가장 흔한 실수다. 지금은 이렇게 되어 있다.

```java
} catch (SQLException e) {
    throw new DataAccessException("고객번호 조회 실패: " + acctNo, e);
}
```

예외를 삼켜도 되는 곳은 **단 하나** 있다 — 채널 로그 적재다.
로그를 못 남겼다고 이미 확정된 거래를 되돌릴 수는 없으므로,
거기서만 경고로 바꾸고 흐름을 이어 간다. 그리고 그 이유를 주석에 적어 두었다.

### 7-6. 오류번호를 사람 말로 바꾼다

`e.getMessage()` 만 찍으면 영문 원문이 나온다.
자주 만나는 번호는 `MySqlError` 한 곳에 모아 뒀다.

| 번호 | 뜻 | 재시도 |
|---|---|---|
| 1213 | 데드락 | **의미 있음** |
| 1205 | 락 대기 시간 초과 | **의미 있음** |
| 1452 | 외래키 위반 — 부모 행 없음 | 무의미 |
| 1062 | 유니크 위반 — 중복 | 무의미 |
| 3819 | CHECK 제약 위반 | 무의미 |

이 구분이 `TxTemplate` 의 재시도 판단 기준이다.

### 7-7. 실제 동작

```
실행 환경: DEV  (오류 시 스택 트레이스를 출력합니다)

 선택 > 4
  계좌번호 > 99999999999999
  금액 > 10000
  [거절] X999 / 존재하지 않는 계좌입니다: 99999999999999

  금액 > abc
  [입력 오류] 금액은 숫자로만 입력해 주세요: Character a is neither a decimal digit...

  거래유형 코드 > XXX
  [거절] X999 — 거래유형 코드가 올바르지 않습니다: XXX (DEP/WDR/TRO/ATO/CLS/LON 중 하나)

  금액 > -500
  [거절] X999 — 금액은 0보다 커야 합니다
```

네 가지 모두 **프로그램이 죽지 않고 메뉴로 돌아온다**.
`Main` 의 `catch (RuntimeException e)` 가 마지막 안전망이기 때문이다.
이게 없으면 `NullPointerException` 하나에 콘솔이 통째로 종료된다.

---

## 8. 트랜잭션 · 락 · 데드락 재시도

`concurrency/` 실습에서 확인한 결론을 코드로 옮긴 부분이다.

### 8-1. 왜 `FOR UPDATE` 가 먼저 와야 하는가

`acct_txn` 에 `INSERT` 하면 FK `fk_txn_acct` 때문에 **부모 `acct` 행에 공유(S) 락**이 걸린다.
두 세션이 각자 S 락을 쥔 채 `UPDATE` 로 배타(X) 락을 요구하면 서로를 기다려 `ERROR 1213` 이 된다
(`concurrency/01_deadlock.sql`).

처음부터 X 락으로 시작하면 두 번째 세션은 그냥 줄을 서서 기다린다.

```java
tx.execute(conn -> {
    // (1) 잔액을 바꿀 계좌를 제일 먼저 X 락으로 잠근다
    Account acct = accountRepository.findByNoForUpdate(conn, acctNo);

    // (2) 판정
    Decision decision = decisionService.judge(conn, acct.custNo(), type, now);
    if (decision.isBlocked()) throw new BlockedException(decision);

    // (3) 락을 쥔 상태이므로 읽은 잔액이 그대로 유효하다 (갱신 손실 없음)
    if (!acct.canAfford(amount)) throw new BusinessException("E030", "잔액이 부족합니다");
    BigDecimal after = acct.balanceAmt().subtract(amount);

    // (4) 잔액 수정 + 원장 적재
    accountRepository.updateBalance(conn, acctNo, after);
    txnRepository.insert(conn, new AccountTxn(txnUniqueNo, acctNo, now, type, amount, after, ...));
    return TxnResult.ok(decision, txnUniqueNo, after);
});
```

`JdbcAccountRepository` 에서 두 쿼리의 차이는 문자열 한 구절뿐이다.

```java
private static final String SELECT_BASE       = "SELECT ... FROM core_bank.acct WHERE acct_no = ?";
private static final String SELECT_FOR_UPDATE = SELECT_BASE + " FOR UPDATE";
```

조회만 할 사람은 `findByNo()` 를 쓰면 락을 걸지 않으므로 아무도 기다리게 하지 않는다.
**잔액을 바꿀 사람만** `findByNoForUpdate()` 를 쓴다.

### 8-2. 재시도는 왜 필요한가

데드락(`ERROR 1213`, SQLSTATE `40001`)은 "잘못된 요청"이 아니라 **"다시 하면 성공할 수 있는 요청"** 이다.
MySQL 이 두 트랜잭션 중 하나를 골라 롤백시킨 것이므로, 재시도 시점에는
상대 트랜잭션이 이미 끝나 있어 대부분 성공한다.

`TxTemplate` 이 이 판단을 한 곳에서 처리한다.

```java
private boolean isRetryable(SQLException e) {
    return "40001".equals(e.getSQLState())   // 직렬화 실패(데드락 포함)
        || e.getErrorCode() == 1213          // ERROR 1213 데드락
        || e.getErrorCode() == 1205;         // ERROR 1205 락 대기 시간 초과
}
```

재시도하면 **안 되는** 오류도 구분한다. `1452`(FK), `1062`(UNIQUE), `3819`(CHECK)는
몇 번을 다시 해도 같은 결과이므로 바로 사용자에게 돌려준다.

### 8-3. 거절도 기록으로 남긴다

업무 트랜잭션이 롤백되면 그 안에서 쓴 채널 로그까지 사라진다.
그런데 "왜 거절됐는지"는 감사 대상이라 반드시 남아야 한다.
그래서 `TransactionService` 는 채널 로그(`chnl_request` / `chnl_block_decision` / `chnl_response`)를
**별도 트랜잭션**으로 적재한다.

---

## 9. 커넥션 풀 (HikariCP)

### 9-1. 왜 필요한가

`DriverManager.getConnection()` 은 호출할 때마다 커넥션을 새로 만든다. 그 한 번에 전부 일어난다.

1. TCP 3-way handshake — 네트워크 왕복
2. MySQL 인증, 아이디/비밀번호 검증 — 왕복 한 번 더
3. 세션 변수 설정 (문자셋, 타임존, autocommit)
4. 서버 쪽에 스레드 하나 생성

다 합쳐 보통 수십 ms 다. 정작 우리 쿼리는 1~2 ms 다.
**일하는 시간보다 준비하는 시간이 10배 이상 길다.**

커넥션 풀은 커넥션을 미리 몇 개 만들어 두고 빌려 주는 방식이다.
준비 비용을 애플리케이션이 뜰 때 한 번만 내는 셈이다.

### 9-2. 가장 헷갈리는 부분: `close()` 가 닫지 않는다

풀에서 받은 `Connection` 의 `close()` 는 실제로 끊는 것이 아니라 **풀에 돌려주는** 동작이다.
HikariCP 가 `Connection` 을 감싼 프록시 객체를 주기 때문이다.

그래서 **기존 코드를 한 줄도 고치지 않았다.** try-with-resources 를 그대로 쓴다.

```java
try (Connection conn = Db.getConnection()) {   // 풀에서 빌림
    ...
}                                              // 블록을 나가며 풀에 반납
```

반대로 **반납하지 않으면 더 위험해진다.** 풀에 커넥션이 10개뿐이라면,
반납을 빠뜨린 코드가 10번 실행되는 순간 애플리케이션 전체가 멈춘다.
`DriverManager` 를 쓸 때는 커넥션이 샐 뿐 프로그램은 돌았지만, 풀에서는 바로 장애가 된다.

그래서 `leakDetectionThreshold` 를 켜 뒀다. 빌린 채로 5초가 지나면
"어디서 빌려 갔는지" 스택 트레이스를 경고로 찍어 준다.

### 9-3. `Db.java` 만 바뀌었다

```
변경 전 : Db.getConnection() → DriverManager.getConnection(url, user, pw)
변경 후 : Db.getConnection() → hikariDataSource.getConnection()

변경된 파일 : support/Db.java  (+ 메뉴 하나와 종료 처리)
변경되지 않은 파일 : repository 10개, service 3개, TxTemplate, domain 전부
```

메서드 시그니처를 그대로 유지했기 때문이다.
"커넥션을 어디서 얻는가"를 처음부터 한 곳에 모아 둔 효과가 여기서 나온다.
이게 3절에서 `Db` 를 따로 만든 이유였다.

### 9-4. 설정값과 그 이유

`Db.java` 의 `HikariConfig` 부분이다. 숫자마다 이유가 있다.

| 설정 | 값 | 이유 |
|---|---|---|
| `maximumPoolSize` | 10 | **크게 잡으면 빨라진다는 건 오해다.** 커넥션 하나당 DB 서버에 스레드가 하나 붙으므로 100개로 잡으면 DB가 문맥 전환에 시간을 쓴다. HikariCP 권장 출발점은 `(코어 수 × 2) + 디스크 수` |
| `minimumIdle` | 2 | 0이면 한동안 안 쓰다가 요청이 오면 다시 만들어야 해서 첫 요청이 느려진다 |
| `connectionTimeout` | 3,000 ms | 풀이 다 찼을 때 기다리는 한계. 무한히 기다리면 창구 직원이 화면 앞에서 영원히 멈춘다. 3초 뒤 "혼잡하다"고 응답하는 쪽이 낫다 |
| `maxLifetime` | 30분 | **MySQL 의 `wait_timeout`(기본 8시간)보다 반드시 짧아야 한다.** 안 그러면 서버가 이미 끊은 커넥션을 풀이 멀쩡한 줄 알고 빌려 줘서 `Communications link failure` 가 간헐적으로 터진다 |
| `idleTimeout` | 10분 | 안 쓰는 커넥션을 `minimumIdle` 까지 줄인다 |
| `leakDetectionThreshold` | 5,000 ms (DEV만) | 반납 누락 탐지. 운영에서는 0(끔) |
| `autoCommit` | true | 아래 참조 |

`autoCommit` 이 특히 중요하다. `TxTemplate` 이 `setAutoCommit(false)` 로 트랜잭션을 시작하는데,
반납할 때 그 상태가 남아 있으면 **다음에 빌려 가는 쪽이 자기도 모르게 트랜잭션 안에서 작업한다.**
HikariCP 는 반납 시 설정된 기본값으로 되돌려 주므로 `true` 로 명시해 둔다.

MySQL 전용 최적화도 넣었다. `PreparedStatement` 를 캐시해 같은 SQL 을 다시 파싱하지 않게 한다.
우리 repository 는 SQL 을 `static final` 상수로 두고 반복 실행하므로 효과가 크다.

```java
cfg.addDataSourceProperty("cachePrepStmts",        "true");
cfg.addDataSourceProperty("prepStmtCacheSize",     "250");
cfg.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
cfg.addDataSourceProperty("useServerPrepStmts",    "true");
```

### 9-5. 숫자로 확인하기

`demo/PoolBenchmark.java` 를 실행하면 두 방식을 같은 조건으로 비교한다.
쿼리는 완전히 같으므로 차이는 커넥션을 얻는 비용뿐이다.

```
=== 커넥션 획득 방식 비교 (60회) ===

DriverManager :    201.7 ms  (1회 평균  3.36 ms)
HikariCP      :     21.8 ms  (1회 평균  0.36 ms)

차이          : 9.3 배 빠름
풀 상태       : [dab-pool total=2 active=0 idle=2 waiting=0 max=10]
```

**로컬 MySQL** 에서 9배다. 네트워크를 건너가는 실제 운영 환경에서는
TCP 왕복과 인증이 네트워크 지연을 그대로 타므로 격차가 훨씬 커진다.

### 9-6. 풀을 눈으로 보기

메뉴 **6번**이 `Db.poolStatus()` 를 찍는다.

```
 선택 > 6
  [dab-pool total=3 active=0 idle=3 waiting=0 max=10]
```

| 항목 | 뜻 |
|---|---|
| `total` | 풀이 들고 있는 전체 커넥션 수 |
| `active` | 지금 누군가 빌려 가서 쓰는 중 |
| `idle` | 대기 중 — 바로 빌려 갈 수 있다 |
| `waiting` | 커넥션이 없어 기다리는 스레드 수. **0이 아니면 풀이 작다는 신호** |

동시 출금 테스트(메뉴 5번)를 돌리면 `total` 이 늘어나고,
끝난 뒤에는 둘 다 반납되어 `active=0` 으로 돌아온다.
반납이 제대로 되는지 확인하는 가장 쉬운 방법이다.

```
  --- 시작 전 ---
  계좌 10200000000001 (고객 C0000000001) 잔액 950,000원 상태 10
  [dab-pool total=3 active=0 idle=3 waiting=0 max=10]
  [A] [성공] 0000 / 거래번호 T20261006162450959003 / 거래후잔액 650,000원
  [B] [성공] 0000 / 거래번호 T20261006162450959004 / 거래후잔액 350,000원
  --- 종료 후 ---
  계좌 10200000000001 (고객 C0000000001) 잔액 350,000원 상태 10
  [dab-pool total=3 active=0 idle=3 waiting=0 max=10]
```

### 9-7. 끝낼 때 닫아야 한다

풀은 살아 있는 TCP 커넥션과 관리용 스레드를 들고 있다.
닫지 않으면 프로그램이 끝나도 JVM 이 바로 빠져나가지 못할 수 있다.

```java
case "0":
    System.out.println("  종료 전 " + Db.poolStatus());
    Db.shutdown();        // 풀이 들고 있던 커넥션을 실제로 끊는다
    return;
```

`Db` 의 static 블록에 종료 훅도 걸어 뒀다.
`Ctrl+C` 로 강제 종료하거나 예외로 죽을 때도 정리된다.

```java
Runtime.getRuntime().addShutdownHook(new Thread(Db::shutdown, "db-shutdown"));
```

### 9-8. 로그가 시끄러우면

HikariCP 는 SLF4J 로 로그를 남긴다. SLF4J 는 **로그 규격**일 뿐이라
실제로 출력할 구현체를 하나 넣어 줘야 한다 (`slf4j-simple`). 없으면 로그가 전부 사라진다.

```
[main] INFO com.zaxxer.hikari.HikariDataSource - dab-pool - Starting...
[main] INFO com.zaxxer.hikari.HikariDataSource - dab-pool - Start completed.
```

이것도 거슬리면 실행 옵션으로 줄인다.

```
-Dorg.slf4j.simpleLogger.defaultLogLevel=warn
```

Eclipse 에서는 `Run Configurations` → `Arguments` 탭 → `VM arguments` 에 넣는다.

---

## 10. 동작 확인 결과

MySQL 8.0.46 + `schema.sql` + `seed_data.sql` 로 실제 실행한 결과다.

**차단 고객(박순자, C0000000002) 출금 → 거절**

```
[거절] D001 / 차단 (D001)
```

**같은 계좌에 입금 → 허용** (`block_rule` 의 `DEP/I` 행이 `allow_yn='Y'`)

```
[성공] 0000 / 거래번호 T20260929094831998003 / 거래후잔액 2,000,000원
```

**채널계에 남은 흔적** — 거절 건도 그대로 남아 있다.

| request_id | acct_no | txn_type_cd | decision_cd | applied_rule_id | rsp_cd |
|---|---|---|---|---|---|
| 8 | 10200000000002 | WDR | 20 (차단) | 3 | D001 |
| 9 | 10200000000002 | DEP | 10 (허용) | 1 | 0000 |
| 10 | 10200000000001 | WDR | 10 (허용) | NULL | 0000 |

`applied_rule_id` 가 NULL 인 건은 차단 대상이 아닌 고객이라 규칙을 볼 필요가 없었던 경우다.

**동시 출금 2건 (스레드 A/B, 각 100만원)**

```
--- 시작 전 ---
계좌 10200000000001 잔액 2,950,000원
  [A] [성공] 거래후잔액 1,950,000원
  [B] [성공] 거래후잔액 950,000원
--- 종료 후 ---
계좌 10200000000001 잔액 950,000원
```

**잔액 정합성 검증** — `acct.balance_amt` 와 거래원장 합계가 모든 계좌에서 일치한다.

```
+----------------+-------------+------------+------+
| acct_no        | balance_amt | calc       | diff |
+----------------+-------------+------------+------+
| 10200000000001 |   950000.00 |  950000.00 | 0.00 |
| 10200000000002 |  2000000.00 | 2000000.00 | 0.00 |
| ...                                              |
+----------------+-------------+------------+------+
```

`FOR UPDATE` 없이 같은 테스트를 하면 `diff` 가 0 이 아니게 된다
(= `concurrency/03_lost_update.sql` 의 갱신 손실).
`JdbcAccountRepository.findByNoForUpdate` 를 `findByNo` 로 바꿔 실행하면 직접 재현해 볼 수 있다.

---

## 11. 다음에 해 볼 것

| 단계 | 내용 |
|---|---|
| 테스트 | JUnit 5 + 가짜 저장소로 `BlockDecisionService` 단위 테스트. DB 없이 판정 규칙만 검증한다. 인터페이스로 분리해 둔 덕분에 바로 가능하다. |
| ~~커넥션 풀~~ | ~~HikariCP~~ — **9절에서 완료** |
| 예외 인출 | `exc_request` → `exc_approval` → `exc_payout` 흐름을 `ExceptionPayoutService` 로 구현 |
| 배치 | `ext_death_file` / `ext_death_record` 를 읽어 `death_block` 을 생성하는 야간 배치. `docs/simulator.html` 의 "사망정보 수신 배치" 탭이 그 동작을 미리 보여 준다 |
| 로깅 | `System.out.println` 을 SLF4J 로 교체. HikariCP 때문에 이미 의존성이 들어와 있다 |

---

## 부록 A. 왜 로컬 MySQL + JDBC 인가

0절에 요약한 내용의 근거다. 보고서에 쓸 수 있도록 조금 더 적어 둔다.

**ORM(JPA/Hibernate)을 쓰지 않은 이유.** JPA 는 SQL 을 자동으로 만들어 준다.
편리하지만 이 프로젝트에서 배우려는 것이 바로 그 숨겨지는 부분이다.

| 이 프로젝트의 핵심 | JPA 에서는 |
|---|---|
| `SELECT ... FOR UPDATE` 로 락을 거는 순서 | `@Lock(PESSIMISTIC_WRITE)` 한 줄로 가려진다 |
| 데드락(`ERROR 1213`)을 잡아 재시도 | 프레임워크가 `PessimisticLockingFailureException` 으로 번역해 버린다 |
| 트랜잭션 경계를 직접 그음 | `@Transactional` 이 보이지 않게 처리 |
| `DECIMAL` ↔ `BigDecimal` 매핑 | 자동 — 그래서 `double` 을 쓰면 안 되는 이유를 모르고 지나간다 |

실무에서는 JPA 를 쓰는 곳이 많고, 금융권에서는 MyBatis 를 더 많이 쓴다.
다만 **JDBC 를 먼저 이해한 다음에** 쓰는 것과 그러지 않은 것은 다르다.
커넥션 풀을 직접 붙여 본 것(9절)도 같은 이유다 —
Spring Boot 는 HikariCP 를 기본으로 깔아 주지만, 그러면 `maximumPoolSize` 를
왜 10으로 두는지 생각할 기회가 없다.

**PostgreSQL 로 옮기면 무엇이 달라지는가** (옮기지는 않지만 알아 둘 값)

| | MySQL 8.0 | PostgreSQL |
|---|---|---|
| 기본 격리수준 | REPEATABLE READ | **READ COMMITTED** |
| 데드락 | `ERROR 1213` / SQLSTATE `40001` | SQLSTATE `40P01` |
| CHECK 위반 | `ERROR 3819` | `23514` |
| 외래키 위반 | `ERROR 1452` | `23503` |
| UNIQUE 위반 | `ERROR 1062` | `23505` |
| 생성 컬럼에 상수 | 가능 | `DEFAULT` + `CHECK` 로 대체 |
| 여러 DB 간 외래키 | 가능 (이 프로젝트가 그렇다) | 불가 — 대신 **한 DB 안의 스키마**끼리 가능 |

바꿀 곳은 적다. `pom.xml` 의존성, `db.properties` 의 URL,
그리고 `MySqlError.isRetryable()` 에 `40P01` 추가뿐이다.
**repository 와 service 코드는 한 줄도 고치지 않는다** — `SELECT ... FOR UPDATE` 는 양쪽 문법이 같다.
인터페이스로 분리해 둔 효과이고, 면접에서 설명하기 좋은 지점이다.

---

## 부록 B. GitHub Pages 로 공개하기

서버가 필요 없다. 저장소의 `docs/` 폴더를 그대로 웹에 올려 주는 기능이다.
HTML·CSS·JS·이미지만 서비스되고 Java 는 실행되지 않는다 —
그래서 `simulator.html` 이 DB 없이 브라우저 안에서만 돌게 만들어 둔 것이다.

### B-1. 켜는 방법 (한 번만)

1. 저장소 → `Settings` → 왼쪽 `Pages`
2. **Source** → `Deploy from a branch`
3. **Branch** → `main`, 폴더는 **`/docs`** 선택 → `Save`
4. 1~2분 뒤 상단에 주소가 뜬다

```
https://doo-lee01.github.io/deceased-account-block-db/
```

> 저장소가 **Public** 이어야 무료로 쓸 수 있다. 이 저장소는 이미 공개 상태다.
> 공개 저장소이므로 `db.properties` 는 절대 커밋하지 않는다 (`.gitignore` 에 이미 있다).

### B-2. 올릴 파일

`docs/` 폴더에 넣으면 바로 그 주소로 열린다.

| 파일 | 주소 | 비고 |
|---|---|---|
| `index.html` | `/` | 산출물 허브. 아래 전부로 연결된다 |
| `simulator.html` | `/simulator.html` | 운영 콘솔 시뮬레이터 |
| `presentation.html` | `/presentation.html` | 발표 자료 31장 |
| `lock-lab-report.html` | `/lock-lab-report.html` | 락 실습 보고서 |
| `erd-overview.png` | `/erd-overview.png` | 전체 ERD |
| `.nojekyll` | — | 빈 파일. 아래 참조 |

`index.html` 이 Pages 의 첫 화면이 된다. 주소만 공유하면
발표 자료·시뮬레이터·실습 보고서·ERD 로 모두 들어갈 수 있다.

### B-3. `.nojekyll` 이 왜 필요한가

GitHub Pages 는 기본적으로 **Jekyll** 이라는 블로그 생성기를 한 번 거친다.
Jekyll 은 `_` 로 시작하는 파일과 폴더를 "내부용"으로 보고 **빼 버린다.**
우리 파일에는 해당하는 것이 없지만, 나중에 `_assets/` 같은 폴더를 만들면
이유를 모른 채 404 가 난다. 빈 파일 하나를 두면 Jekyll 을 건너뛴다.

```bash
# docs 폴더에 빈 파일 생성
cd docs
type nul > .nojekyll      # 윈도우 cmd
# 또는
New-Item .nojekyll        # PowerShell
```

### B-4. 올리는 순서

```bash
# 저장소 루트에서
git add docs/ java/
git commit -m "feat: 시뮬레이터와 GitHub Pages 허브 추가, HikariCP 커넥션 풀 적용"
git push origin main
```

푸시하고 1~2분이면 반영된다. 바로 안 보이면 브라우저 강력 새로고침
(`Ctrl+Shift+R`)을 한다 — Pages 가 CDN 을 거치므로 이전 파일이 잠깐 캐시에 남는다.

배포 상태는 저장소 → `Actions` 탭에서 확인할 수 있다.
`pages build and deployment` 가 초록색이면 끝난 것이다.

### B-5. 발표 때

| 상황 | 대응 |
|---|---|
| 와이파이가 불안하다 | `docs/` 폴더를 USB 에 담아 가면 `index.html` 을 더블클릭해 **오프라인으로도** 똑같이 돌아간다. 외부 리소스는 Google Fonts 뿐이고, 폰트가 안 떠도 레이아웃은 유지된다 |
| 링크를 띄워 두고 싶다 | Pages 주소를 QR 로 만들어 슬라이드에 넣으면 청중이 직접 눌러 볼 수 있다 |
| 시뮬레이터 시연 순서 | 좌측 "시연 시나리오" 버튼을 위에서부터 누르면 된다. 1번(차단개시 1초 전) → 2번(직후) 순서로 누르는 것이 경계값 설명에 가장 효과적이다 |

---

## 파일 구성

```
저장소 루트
├─ schema.sql / seed_data.sql / validation_queries.sql / constraint_tests.sql
├─ README.md                          설계 의사결정 보고서
├─ concurrency/                       락 · 데드락 실습 스크립트
├─ docs/                              ← GitHub Pages 가 서비스하는 폴더
│   ├─ index.html                     산출물 허브 (Pages 첫 화면)
│   ├─ simulator.html                 운영 콘솔 시뮬레이터
│   ├─ presentation.html              발표 자료 31장
│   ├─ lock-lab-report.html           락 실습 보고서
│   ├─ erd-overview.png / erd-core.png
│   ├─ logical-erd.drawio / conceptual-erd.drawio
│   └─ .nojekyll
└─ java/
   ├─ README.md                       이 문서
   └─ deceased-block-app/
      ├─ pom.xml                      Connector/J 8.4.0 · HikariCP 5.1.0 · slf4j-simple
      ├─ .gitignore                   db.properties 제외
      └─ src/main/
         ├─ java/kr/doolee/dab/
         │   ├─ Main.java             조립 + 콘솔 + 예외 최종 처리
         │   ├─ domain/      (8)      엔티티와 enum
         │   ├─ repository/  (5+5)    인터페이스 + JDBC 구현
         │   ├─ service/     (3)      비즈니스 로직
         │   ├─ support/     (8)      Db(커넥션 풀) · 트랜잭션 · 예외 · 환경
         │   └─ demo/        (1)      PoolBenchmark — 커넥션 풀 효과 측정
         └─ resources/
            └─ db.properties.example  복사해서 db.properties 로 사용
```

소스 31개. `db.properties` 는 커밋하지 않는다.

---

## 참고한 글

이 가이드의 용어와 계층 구분은 아래 글에 정리한 개념을 따랐다.

- [예외 처리 (Exception, try-catch, throw/throws)](https://lxvxxu.tistory.com/235) — 7절
- [service 와 repository 패키지](https://lxvxxu.tistory.com/233) — 4절 패키지 구조
- [스택 트레이스](https://lxvxxu.tistory.com/232) — 7-2절
- [Java API](https://lxvxxu.tistory.com/231) — `Db.java` 주석
- [문서화 주석 작성하는 습관](https://lxvxxu.tistory.com/236) — 전체 JavaDoc
- [개발 환경과 운영 환경](https://lxvxxu.tistory.com/230) — `AppEnv`
