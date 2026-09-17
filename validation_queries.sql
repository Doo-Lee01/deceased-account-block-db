-- =====================================================================
-- validation_queries.sql
-- 비즈니스 검증용 쿼리 모음 — 14개 시나리오
-- =====================================================================
SET NAMES utf8mb4;

SET NAMES utf8mb4;
USE core_bank;

SELECT '########## [S1] 차단개시일시 = 사망신고일 + 1일 ##########' AS scenario;
SELECT b.cust_no, c.cust_nm, b.death_dt AS 사망일, b.death_report_dt AS 신고일,
       b.block_start_dtm AS 차단개시,
       DATEDIFF(DATE(b.block_start_dtm), b.death_report_dt) AS 신고일차,
       d.code_nm AS 차단상태
  FROM death_block b
  JOIN cust c ON c.cust_no = b.cust_no
  JOIN comm_code.code_detail d ON d.group_cd='BLK_ST' AND d.code_cd=b.block_status_cd
 ORDER BY b.block_id;

SELECT '########## [S2] 경계값 - 같은 계좌, 같은 금액, 1분 차이 ##########' AS scenario;
SELECT r.request_dtm AS 요청일시,
       CASE WHEN r.request_dtm >= b.block_start_dtm THEN '차단개시 후' ELSE '차단개시 전' END AS 시점,
       dd.code_nm AS 판정, s.rsp_cd AS 응답, s.rsp_message AS 메시지
  FROM channel.chnl_request r
  JOIN channel.chnl_block_decision k ON k.request_id = r.request_id
  JOIN channel.chnl_response      s ON s.request_id = r.request_id
  JOIN comm_code.code_detail     dd ON dd.group_cd='DEC_CD' AND dd.code_cd=k.decision_cd
  LEFT JOIN death_block b ON b.cust_no = r.cust_no
 WHERE r.acct_no='10200000000002' AND r.txn_type_cd='WDR'
 ORDER BY r.request_dtm;

SELECT '########## [S3] 차단 중에도 입금은 허용 ##########' AS scenario;
SELECT t.txn_dtm AS 거래일시, tt.code_nm AS 거래유형,
       CASE t.dr_cr_cd WHEN 'I' THEN '입금' ELSE '출금' END AS 구분,
       t.txn_amt AS 금액, t.balance_after_amt AS 거래후잔액, t.rsp_cd AS 응답
  FROM acct_txn t
  JOIN comm_code.code_detail tt ON tt.group_cd='TXN_TP' AND tt.code_cd=t.txn_type_cd
 WHERE t.acct_no='10200000000002' AND t.txn_dtm >= '2026-09-13'
 ORDER BY t.txn_dtm;

SELECT '########## [S4] 차단 판정 시뮬레이션 (규칙 테이블 기반) ##########' AS scenario;
SELECT x.cust_no, x.txn_type_cd AS 거래유형, x.txn_dtm AS 거래일시,
       CASE WHEN b.cust_no IS NULL OR b.block_status_cd <> '20' THEN '허용 (차단 없음)'
            WHEN x.txn_dtm < b.block_start_dtm                  THEN '허용 (차단개시 전)'
            WHEN r.allow_yn = 'Y'                               THEN '허용 (규칙상 필수거래)'
            WHEN r.exception_possible_yn = 'Y'                  THEN CONCAT('차단 / 예외인출 가능 (', r.reject_rsp_cd, ')')
            ELSE CONCAT('차단 (', r.reject_rsp_cd, ')')
       END AS 판정
  FROM ( SELECT 'C0000000002' AS cust_no,'WDR' AS txn_type_cd,'O' AS dr_cr_cd,'2026-09-12 23:59:00' AS txn_dtm
   UNION ALL SELECT 'C0000000002','WDR','O','2026-09-13 00:00:01'
   UNION ALL SELECT 'C0000000002','DEP','I','2026-09-14 10:00:00'
   UNION ALL SELECT 'C0000000002','CLS','O','2026-09-15 09:30:00'
   UNION ALL SELECT 'C0000000002','ATO','O','2026-09-15 03:00:00'
   UNION ALL SELECT 'C0000000005','WDR','O','2026-09-15 15:00:00'
   UNION ALL SELECT 'C0000000001','WDR','O','2026-09-15 10:00:00' ) x
  LEFT JOIN death_block b ON b.cust_no = x.cust_no
  LEFT JOIN block_rule  r ON r.txn_type_cd = x.txn_type_cd AND r.dr_cr_cd = x.dr_cr_cd
                         AND DATE(x.txn_dtm) BETWEEN r.apply_from_dt AND r.apply_to_dt
 ORDER BY x.cust_no, x.txn_dtm;

