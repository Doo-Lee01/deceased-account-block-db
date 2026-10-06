package kr.doolee.dab.repository.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

import kr.doolee.dab.domain.BlockRule;
import kr.doolee.dab.domain.DrCr;
import kr.doolee.dab.domain.TxnType;
import kr.doolee.dab.repository.BlockRuleRepository;
import kr.doolee.dab.support.DataAccessException;

/**
 * {@link BlockRuleRepository} 의 JDBC 구현.
 *
 * @author Doo-Lee
 * @version 1.1
 */
public class JdbcBlockRuleRepository implements BlockRuleRepository {

    /**
     * 기준일자에 유효한 규칙만 뽑는다.
     * {@code BETWEEN apply_from_dt AND apply_to_dt} 가 시점 버전 관리의 핵심이다.
     */
    private static final String SELECT_EFFECTIVE =
        "SELECT rule_id, txn_type_cd, dr_cr_cd, allow_yn, "
      + "       exception_possible_yn, reject_rsp_cd "
      + "  FROM core_bank.block_rule "
      + " WHERE txn_type_cd = ? "
      + "   AND dr_cr_cd    = ? "
      + "   AND ? BETWEEN apply_from_dt AND apply_to_dt";

    @Override
    public BlockRule findEffective(Connection conn, TxnType type, DrCr drCr, LocalDate baseDt) {
        try (PreparedStatement ps = conn.prepareStatement(SELECT_EFFECTIVE)) {
            ps.setString(1, type.code());
            ps.setString(2, drCr.code());
            ps.setObject(3, baseDt);                 // LocalDate → DATE
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new BlockRule(
                    rs.getLong("rule_id"),
                    rs.getString("txn_type_cd"),
                    rs.getString("dr_cr_cd"),
                    "Y".equals(rs.getString("allow_yn")),
                    "Y".equals(rs.getString("exception_possible_yn")),
                    rs.getString("reject_rsp_cd"));   // 허용 규칙이면 null
            }
        } catch (SQLException e) {
            throw new DataAccessException(
                    "차단규칙 조회 실패 (" + type.code() + "/" + drCr.code() + ")", e);
        }
    }
}
