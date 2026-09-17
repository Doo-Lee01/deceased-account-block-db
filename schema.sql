-- =====================================================================
-- schema.sql
-- 사망자 명의 금융거래 신속차단 시스템 - DDL 스크립트
--
-- 스키마 5개 / 테이블 30개 / PK 30 · UNIQUE 17 · FK 67 · CHECK 41 · 인덱스 15
-- 검증 환경 : MySQL 8.0.46 / InnoDB / utf8mb4
--
-- 실행 순서 : schema.sql → seed_data.sql → validation_queries.sql
-- 재실행 가능 : 첫 부분에서 기존 스키마를 모두 DROP 후 재생성합니다.
--
-- [MySQL Workbench]  File > Open SQL Script > 번개(Execute)
-- [Command Line]     SOURCE C:/경로/schema.sql   (역슬래시가 아니라 슬래시)
-- =====================================================================

SET NAMES utf8mb4;
SET character_set_client = utf8mb4;
SET character_set_connection = utf8mb4;
SET character_set_results = utf8mb4;


-- ## 01_create_schema.sql
-- =====================================================================
-- 01. 스키마 생성 (은행 시스템 계층 = MySQL 스키마)
-- =====================================================================
DROP DATABASE IF EXISTS info_mart;
DROP DATABASE IF EXISTS channel;
DROP DATABASE IF EXISTS core_bank;
DROP DATABASE IF EXISTS ext_link;
DROP DATABASE IF EXISTS comm_code;

CREATE DATABASE comm_code DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE ext_link  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE core_bank DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE channel   DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE info_mart DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- ## 02_comm_code_ddl.sql
-- =====================================================================
-- 02. 공통 스키마 DDL
-- =====================================================================
USE comm_code;

CREATE TABLE code_group (
  group_cd    CHAR(10)     NOT NULL                COMMENT '코드그룹',
  group_nm    VARCHAR(60)  NOT NULL                COMMENT '그룹명',
  group_desc  VARCHAR(200) NULL                    COMMENT '설명',
  use_yn      CHAR(1)      NOT NULL DEFAULT 'Y'    COMMENT '사용여부',
  reg_dtm     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  reg_user_id VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM',
  upd_dtm     DATETIME(3)  NULL,
  upd_user_id VARCHAR(20)  NULL,
  PRIMARY KEY (group_cd),
  CONSTRAINT ck_cgrp_use CHECK (use_yn IN ('Y','N'))
) ENGINE=InnoDB COMMENT='공통코드 그룹';

CREATE TABLE code_detail (
  group_cd    CHAR(10)     NOT NULL                COMMENT '코드그룹',
  code_cd     CHAR(4)      NOT NULL                COMMENT '코드값',
  code_nm     VARCHAR(60)  NOT NULL                COMMENT '코드명',
  sort_seq    INT          NOT NULL DEFAULT 1      COMMENT '정렬순서',
  use_yn      CHAR(1)      NOT NULL DEFAULT 'Y'    COMMENT '사용여부',
  reg_dtm     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  reg_user_id VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM',
  upd_dtm     DATETIME(3)  NULL,
  upd_user_id VARCHAR(20)  NULL,
  PRIMARY KEY (group_cd, code_cd),
  CONSTRAINT fk_cdtl_group FOREIGN KEY (group_cd) REFERENCES code_group (group_cd),
  CONSTRAINT ck_cdtl_use CHECK (use_yn IN ('Y','N'))
) ENGINE=InnoDB COMMENT='공통코드 상세';

CREATE TABLE rsp_code (
  rsp_cd         CHAR(4)      NOT NULL             COMMENT '응답코드',
  rsp_message    VARCHAR(200) NOT NULL             COMMENT '응답메시지',
  openbanking_cd CHAR(3)      NULL                 COMMENT '오픈뱅킹 대응코드',
  success_yn     CHAR(1)      NOT NULL             COMMENT '정상처리 여부',
  reg_dtm        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  reg_user_id    VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM',
  upd_dtm        DATETIME(3)  NULL,
  upd_user_id    VARCHAR(20)  NULL,
  PRIMARY KEY (rsp_cd),
  CONSTRAINT ck_rsp_success CHECK (success_yn IN ('Y','N'))
) ENGINE=InnoDB COMMENT='응답코드';

CREATE TABLE org_code (
  org_cd      CHAR(3)     NOT NULL                 COMMENT '금융기관코드',
  org_nm      VARCHAR(60) NOT NULL                 COMMENT '기관명',
  use_yn      CHAR(1)     NOT NULL DEFAULT 'Y'     COMMENT '사용여부',
  reg_dtm     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  reg_user_id VARCHAR(20) NOT NULL DEFAULT 'SYSTEM',
  upd_dtm     DATETIME(3) NULL,
  upd_user_id VARCHAR(20) NULL,
  PRIMARY KEY (org_cd),
  CONSTRAINT ck_org_use CHECK (use_yn IN ('Y','N')),
  CONSTRAINT ck_org_cd  CHECK (org_cd REGEXP '^[0-9]{3}$')
) ENGINE=InnoDB COMMENT='금융기관 코드';

CREATE TABLE biz_calendar (
  base_biz_dt CHAR(8)     NOT NULL                 COMMENT '기준일자 YYYYMMDD',
  biz_day_yn  CHAR(1)     NOT NULL                 COMMENT '영업일 여부',
  holiday_nm  VARCHAR(60) NULL                     COMMENT '공휴일명',
  reg_dtm     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  reg_user_id VARCHAR(20) NOT NULL DEFAULT 'SYSTEM',
  upd_dtm     DATETIME(3) NULL,
  upd_user_id VARCHAR(20) NULL,
  PRIMARY KEY (base_biz_dt),
  CONSTRAINT ck_cal_biz CHECK (biz_day_yn IN ('Y','N')),
  CONSTRAINT ck_cal_dt  CHECK (base_biz_dt REGEXP '^[0-9]{8}$')
) ENGINE=InnoDB COMMENT='영업일 달력';

-- ## 03_comm_code_data.sql
-- =====================================================================
-- 03. 공통코드 데이터
-- =====================================================================
USE comm_code;

INSERT INTO code_group (group_cd, group_nm) VALUES
 ('CUST_ST','고객상태'), ('ACCT_ST','계좌상태'), ('PROD','상품구분'),
 ('TXN_TP','거래유형'), ('CHNL','채널구분'), ('BLK_ST','차단상태'),
 ('BLK_ACT','차단조치구분'), ('REL_RSN','차단해제사유'), ('CHG_RSN','상태변경사유'),
 ('MTST','매칭상태'), ('FILE_ST','파일처리상태'), ('EXC_ST','예외인출상태'),
 ('EXC_RSN','예외인출사유'), ('DOC_TP','증빙서류구분'), ('VRF_RES','서류검증결과'),
 ('APV_RES','승인결과'), ('PAYEE_TP','수취처구분'), ('PAYEE_ST','수취처상태'),
 ('REL_TP','신청인관계'), ('AT_ST','자동이체상태'), ('AT_RES','자동이체결과'),
 ('DEC_CD','차단판정결과'), ('ACTOR_TP','조회주체구분'), ('PURP','조회목적');

