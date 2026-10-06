package kr.doolee.dab.repository.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

import kr.doolee.dab.domain.AccountTxn;
import kr.doolee.dab.repository.AccountTxnRepository;
import kr.doolee.dab.support.DataAccessException;

/**
 * {@link AccountTxnRepository} 의 JDBC 구현.
 *
 * @author Doo-Lee
 * @version 1.1
 */
public class JdbcAccountTxnRepository implements AccountTxnRepository {

    private static final String INSERT =
        "INSERT INTO core_bank.acct_txn "
      + "  (txn_unique_no, acct_no, txn_dtm, txn_type_cd, dr_cr_cd, txn_amt, "
      + "   balance_after_amt, chnl_cd, terminal_id, rsp_cd, memo, reg_user_id) "
      + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'JAVA-APP')";

    @Override
    public long insert(Connection conn, AccountTxn t) {
        // RETURN_GENERATED_KEYS: AUTO_INCREMENT 로 생긴 txn_id 를 돌려받는다.
        try (PreparedStatement ps =
                 conn.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString    (1,  t.txnUniqueNo());
            ps.setString    (2,  t.acctNo());
            ps.setObject    (3,  t.txnDtm());               // LocalDateTime → DATETIME(3)
            ps.setString    (4,  t.txnType().code());
            ps.setString    (5,  t.txnType().drCr().code());
            ps.setBigDecimal(6,  t.txnAmt());
            ps.setBigDecimal(7,  t.balanceAfterAmt());
            ps.setString    (8,  t.chnlCd());

            // NULL 가능 컬럼은 setNull 로 명시한다. setString(null) 도 동작하지만
            // 의도가 드러나지 않아 나중에 읽는 사람이 실수로 오해한다.
            if (t.terminalId() == null) ps.setNull(9, Types.VARCHAR);
            else                        ps.setString(9, t.terminalId());

            ps.setString    (10, t.rspCd());

            if (t.memo() == null) ps.setNull(11, Types.VARCHAR);
            else                  ps.setString(11, t.memo());

            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1L;
            }
        } catch (SQLException e) {
            /*
             * 여기서 자주 만나는 오류번호 (MySqlError 에 설명이 들어 있다)
             *   1062 — txn_unique_no 중복 (uk_txn_unique).
             *          단말이 같은 요청을 두 번 보냈다는 뜻이므로 중복 출금을 DB 가 막아 준 것이다.
             *   1452 — acct_no 가 acct 에 없음 (fk_txn_acct)
             *   3819 — txn_amt <= 0 또는 balance_after_amt < 0 (CHECK 위반)
             */
            throw new DataAccessException("거래원장 적재 실패: " + t.txnUniqueNo(), e);
        }
    }
}
