package kr.doolee.dab.service;

import java.sql.Connection;
import java.time.LocalDateTime;

import kr.doolee.dab.domain.BlockRule;
import kr.doolee.dab.domain.BlockStatus;
import kr.doolee.dab.domain.DeathBlock;
import kr.doolee.dab.domain.Decision;
import kr.doolee.dab.domain.DrCr;
import kr.doolee.dab.domain.TxnType;
import kr.doolee.dab.repository.BlockRuleRepository;
import kr.doolee.dab.repository.DeathBlockRepository;

/**
 * 차단 판정 — 이 프로젝트의 <b>핵심 비즈니스 로직</b>이다.
 *
 * <p><b>service 패키지가 무엇인가.</b> 업무 규칙이 사는 곳이다.
 * 은행으로 치면 이체·출금·조회 같은 "은행이 실제로 하는 일"을 담는다.
 * 화면(Main)도 아니고 저장소(repository)도 아닌, 그 사이에서 판단하는 계층이다.
 * 소스에서 가장 중요한 부분이고, 그래서 가장 테스트하기 쉬워야 한다.
 *
 * <p><b>설계 원칙: "규칙은 테이블에, 조합과 분기는 서비스 계층에"</b>
 * <table border="1">
 *   <caption>책임 분담</caption>
 *   <tr><th>DB 가 책임지는 것</th><th>이 클래스가 책임지는 것</th></tr>
 *   <tr><td>차단개시일시 = 신고일 + 1일 (CHECK 제약)</td>
 *       <td>지금 시각이 그 값을 넘었는지 <b>비교</b></td></tr>
 *   <tr><td>거래유형별 허용/차단 ({@code block_rule} 행)</td>
 *       <td>어떤 순서로 무엇을 먼저 볼지 <b>판정 흐름</b></td></tr>
 * </table>
 *
 * <p><b>판정 순서가 곧 업무 규칙이다.</b> 순서를 바꾸면 결과가 달라진다.
 * <ol>
 *   <li>차단 정보가 있는가</li>
 *   <li>상태가 '차단중'인가</li>
 *   <li>차단개시일시를 지났는가</li>
 *   <li>이 거래유형이 규칙상 허용인가</li>
 * </ol>
 *
 * @author Doo-Lee
 * @version 1.1
 */
public class BlockDecisionService {

    private final DeathBlockRepository deathBlockRepository;
    private final BlockRuleRepository  blockRuleRepository;

    /**
     * <b>생성자 주입(constructor injection).</b>
     * 서비스가 저장소를 스스로 {@code new} 하지 않고 밖에서 받는다.
     *
     * <p>그 덕분에 테스트할 때 가짜 저장소를 끼워 DB 없이 판정 로직만 검증할 수 있다.
     * <pre>
     * var svc = new BlockDecisionService(
     *         (conn, custNo) -&gt; 차단정보,      // 가짜 구현
     *         (conn, t, d, dt) -&gt; 규칙);
     * </pre>
     * 만약 생성자 안에서 {@code new JdbcDeathBlockRepository()} 를 했다면
     * 이 테스트를 쓰려면 MySQL 을 먼저 띄워야 한다.
     *
     * @param deathBlockRepository 사망차단 저장소
     * @param blockRuleRepository  차단규칙 저장소
     */
    public BlockDecisionService(DeathBlockRepository deathBlockRepository,
                                BlockRuleRepository blockRuleRepository) {
        this.deathBlockRepository = deathBlockRepository;
        this.blockRuleRepository  = blockRuleRepository;
    }

    /**
     * 거래 한 건을 판정한다.
     *
     * @param conn   트랜잭션 커넥션 (호출자가 열어 둔 것을 그대로 쓴다)
     * @param custNo 고객번호
     * @param type   거래유형
     * @param at     거래일시. 차단개시일시와 비교할 기준이 된다
     * @return 판정 결과와 그 근거
     * @throws kr.doolee.dab.support.DataAccessException 조회 중 DB 오류가 난 경우
     */
    public Decision judge(Connection conn, String custNo, TxnType type, LocalDateTime at) {

        /* 1) 차단 정보가 아예 없다 → 평범한 고객 */
        DeathBlock block = deathBlockRepository.findByCustNo(conn, custNo);
        if (block == null) {
            return Decision.allow("허용 (차단 정보 없음)");
        }

        /* 2) 상태가 '차단중'이 아니다 (차단예정 / 해제)
         *    해제된 행을 지우지 않고 남겨 두기 때문에, 상태를 꼭 확인해야 한다. */
        if (block.status() != BlockStatus.BLOCKED) {
            return Decision.allow("허용 (차단상태 아님: " + block.status().label() + ")");
        }

        /* 2-1) 차단개시일시 이전의 거래 → 아직 효력이 없다
         *      경계값: 신고일 23:59:59.999 는 허용, 다음날 00:00:00.000 은 차단
         *      신고 당일은 유족이 장례비를 쓸 수 있어야 하므로 즉시 막지 않는다. */
        if (!block.isEffectiveAt(at)) {
            return Decision.allow("허용 (차단개시 전, 개시=" + block.blockStartDtm() + ")");
        }

        /* 3) 규칙 조회. 입금인지 출금인지는 거래유형 enum 이 스스로 안다. */
        DrCr drCr = type.drCr();
        BlockRule rule = blockRuleRepository.findEffective(conn, type, drCr, at.toLocalDate());

        if (rule == null) {
            /*
             * 규칙 행이 없을 때의 기본값은 '정책 결정'이다.
             * 입금은 막을 이유가 없으므로 허용하고, 출금은 안전측으로 차단한다.
             * "규칙이 없으면 통과"로 두면, 코드값이 하나 빠졌을 때
             * 사망자 계좌에서 돈이 나간다. 모르면 막는 쪽이 맞다.
             */
            if (drCr == DrCr.I) {
                return Decision.allow("허용 (입금 - 해당 규칙 없음)");
            }
            return Decision.blockWithoutRule("D001",
                    "차단 (출금 규칙 미정의 - 안전측 기본값)", block.blockStartDtm());
        }

        if (rule.isAllow()) {
            return Decision.allowByRule(rule, block.blockStartDtm());
        }
        return Decision.block(rule, block.blockStartDtm());
    }
}
