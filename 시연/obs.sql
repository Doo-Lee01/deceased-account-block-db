-- =====================================================================
-- obs.sql  —  OBS 터미널에서 쓰는 관찰 명령 모음
--
-- OBS 는 시연에 직접 참여하지 않고 A · B 의 상태만 들여다본다.
-- 아래 명령을 필요할 때 골라서 친다.
-- =====================================================================


-- 1. 누가 어떤 락을 쥐고 / 기다리고 있나 ------------------------------
SELECT who, tbl, kind, raw_mode, status, row_key FROM lock_lab.v_locks;
--   who       : A / B
--   kind      : S (read) 읽는 중  /  X (write) 고치는 중
--   raw_mode  : MySQL 원래 표기. REC_NOT_GAP 은 "행 하나만 잠금"
--   status    : GRANTED 쥐고 있음  /  WAITING 기다리는 중
--   row_key   : 잠긴 행의 기본키 (시연 계좌번호)


-- 2. 누가 누구 때문에 기다리나 ----------------------------------------
SELECT * FROM lock_lab.v_waits;
--   waiting    : 기다리는 쪽
--   wants      : 원하는 락
--   blocked_by : 막고 있는 쪽
--   holds      : 막는 쪽이 쥔 락
--   결과가 비어 있으면 아무도 기다리지 않는 상태다.


-- 3. 트랜잭션 상태 ----------------------------------------------------
SELECT who, state, open_sec, rows_locked, running_sql FROM lock_lab.v_trx;
--   state       : RUNNING 진행 중  /  LOCK WAIT 락 대기 중
--   open_sec    : BEGIN 한 지 몇 초 지났나
--   running_sql : 지금 실행 중(대기 중)인 문장. 끝났으면 NULL


-- 4. 시연 계좌 잔액과 거래 --------------------------------------------
SELECT * FROM lock_lab.v_acct;


-- 5. 마지막 데드락 기록 -----------------------------------------------
SHOW ENGINE INNODB STATUS\G
--   "LATEST DETECTED DEADLOCK" 부분만 보면 된다.
--   HOLDS THE LOCK      = 쥐고 있던 락
--   WAITING FOR THIS    = 기다리던 락
--   WE ROLL BACK        = MySQL 이 취소한 쪽


-- 6. 등록된 터미널 이름표 ---------------------------------------------
SELECT * FROM lock_lab.session_label;
--   A · B · OBS 가 보이지 않으면 해당 터미널에서 CALL lock_lab.iam('A'); 실행


-- 7. 시연 계좌 초기화 -------------------------------------------------
CALL lock_lab.reset();
--   멈추면 A · B 에서 트랜잭션이 안 끝난 것. A · B 에서 ROLLBACK; 후 다시.
