package kr.doolee.dab.repository;

import java.sql.Connection;
import java.time.LocalDate;

import kr.doolee.dab.domain.BlockRule;
import kr.doolee.dab.domain.DrCr;
import kr.doolee.dab.domain.TxnType;
import kr.doolee.dab.support.DataAccessException;

/**
 * 차단규칙 저장소.
 *
 * <p>규칙은 {@code apply_from_dt} ~ {@code apply_to_dt} 로 시점 버전 관리가 되어 있다.
 * 정책이 바뀌면 옛 행의 {@code apply_to_dt} 를 닫고 새 행을 넣기 때문에,
 * 과거 거래를 다시 판정해도 <b>그때의 규칙</b>으로 판정된다.
 *
 * @author Doo-Lee
 * @version 1.1
 */
public interface BlockRuleRepository {

    /**
     * 기준일자에 유효한 규칙 1건을 조회한다.
     *
     * @param conn   트랜잭션 커넥션
     * @param type   거래유형
     * @param drCr   입출금 구분
     * @param baseDt 기준일자 (보통 거래일시의 날짜 부분)
     * @return 규칙. 해당 조합의 규칙이 정의되어 있지 않으면 {@code null}
     * @throws DataAccessException 조회 중 DB 오류가 난 경우
     */
    BlockRule findEffective(Connection conn, TxnType type, DrCr drCr, LocalDate baseDt);
}
