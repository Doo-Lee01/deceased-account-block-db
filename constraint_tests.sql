-- =====================================================================
-- constraint_tests.sql
-- 제약조건 위반 테스트 12종
--
-- 일부러 잘못된 데이터를 넣어 DB 가 막는지 확인합니다.
-- 오류가 발생하는 것이 정상이므로 --force 옵션이 필요합니다.
--
--   mysql -u root -p --force < constraint_tests.sql
--
-- schema.sql 직후에도, seed_data.sql 적재 후에도 실행할 수 있도록
-- 전용 식별자(C9..., 102990...)를 사용합니다.
-- =====================================================================
SET NAMES utf8mb4;
USE core_bank;

-- 테스트 전용 데이터 -------------------------------------------------
INSERT INTO cust (cust_no, cust_nm, rrn_hash, birth_dt, cust_status_cd, open_dt) VALUES
 ('C9000000001','테스트정상', SHA2('test-alive',256), '1950-03-11','10','2010-01-04'),
 ('C9000000002','테스트사망', SHA2('test-dead',256),  '1948-07-22','20','2008-05-19');
INSERT INTO acct (acct_no, cust_no, product_cd, balance_amt, acct_status_cd, open_dt) VALUES
 ('10299000000001','C9000000001','1010', 5000000.00,'10','2010-01-04'),
 ('10299000000002','C9000000002','1010', 3000000.00,'20','2008-05-19');
INSERT INTO death_block
 (cust_no, source_record_id, death_dt, death_report_dt, block_start_dtm, block_status_cd)
VALUES ('C9000000002', 9001,'2026-09-10','2026-09-12','2026-09-13 00:00:00.000','20');
INSERT INTO payee_registry
 (payee_id, payee_type_cd, payee_nm, biz_reg_no, org_cd, acct_no, status_cd, reg_dt)
VALUES (9001,'30','테스트장례식장','9998887776','020','10299999999999','10','2026-01-02');

SELECT '=== TEST 1 : 공통코드에 없는 코드값 → FK 차단 ===' AS test;
INSERT INTO cust (cust_no, cust_nm, rrn_hash, birth_dt, cust_status_cd, open_dt)
VALUES ('C9000000003','오류', SHA2('t3',256), '1970-01-01','99','2020-01-01');

SELECT '=== TEST 2 : 다른 그룹의 코드값 사용 → FK 차단 ===' AS test;
-- REQ 는 EXC_ST 그룹의 코드이지 CUST_ST 그룹의 코드가 아님
INSERT INTO cust (cust_no, cust_nm, rrn_hash, birth_dt, cust_status_cd, open_dt)
VALUES ('C9000000004','오류', SHA2('t4',256), '1970-01-01','REQ','2020-01-01');

SELECT '=== TEST 3 : 차단개시일시를 신고일 당일로 지정 → CHECK 차단 ===' AS test;
INSERT INTO death_block
 (cust_no, source_record_id, death_dt, death_report_dt, block_start_dtm, block_status_cd)
VALUES ('C9000000001', 9002,'2026-09-10','2026-09-12','2026-09-12 00:00:00.000','20');

SELECT '=== TEST 4 : 한 고객에게 차단 2건 → UNIQUE 차단 ===' AS test;
INSERT INTO death_block
 (cust_no, source_record_id, death_dt, death_report_dt, block_start_dtm, block_status_cd)
VALUES ('C9000000002', 9003,'2026-09-10','2026-09-12','2026-09-13 00:00:00.000','20');

SELECT '=== TEST 5 : 거래금액 음수 → CHECK 차단 ===' AS test;
INSERT INTO acct_txn
 (txn_unique_no, acct_no, txn_dtm, txn_type_cd, dr_cr_cd, txn_amt, balance_after_amt, chnl_cd, rsp_cd)
VALUES ('T9000000000000000000000001','10299000000001','2026-09-15 10:00:00.000',
        'WDR','O', -50000.00, 4950000.00,'10','0000');

SELECT '=== TEST 6 : 입출금구분에 잘못된 값 → CHECK 차단 ===' AS test;
INSERT INTO acct_txn
 (txn_unique_no, acct_no, txn_dtm, txn_type_cd, dr_cr_cd, txn_amt, balance_after_amt, chnl_cd, rsp_cd)
