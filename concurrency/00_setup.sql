-- =====================================================================
-- 00_setup.sql  —  락·데드락 시연 준비
--
-- 무엇을 만드나
--   1) lock_lab 데이터베이스
--        · 프로젝트 스키마(core_bank 등)를 더럽히지 않도록 시연용 도구를
--          별도 DB 에 모아 둔다.
--   2) iam('A')        — 이 터미널이 A 인지 B 인지 이름표를 붙인다
--   3) reset()         — 시연 계좌를 처음 상태로 되돌린다
--   4) 관찰용 뷰 4개   — OBS 터미널에서 락 상태를 읽기 쉽게 보여준다
--   5) 시연 전용 고객·계좌 (잔액 1,000,000원)
--
-- 전제
--   schema.sql 을 먼저 실행해 core_bank · comm_code 가 있어야 한다.
--   schema.sql 을 다시 돌리면 시연 계좌가 사라지므로 이 파일도 다시 실행한다.
--
-- 실행
--   Workbench 에서 열어 번개 버튼, 또는
--   mysql -u root -p < 00_setup.sql
-- =====================================================================
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS lock_lab DEFAULT CHARACTER SET utf8mb4;
USE lock_lab;

-- ---------------------------------------------------------------------
-- 1. 세션 이름표
--    MySQL 은 접속마다 번호(CONNECTION_ID)만 붙인다. 어느 번호가 A 이고
--    어느 번호가 B 인지 알 수 없으므로, 각 터미널이 접속하자마자
--    자기 번호와 이름을 이 표에 적어 둔다.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS session_label (
  conn_id BIGINT UNSIGNED NOT NULL PRIMARY KEY,   -- CONNECTION_ID()
  label   VARCHAR(10)     NOT NULL,               -- 'A' / 'B' / 'OBS'
  reg_dtm DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
);

DROP PROCEDURE IF EXISTS iam;
DELIMITER //
CREATE PROCEDURE iam(IN p_label VARCHAR(10))
BEGIN
  -- 같은 번호가 재사용될 수 있으므로 덮어쓴다
  REPLACE INTO lock_lab.session_label (conn_id, label) VALUES (CONNECTION_ID(), p_label);
  -- 락을 기다리는 최대 시간(초). 기본 50초보다 넉넉히 잡아
  -- 시연 중 설명하는 동안 대기가 풀려 버리지 않게 한다.
  SET SESSION innodb_lock_wait_timeout = 120;
  SELECT CONCAT('이 터미널은 ', p_label, ' 입니다 (접속번호 ', CONNECTION_ID(), ')') AS hello;
END //
DELIMITER ;

-- ---------------------------------------------------------------------
-- 2. 시연 계좌 초기화
--    · 시연 중 넣은 거래(DEMO-S*)를 지우고 잔액을 1,000,000원으로 돌린다.
--    · A · B 터미널이 트랜잭션을 끝내지 않았으면 여기서 기다리게 된다.
--      그럴 땐 A · B 에서 ROLLBACK; 을 먼저 친다.
-- ---------------------------------------------------------------------
DROP PROCEDURE IF EXISTS reset;
DELIMITER //
CREATE PROCEDURE reset()
BEGIN
  DELETE FROM core_bank.acct_txn
   WHERE txn_unique_no LIKE 'DEMO-S%';
  UPDATE core_bank.acct
     SET balance_amt = 1000000
   WHERE acct_no = '10288000000001';
  SELECT '초기화 완료 — 시연 계좌 잔액 1,000,000원' AS reset;
END //
DELIMITER ;

-- ---------------------------------------------------------------------
-- 3. 관찰용 뷰
--    performance_schema.data_locks      : 지금 걸려 있는 락 전부
--    performance_schema.data_lock_waits : 누가 누구를 기다리는지
--    information_schema.innodb_trx      : 진행 중인 트랜잭션
--    원본 표는 컬럼이 많아 읽기 어려우니 필요한 것만 추려 이름표를 붙인다.
--    콘솔에서 줄이 어긋나지 않도록 컬럼 이름은 영문으로 둔다.
-- ---------------------------------------------------------------------

-- 3-1. 지금 누가 어떤 행에 어떤 락을 쥐고 있나
CREATE OR REPLACE VIEW v_locks AS
SELECT COALESCE(l.label, CONCAT('conn', t.PROCESSLIST_ID)) AS who,
       d.OBJECT_NAME                                         AS tbl,
       d.INDEX_NAME                                          AS idx,
       CASE WHEN d.LOCK_MODE LIKE 'S%' THEN 'S (read)'
            WHEN d.LOCK_MODE LIKE 'X%' THEN 'X (write)'
            ELSE d.LOCK_MODE END                             AS kind,
       d.LOCK_MODE                                           AS raw_mode,
       d.LOCK_STATUS                                         AS status,   -- GRANTED=쥐고 있음, WAITING=기다리는 중
       d.LOCK_DATA                                           AS row_key
  FROM performance_schema.data_locks d
  JOIN performance_schema.threads    t ON t.THREAD_ID = d.THREAD_ID
  LEFT JOIN lock_lab.session_label   l ON l.conn_id   = t.PROCESSLIST_ID
 WHERE d.OBJECT_SCHEMA = 'core_bank'     -- 공통코드 쪽 락은 충돌이 없어 제외
   AND d.LOCK_TYPE     = 'RECORD'        -- 테이블 단위 의도락(IX/IS)은 제외
   AND d.OBJECT_NAME   = 'acct';         -- 이 시연의 주인공은 계좌 행 하나