INSERT INTO code_detail (group_cd, code_cd, code_nm, sort_seq) VALUES
 ('CUST_ST','10','정상',1), ('CUST_ST','20','사망',2), ('CUST_ST','30','휴면',3),
 ('CUST_ST','40','거래제한',4),
 ('ACCT_ST','10','정상',1), ('ACCT_ST','20','거래중지',2), ('ACCT_ST','30','해지',3),
 ('PROD','1010','입출금이자유로운예금',1),
 ('TXN_TP','DEP','입금',1), ('TXN_TP','WDR','출금',2), ('TXN_TP','TRO','이체출금',3),
 ('TXN_TP','TRI','이체입금',4), ('TXN_TP','ATO','자동이체출금',5),
 ('TXN_TP','CLS','계좌해지',6), ('TXN_TP','LON','대출실행',7), ('TXN_TP','INQ','조회',8),
 ('CHNL','10','창구',1), ('CHNL','20','ATM',2), ('CHNL','30','인터넷뱅킹',3),
 ('CHNL','40','모바일',4), ('CHNL','50','오픈뱅킹',5), ('CHNL','60','배치',6),
 ('BLK_ST','10','차단예정',1), ('BLK_ST','20','차단중',2), ('BLK_ST','30','해제',3),
 ('BLK_ACT','10','설정',1), ('BLK_ACT','20','해제',2), ('BLK_ACT','30','재설정',3),
 ('REL_RSN','10','동명이인 오매칭',1), ('REL_RSN','20','사망신고 취소',2),
 ('REL_RSN','30','정보 정정',3), ('REL_RSN','90','기타',4),
 ('CHG_RSN','10','신규등록',1), ('CHG_RSN','20','사망정보 수신',2),
 ('CHG_RSN','30','차단해제',3), ('CHG_RSN','90','기타',4),
 ('MTST','10','미처리',1), ('MTST','20','매칭성공',2), ('MTST','30','미매칭',3),
 ('MTST','40','다건매칭',4),
 ('FILE_ST','10','수신완료',1), ('FILE_ST','20','처리중',2), ('FILE_ST','30','처리완료',3),
 ('FILE_ST','90','오류',4),
 ('EXC_ST','REQ','접수',1), ('EXC_ST','DOC','서류검토',2), ('EXC_ST','APV','승인',3),
 ('EXC_ST','REJ','반려',4), ('EXC_ST','PAY','이체실행',5), ('EXC_ST','CMP','완료',6),
 ('EXC_RSN','10','치료비',1), ('EXC_RSN','20','장례비',2), ('EXC_RSN','30','기타',3),
 ('DOC_TP','10','가족관계증명서',1), ('DOC_TP','20','사망진단서',2),
 ('DOC_TP','30','진료비영수증',3), ('DOC_TP','40','장례비견적서',4),
 ('VRF_RES','10','적합',1), ('VRF_RES','20','부적합',2), ('VRF_RES','30','보완요청',3),
 ('APV_RES','10','승인',1), ('APV_RES','20','반려',2),
 ('PAYEE_TP','10','병원',1), ('PAYEE_TP','20','요양원',2), ('PAYEE_TP','30','장례식장',3),
 ('PAYEE_ST','10','정상',1), ('PAYEE_ST','20','거래중지',2),
 ('REL_TP','10','배우자',1), ('REL_TP','20','직계비속',2), ('REL_TP','30','직계존속',3),
 ('REL_TP','40','형제자매',4), ('REL_TP','90','기타',5),
 ('AT_ST','10','정상',1), ('AT_ST','20','일시중지',2), ('AT_ST','30','해지',3),
 ('AT_RES','10','성공',1), ('AT_RES','20','잔액부족',2), ('AT_RES','30','차단실패',3),
 ('DEC_CD','10','허용',1), ('DEC_CD','20','차단',2), ('DEC_CD','30','예외허용',3),
 ('ACTOR_TP','10','직원',1), ('ACTOR_TP','20','배치',2), ('ACTOR_TP','30','API',3),
 ('PURP','10','거래 차단판정',1), ('PURP','20','예외인출 심사',2),
 ('PURP','30','감독기관 제출',3), ('PURP','90','기타',4);

INSERT INTO rsp_code (rsp_cd, rsp_message, openbanking_cd, success_yn) VALUES
 ('0000','정상 처리되었습니다','000','Y'),
 ('D001','사망자 명의 계좌로 출금 거래가 제한됩니다',NULL,'N'),
 ('D002','사망자 명의 계좌의 자동이체가 중단되었습니다',NULL,'N'),
 ('D003','사망자 명의 계좌는 해지할 수 없습니다',NULL,'N'),
 ('D004','사망자 명의로 신규 여신을 실행할 수 없습니다',NULL,'N'),
 ('E010','예외 인출 승인 거래입니다','000','Y'),
 ('E020','등록되지 않은 수취처입니다',NULL,'N'),
 ('E021','증빙서류가 미비합니다',NULL,'N'),
 ('E022','신청금액이 잔액을 초과합니다',NULL,'N'),
 ('E030','잔액이 부족합니다',NULL,'N'),
 ('X999','시스템 오류가 발생했습니다',NULL,'N');

INSERT INTO org_code (org_cd, org_nm) VALUES
 ('020','우리은행'), ('004','KB국민은행'), ('088','신한은행'), ('081','하나은행'),
 ('011','NH농협은행'), ('090','카카오뱅크'), ('092','토스뱅크');

INSERT INTO biz_calendar (base_biz_dt, biz_day_yn, holiday_nm) VALUES
 ('20260914','Y',NULL), ('20260915','Y',NULL), ('20260916','Y',NULL),
 ('20260917','Y',NULL), ('20260918','Y',NULL),
 ('20260919','N','토요일'), ('20260920','N','일요일'), ('20260921','Y',NULL);

-- ## 04_core_bank_ddl.sql
-- =====================================================================
-- 04. 계정계 DDL (core_bank)
-- =====================================================================
USE core_bank;