VALUES ('T9000000000000000000000002','10299000000001','2026-09-15 10:00:00.000',
        'WDR','X', 50000.00, 4950000.00,'10','0000');

SELECT '=== TEST 7 : 존재하지 않는 고객의 계좌 개설 → FK 차단 ===' AS test;
INSERT INTO acct (acct_no, cust_no, product_cd, balance_amt, acct_status_cd, open_dt)
VALUES ('10299000000099','C9999999999','1010', 0,'10','2026-09-15');

SELECT '=== TEST 8 : 허용규칙인데 거절코드 지정 → CHECK 차단 ===' AS test;
INSERT INTO block_rule
 (txn_type_cd, dr_cr_cd, allow_yn, exception_possible_yn, reject_rsp_cd, apply_from_dt)
VALUES ('INQ','I','Y','N','D001','2026-09-11');

SELECT '=== TEST 9 : 미등록 수취처로 예외인출 지급 → FK 차단 ===' AS test;
INSERT INTO exc_request
 (exc_request_id, cust_no, acct_no, payee_id, applicant_nm, applicant_rel_cd,
  exc_reason_cd, request_amt, exc_status_cd, request_dtm)
VALUES (9001,'C9000000002','10299000000002', 9001,'테스트상속','20','20',
        1500000.00,'APV','2026-09-15 09:00:00.000');
INSERT INTO acct_txn
 (txn_unique_no, acct_no, txn_dtm, txn_type_cd, dr_cr_cd, txn_amt, balance_after_amt,
  chnl_cd, counter_org_cd, counter_acct_no, rsp_cd)
VALUES ('T9000000000000000000000010','10299000000002','2026-09-15 14:00:00.000',
        'TRO','O', 1500000.00, 1500000.00,'10','020','10299999999999','E010');
-- payee_id = 9999 는 등록되지 않은 수취처 (= 상속인 개인계좌 상황)
INSERT INTO exc_payout (exc_request_id, payee_id, payout_amt, txn_unique_no, payout_dtm, rsp_cd)
VALUES (9001, 9999, 1500000.00,'T9000000000000000000000010','2026-09-15 14:00:00.000','E010');

SELECT '=== TEST 10 : 등록된 수취처로 지급 → 정상 처리 ===' AS test;
INSERT INTO exc_payout (exc_request_id, payee_id, payout_amt, txn_unique_no, payout_dtm, rsp_cd)
VALUES (9001, 9001, 1500000.00,'T9000000000000000000000010','2026-09-15 14:00:00.000','E010');

SELECT '=== TEST 11 : 같은 신청에 두 번째 지급 → UNIQUE 차단 ===' AS test;
INSERT INTO acct_txn
 (txn_unique_no, acct_no, txn_dtm, txn_type_cd, dr_cr_cd, txn_amt, balance_after_amt,
  chnl_cd, counter_org_cd, counter_acct_no, rsp_cd)
VALUES ('T9000000000000000000000011','10299000000002','2026-09-15 15:00:00.000',
        'TRO','O', 1500000.00, 0.00,'10','020','10299999999999','E010');
INSERT INTO exc_payout (exc_request_id, payee_id, payout_amt, txn_unique_no, payout_dtm, rsp_cd)
VALUES (9001, 9001, 1500000.00,'T9000000000000000000000011','2026-09-15 15:00:00.000','E010');

SELECT '=== TEST 12 : 이력 현재행 2건 등록 → UNIQUE 차단 ===' AS test;
INSERT INTO cust_status_hist
 (cust_no, before_status_cd, after_status_cd, change_reason_cd, valid_from_dtm)
VALUES ('C9000000002', NULL,'10','10','2008-05-19 09:00:00.000');
INSERT INTO cust_status_hist
 (cust_no, before_status_cd, after_status_cd, change_reason_cd, valid_from_dtm)
VALUES ('C9000000002','10','20','20','2026-09-13 00:00:00.000');

SELECT '=== 결과 요약 : 위 12건 중 10번만 성공해야 정상 ===' AS test;
SELECT payout_id, exc_request_id, payee_id, payout_amt FROM exc_payout WHERE exc_request_id = 9001;
SELECT cust_no, COUNT(*) AS hist_cnt FROM cust_status_hist WHERE cust_no LIKE 'C9%' GROUP BY cust_no;
