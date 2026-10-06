package kr.doolee.dab.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * core_bank.death_block 한 행.
 *
 * block_start_dtm 은 DB 의 CHECK 제약
 *   CHECK (block_start_dtm = TIMESTAMP(DATE_ADD(death_report_dt, INTERVAL 1 DAY)))
 * 이 이미 보장한다. 자바에서 다시 계산하지 않고 읽어 쓰기만 한다.
 * ("규칙은 테이블에, 조합과 분기는 서비스 계층에")
 */
public final class DeathBlock {

    private final long          blockId;
    private final String        custNo;
    private final LocalDate     deathDt;
    private final LocalDate     deathReportDt;
    private final LocalDateTime blockStartDtm;
    private final BlockStatus   status;

    public DeathBlock(long blockId, String custNo, LocalDate deathDt,
                      LocalDate deathReportDt, LocalDateTime blockStartDtm,
                      BlockStatus status) {
        this.blockId       = blockId;
        this.custNo        = custNo;
        this.deathDt       = deathDt;
        this.deathReportDt = deathReportDt;
        this.blockStartDtm = blockStartDtm;
        this.status        = status;
    }

    public long          blockId()       { return blockId; }
    public String        custNo()        { return custNo; }
    public LocalDate     deathDt()       { return deathDt; }
    public LocalDate     deathReportDt() { return deathReportDt; }
    public LocalDateTime blockStartDtm() { return blockStartDtm; }
    public BlockStatus   status()        { return status; }

    /** 이 시각에 차단 효력이 실제로 살아 있는가. 두 조건을 객체가 직접 안다. */
    public boolean isEffectiveAt(LocalDateTime at) {
        return status == BlockStatus.BLOCKED && !at.isBefore(blockStartDtm);
    }
}
