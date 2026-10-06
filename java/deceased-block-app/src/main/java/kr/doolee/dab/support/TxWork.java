package kr.doolee.dab.support;

import java.sql.Connection;

/**
 * 하나의 트랜잭션 안에서 실행할 작업.
 *
 * <p>추상 메서드가 딱 하나인 인터페이스(함수형 인터페이스)라서 람다식으로 넘길 수 있다.
 * <pre>
 * tx.execute(conn -&gt; {
 *     Account a = accountRepository.findByNoForUpdate(conn, acctNo);
 *     ...
 *     return result;
 * });
 * </pre>
 *
 * <p><b>왜 인터페이스로 분리하는가.</b>
 * "무엇을 할지"(이 인터페이스)와 "어떻게 트랜잭션을 관리할지"({@link TxTemplate})를
 * 따로 두면, 트랜잭션 처리 방식을 바꿀 때 업무 코드를 건드리지 않는다.
 * 스프링의 {@code @Transactional} 이 하는 일을 손으로 만들어 본 것이다.
 *
 * <p>{@code throws Exception} 으로 넓게 열어 둔 이유는, 작업 안에서
 * 체크 예외가 나든 언체크 예외가 나든 {@link TxTemplate} 이 받아서
 * 롤백까지 책임지게 하려는 것이다.
 *
 * @param <T> 작업이 돌려주는 값의 타입
 * @author Doo-Lee
 * @version 1.0
 */
@FunctionalInterface
public interface TxWork<T> {

    /**
     * @param conn 트랜잭션이 시작된 커넥션. 이 안의 모든 저장소 호출이 이 커넥션을 공유해야 한다
     * @return 작업 결과
     * @throws Exception 작업 중 발생한 모든 예외
     */
    T doInTransaction(Connection conn) throws Exception;
}
