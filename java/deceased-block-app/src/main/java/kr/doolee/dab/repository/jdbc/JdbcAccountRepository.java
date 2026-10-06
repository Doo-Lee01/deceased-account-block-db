package kr.doolee.dab.repository.jdbc;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import kr.doolee.dab.domain.Account;
import kr.doolee.dab.repository.AccountRepository;
import kr.doolee.dab.support.DataAccessException;

/**
 * {@link AccountRepository} 의 JDBC 구현.
 *
 * <p>SQL 문자열은 전부 이 클래스 안에만 있다. 서비스 계층은 SQL 을 한 줄도 모른다.
 *
 * @author Doo-Lee
 * @version 1.1
 */
public class JdbcAccountRepository implements AccountRepository {

    private static final String SELECT_BASE =
        "SELECT acct_no, cust_no, product_cd, balance_amt, acct_status_cd "
      + "  FROM core_bank.acct "
      + " WHERE acct_no = ?";

    /**
     * 위 쿼리와 <b>딱 한 구절만</b> 다르다. 이 세 단어가 락의 전부다.
     * 쿼리를 따로 쓰지 않고 이어 붙여, 두 경로가 같은 컬럼을 읽는다는 것을 코드로 보장한다.
     */
    private static final String SELECT_FOR_UPDATE = SELECT_BASE + " FOR UPDATE";

    private static final String UPDATE_BALANCE =
        "UPDATE core_bank.acct "
      + "   SET balance_amt = ?, upd_dtm = CURRENT_TIMESTAMP(3), upd_user_id = ? "
      + " WHERE acct_no = ?";

    @Override
    public Account findByNo(Connection conn, String acctNo) {
        return select(conn, SELECT_BASE, acctNo, "계좌 조회 실패");
    }

    @Override
    public Account findByNoForUpdate(Connection conn, String acctNo) {
        return select(conn, SELECT_FOR_UPDATE, acctNo, "계좌 조회(FOR UPDATE) 실패");
    }

    /**
     * 조회 공통 처리.
     *
     * <p><b>PreparedStatement 를 쓰는 이유.</b> 값을 문자열로 이어 붙이면
     * {@code "WHERE acct_no = '" + acctNo + "'"} 처럼 되는데,
     * 입력값에 따옴표가 섞여 들어오면 SQL 문장 자체가 바뀌어 버린다(SQL 인젝션).
     * {@code ?} 자리표시자에 {@code setString} 으로 넣으면 값은 끝까지 값으로만 취급된다.
     *
     * <p><b>try-with-resources 를 두 겹으로 쓰는 이유.</b>
     * {@code PreparedStatement} 와 {@code ResultSet} 은 둘 다 닫아야 하는 자원이다.
     * 닫지 않으면 커넥션당 커서가 쌓여 금방 바닥난다.
     * Connection 은 여기서 닫지 않는다 — 트랜잭션을 시작한 쪽의 소유물이기 때문이다.
     */
    private Account select(Connection conn, String sql, String acctNo, String errMsg) {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, acctNo);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;    // 0건은 오류가 아니다. null 로 알린다
                return map(rs);
            }
        } catch (SQLException e) {
            // 원인 예외(e)를 넘기지 않으면 스택 트레이스에 Caused by 가 안 붙어
            // "계좌 조회 실패"라는 말만 남고 진짜 원인이 사라진다.
            throw new DataAccessException(errMsg + ": " + acctNo, e);
        }
    }

    @Override
    public void updateBalance(Connection conn, String acctNo, BigDecimal newBalance) {
        try (PreparedStatement ps = conn.prepareStatement(UPDATE_BALANCE)) {
            ps.setBigDecimal(1, newBalance);
            ps.setString(2, "JAVA-APP");
            ps.setString(3, acctNo);

            int updated = ps.executeUpdate();
            if (updated != 1) {
                // SQL 오류는 아니지만 업무상 있을 수 없는 상태다.
                // 조용히 넘기면 잔액이 안 바뀐 채로 원장만 쌓인다.
                throw new DataAccessException(
                        "잔액 수정 대상이 1건이 아닙니다 (실제 " + updated + "건): " + acctNo);
            }
        } catch (SQLException e) {
            // 잔액이 음수가 되면 ck_acct_balance CHECK 위반 → ERROR 3819
            throw new DataAccessException("잔액 수정 실패: " + acctNo, e);
        }
    }

    /**
     * {@link ResultSet} 한 행을 객체로 바꾼다. 타입 변환 규칙이 이 한 곳에만 있다.
     *
     * <table border="1">
     *   <caption>MySQL → Java 타입 대응</caption>
     *   <tr><td>{@code VARCHAR / CHAR}</td><td>{@code String}</td></tr>
     *   <tr><td>{@code DECIMAL(18,2)}</td><td>{@code BigDecimal} — double 은 절대 금지</td></tr>
     * </table>
     *
     * @param rs 현재 행이 가리켜진 ResultSet
     * @return 계좌 객체
     * @throws SQLException 컬럼 이름이 틀렸거나 읽기에 실패한 경우.
     *                      이 메서드는 감싸지 않고 호출자에게 넘긴다 — 호출자가 맥락을 알기 때문이다
     */
    private Account map(ResultSet rs) throws SQLException {
        return new Account(
            rs.getString("acct_no"),
            rs.getString("cust_no"),
            rs.getString("product_cd"),
            rs.getBigDecimal("balance_amt"),
            rs.getString("acct_status_cd"));
    }
}
