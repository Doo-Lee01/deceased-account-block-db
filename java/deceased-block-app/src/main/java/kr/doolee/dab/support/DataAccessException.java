package kr.doolee.dab.support;

import java.sql.SQLException;

/**
 * 저장소 계층에서 생긴 <b>시스템 장애</b>를 나타내는 예외.
 *
 * <p><b>체크 예외를 언체크 예외로 바꾸는 자리다.</b>
 * {@link SQLException} 은 체크 예외(checked exception)라서,
 * 컴파일러가 "try-catch 를 달든지 throws 로 넘기든지 하라"고 강제한다.
 * 그 말을 그대로 따르면 저장소를 호출하는 서비스 계층이 전부 이렇게 된다.
 *
 * <pre>
 * try {
 *     accountRepository.findByNo(conn, acctNo);
 * } catch (SQLException e) {
 *     // 서비스가 할 수 있는 일이 사실 없다. 다시 던지는 것 말고는.
 * }
 * </pre>
 *
 * <p>서비스 계층은 JDBC 를 쓰는지 JPA 를 쓰는지 알 필요가 없고,
 * SQLException 을 받아도 복구할 방법이 없다. 그래서
 * {@code RuntimeException} 을 상속한 <b>언체크 예외</b>로 한 번 감싸
 * 중간 계층을 깨끗하게 비워 둔다. 처리할 수 있는 맨 위(Main)에서만 잡는다.
 *
 * <p><b>원인 예외(cause)를 반드시 넘긴다.</b>
 * {@code new DataAccessException("실패", e)} 처럼 두 번째 인자로 원래 예외를 주면
 * 스택 트레이스에 {@code Caused by: ...} 가 따라붙어 진짜 원인 줄까지 추적할 수 있다.
 * 이걸 빠뜨리면 "조회 실패"라는 메시지만 남고 어디서 왜 깨졌는지 사라진다.
 *
 * @author Doo-Lee
 * @version 1.1
 * @see BusinessException 업무 규칙 위반(차단, 잔액부족)은 이쪽이다
 * @see MySqlError
 */
public class DataAccessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int    errorCode;   // MySQL 오류번호 (없으면 0)
    private final String sqlState;    // 표준 SQLSTATE (없으면 null)

    /**
     * 원인이 된 SQLException 과 함께 만든다. 평소에는 이 생성자를 쓴다.
     *
     * @param message 무엇을 하다 실패했는지 (예: "계좌 조회 실패: 10200000000001")
     * @param cause   원래 발생한 SQLException. 절대 null 로 두지 않는다
     */
    public DataAccessException(String message, SQLException cause) {
        super(message + " — " + MySqlError.describe(cause), cause);
        this.errorCode = cause == null ? 0    : cause.getErrorCode();
        this.sqlState  = cause == null ? null : cause.getSQLState();
    }

    /**
     * SQL 예외는 아니지만 저장소 계층에서 생긴 문제일 때 쓴다.
     * (예: UPDATE 대상이 1건이어야 하는데 0건이었다)
     *
     * @param message 설명
     */
    public DataAccessException(String message) {
        super(message);
        this.errorCode = 0;
        this.sqlState  = null;
    }

    /**
     * SQL 예외가 아닌 다른 원인(예: NullPointerException)을 감쌀 때 쓴다.
     * 원인을 반드시 넘겨야 스택 트레이스에 {@code Caused by:} 가 남는다.
     *
     * @param message 설명
     * @param cause   원래 발생한 예외
     */
    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = 0;
        this.sqlState  = null;
    }

    /** @return MySQL 오류번호. SQL 예외가 원인이 아니면 0 */
    public int errorCode() { return errorCode; }

    /** @return 표준 SQLSTATE. SQL 예외가 원인이 아니면 null */
    public String sqlState() { return sqlState; }
}
