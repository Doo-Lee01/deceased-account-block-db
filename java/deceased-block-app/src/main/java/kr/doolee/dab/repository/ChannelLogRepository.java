package kr.doolee.dab.repository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDateTime;

import kr.doolee.dab.domain.Decision;
import kr.doolee.dab.domain.TxnType;
import kr.doolee.dab.support.DataAccessException;

/**
 * 채널계 저장소. 요청 → 판정 → 응답이 각각 다른 테이블에 남는 설계를 그대로 옮긴다.
 *
 * <p>세 테이블로 나눈 이유는, 요청은 들어왔지만 판정 전에 장애가 난 경우와
 * 판정은 했지만 응답을 못 보낸 경우를 구분해야 하기 때문이다.
 * 한 테이블에 다 넣으면 어디까지 진행됐는지 알 수 없다.
 *
 * @author Doo-Lee
 * @version 1.1
 */
public interface ChannelLogRepository {

    /**
     * 단말이 보낸 거래요청을 적재한다.
     *
     * @param conn            트랜잭션 커넥션
     * @param requestUniqueNo 요청고유번호 (UNIQUE)
     * @param chnlCd          채널코드 (10 창구 / 20 ATM / 30 인터넷뱅킹 / 40 모바일)
     * @param custNo          고객번호. 모를 수 있으므로 {@code null} 허용
     * @param acctNo          계좌번호
     * @param type            거래유형
     * @param amt             요청금액. 조회성 거래는 {@code null}
     * @param requestDtm      요청일시
     * @return 생성된 {@code request_id}
     * @throws DataAccessException 적재 실패
     */
    long insertRequest(Connection conn, String requestUniqueNo, String chnlCd,
                       String custNo, String acctNo, TxnType type,
                       BigDecimal amt, LocalDateTime requestDtm);

    /**
     * 판정 결과와 그 근거를 적재한다.
     *
     * @param conn        트랜잭션 커넥션
     * @param requestId   요청ID
     * @param decision    판정 결과 (적용규칙 ID, 차단개시일시 포함)
     * @param decisionDtm 판정일시
     * @param txnUniqueNo 성사된 경우의 거래고유번호. 거절이면 {@code null}
     * @throws DataAccessException 적재 실패
     */
    void insertDecision(Connection conn, long requestId, Decision decision,
                        LocalDateTime decisionDtm, String txnUniqueNo);

    /**
     * 단말에 보낸 응답을 적재한다.
     *
     * <p>응답 문구를 자바 문자열로 들고 있지 않고 {@code comm_code.rsp_code} 에서
     * 가져와 넣는다. 그래야 문구 원본이 코드 테이블 한 곳에만 있게 된다.
     *
     * @param conn        트랜잭션 커넥션
     * @param requestId   요청ID
     * @param rspCd       응답코드
     * @param responseDtm 응답일시
     * @param elapsedMs   소요시간(ms)
     * @throws DataAccessException 적재 실패
     */
    void insertResponse(Connection conn, long requestId, String rspCd,
                        LocalDateTime responseDtm, int elapsedMs);
}
