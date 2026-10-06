package kr.doolee.dab.repository;

import java.sql.Connection;

import kr.doolee.dab.domain.DeathBlock;
import kr.doolee.dab.support.DataAccessException;

/**
 * 사망차단 저장소.
 *
 * @author Doo-Lee
 * @version 1.1
 */
public interface DeathBlockRepository {

    /**
     * 고객의 차단 정보를 조회한다.
     *
     * <p>{@code death_block} 에는 고객 1명당 1행만 존재한다
     * ({@code UNIQUE (cust_no)}). 그래서 단건 조회가 보장된다.
     *
     * @param conn   트랜잭션 커넥션
     * @param custNo 고객번호
     * @return 차단 정보. <b>{@code null} 이면 차단 대상이 아니라는 뜻</b>이다
     * @throws DataAccessException 조회 중 DB 오류가 난 경우
     */
    DeathBlock findByCustNo(Connection conn, String custNo);
}
