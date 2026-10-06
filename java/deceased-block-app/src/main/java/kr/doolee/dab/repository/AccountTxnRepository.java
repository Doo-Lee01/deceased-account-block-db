package kr.doolee.dab.repository;

import java.sql.Connection;

import kr.doolee.dab.domain.AccountTxn;
import kr.doolee.dab.support.DataAccessException;

/**
 * 거래원장 저장소. 거래가 성사된 것만 적재한다.
 *
 * @author Doo-Lee
 * @version 1.1
 */
public interface AccountTxnRepository {

    /**
     * 거래 한 건을 원장에 적재한다.
     *
     * @param conn 트랜잭션 커넥션
     * @param txn  적재할 거래
     * @return AUTO_INCREMENT 로 생성된 {@code txn_id}
     * @throws DataAccessException 거래고유번호 중복(1062), 계좌 없음(1452),
     *                             금액 CHECK 위반(3819) 등
     */
    long insert(Connection conn, AccountTxn txn);
}
