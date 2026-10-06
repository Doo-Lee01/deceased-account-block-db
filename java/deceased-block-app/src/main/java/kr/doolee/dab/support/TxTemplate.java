package kr.doolee.dab.support;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * 트랜잭션 경계와 데드락 재시도를 한 곳에 모은 템플릿.
 *
 * <p><b>이 클래스가 없으면</b> 서비스 메서드마다 아래가 반복된다.
 * <pre>
 * Connection conn = null;
 * try {
 *     conn = Db.getConnection();
 *     conn.setAutoCommit(false);
 *     ... 업무 ...
 *     conn.commit();
 * } catch (SQLException e) {
 *     if (conn != null) conn.rollback();   // 이 rollback 도 예외를 던질 수 있다
 *     throw ...;
 * } finally {
 *     if (conn != null) conn.close();      // 빠뜨리면 커넥션이 샌다
 * }
 * </pre>
 * 메서드가 열 개면 같은 코드가 열 번 복사되고, 한 군데만 {@code rollback} 을
 * 빼먹어도 데이터가 어긋난다. 그래서 한 번만 제대로 쓰고 재사용한다.
 *
 * <p><b>예외를 세 갈래로 나눠 처리한다.</b>
 * <ol>
 *   <li>재시도 가능한 SQLException (1213 데드락, 1205 락 타임아웃) — 롤백 후 다시 시도</li>
 *   <li>그 외 SQLException — 롤백하고 {@link DataAccessException} 으로 바꿔 던진다</li>
 *   <li>{@link BusinessException} — 롤백하고 그대로 올려 보낸다.
 *       업무 거절이므로 메시지를 바꾸지 않는다</li>
 * </ol>
 *
 * <p><b>왜 데드락은 재시도하는가.</b>
 * 데드락은 "잘못된 요청"이 아니라 "타이밍이 겹친 요청"이다.
 * MySQL 이 두 트랜잭션 중 하나를 골라 강제 롤백시킨 것이므로,
 * 다시 시도할 때는 상대 트랜잭션이 이미 끝나 있어 대부분 성공한다.
 * 반대로 1452(외래키) · 1062(중복) · 3819(CHECK)는 몇 번을 다시 해도
 * 같은 결과이므로 재시도하지 않고 바로 올려 보낸다.
 *
 * @author Doo-Lee
 * @version 1.1
 * @see MySqlError
 */
public final class TxTemplate {

    private static final int MAX_RETRY = 3;

    /**
     * 작업을 트랜잭션으로 감싸 실행한다. 데드락이면 최대 3회까지 자동 재시도한다.
     *
     * @param <T>  작업 결과 타입
     * @param work 트랜잭션 안에서 실행할 작업 (람다로 넘긴다)
     * @return 작업 결과
     * @throws BusinessException   업무 규칙에 걸려 거절된 경우 (롤백 후 그대로 전달)
     * @throws DataAccessException 시스템 장애이거나 재시도를 모두 소진한 경우
     */
    public <T> T execute(TxWork<T> work) {
        SQLException last = null;

        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            // try-with-resources 로 Connection 을 받으면 어떤 경로로 빠져나가도 close 된다
            try (Connection conn = Db.getConnection()) {

                conn.setAutoCommit(false);          // 여기서 트랜잭션이 시작된다 (= BEGIN)

                try {
                    T result = work.doInTransaction(conn);
                    conn.commit();                   // 모든 변경을 한꺼번에 확정
                    if (attempt > 1) {
                        System.out.printf("  [재시도 성공] %d번째 시도에서 커밋되었습니다.%n", attempt);
                    }
                    return result;

                } catch (BusinessException e) {
                    // 업무 거절. 돈이 움직이지 않았어야 하므로 되돌리고 그대로 올려 보낸다.
                    conn.rollback();
                    throw e;

                } catch (SQLException e) {
                    conn.rollback();
                    if (MySqlError.isRetryable(e) && attempt < MAX_RETRY) {
                        System.out.printf("  [재시도] SQLSTATE %s / ERROR %d — %d/%d%n",
                                e.getSQLState(), e.getErrorCode(), attempt, MAX_RETRY);
                        last = e;
                        backoff(attempt);
                        continue;                    // for 문 처음으로 돌아가 다시 시도
                    }
                    throw new DataAccessException("트랜잭션 실패", e);

                } catch (Exception e) {
                    // 예상하지 못한 예외(NullPointerException 등)도 반드시 롤백한다.
                    conn.rollback();
                    throw new DataAccessException("트랜잭션 처리 중 예상치 못한 오류: "
                            + e.getClass().getSimpleName(), e);
                }

            } catch (SQLException e) {
                // setAutoCommit / rollback / close 자체가 실패한 경우
                last = e;
                if (!MySqlError.isRetryable(e) || attempt == MAX_RETRY) {
                    throw new DataAccessException("커넥션 처리 실패", e);
                }
                backoff(attempt);
            }
        }
        throw new DataAccessException("재시도 " + MAX_RETRY + "회를 모두 소진했습니다", last);
    }

    /**
     * 재시도 전에 잠시 쉰다. 두 트랜잭션이 동시에 재시도해 또 부딪히는 것을 피한다.
     *
     * @param attempt 현재 시도 횟수 (쉬는 시간이 조금씩 늘어난다)
     */
    private void backoff(int attempt) {
        try {
            Thread.sleep(50L * attempt);
        } catch (InterruptedException ie) {
            // 인터럽트를 삼키면 상위에서 중단 요청을 알 수 없게 된다. 반드시 되돌려 놓는다.
            Thread.currentThread().interrupt();
        }
    }
}