CREATE TABLE cust (
  cust_no         VARCHAR(12)  NOT NULL             COMMENT '고객번호',
  cust_nm         VARCHAR(60)  NOT NULL             COMMENT '성명',
  rrn_hash        CHAR(64)     NOT NULL             COMMENT '실명확인번호 해시(검색용)',
  rrn_enc         VARBINARY(128) NULL                COMMENT '실명확인번호 암호문(저장용, 개인정보보호법 제24조의2)',
  birth_dt        DATE         NOT NULL             COMMENT '생년월일',
  cust_status_cd  CHAR(4)      NOT NULL             COMMENT '고객상태',
  cust_status_grp CHAR(10) GENERATED ALWAYS AS ('CUST_ST') STORED,
  open_dt         DATE         NOT NULL             COMMENT '최초거래일',
  close_dt        DATE         NULL                 COMMENT '상거래관계 종료일(신용정보법 제20조의2 기산점)',
  reg_dtm         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  reg_user_id     VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM',
  upd_dtm         DATETIME(3)  NULL,
  upd_user_id     VARCHAR(20)  NULL,
  PRIMARY KEY (cust_no),
  UNIQUE KEY uk_cust_rrn (rrn_hash),
  CONSTRAINT fk_cust_status FOREIGN KEY (cust_status_grp, cust_status_cd)
    REFERENCES comm_code.code_detail (group_cd, code_cd),
  CONSTRAINT ck_cust_rrn CHECK (rrn_hash REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB COMMENT='고객';

CREATE TABLE cust_status_hist (
  hist_id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '이력ID',
  cust_no           VARCHAR(12)  NOT NULL           COMMENT '고객번호',
  before_status_cd  CHAR(4)      NULL               COMMENT '변경전 상태',
  before_status_grp CHAR(10) GENERATED ALWAYS AS ('CUST_ST') STORED,
  after_status_cd   CHAR(4)      NOT NULL           COMMENT '변경후 상태',
  after_status_grp  CHAR(10) GENERATED ALWAYS AS ('CUST_ST') STORED,
  change_reason_cd  CHAR(4)      NOT NULL           COMMENT '변경사유',
  change_reason_grp CHAR(10) GENERATED ALWAYS AS ('CHG_RSN') STORED,
  valid_from_dtm    DATETIME(3)  NOT NULL           COMMENT '유효시작',
  valid_to_dtm      DATETIME(3)  NOT NULL DEFAULT '9999-12-31 23:59:59.999' COMMENT '유효종료',
  reg_dtm           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  reg_user_id       VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM',
  PRIMARY KEY (hist_id),
  UNIQUE KEY uk_cust_hist_current (cust_no, valid_to_dtm),
  CONSTRAINT fk_chist_cust   FOREIGN KEY (cust_no) REFERENCES cust (cust_no),
  CONSTRAINT fk_chist_before FOREIGN KEY (before_status_grp, before_status_cd)
    REFERENCES comm_code.code_detail (group_cd, code_cd),
  CONSTRAINT fk_chist_after  FOREIGN KEY (after_status_grp, after_status_cd)
    REFERENCES comm_code.code_detail (group_cd, code_cd),
  CONSTRAINT fk_chist_reason FOREIGN KEY (change_reason_grp, change_reason_cd)
    REFERENCES comm_code.code_detail (group_cd, code_cd),
  CONSTRAINT ck_chist_period CHECK (valid_to_dtm > valid_from_dtm)
) ENGINE=InnoDB COMMENT='고객상태 이력';

CREATE TABLE acct (
  acct_no         VARCHAR(14)   NOT NULL            COMMENT '계좌번호',
  cust_no         VARCHAR(12)   NOT NULL            COMMENT '고객번호',
  product_cd      CHAR(4)       NOT NULL            COMMENT '상품코드',
  product_grp     CHAR(10) GENERATED ALWAYS AS ('PROD') STORED,
  balance_amt     DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '잔액',
  acct_status_cd  CHAR(4)       NOT NULL            COMMENT '계좌상태',
  acct_status_grp CHAR(10) GENERATED ALWAYS AS ('ACCT_ST') STORED,
  open_dt         DATE          NOT NULL            COMMENT '개설일',
  close_dt        DATE          NULL                COMMENT '해지일',
  reg_dtm         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  reg_user_id     VARCHAR(20)   NOT NULL DEFAULT 'SYSTEM',
  upd_dtm         DATETIME(3)   NULL,
  upd_user_id     VARCHAR(20)   NULL,
  PRIMARY KEY (acct_no),
  CONSTRAINT fk_acct_cust    FOREIGN KEY (cust_no) REFERENCES cust (cust_no),
  CONSTRAINT fk_acct_product FOREIGN KEY (product_grp, product_cd)
    REFERENCES comm_code.code_detail (group_cd, code_cd),
  CONSTRAINT fk_acct_status  FOREIGN KEY (acct_status_grp, acct_status_cd)
    REFERENCES comm_code.code_detail (group_cd, code_cd),
  CONSTRAINT ck_acct_balance CHECK (balance_amt >= 0),
  CONSTRAINT ck_acct_close   CHECK (close_dt IS NULL OR close_dt >= open_dt)
) ENGINE=InnoDB COMMENT='계좌';

CREATE TABLE acct_status_hist (
  hist_id           BIGINT      NOT NULL AUTO_INCREMENT,
  acct_no           VARCHAR(14) NOT NULL            COMMENT '계좌번호',
  before_status_cd  CHAR(4)     NULL                COMMENT '변경전 상태',
  before_status_grp CHAR(10) GENERATED ALWAYS AS ('ACCT_ST') STORED,
  after_status_cd   CHAR(4)     NOT NULL            COMMENT '변경후 상태',
  after_status_grp  CHAR(10) GENERATED ALWAYS AS ('ACCT_ST') STORED,
  valid_from_dtm    DATETIME(3) NOT NULL,
  valid_to_dtm      DATETIME(3) NOT NULL DEFAULT '9999-12-31 23:59:59.999',
  reg_dtm           DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  reg_user_id       VARCHAR(20) NOT NULL DEFAULT 'SYSTEM',
  PRIMARY KEY (hist_id),
  UNIQUE KEY uk_acct_hist_current (acct_no, valid_to_dtm),
  CONSTRAINT fk_ahist_acct   FOREIGN KEY (acct_no) REFERENCES acct (acct_no),
  CONSTRAINT fk_ahist_before FOREIGN KEY (before_status_grp, before_status_cd)
    REFERENCES comm_code.code_detail (group_cd, code_cd),
  CONSTRAINT fk_ahist_after  FOREIGN KEY (after_status_grp, after_status_cd)
    REFERENCES comm_code.code_detail (group_cd, code_cd),
  CONSTRAINT ck_ahist_period CHECK (valid_to_dtm > valid_from_dtm)
) ENGINE=InnoDB COMMENT='계좌상태 이력';

CREATE TABLE acct_txn (
  txn_id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '거래ID',
  txn_unique_no     VARCHAR(30)   NOT NULL          COMMENT '거래고유번호',
  acct_no           VARCHAR(14)   NOT NULL          COMMENT '계좌번호',
  txn_dtm           DATETIME(3)   NOT NULL          COMMENT '거래일시',
  txn_type_cd       CHAR(4)       NOT NULL          COMMENT '거래유형',
  txn_type_grp      CHAR(10) GENERATED ALWAYS AS ('TXN_TP') STORED,
  dr_cr_cd          CHAR(1)       NOT NULL          COMMENT 'I=입금 O=출금',
  txn_amt           DECIMAL(18,2) NOT NULL          COMMENT '거래금액(항상 양수)',
  balance_after_amt DECIMAL(18,2) NOT NULL          COMMENT '거래후잔액',
  chnl_cd           CHAR(4)       NOT NULL          COMMENT '채널',
  chnl_grp          CHAR(10) GENERATED ALWAYS AS ('CHNL') STORED,
  terminal_id       VARCHAR(20)   NULL              COMMENT '단말 식별값(전자금융거래법 제22조)',
  counter_org_cd    CHAR(3)       NULL              COMMENT '상대 기관코드',
  counter_acct_no   VARCHAR(14)   NULL              COMMENT '상대 계좌번호',
  rsp_cd            CHAR(4)       NOT NULL          COMMENT '응답코드',
  memo              VARCHAR(500)  NULL              COMMENT '적요',
  reg_dtm           DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  reg_user_id       VARCHAR(20)   NOT NULL DEFAULT 'SYSTEM',
  PRIMARY KEY (txn_id),
  UNIQUE KEY uk_txn_unique (txn_unique_no),
  CONSTRAINT fk_txn_acct    FOREIGN KEY (acct_no) REFERENCES acct (acct_no),
  CONSTRAINT fk_txn_type    FOREIGN KEY (txn_type_grp, txn_type_cd)
    REFERENCES comm_code.code_detail (group_cd, code_cd),
  CONSTRAINT fk_txn_chnl    FOREIGN KEY (chnl_grp, chnl_cd)
    REFERENCES comm_code.code_detail (group_cd, code_cd),
  CONSTRAINT fk_txn_org     FOREIGN KEY (counter_org_cd) REFERENCES comm_code.org_code (org_cd),
  CONSTRAINT fk_txn_rsp     FOREIGN KEY (rsp_cd) REFERENCES comm_code.rsp_code (rsp_cd),
  CONSTRAINT ck_txn_amt     CHECK (txn_amt > 0),
  CONSTRAINT ck_txn_bal     CHECK (balance_after_amt >= 0),
  CONSTRAINT ck_txn_drcr    CHECK (dr_cr_cd IN ('I','O'))
) ENGINE=InnoDB COMMENT='거래원장';

CREATE TABLE death_block (
  block_id           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '차단ID',
  cust_no            VARCHAR(12) NOT NULL          COMMENT '고객번호',
  source_record_id   BIGINT      NOT NULL          COMMENT '근거 원천레코드ID(논리참조)',
  death_dt           DATE        NOT NULL          COMMENT '사망일자',
  death_report_dt    DATE        NOT NULL          COMMENT '사망신고일자',
  block_start_dtm    DATETIME(3) NOT NULL          COMMENT '차단개시일시 = 신고일+1일 00:00',
  block_status_cd    CHAR(4)     NOT NULL          COMMENT '차단상태',
  block_status_grp   CHAR(10) GENERATED ALWAYS AS ('BLK_ST') STORED,
  release_dtm        DATETIME(3) NULL              COMMENT '해제일시',
  release_reason_cd  CHAR(4)     NULL              COMMENT '해제사유',
  release_reason_grp CHAR(10) GENERATED ALWAYS AS ('REL_RSN') STORED,
  reg_dtm            DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  reg_user_id        VARCHAR(20) NOT NULL DEFAULT 'SYSTEM',
  upd_dtm            DATETIME(3) NULL,
  upd_user_id        VARCHAR(20) NULL,
  PRIMARY KEY (block_id),
  UNIQUE KEY uk_block_cust (cust_no),
  CONSTRAINT fk_block_cust    FOREIGN KEY (cust_no) REFERENCES cust (cust_no),
  CONSTRAINT fk_block_status  FOREIGN KEY (block_status_grp, block_status_cd)
    REFERENCES comm_code.code_detail (group_cd, code_cd),
  CONSTRAINT fk_block_release FOREIGN KEY (release_reason_grp, release_reason_cd)
    REFERENCES comm_code.code_detail (group_cd, code_cd),
  CONSTRAINT ck_block_report CHECK (death_report_dt >= death_dt),
  CONSTRAINT ck_block_start  CHECK (block_start_dtm = TIMESTAMP(DATE_ADD(death_report_dt, INTERVAL 1 DAY))),
  CONSTRAINT ck_block_rel    CHECK ((release_dtm IS NULL AND release_reason_cd IS NULL)
                                 OR (release_dtm IS NOT NULL AND release_reason_cd IS NOT NULL))
) ENGINE=InnoDB COMMENT='사망차단';

CREATE TABLE death_block_hist (
  hist_id     BIGINT      NOT NULL AUTO_INCREMENT,
  block_id    BIGINT      NOT NULL                 COMMENT '차단ID',
  action_cd   CHAR(4)     NOT NULL                 COMMENT '조치구분',
  action_grp  CHAR(10) GENERATED ALWAYS AS ('BLK_ACT') STORED,
  reason_cd   CHAR(4)     NULL                     COMMENT '사유',
  reason_grp  CHAR(10) GENERATED ALWAYS AS ('REL_RSN') STORED,
  action_dtm  DATETIME(3) NOT NULL                 COMMENT '조치일시',
  actor_id    VARCHAR(20) NOT NULL                 COMMENT '조치자',
  reg_dtm     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  reg_user_id VARCHAR(20) NOT NULL DEFAULT 'SYSTEM',
  PRIMARY KEY (hist_id),
  CONSTRAINT fk_bhist_block  FOREIGN KEY (block_id) REFERENCES death_block (block_id),
  CONSTRAINT fk_bhist_action FOREIGN KEY (action_grp, action_cd)
    REFERENCES comm_code.code_detail (group_cd, code_cd),
  CONSTRAINT fk_bhist_reason FOREIGN KEY (reason_grp, reason_cd)
    REFERENCES comm_code.code_detail (group_cd, code_cd)
) ENGINE=InnoDB COMMENT='사망차단 이력';

CREATE TABLE block_rule (
  rule_id               BIGINT  NOT NULL AUTO_INCREMENT COMMENT '규칙ID',
  txn_type_cd           CHAR(4) NOT NULL              COMMENT '거래유형',
  txn_type_grp          CHAR(10) GENERATED ALWAYS AS ('TXN_TP') STORED,
  dr_cr_cd              CHAR(1) NOT NULL              COMMENT 'I=입금 O=출금',
  allow_yn              CHAR(1) NOT NULL              COMMENT '차단중 허용여부',
  exception_possible_yn CHAR(1) NOT NULL              COMMENT '예외인출 가능여부',
  reject_rsp_cd         CHAR(4) NULL                  COMMENT '거절 응답코드',
  apply_from_dt         DATE    NOT NULL              COMMENT '적용시작일',
  apply_to_dt           DATE    NOT NULL DEFAULT '9999-12-31' COMMENT '적용종료일',
  reg_dtm               DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  reg_user_id           VARCHAR(20) NOT NULL DEFAULT 'SYSTEM',
  PRIMARY KEY (rule_id),
  UNIQUE KEY uk_rule_current (txn_type_cd, dr_cr_cd, apply_to_dt),
  CONSTRAINT fk_rule_txntype FOREIGN KEY (txn_type_grp, txn_type_cd)
    REFERENCES comm_code.code_detail (group_cd, code_cd),
  CONSTRAINT fk_rule_rsp FOREIGN KEY (reject_rsp_cd) REFERENCES comm_code.rsp_code (rsp_cd),
  CONSTRAINT ck_rule_drcr    CHECK (dr_cr_cd IN ('I','O')),
  CONSTRAINT ck_rule_allow   CHECK (allow_yn IN ('Y','N')),
  CONSTRAINT ck_rule_exc     CHECK (exception_possible_yn IN ('Y','N')),
  CONSTRAINT ck_rule_period  CHECK (apply_to_dt >= apply_from_dt),
  CONSTRAINT ck_rule_rsp_req CHECK ((allow_yn = 'Y' AND reject_rsp_cd IS NULL)
                                 OR (allow_yn = 'N' AND reject_rsp_cd IS NOT NULL))
) ENGINE=InnoDB COMMENT='차단규칙';

CREATE TABLE auto_transfer (
  auto_transfer_id BIGINT        NOT NULL AUTO_INCREMENT,
  acct_no          VARCHAR(14)   NOT NULL           COMMENT '출금계좌',
  payee_org_cd     CHAR(3)       NOT NULL           COMMENT '수납기관',
  payee_acct_no    VARCHAR(14)   NOT NULL           COMMENT '수납계좌',
  transfer_day     INT           NOT NULL           COMMENT '이체일(1~31)',
  transfer_amt     DECIMAL(18,2) NOT NULL           COMMENT '이체금액',
  status_cd        CHAR(4)       NOT NULL           COMMENT '약정상태',
  status_grp       CHAR(10) GENERATED ALWAYS AS ('AT_ST') STORED,
  reg_dtm          DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  reg_user_id      VARCHAR(20)   NOT NULL DEFAULT 'SYSTEM',
  upd_dtm          DATETIME(3)   NULL,
  upd_user_id      VARCHAR(20)   NULL,
  PRIMARY KEY (auto_transfer_id),
  CONSTRAINT fk_at_acct   FOREIGN KEY (acct_no) REFERENCES acct (acct_no),
  CONSTRAINT fk_at_org    FOREIGN KEY (payee_org_cd) REFERENCES comm_code.org_code (org_cd),
  CONSTRAINT fk_at_status FOREIGN KEY (status_grp, status_cd)
    REFERENCES comm_code.code_detail (group_cd, code_cd),
  CONSTRAINT ck_at_day    CHECK (transfer_day BETWEEN 1 AND 31),
  CONSTRAINT ck_at_amt    CHECK (transfer_amt > 0)
) ENGINE=InnoDB COMMENT='자동이체 약정';

CREATE TABLE auto_transfer_result (
  result_id        BIGINT      NOT NULL AUTO_INCREMENT,
  auto_transfer_id BIGINT      NOT NULL             COMMENT '약정ID',
  process_dt       DATE        NOT NULL             COMMENT '처리일자',
  result_cd        CHAR(4)     NOT NULL             COMMENT '처리결과',
  result_grp       CHAR(10) GENERATED ALWAYS AS ('AT_RES') STORED,
  fail_rsp_cd      CHAR(4)     NULL                 COMMENT '실패 응답코드',
  txn_unique_no    VARCHAR(30) NULL                 COMMENT '성공시 생성된 거래고유번호',
  reg_dtm          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  reg_user_id      VARCHAR(20) NOT NULL DEFAULT 'SYSTEM',
  PRIMARY KEY (result_id),
  UNIQUE KEY uk_atres (auto_transfer_id, process_dt),
  CONSTRAINT fk_atres_at   FOREIGN KEY (auto_transfer_id) REFERENCES auto_transfer (auto_transfer_id),
  CONSTRAINT fk_atres_code FOREIGN KEY (result_grp, result_cd)
    REFERENCES comm_code.code_detail (group_cd, code_cd),
  CONSTRAINT fk_atres_rsp  FOREIGN KEY (fail_rsp_cd) REFERENCES comm_code.rsp_code (rsp_cd)
) ENGINE=InnoDB COMMENT='자동이체 처리결과';

CREATE TABLE payee_registry (
  payee_id      BIGINT      NOT NULL AUTO_INCREMENT COMMENT '수취처ID',
  payee_type_cd CHAR(4)     NOT NULL                COMMENT '수취처구분',
  payee_type_grp CHAR(10) GENERATED ALWAYS AS ('PAYEE_TP') STORED,
  payee_nm      VARCHAR(60) NOT NULL                COMMENT '수취처명',
  biz_reg_no    VARCHAR(12) NOT NULL                COMMENT '사업자등록번호',
  org_cd        CHAR(3)     NOT NULL                COMMENT '은행코드',
  acct_no       VARCHAR(14) NOT NULL                COMMENT '수취 계좌번호',
  status_cd     CHAR(4)     NOT NULL                COMMENT '등록상태',
  status_grp    CHAR(10) GENERATED ALWAYS AS ('PAYEE_ST') STORED,
  reg_dt        DATE        NOT NULL                COMMENT '등록일',
  reg_dtm       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  reg_user_id   VARCHAR(20) NOT NULL DEFAULT 'SYSTEM',
  upd_dtm       DATETIME(3) NULL,
  upd_user_id   VARCHAR(20) NULL,
  PRIMARY KEY (payee_id),
  UNIQUE KEY uk_payee_biz (biz_reg_no),
  CONSTRAINT fk_payee_type   FOREIGN KEY (payee_type_grp, payee_type_cd)
    REFERENCES comm_code.code_detail (group_cd, code_cd),
  CONSTRAINT fk_payee_status FOREIGN KEY (status_grp, status_cd)
    REFERENCES comm_code.code_detail (group_cd, code_cd),
  CONSTRAINT fk_payee_org    FOREIGN KEY (org_cd) REFERENCES comm_code.org_code (org_cd),
  CONSTRAINT ck_payee_biz    CHECK (biz_reg_no REGEXP '^[0-9]{10}$')
) ENGINE=InnoDB COMMENT='지정 수취처';

CREATE TABLE exc_request (
  exc_request_id     BIGINT        NOT NULL AUTO_INCREMENT,
  cust_no            VARCHAR(12)   NOT NULL         COMMENT '사망 고객',
  acct_no            VARCHAR(14)   NOT NULL         COMMENT '출금계좌',
  payee_id           BIGINT        NOT NULL         COMMENT '지정 수취처',
  applicant_nm       VARCHAR(60)   NOT NULL         COMMENT '신청인',
  applicant_rel_cd   CHAR(4)       NOT NULL         COMMENT '신청인 관계',
  applicant_rel_grp  CHAR(10) GENERATED ALWAYS AS ('REL_TP') STORED,
  exc_reason_cd      CHAR(4)       NOT NULL         COMMENT '신청사유',
  exc_reason_grp     CHAR(10) GENERATED ALWAYS AS ('EXC_RSN') STORED,
  request_amt        DECIMAL(18,2) NOT NULL         COMMENT '신청금액',
  exc_status_cd      CHAR(4)       NOT NULL         COMMENT '처리상태',
  exc_status_grp     CHAR(10) GENERATED ALWAYS AS ('EXC_ST') STORED,
  request_dtm        DATETIME(3)   NOT NULL         COMMENT '신청일시',
  reject_reason_desc VARCHAR(500)  NULL             COMMENT '반려사유',
  reg_dtm            DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  reg_user_id        VARCHAR(20)   NOT NULL DEFAULT 'SYSTEM',
  upd_dtm            DATETIME(3)   NULL,
  upd_user_id        VARCHAR(20)   NULL,
  PRIMARY KEY (exc_request_id),
  CONSTRAINT fk_exreq_cust   FOREIGN KEY (cust_no)  REFERENCES cust (cust_no),
  CONSTRAINT fk_exreq_acct   FOREIGN KEY (acct_no)  REFERENCES acct (acct_no),
  CONSTRAINT fk_exreq_payee  FOREIGN KEY (payee_id) REFERENCES payee_registry (payee_id),
  CONSTRAINT fk_exreq_rel    FOREIGN KEY (applicant_rel_grp, applicant_rel_cd)
    REFERENCES comm_code.code_detail (group_cd, code_cd),
  CONSTRAINT fk_exreq_reason FOREIGN KEY (exc_reason_grp, exc_reason_cd)
    REFERENCES comm_code.code_detail (group_cd, code_cd),
  CONSTRAINT fk_exreq_status FOREIGN KEY (exc_status_grp, exc_status_cd)
    REFERENCES comm_code.code_detail (group_cd, code_cd),
  CONSTRAINT ck_exreq_amt    CHECK (request_amt > 0),
  CONSTRAINT ck_exreq_reject CHECK (exc_status_cd <> 'REJ' OR reject_reason_desc IS NOT NULL)
) ENGINE=InnoDB COMMENT='예외인출 신청';

CREATE TABLE exc_document (
  document_id      BIGINT       NOT NULL AUTO_INCREMENT,
  exc_request_id   BIGINT       NOT NULL            COMMENT '신청ID',
  doc_type_cd      CHAR(4)      NOT NULL            COMMENT '서류구분',
  doc_type_grp     CHAR(10) GENERATED ALWAYS AS ('DOC_TP') STORED,
  receive_dtm      DATETIME(3)  NOT NULL            COMMENT '접수일시',
  verify_result_cd CHAR(4)      NOT NULL            COMMENT '검증결과',
  verify_grp       CHAR(10) GENERATED ALWAYS AS ('VRF_RES') STORED,
  file_ref         VARCHAR(200) NULL                COMMENT '파일 경로',
  reg_dtm          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  reg_user_id      VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM',
  PRIMARY KEY (document_id),
  UNIQUE KEY uk_exdoc (exc_request_id, doc_type_cd),
  CONSTRAINT fk_exdoc_req    FOREIGN KEY (exc_request_id) REFERENCES exc_request (exc_request_id),
  CONSTRAINT fk_exdoc_type   FOREIGN KEY (doc_type_grp, doc_type_cd)
    REFERENCES comm_code.code_detail (group_cd, code_cd),
  CONSTRAINT fk_exdoc_verify FOREIGN KEY (verify_grp, verify_result_cd)
    REFERENCES comm_code.code_detail (group_cd, code_cd)
) ENGINE=InnoDB COMMENT='예외인출 증빙서류';

CREATE TABLE exc_approval (
  approval_id        BIGINT       NOT NULL AUTO_INCREMENT,
  exc_request_id     BIGINT       NOT NULL          COMMENT '신청ID',
  approval_seq       INT          NOT NULL          COMMENT '승인차수',
  approver_id        VARCHAR(20)  NOT NULL          COMMENT '승인자',
  approval_dtm       DATETIME(3)  NOT NULL          COMMENT '승인일시',
  approval_result_cd CHAR(4)      NOT NULL          COMMENT '승인결과',
  approval_grp       CHAR(10) GENERATED ALWAYS AS ('APV_RES') STORED,
  opinion_desc       VARCHAR(500) NULL              COMMENT '의견',
  reg_dtm            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  reg_user_id        VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM',
  PRIMARY KEY (approval_id),
  UNIQUE KEY uk_exapv (exc_request_id, approval_seq),
  CONSTRAINT fk_exapv_req    FOREIGN KEY (exc_request_id) REFERENCES exc_request (exc_request_id),
  CONSTRAINT fk_exapv_result FOREIGN KEY (approval_grp, approval_result_cd)
    REFERENCES comm_code.code_detail (group_cd, code_cd),
  CONSTRAINT ck_exapv_seq    CHECK (approval_seq BETWEEN 1 AND 5)
) ENGINE=InnoDB COMMENT='예외인출 승인';

CREATE TABLE exc_payout (
  payout_id      BIGINT        NOT NULL AUTO_INCREMENT,
  exc_request_id BIGINT        NOT NULL             COMMENT '신청ID',
  payee_id       BIGINT        NOT NULL             COMMENT '실제 수취처',
  payout_amt     DECIMAL(18,2) NOT NULL             COMMENT '지급금액',
  txn_unique_no  VARCHAR(30)   NOT NULL             COMMENT '생성된 거래고유번호',
  payout_dtm     DATETIME(3)   NOT NULL             COMMENT '실행일시',
  rsp_cd         CHAR(4)       NOT NULL             COMMENT '처리결과',
  reg_dtm        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  reg_user_id    VARCHAR(20)   NOT NULL DEFAULT 'SYSTEM',
  PRIMARY KEY (payout_id),
  UNIQUE KEY uk_expay_req (exc_request_id),
  UNIQUE KEY uk_expay_txn (txn_unique_no),
  CONSTRAINT fk_expay_req   FOREIGN KEY (exc_request_id) REFERENCES exc_request (exc_request_id),
  CONSTRAINT fk_expay_payee FOREIGN KEY (payee_id) REFERENCES payee_registry (payee_id),
  CONSTRAINT fk_expay_txn   FOREIGN KEY (txn_unique_no) REFERENCES acct_txn (txn_unique_no),
  CONSTRAINT fk_expay_rsp   FOREIGN KEY (rsp_cd) REFERENCES comm_code.rsp_code (rsp_cd),
  CONSTRAINT ck_expay_amt   CHECK (payout_amt > 0)
) ENGINE=InnoDB COMMENT='예외인출 지급';

-- ## 05_ext_link_ddl.sql
-- =====================================================================
-- 05. 대외계 DDL (ext_link)
-- 계층 경계를 넘는 관계(고객·차단)에는 FK 를 걸지 않는다.
-- =====================================================================
USE ext_link;

CREATE TABLE ext_death_file (
  death_file_id     BIGINT      NOT NULL AUTO_INCREMENT COMMENT '수신파일ID',
  base_biz_dt       CHAR(8)     NOT NULL           COMMENT '기준일자 YYYYMMDD',
  total_cnt         INT         NOT NULL           COMMENT '총 건수',
  receive_dtm       DATETIME(3) NOT NULL           COMMENT '수신일시',
  process_status_cd CHAR(4)     NOT NULL           COMMENT '처리상태',
  process_grp       CHAR(10) GENERATED ALWAYS AS ('FILE_ST') STORED,
  error_desc        VARCHAR(500) NULL              COMMENT '오류내용',
  reg_dtm           DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  reg_user_id       VARCHAR(20) NOT NULL DEFAULT 'SYSTEM',
  upd_dtm           DATETIME(3) NULL,
  upd_user_id       VARCHAR(20) NULL,
  PRIMARY KEY (death_file_id),
  UNIQUE KEY uk_efile_bizdt (base_biz_dt),
  CONSTRAINT fk_efile_status FOREIGN KEY (process_grp, process_status_cd)
    REFERENCES comm_code.code_detail (group_cd, code_cd),
  CONSTRAINT ck_efile_cnt  CHECK (total_cnt >= 0),
  CONSTRAINT ck_efile_dt   CHECK (base_biz_dt REGEXP '^[0-9]{8}$')
) ENGINE=InnoDB COMMENT='사망자정보 수신파일';

CREATE TABLE ext_death_record (
  death_record_id  BIGINT       NOT NULL AUTO_INCREMENT,
  death_file_id    BIGINT       NOT NULL           COMMENT '수신파일ID',
  record_seq       INT          NOT NULL           COMMENT '파일내 순번',
  cust_nm          VARCHAR(60)  NOT NULL           COMMENT '성명(원천)',
  rrn_hash         CHAR(64)     NOT NULL           COMMENT '실명확인번호 해시',
  death_dt         DATE         NOT NULL           COMMENT '사망일자',
  death_report_dt  DATE         NOT NULL           COMMENT '사망신고일자',
  report_org_nm    VARCHAR(60)  NULL               COMMENT '신고 접수기관',
  match_status_cd  CHAR(4)      NOT NULL DEFAULT '10' COMMENT '매칭상태',
  match_grp        CHAR(10) GENERATED ALWAYS AS ('MTST') STORED,
  matched_cust_no  VARCHAR(12)  NULL               COMMENT '매칭 고객번호(논리참조)',
  error_desc       VARCHAR(500) NULL               COMMENT '오류내용',
  reg_dtm          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  reg_user_id      VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM',
  upd_dtm          DATETIME(3)  NULL,
  upd_user_id      VARCHAR(20)  NULL,
  PRIMARY KEY (death_record_id),
  UNIQUE KEY uk_erec_seq (death_file_id, record_seq),
  CONSTRAINT fk_erec_file  FOREIGN KEY (death_file_id) REFERENCES ext_death_file (death_file_id),
  CONSTRAINT fk_erec_match FOREIGN KEY (match_grp, match_status_cd)
    REFERENCES comm_code.code_detail (group_cd, code_cd),
  CONSTRAINT ck_erec_report CHECK (death_report_dt >= death_dt),
  CONSTRAINT ck_erec_hash   CHECK (rrn_hash REGEXP '^[0-9a-f]{64}$'),
  CONSTRAINT ck_erec_match  CHECK ((match_status_cd = '20' AND matched_cust_no IS NOT NULL)
                                OR (match_status_cd <> '20' AND matched_cust_no IS NULL))
) ENGINE=InnoDB COMMENT='사망자 원천레코드';

CREATE TABLE ext_inquiry_log (
  inquiry_id     BIGINT      NOT NULL AUTO_INCREMENT,
  target_cust_no VARCHAR(12) NOT NULL              COMMENT '조회대상 고객번호(논리참조)',
  request_dtm    DATETIME(3) NOT NULL              COMMENT '요청일시',
  response_cd    CHAR(4)     NOT NULL              COMMENT '응답코드',
  death_found_yn CHAR(1)     NOT NULL              COMMENT '사망정보 존재여부',
  elapsed_ms     INT         NOT NULL              COMMENT '소요시간(ms)',
  reg_dtm        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  reg_user_id    VARCHAR(20) NOT NULL DEFAULT 'SYSTEM',
  PRIMARY KEY (inquiry_id),
  CONSTRAINT fk_einq_rsp FOREIGN KEY (response_cd) REFERENCES comm_code.rsp_code (rsp_cd),
  CONSTRAINT ck_einq_found CHECK (death_found_yn IN ('Y','N')),
  CONSTRAINT ck_einq_ms    CHECK (elapsed_ms >= 0)
) ENGINE=InnoDB COMMENT='실시간 조회로그';

-- ## 06_channel_ddl.sql
-- =====================================================================
-- 06. 채널계 DDL (channel)
-- cust_no / acct_no 에 FK 를 걸지 않는다.
-- 존재하지 않는 계좌번호로 들어온 요청도 기록되어야 하기 때문.
-- =====================================================================
USE channel;

CREATE TABLE chnl_request (
  request_id        BIGINT        NOT NULL AUTO_INCREMENT,
  request_unique_no VARCHAR(30)   NOT NULL         COMMENT '요청고유번호',
  chnl_cd           CHAR(4)       NOT NULL         COMMENT '채널',
  chnl_grp          CHAR(10) GENERATED ALWAYS AS ('CHNL') STORED,
  cust_no           VARCHAR(12)   NULL             COMMENT '고객번호(논리참조)',
  acct_no           VARCHAR(14)   NOT NULL         COMMENT '계좌번호(논리참조)',
  txn_type_cd       CHAR(4)       NOT NULL         COMMENT '거래유형',
  txn_type_grp      CHAR(10) GENERATED ALWAYS AS ('TXN_TP') STORED,
  request_amt       DECIMAL(18,2) NULL             COMMENT '요청금액',
  request_dtm       DATETIME(3)   NOT NULL         COMMENT '요청일시',
  reg_dtm           DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  reg_user_id       VARCHAR(20)   NOT NULL DEFAULT 'SYSTEM',
  PRIMARY KEY (request_id),
  UNIQUE KEY uk_creq_unique (request_unique_no),
  CONSTRAINT fk_creq_chnl FOREIGN KEY (chnl_grp, chnl_cd)
    REFERENCES comm_code.code_detail (group_cd, code_cd),
  CONSTRAINT fk_creq_type FOREIGN KEY (txn_type_grp, txn_type_cd)
    REFERENCES comm_code.code_detail (group_cd, code_cd),
  CONSTRAINT ck_creq_amt CHECK (request_amt IS NULL OR request_amt > 0)
) ENGINE=InnoDB COMMENT='채널 거래요청';

CREATE TABLE chnl_response (
  response_id  BIGINT       NOT NULL AUTO_INCREMENT,
  request_id   BIGINT       NOT NULL               COMMENT '요청ID',
  rsp_cd       CHAR(4)      NOT NULL               COMMENT '응답코드',
  rsp_message  VARCHAR(200) NOT NULL               COMMENT '응답메시지',
  response_dtm DATETIME(3)  NOT NULL               COMMENT '응답일시',
  elapsed_ms   INT          NOT NULL               COMMENT '소요시간(ms)',
  reg_dtm      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  reg_user_id  VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM',
  PRIMARY KEY (response_id),
  UNIQUE KEY uk_cres_req (request_id),
  CONSTRAINT fk_cres_req FOREIGN KEY (request_id) REFERENCES chnl_request (request_id),
  CONSTRAINT fk_cres_rsp FOREIGN KEY (rsp_cd) REFERENCES comm_code.rsp_code (rsp_cd),
  CONSTRAINT ck_cres_ms  CHECK (elapsed_ms >= 0)
) ENGINE=InnoDB COMMENT='채널 거래응답';

CREATE TABLE chnl_block_decision (
  decision_id     BIGINT      NOT NULL AUTO_INCREMENT,
  request_id      BIGINT      NOT NULL             COMMENT '요청ID',
  decision_cd     CHAR(4)     NOT NULL             COMMENT '판정결과',
  decision_grp    CHAR(10) GENERATED ALWAYS AS ('DEC_CD') STORED,
  applied_rule_id BIGINT      NULL                 COMMENT '적용규칙ID(스냅샷, FK 없음)',
  block_start_dtm DATETIME(3) NULL                 COMMENT '판정 근거 차단개시일시',
  decision_dtm    DATETIME(3) NOT NULL             COMMENT '판정일시',
  txn_unique_no   VARCHAR(30) NULL                 COMMENT '성사시 거래고유번호(논리참조)',
  reg_dtm         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  reg_user_id     VARCHAR(20) NOT NULL DEFAULT 'SYSTEM',
  PRIMARY KEY (decision_id),
  UNIQUE KEY uk_cdec_req (request_id),
  CONSTRAINT fk_cdec_req  FOREIGN KEY (request_id) REFERENCES chnl_request (request_id),
  CONSTRAINT fk_cdec_code FOREIGN KEY (decision_grp, decision_cd)
    REFERENCES comm_code.code_detail (group_cd, code_cd)
) ENGINE=InnoDB COMMENT='차단 판정이력';

-- ## 07_info_mart_ddl.sql
-- =====================================================================
-- 07. 정보계 DDL (info_mart)
-- 계정계를 직접 참조하지 않는다. 개인식별정보를 집계에 보관하지 않는다.
-- =====================================================================
USE info_mart;

CREATE TABLE audit_death_access (
  access_log_id  BIGINT      NOT NULL AUTO_INCREMENT,
  access_dtm     DATETIME(3) NOT NULL              COMMENT '조회일시',
  actor_id       VARCHAR(20) NOT NULL              COMMENT '조회주체',
  actor_type_cd  CHAR(4)     NOT NULL              COMMENT '주체구분',
  actor_grp      CHAR(10) GENERATED ALWAYS AS ('ACTOR_TP') STORED,
  target_cust_no VARCHAR(12) NOT NULL              COMMENT '조회대상(논리참조)',
  purpose_cd     CHAR(4)     NOT NULL              COMMENT '조회목적',
  purpose_grp    CHAR(10) GENERATED ALWAYS AS ('PURP') STORED,
  chnl_cd        CHAR(4)     NOT NULL              COMMENT '접근경로',
  chnl_grp       CHAR(10) GENERATED ALWAYS AS ('CHNL') STORED,
  result_cd      CHAR(4)     NOT NULL              COMMENT '처리결과',
  reg_dtm        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  reg_user_id    VARCHAR(20) NOT NULL DEFAULT 'SYSTEM',
  PRIMARY KEY (access_log_id),
  CONSTRAINT fk_audit_actor   FOREIGN KEY (actor_grp, actor_type_cd)
    REFERENCES comm_code.code_detail (group_cd, code_cd),
  CONSTRAINT fk_audit_purpose FOREIGN KEY (purpose_grp, purpose_cd)
    REFERENCES comm_code.code_detail (group_cd, code_cd),
  CONSTRAINT fk_audit_chnl    FOREIGN KEY (chnl_grp, chnl_cd)
    REFERENCES comm_code.code_detail (group_cd, code_cd),
  CONSTRAINT fk_audit_result  FOREIGN KEY (result_cd) REFERENCES comm_code.rsp_code (rsp_cd)
) ENGINE=InnoDB COMMENT='사망정보 접근 감사로그';

CREATE TABLE stat_block_daily (
  base_biz_dt        CHAR(8)       NOT NULL        COMMENT '기준일자',
  chnl_cd            CHAR(4)       NOT NULL        COMMENT '채널',
  chnl_grp           CHAR(10) GENERATED ALWAYS AS ('CHNL') STORED,
  block_txn_cnt      INT           NOT NULL DEFAULT 0 COMMENT '차단 거래건수',
  block_txn_amt      DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '차단 거래금액',
  new_block_cust_cnt INT           NOT NULL DEFAULT 0 COMMENT '신규 차단 고객수',
  reg_dtm            DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  reg_user_id        VARCHAR(20)   NOT NULL DEFAULT 'SYSTEM',
  PRIMARY KEY (base_biz_dt, chnl_cd),
  CONSTRAINT fk_stblk_cal  FOREIGN KEY (base_biz_dt) REFERENCES comm_code.biz_calendar (base_biz_dt),
  CONSTRAINT fk_stblk_chnl FOREIGN KEY (chnl_grp, chnl_cd)
    REFERENCES comm_code.code_detail (group_cd, code_cd),
  CONSTRAINT ck_stblk CHECK (block_txn_cnt >= 0 AND block_txn_amt >= 0)
) ENGINE=InnoDB COMMENT='일별 차단집계';

CREATE TABLE stat_exception_daily (
  base_biz_dt CHAR(8)       NOT NULL               COMMENT '기준일자',
  request_cnt INT           NOT NULL DEFAULT 0     COMMENT '신청건수',
  approve_cnt INT           NOT NULL DEFAULT 0     COMMENT '승인건수',
  reject_cnt  INT           NOT NULL DEFAULT 0     COMMENT '반려건수',
  payout_amt  DECIMAL(18,2) NOT NULL DEFAULT 0     COMMENT '지급금액',
  reg_dtm     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  reg_user_id VARCHAR(20)   NOT NULL DEFAULT 'SYSTEM',
  PRIMARY KEY (base_biz_dt),
  CONSTRAINT fk_stexc_cal FOREIGN KEY (base_biz_dt) REFERENCES comm_code.biz_calendar (base_biz_dt),
  CONSTRAINT ck_stexc CHECK (request_cnt >= approve_cnt + reject_cnt)
) ENGINE=InnoDB COMMENT='일별 예외인출 집계';

CREATE TABLE stat_mismatch (
  base_biz_dt     CHAR(8)     NOT NULL             COMMENT '기준일자',
  unmatched_cnt   INT         NOT NULL DEFAULT 0   COMMENT '미매칭 건수',
  multi_match_cnt INT         NOT NULL DEFAULT 0   COMMENT '다건매칭 건수',
  reg_dtm         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  reg_user_id     VARCHAR(20) NOT NULL DEFAULT 'SYSTEM',
  PRIMARY KEY (base_biz_dt),
  CONSTRAINT fk_stmis_cal FOREIGN KEY (base_biz_dt) REFERENCES comm_code.biz_calendar (base_biz_dt),
  CONSTRAINT ck_stmis CHECK (unmatched_cnt >= 0 AND multi_match_cnt >= 0)
) ENGINE=InnoDB COMMENT='매칭오류 집계';

-- ## 08_index.sql
-- =====================================================================
-- 08. 인덱스
-- FK 컬럼은 MySQL 이 자동으로 인덱스를 만들므로 여기서는 제외한다.
-- =====================================================================
USE core_bank;
CREATE INDEX ix_txn_acct_dtm    ON acct_txn (acct_no, txn_dtm);
CREATE INDEX ix_txn_dtm         ON acct_txn (txn_dtm);
CREATE INDEX ix_block_start     ON death_block (block_start_dtm);
CREATE INDEX ix_block_status    ON death_block (block_status_cd);
CREATE INDEX ix_chist_current   ON cust_status_hist (cust_no, valid_from_dtm);
CREATE INDEX ix_ahist_current   ON acct_status_hist (acct_no, valid_from_dtm);
CREATE INDEX ix_exreq_status    ON exc_request (exc_status_cd, request_dtm);
CREATE INDEX ix_atres_dt        ON auto_transfer_result (process_dt);

USE ext_link;
CREATE INDEX ix_erec_hash   ON ext_death_record (rrn_hash);
CREATE INDEX ix_erec_match  ON ext_death_record (match_status_cd);
CREATE INDEX ix_erec_report ON ext_death_record (death_report_dt);
CREATE INDEX ix_einq_target ON ext_inquiry_log (target_cust_no, request_dtm);

USE channel;
CREATE INDEX ix_creq_acct ON chnl_request (acct_no, request_dtm);
CREATE INDEX ix_cdec_dtm  ON chnl_block_decision (decision_dtm);

USE info_mart;
CREATE INDEX ix_audit_target  ON audit_death_access (target_cust_no, access_dtm);
CREATE INDEX ix_audit_purpose ON audit_death_access (purpose_cd, access_dtm);

-- ## 09_block_rule_data.sql
-- =====================================================================
-- 09. 차단규칙 초기 데이터
-- "입금 등 필수 거래를 제외한 모든 거래를 차단" 을 데이터로 표현한다.
-- =====================================================================
USE core_bank;
INSERT INTO block_rule
  (txn_type_cd, dr_cr_cd, allow_yn, exception_possible_yn, reject_rsp_cd, apply_from_dt) VALUES
  ('DEP','I','Y','N',NULL  ,'2026-09-11'),
  ('TRI','I','Y','N',NULL  ,'2026-09-11'),
  ('WDR','O','N','Y','D001','2026-09-11'),
  ('TRO','O','N','Y','D001','2026-09-11'),
  ('ATO','O','N','N','D002','2026-09-11'),
  ('CLS','O','N','N','D003','2026-09-11'),
  ('LON','O','N','N','D004','2026-09-11');

SELECT 'BUILD COMPLETE' AS result;
