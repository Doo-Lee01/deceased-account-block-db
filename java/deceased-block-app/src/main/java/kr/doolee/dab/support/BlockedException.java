package kr.doolee.dab.support;

import kr.doolee.dab.domain.Decision;

/**
 * 사망 차단 규칙에 걸려 거절된 경우.
 *
 * <p><b>사용자 정의 예외(custom exception)</b>를 왜 따로 만드는가.
 * {@code BusinessException("D001", "차단")} 으로도 거절은 전달된다.
 * 하지만 그러면 <b>판정 근거</b>(어떤 규칙 몇 번이 적용됐는지, 차단개시일시가 언제인지)가
 * 문자열 속으로 사라진다. 채널계 {@code chnl_block_decision} 에는
 * {@code applied_rule_id} 와 {@code block_start_dtm} 을 남겨야 하므로,
 * 예외가 {@link Decision} 객체를 통째로 들고 다니게 만든다.
 *
 * <p>예외는 "오류를 알리는 수단"이자 "값을 위로 전달하는 수단"이기도 하다.
 * 다만 흐름 제어용으로 남발하면 읽기 어려워지므로,
 * <b>정상 흐름에서 벗어난 경우에만</b> 쓴다.
 *
 * @author Doo-Lee
 * @version 1.0
 * @see BusinessException 부모 클래스
 */
public class BlockedException extends BusinessException {

    private static final long serialVersionUID = 1L;

    private final transient Decision decision;

    /**
     * @param d 차단으로 끝난 판정 결과. 응답코드와 사유를 여기서 꺼내 부모에 넘긴다
     */
    public BlockedException(Decision d) {
        super(d.rspCd(), d.reason());
        this.decision = d;
    }

    /** @return 판정 결과 전체 (적용규칙 ID, 차단개시일시 포함) */
    public Decision decision() { return decision; }
}