SELECT '########## [S5] 자동이체 처리결과 ##########' AS scenario;
SELECT a.auto_transfer_id AS 약정ID, a.acct_no AS 출금계좌, a.transfer_amt AS 이체금액,
       r.process_dt AS 처리일, rc.code_nm AS 결과, COALESCE(p.rsp_message,'-') AS 실패사유
  FROM auto_transfer a
  JOIN auto_transfer_result r ON r.auto_transfer_id = a.auto_transfer_id
  JOIN comm_code.code_detail rc ON rc.group_cd='AT_RES' AND rc.code_cd=r.result_cd
  LEFT JOIN comm_code.rsp_code p ON p.rsp_cd = r.fail_rsp_cd
 ORDER BY a.auto_transfer_id;

SELECT '########## [S6] 예외인출 전 과정 추적 ##########' AS scenario;
SELECT q.exc_request_id AS 신청ID, rs.code_nm AS 사유, q.request_amt AS 신청금액,
       st.code_nm AS 상태,
       (SELECT COUNT(*) FROM exc_document   d WHERE d.exc_request_id=q.exc_request_id) AS 서류수,
       (SELECT COUNT(*) FROM exc_document   d WHERE d.exc_request_id=q.exc_request_id AND d.verify_result_cd='10') AS 적합,
       (SELECT COUNT(*) FROM exc_approval   v WHERE v.exc_request_id=q.exc_request_id AND v.approval_result_cd='10') AS 승인차수,
       COALESCE((SELECT y.payout_amt FROM exc_payout y WHERE y.exc_request_id=q.exc_request_id),0) AS 지급금액,
       COALESCE(q.reject_reason_desc,'-') AS 반려사유
  FROM exc_request q
  JOIN comm_code.code_detail rs ON rs.group_cd='EXC_RSN' AND rs.code_cd=q.exc_reason_cd
  JOIN comm_code.code_detail st ON st.group_cd='EXC_ST'  AND st.code_cd=q.exc_status_cd
 ORDER BY q.exc_request_id;

SELECT '########## [S7] 지급 수취처가 지정 수취처와 같은지 검증 ##########' AS scenario;
SELECT y.payout_id AS 지급ID, q.payee_id AS 신청수취처, y.payee_id AS 지급수취처,
       g.payee_nm AS 수취처명, pt.code_nm AS 구분,
       CASE WHEN q.payee_id = y.payee_id THEN '일치' ELSE '★불일치 - 조사 필요' END AS 검증
  FROM exc_payout y
  JOIN exc_request q ON q.exc_request_id = y.exc_request_id
  JOIN payee_registry g ON g.payee_id = y.payee_id
  JOIN comm_code.code_detail pt ON pt.group_cd='PAYEE_TP' AND pt.code_cd=g.payee_type_cd;

SELECT '########## [S8] 매칭 결과 분포 (동명이인 다건매칭 포함) ##########' AS scenario;
SELECT f.base_biz_dt AS 기준일자, m.code_nm AS 매칭상태, COUNT(*) AS 건수,
       GROUP_CONCAT(COALESCE(e.error_desc,'-') SEPARATOR ' / ') AS 비고
  FROM ext_link.ext_death_record e
  JOIN ext_link.ext_death_file   f ON f.death_file_id = e.death_file_id
  JOIN comm_code.code_detail     m ON m.group_cd='MTST' AND m.code_cd=e.match_status_cd
 GROUP BY f.base_biz_dt, m.code_nm, e.match_status_cd
 ORDER BY f.base_biz_dt, e.match_status_cd;

