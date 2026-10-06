package kr.doolee.dab.domain;

import java.time.LocalDateTime;

/**
 * 차단 판정 결과. channel.chnl_block_decision 에 그대로 적재된다.
 *
 * [OOP 포인트] 판정 결과를 boolean 하나로 돌려주면
 * "왜 허용/차단됐는지" 를 호출자가 알 수 없다.
 * 결과 + 근거(사유, 적용규칙, 응답코드)를 한 객체로 묶어 돌려준다.
 */
public final class Decision {

    /** comm_code group_cd='DEC_CD' */
    public enum Result {
        ALLOW       ("10", "허용"),
        BLOCK       ("20", "차단"),
        EXC_ALLOW   ("30", "예외허용");

        private final String code;
        private final String label;
        Result(String code, String label) { this.code = code; this.label = label; }
        public String code()  { return code; }
        public String label() { return label; }
    }

    private final Result        result;
    private final String        rspCd;          // comm_code.rsp_code
    private final String        reason;         // 사람이 읽는 사유
    private final Long          appliedRuleId;  // 근거 규칙 (없으면 null)
    private final LocalDateTime blockStartDtm;  // 근거 차단개시일시 (없으면 null)

    private Decision(Result result, String rspCd, String reason,
                     Long appliedRuleId, LocalDateTime blockStartDtm) {
        this.result        = result;
        this.rspCd         = rspCd;
        this.reason        = reason;
        this.appliedRuleId = appliedRuleId;
        this.blockStartDtm = blockStartDtm;
    }

    /* 정적 팩토리 메서드: 생성자를 4개 만드는 대신 이름으로 의도를 드러낸다. */

    public static Decision allow(String reason) {
        return new Decision(Result.ALLOW, "0000", reason, null, null);
    }

    public static Decision allowByRule(BlockRule rule, LocalDateTime blockStartDtm) {
        return new Decision(Result.ALLOW, "0000",
                "허용 (규칙상 필수거래)", rule.ruleId(), blockStartDtm);
    }

    public static Decision block(BlockRule rule, LocalDateTime blockStartDtm) {
        return new Decision(Result.BLOCK, rule.rejectRspCd(),
                "차단 (" + rule.rejectRspCd() + ")", rule.ruleId(), blockStartDtm);
    }

    /** 규칙 행이 없는데도 차단해야 하는 경우(안전측 기본값). */
    public static Decision blockWithoutRule(String rspCd, String reason,
                                            LocalDateTime blockStartDtm) {
        return new Decision(Result.BLOCK, rspCd, reason, null, blockStartDtm);
    }

    public Result        result()        { return result; }
    public String        rspCd()         { return rspCd; }
    public String        reason()        { return reason; }
    public Long          appliedRuleId() { return appliedRuleId; }
    public LocalDateTime blockStartDtm() { return blockStartDtm; }

    public boolean isBlocked() { return result == Result.BLOCK; }

    @Override
    public String toString() {
        return result.label() + " / 응답코드 " + rspCd + " / " + reason;
    }
}
