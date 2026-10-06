package kr.doolee.dab.domain;

/**
 * core_bank.block_rule 한 행.
 *
 * "입금 등 필수 거래를 제외한 모든 거래를 차단" 이라는 정책을
 * if 문이 아니라 테이블 행으로 표현한 것이다. 정책이 바뀌면
 * 자바 코드를 고쳐 재배포하는 대신 apply_to_dt 를 닫고 새 행을 넣는다.
 */
public final class BlockRule {

    private final long   ruleId;
    private final String txnTypeCd;
    private final String drCrCd;
    private final boolean allow;
    private final boolean exceptionPossible;
    private final String rejectRspCd;   // allow=true 이면 DB CHECK 에 의해 반드시 null

    public BlockRule(long ruleId, String txnTypeCd, String drCrCd,
                     boolean allow, boolean exceptionPossible, String rejectRspCd) {
        this.ruleId            = ruleId;
        this.txnTypeCd         = txnTypeCd;
        this.drCrCd            = drCrCd;
        this.allow             = allow;
        this.exceptionPossible = exceptionPossible;
        this.rejectRspCd       = rejectRspCd;
    }

    public long    ruleId()            { return ruleId; }
    public String  txnTypeCd()         { return txnTypeCd; }
    public String  drCrCd()            { return drCrCd; }
    public boolean isAllow()           { return allow; }
    public boolean isExceptionPossible(){ return exceptionPossible; }
    public String  rejectRspCd()       { return rejectRspCd; }
}
