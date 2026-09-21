-- =====================================================================
-- 03_lost_update.sql  —  시나리오 3 : 출금에서 생기는 문제
--
-- 출금은 입금과 달리 "잔액이 충분한가" 를 먼저 봐야 한다.
-- 잔액을 잠그지 않고 읽으면, 판단한 시점과 실제로 빼는 시점 사이에
-- 다른 사람이 끼어들 수 있다.
--
-- 이 파일은 두 부분이다.
--   PART A  갱신 분실 — 애플리케이션이 계산한 값으로 덮어쓰면 돈이 사라진다
--   PART B  DB 에서 계산하면 CHECK 제약이 마이너스 잔액을 막아 준다
--
-- 공통 상황 : 잔액 100만원 계좌에서 A · B 가 동시에 70만원씩 출금
--             올바른 결과는 "한 명 승인, 한 명 잔액 부족으로 거절"
--
-- 참고 : 이 시나리오에서는 원장(acct_txn) 기록을 뺐다.
--        넣으면 시나리오 1 의 데드락이 먼저 나서 이 문제를 볼 수 없다.
--
-- 사용 터미널 : A · B · OBS
-- =====================================================================


-- #####################################################################
-- PART A  —  갱신 분실 (lost update)
-- #####################################################################

-- [STEP A0] ▶ OBS
CALL lock_lab.reset();


-- [STEP A1] ▶ A  —  잔액 읽기 (잠그지 않음)
BEGIN;
SELECT balance_amt INTO @bal FROM acct WHERE acct_no = '10288000000001';
SELECT @bal AS read_balance;               -- 기대: 1000000.00
-- A 의 판단: "100만원 있으니 70만원 출금 가능"


-- [STEP A2] ▶ B  —  B 도 잔액 읽기
BEGIN;
SELECT balance_amt INTO @bal FROM acct WHERE acct_no = '10288000000001';
SELECT @bal AS read_balance;               -- 기대: 1000000.00
-- B 의 판단: "100만원 있으니 70만원 출금 가능"
-- 둘 다 같은 값을 봤다. 아직 아무도 바꾸지 않았으니 당연하다.


-- [STEP A3] ▶ A  —  계산한 값으로 잔액 덮어쓰기
UPDATE acct
   SET balance_amt = @bal - 700000         -- ★ 애플리케이션이 계산한 값(30만)을 그대로 저장
 WHERE acct_no = '10288000000001';
-- 기대: Query OK (A 가 X 락을 잡음)


-- [STEP A4] ▶ B  —  B 도 계산한 값으로 덮어쓰기  →  멈춘다
UPDATE acct
   SET balance_amt = @bal - 700000         -- B 의 @bal 도 100만 → 30만을 저장하려 함
 WHERE acct_no = '10288000000001';
-- 기대: 멈춤 (A 의 X 락 때문에 대기)


-- [STEP A5] ▶ OBS  —  B 가 A 를 기다리는 중
SELECT * FROM lock_lab.v_waits;
-- 기대: waiting=B, blocked_by=A
-- 여기까지는 락이 제 역할을 하고 있다. 문제는 풀린 다음이다.


-- [STEP A6] ▶ A  —  커밋
COMMIT;
-- 기대 (B 화면): 대기가 풀리며 Query OK, 1 row affected
--   B 는 기다린 뒤 "30만원" 을 그대로 저장했다.
--   A 가 이미 30만원으로 바꿔 놨다는 사실은 B 의 계산에 반영되지 않았다.


-- [STEP A7] ▶ B  —  커밋
COMMIT;


-- [STEP A8] ▶ OBS  —  결과
SELECT * FROM lock_lab.v_acct;
-- 기대:
-- | item    | value   |
-- | BALANCE | 300,000 |
--
-- ★ 140만원이 나갔는데 30만원이 남았다.
--   정상이라면 B 는 거절됐어야 한다. 은행이 70만원을 잃었다.
--   CHECK(balance_amt >= 0) 도 못 잡는다. 30만원은 마이너스가 아니기 때문.
--   에러가 하나도 나지 않아서 데드락보다 훨씬 위험하다.
--
-- 순서 정리
--   ① A 읽음 100만  ② B 읽음 100만  ③ A 가 30만 저장
--   ④ B 가 (낡은) 100만 기준으로 30만 저장  → A 의 출금이 지워짐


-- #####################################################################
-- PART B  —  DB 에서 계산하면 CHECK 가 막는다
-- #####################################################################

-- [STEP B0] ▶ OBS
CALL lock_lab.reset();


-- [STEP B1] ▶ A  —  DB 에게 "현재 값에서 빼라" 고 맡긴다
BEGIN;
UPDATE acct
   SET balance_amt = balance_amt - 700000  -- ★ 값을 덮어쓰지 않고 현재 값에서 뺀다
 WHERE acct_no = '10288000000001';
-- 기대: Query OK (아직 커밋 전, 잔액 30만)


-- [STEP B2] ▶ B  —  잔액을 읽고, 빼려고 시도  →  멈춘다
BEGIN;
SELECT balance_amt INTO @bal FROM acct WHERE acct_no = '10288000000001';
SELECT @bal AS read_balance;
-- 기대: 1000000.00
--   A 가 이미 30만으로 바꿨지만 아직 커밋 전이라 B 에게는 안 보인다.
--   B 는 "100만원 있으니 승인" 이라고 판단할 것이다.

UPDATE acct
   SET balance_amt = balance_amt - 700000
 WHERE acct_no = '10288000000001';
-- 기대: 멈춤 (A 의 X 락 대기)


-- [STEP B3] ▶ A  —  커밋
COMMIT;
-- 기대 (B 화면): 대기가 풀리자마자
--   ERROR 3819 (HY000): Check constraint 'ck_acct_balance' is violated.
--
-- B 의 UPDATE 는 대기가 풀린 뒤 "그 순간의 진짜 잔액" 30만에서 70만을 빼려 했다.
-- 결과가 -40만이 되니 schema.sql 에 걸어 둔 제약이 막았다.
--   CONSTRAINT ck_acct_balance CHECK (balance_amt >= 0)


-- [STEP B4] ▶ B
ROLLBACK;


-- [STEP B5] ▶ OBS
SELECT * FROM lock_lab.v_acct;
-- 기대: BALANCE 300,000
--
-- ★ 잔액은 지켜졌다. 하지만 여전히 문제가 남는다.
--   B 는 100만원을 보고 "승인" 판단을 한 뒤에 실패했다.
--   ATM 이 판단 직후 현금을 내줬다면 돈은 나갔는데 기록은 없는 상태가 된다.
--   CHECK 는 사고를 막는 마지막 방어선이지, 올바른 처리 방법이 아니다.
--   → 시나리오 4 에서 해결한다.
