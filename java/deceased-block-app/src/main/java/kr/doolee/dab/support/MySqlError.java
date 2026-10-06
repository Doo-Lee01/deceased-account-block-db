package kr.doolee.dab.support;

import java.sql.SQLException;

/**
 * MySQL 오류번호를 사람이 읽을 수 있는 설명으로 바꿔 주는 사전.
 *
 * <p><b>왜 따로 뺐는가.</b> {@code e.getMessage()} 만 찍으면
 * <i>"Cannot add or update a child row: a foreign key constraint fails"</i> 처럼
 * 영문 원문이 나온다. 이 메시지만 보고 "어느 제약이 왜 걸렸는지" 알아내려면
 * DDL 을 뒤져야 한다. 자주 만나는 번호를 여기 한 곳에 모아 두면
 * 오류 화면에서 바로 원인을 짚을 수 있다.
 *
 * <p>또 하나 중요한 구분이 있다. 아래 번호 중
 * <b>1213 / 1205 는 다시 시도하면 성공할 수 있는 오류</b>이고,
 * <b>1452 / 1062 / 3819 는 몇 번을 다시 해도 같은 결과</b>다.
 * {@link TxTemplate} 이 이 차이로 재시도 여부를 판단한다.
 *
 * @author Doo-Lee
 * @version 1.0
 * @see TxTemplate
 */
public final class MySqlError {

    /** 데드락. 두 트랜잭션이 서로의 락을 기다려 MySQL 이 한쪽을 롤백시켰다. */
    public static final int DEADLOCK      = 1213;
    /** 락 대기 시간 초과. 앞 트랜잭션이 너무 오래 락을 쥐고 있었다. */
    public static final int LOCK_TIMEOUT  = 1205;
    /** 외래키 위반. 부모 행이 없는데 자식 행을 넣으려 했다. */
    public static final int FK_VIOLATION  = 1452;
    /** 유니크 위반. 이미 같은 값이 있다. */
    public static final int DUPLICATE_KEY = 1062;
    /** CHECK 제약 위반. DB 가 값 자체를 거부했다. */
    public static final int CHECK_FAILED  = 3819;

    private MySqlError() { }

    /**
     * 같은 요청을 다시 보내면 성공할 가능성이 있는 오류인지 판단한다.
     *
     * <p>SQLSTATE {@code 40001} 은 "직렬화 실패"라는 표준 상태코드로,
     * MySQL 의 1213 과 PostgreSQL 의 데드락이 공통으로 쓴다.
     * 번호(제품마다 다름)보다 SQLSTATE(표준)를 먼저 보는 편이 이식성이 좋다.
     *
     * @param e 판단할 예외
     * @return 재시도할 가치가 있으면 true
     */
    public static boolean isRetryable(SQLException e) {
        if (e == null) return false;
        return "40001".equals(e.getSQLState())
            || e.getErrorCode() == DEADLOCK
            || e.getErrorCode() == LOCK_TIMEOUT;
    }

    /**
     * 오류번호를 한글 설명으로 바꾼다.
     *
     * @param e 설명할 예외
     * @return 사람이 읽을 수 있는 한 줄 설명
     */
    public static String describe(SQLException e) {
        if (e == null) return "알 수 없는 DB 오류";
        switch (e.getErrorCode()) {
            case DEADLOCK:
                return "데드락이 발생했습니다 (ERROR 1213). 두 거래가 서로의 락을 기다렸습니다.";
            case LOCK_TIMEOUT:
                return "락 대기 시간을 초과했습니다 (ERROR 1205). 앞선 거래가 오래 걸리고 있습니다.";
            case FK_VIOLATION:
                return "참조 무결성 위반입니다 (ERROR 1452). 존재하지 않는 부모 행을 참조했습니다.";
            case DUPLICATE_KEY:
                return "중복 키 위반입니다 (ERROR 1062). 이미 같은 값이 등록되어 있습니다.";
            case CHECK_FAILED:
                return "CHECK 제약 위반입니다 (ERROR 3819). DB 가 값 자체를 거부했습니다.";
            default:
                return "DB 오류 (" + e.getErrorCode() + " / SQLSTATE " + e.getSQLState()
                     + "): " + e.getMessage();
        }
    }
}
