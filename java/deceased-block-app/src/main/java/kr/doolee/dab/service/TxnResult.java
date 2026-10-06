package kr.doolee.dab.service;

import java.math.BigDecimal;

import kr.doolee.dab.domain.Decision;

/**
 * 거래 처리 결과. 성공이든 거절이든 이 객체로 돌려준다.
 *
 * <p><b>왜 예외만으로 끝내지 않는가.</b> 거절도 "정상적인 업무 결과"다.
 * 채널계에 응답코드를 남겨야 하고 단말에 안내 문구를 내려 줘야 한다.
 * 거절을 예외로만 던지면 호출자가 try-catch 안에서 그 뒤처리를 해야 하는데,
 * 그러면 정상 흐름과 거절 흐름이 코드상으로 뒤섞인다.
 * 그래서 서비스 안에서는 예외로 던지되, <b>밖으로는 결과 객체로 바꿔</b> 내보낸다.
 *
 * @author Doo-Lee
 * @version 1.1
 */
public final class TxnResult {

    private final boolean    success;
    private final Decision   decision;
    private final String     rspCd;
    private final String     txnUniqueNo;     // 성사된 경우에만
    private final BigDecimal balanceAfter;    // 성사된 경우에만
    private final String     message;

    private TxnResult(boolean success, Decision decision, String rspCd,
                      String txnUniqueNo, BigDecimal balanceAfter, String message) {
        this.success      = success;
        this.decision     = decision;
        this.rspCd        = rspCd;
        this.txnUniqueNo  = txnUniqueNo;
        this.balanceAfter = balanceAfter;
        this.message      = message;
    }

    /**
     * 성사된 거래.
     *
     * @param d            판정 결과
     * @param txnUniqueNo  거래고유번호
     * @param balanceAfter 거래후잔액
     * @return 성공 결과
     */
    public static TxnResult ok(Decision d, String txnUniqueNo, BigDecimal balanceAfter) {
        return new TxnResult(true, d, "0000", txnUniqueNo, balanceAfter, "정상 처리되었습니다");
    }

    /**
     * 거절된 거래.
     *
     * @param d       판정 결과 (차단이 아닌 사유여도 근거를 남긴다)
     * @param rspCd   응답코드
     * @param message 거절 사유
     * @return 실패 결과
     */
    public static TxnResult fail(Decision d, String rspCd, String message) {
        return new TxnResult(false, d, rspCd, null, null, message);
    }

    /** @return 성사되었는지 */
    public boolean isSuccess() { return success; }
    /** @return 판정 결과 */
    public Decision decision() { return decision; }
    /** @return 응답코드 */
    public String rspCd() { return rspCd; }
    /** @return 거래고유번호. 거절이면 null */
    public String txnUniqueNo() { return txnUniqueNo; }
    /** @return 거래후잔액. 거절이면 null */
    public BigDecimal balanceAfter() { return balanceAfter; }
    /** @return 사람이 읽을 메시지 */
    public String message() { return message; }

    @Override
    public String toString() {
        if (success) {
            return String.format("[성공] %s / 거래번호 %s / 거래후잔액 %,.0f원",
                    rspCd, txnUniqueNo, balanceAfter);
        }
        return String.format("[거절] %s / %s", rspCd, message);
    }
}