SELECT '########## [S9] 무결성 검증 1 - 잔액 = 거래원장 합계 ##########' AS scenario;
SELECT a.acct_no AS 계좌번호, a.balance_amt AS 원장잔액,
       COALESCE(SUM(CASE t.dr_cr_cd WHEN 'I' THEN t.txn_amt ELSE -t.txn_amt END),0) AS 거래합계,
       CASE WHEN a.balance_amt =
            COALESCE(SUM(CASE t.dr_cr_cd WHEN 'I' THEN t.txn_amt ELSE -t.txn_amt END),0)
            THEN 'OK' ELSE '★불일치' END AS 결과
  FROM acct a LEFT JOIN acct_txn t ON t.acct_no = a.acct_no
 GROUP BY a.acct_no, a.balance_amt ORDER BY a.acct_no;

SELECT '########## [S10] 무결성 검증 2 - 이력 현재행은 고객당 1건 ##########' AS scenario;
SELECT h.cust_no AS 고객번호, COUNT(*) AS 전체이력,
       SUM(CASE WHEN h.valid_to_dtm='9999-12-31 23:59:59.999' THEN 1 ELSE 0 END) AS 현재행,
       CASE WHEN SUM(CASE WHEN h.valid_to_dtm='9999-12-31 23:59:59.999' THEN 1 ELSE 0 END)=1
            THEN 'OK' ELSE '★오류' END AS 결과
  FROM cust_status_hist h GROUP BY h.cust_no ORDER BY h.cust_no;

SELECT '########## [S11] 무결성 검증 3 - 이력 구간 연속성 ##########' AS scenario;
SELECT cust_no AS 고객번호, valid_from_dtm AS 시작, valid_to_dtm AS 종료,
       LAG(valid_to_dtm) OVER (PARTITION BY cust_no ORDER BY valid_from_dtm) AS 이전종료,
       CASE WHEN LAG(valid_to_dtm) OVER (PARTITION BY cust_no ORDER BY valid_from_dtm) IS NULL
              OR LAG(valid_to_dtm) OVER (PARTITION BY cust_no ORDER BY valid_from_dtm) = valid_from_dtm
            THEN 'OK' ELSE '★단절/중복' END AS 결과
  FROM cust_status_hist ORDER BY cust_no, valid_from_dtm;

SELECT '########## [S12] 감사 - 사망정보 조회 목적별 현황 ##########' AS scenario;
SELECT p.code_nm AS 조회목적, at.code_nm AS 주체구분, COUNT(*) AS 조회건수
  FROM info_mart.audit_death_access a
  JOIN comm_code.code_detail p  ON p.group_cd='PURP'     AND p.code_cd=a.purpose_cd
  JOIN comm_code.code_detail at ON at.group_cd='ACTOR_TP' AND at.code_cd=a.actor_type_cd
 GROUP BY p.code_nm, at.code_nm, a.purpose_cd, a.actor_type_cd
 ORDER BY a.purpose_cd;

SELECT '########## [S13] 현재 차단 중인 고객 ##########' AS scenario;
SELECT c.cust_no AS 고객번호, c.cust_nm AS 성명, b.block_start_dtm AS 차단개시,
       COUNT(a.acct_no) AS 보유계좌, COALESCE(SUM(a.balance_amt),0) AS 차단잔액
  FROM death_block b
  JOIN cust c ON c.cust_no=b.cust_no
  LEFT JOIN acct a ON a.cust_no=c.cust_no
 WHERE b.block_status_cd='20'
 GROUP BY c.cust_no, c.cust_nm, b.block_start_dtm;

SELECT '########## [S14] 개인정보 - 실명확인번호 평문 미저장 확인 ##########' AS scenario;
SELECT cust_no AS 고객번호, cust_nm AS 성명,
       CONCAT(LEFT(rrn_hash,12),'...') AS 해시_앞12자,
       CHAR_LENGTH(rrn_hash) AS 해시길이,
       CASE WHEN rrn_hash REGEXP '^[0-9a-f]{64}$' THEN 'OK' ELSE '★형식오류' END AS 형식검증,
       COALESCE(close_dt,'-') AS 상거래종료일
  FROM cust ORDER BY cust_no;
