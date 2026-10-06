package kr.doolee.dab.repository;

import java.math.BigDecimal;
import java.sql.Connection;

import kr.doolee.dab.domain.Account;
import kr.doolee.dab.support.DataAccessException;

/**
 * 계좌 저장소 — <b>데이터를 꺼내고 넣는 방법</b>만 정의하는 계약(인터페이스)이다.
 *
 * <p><b>repository 패키지가 무엇인가.</b> 데이터를 보관하고 꺼내는 일만 담당하는 계층이다.
 * 업무 판단은 하지 않는다. "사망자면 막는다" 같은 결정은 service 패키지가 하고,
 * 여기서는 "계좌 한 건을 읽어 온다"까지만 한다.
 * 실무에서는 같은 역할을 <b>DAO(Data Access Object)</b> 라고도 부른다. 이름만 다르다.
 *
 * <p><b>왜 인터페이스와 구현을 나누는가.</b>
 * <ul>
 *   <li>service 는 이 인터페이스만 보므로, MySQL 을 PostgreSQL 로 바꿀 때
 *       {@code PostgresAccountRepository} 를 새로 만들어 끼우면 service 코드는 그대로다</li>
 *   <li>테스트할 때 DB 없이 돌 수 있다. 메모리에 값을 들고 있는 가짜 구현
 *       ({@code FakeAccountRepository})을 만들어 넣으면 판정 로직만 따로 검증할 수 있다</li>
 * </ul>
 *
 * <p><b>모든 메서드가 {@link Connection} 을 받는 이유.</b>
 * "잔액 조회 → 판정 → 잔액 수정 → 원장 적재"가 하나의 트랜잭션이어야 한다.
 * 저장소가 각자 커넥션을 새로 열면 서로 다른 트랜잭션이 되어
 * {@code FOR UPDATE} 로 잡은 락이 아무 의미가 없어진다.
 *
 * @author Doo-Lee
 * @version 1.1
 */
public interface AccountRepository {

    /**
     * 계좌를 조회한다. <b>락을 걸지 않는다.</b>
     *
     * <p>MVCC 덕분에 다른 트랜잭션이 같은 행을 수정 중이어도 기다리지 않고
     * 그 시점의 스냅샷을 바로 읽는다. 잔액을 바꿀 생각이 없다면 이 메서드를 쓴다.
     * 그래야 조회하는 사람이 출금하는 사람을 기다리게 만들지 않는다.
     *
     * @param conn   트랜잭션 커넥션
     * @param acctNo 계좌번호 (14자리)
     * @return 계좌. 없으면 {@code null}
     * @throws DataAccessException 조회 중 DB 오류가 난 경우
     */
    Account findByNo(Connection conn, String acctNo);

    /**
     * 계좌를 조회하면서 그 행에 <b>배타 락</b>을 건다 ({@code SELECT ... FOR UPDATE}).
     *
     * <p>잔액처럼 <b>읽고 계산해서 다시 쓰는</b> 값은 반드시 이 메서드로 읽어야 한다.
     * 그냥 {@code findByNo} 로 읽고 수정하면 두 세션이 같은 잔액을 읽어
     * 나중에 쓴 값이 먼저 쓴 값을 덮는 <b>갱신 손실</b>이 생긴다.
     * 더 나쁜 점은 오류가 전혀 나지 않는다는 것이다.
     *
     * @param conn   트랜잭션 커넥션
     * @param acctNo 계좌번호
     * @return 계좌. 없으면 {@code null}
     * @throws DataAccessException 조회 중 DB 오류가 난 경우
     */
    Account findByNoForUpdate(Connection conn, String acctNo);

    /**
     * 잔액을 새 값으로 바꾼다. 반드시 {@link #findByNoForUpdate} 이후에 호출한다.
     *
     * @param conn       트랜잭션 커넥션
     * @param acctNo     계좌번호
     * @param newBalance 새 잔액. 음수면 DB 의 {@code ck_acct_balance} CHECK 제약이 거부한다
     * @throws DataAccessException 수정 대상이 1건이 아니거나 DB 오류가 난 경우
     */
    void updateBalance(Connection conn, String acctNo, BigDecimal newBalance);
}