-- 3-2. 누가 누구 때문에 기다리고 있나
CREATE OR REPLACE VIEW v_waits AS
SELECT COALESCE(rl.label, CONCAT('conn', rt.PROCESSLIST_ID)) AS waiting,
       r.LOCK_MODE                                            AS wants,
       COALESCE(bl.label, CONCAT('conn', bt.PROCESSLIST_ID)) AS blocked_by,
       b.LOCK_MODE                                            AS holds,
       r.OBJECT_NAME                                          AS tbl,
       r.LOCK_DATA                                            AS row_key
  FROM performance_schema.data_lock_waits w
  JOIN performance_schema.data_locks r ON r.ENGINE_LOCK_ID = w.REQUESTING_ENGINE_LOCK_ID
  JOIN performance_schema.data_locks b ON b.ENGINE_LOCK_ID = w.BLOCKING_ENGINE_LOCK_ID
  JOIN performance_schema.threads   rt ON rt.THREAD_ID = r.THREAD_ID
  JOIN performance_schema.threads   bt ON bt.THREAD_ID = b.THREAD_ID
  LEFT JOIN lock_lab.session_label  rl ON rl.conn_id = rt.PROCESSLIST_ID
  LEFT JOIN lock_lab.session_label  bl ON bl.conn_id = bt.PROCESSLIST_ID;

-- 3-3. 진행 중인 트랜잭션
CREATE OR REPLACE VIEW v_trx AS
SELECT COALESCE(l.label, CONCAT('conn', x.trx_mysql_thread_id)) AS who,
       x.trx_state                                               AS state,       -- RUNNING / LOCK WAIT
       TIMESTAMPDIFF(SECOND, x.trx_started, NOW())               AS open_sec,    -- 트랜잭션을 연 지 몇 초
       x.trx_rows_locked                                         AS rows_locked,
       x.trx_rows_modified                                       AS rows_changed,
       LEFT(x.trx_query, 60)                                     AS running_sql  -- 지금 실행 중(대기 중)인 문장
  FROM information_schema.innodb_trx x
  LEFT JOIN lock_lab.session_label l ON l.conn_id = x.trx_mysql_thread_id;

-- 3-4. 시연 계좌의 잔액과 거래 — 결과 확인용
CREATE OR REPLACE VIEW v_acct AS
SELECT 'BALANCE'                    AS item,
       FORMAT(balance_amt, 0)       AS value,
       NULL                         AS note
  FROM core_bank.acct WHERE acct_no = '10288000000001'
UNION ALL
SELECT txn_unique_no,
       CONCAT(IF(dr_cr_cd='I','+','-'), FORMAT(txn_amt,0)),
       CONCAT('after ', FORMAT(balance_after_amt,0))
  FROM core_bank.acct_txn
 WHERE acct_no = '10288000000001' AND txn_unique_no LIKE 'DEMO-S%';

-- ---------------------------------------------------------------------
-- 4. 시연 전용 고객 · 계좌
--    · 기존 샘플 데이터(C0000000001~5)와 겹치지 않는 번호를 쓴다.
--    · 개설 입금 1건(DEMO-OPEN)을 함께 넣어 잔액 = 원장 합계가 맞게 한다.
--      (validation_queries.sql 의 S9 잔액 대사가 깨지지 않도록)
-- ---------------------------------------------------------------------
INSERT IGNORE INTO core_bank.cust
  (cust_no, cust_nm, rrn_hash, birth_dt, cust_status_cd, open_dt)
VALUES ('C8000000001', '락시연', SHA2('lock-lab-demo', 256), '1990-01-01', '10', '2026-01-02');

INSERT IGNORE INTO core_bank.acct
  (acct_no, cust_no, product_cd, balance_amt, acct_status_cd, open_dt)
VALUES ('10288000000001', 'C8000000001', '1010', 1000000.00, '10', '2026-01-02');

INSERT IGNORE INTO core_bank.acct_txn
  (txn_unique_no, acct_no, txn_dtm, txn_type_cd, dr_cr_cd, txn_amt,
   balance_after_amt, chnl_cd, rsp_cd, memo)
VALUES ('DEMO-OPEN', '10288000000001', '2026-01-02 09:00:00.000', 'DEP', 'I',
        1000000.00, 1000000.00, '10', '0000', '시연 계좌 개설입금');

CALL reset();
SELECT '준비 완료 — lock-lab.bat 으로 A · B · OBS 터미널을 여세요' AS setup;
