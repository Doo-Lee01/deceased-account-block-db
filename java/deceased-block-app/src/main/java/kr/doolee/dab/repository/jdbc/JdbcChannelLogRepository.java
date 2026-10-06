package kr.doolee.dab.repository.jdbc;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;

import kr.doolee.dab.domain.Decision;
import kr.doolee.dab.domain.TxnType;
import kr.doolee.dab.repository.ChannelLogRepository;
import kr.doolee.dab.support.DataAccessException;

/**
 * {@link ChannelLogRepository} 의 JDBC 구현.
 *
 * @author Doo-Lee
 * @version 1.1
 */
public class JdbcChannelLogRepository implements ChannelLogRepository {

    private static final String INS_REQ =
        "INSERT INTO channel.chnl_request "
      + "  (request_unique_no, chnl_cd, cust_no, acct_no, txn_type_cd, "
      + "   request_amt, request_dtm, reg_user_id) "
      + "VALUES (?, ?, ?, ?, ?, ?, ?, 'JAVA-APP')";

    private static final String INS_DEC =
        "INSERT INTO channel.chnl_block_decision "
      + "  (request_id, decision_cd, applied_rule_id, block_start_dtm, "
      + "   decision_dtm, txn_unique_no, reg_user_id) "
      + "VALUES (?, ?, ?, ?, ?, ?, 'JAVA-APP')";

    /**
     * 응답 메시지를 자바에 들고 있지 않고 {@code comm_code.rsp_code} 에서 가져와 넣는다.
     * {@code INSERT ... SELECT} 를 쓰면 왕복 한 번으로 끝나고,
     * 문구 원본이 코드 테이블 하나로 유지된다.
     */
    private static final String INS_RSP =
        "INSERT INTO channel.chnl_response "
      + "  (request_id, rsp_cd, rsp_message, response_dtm, elapsed_ms, reg_user_id) "
      + "SELECT ?, r.rsp_cd, r.rsp_message, ?, ?, 'JAVA-APP' "
      + "  FROM comm_code.rsp_code r WHERE r.rsp_cd = ?";

    @Override
    public long insertRequest(Connection conn, String requestUniqueNo, String chnlCd,
                              String custNo, String acctNo, TxnType type,
                              BigDecimal amt, LocalDateTime requestDtm) {
        try (PreparedStatement ps =
                 conn.prepareStatement(INS_REQ, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, requestUniqueNo);
            ps.setString(2, chnlCd);
            if (custNo == null) ps.setNull(3, Types.VARCHAR); else ps.setString(3, custNo);
            ps.setString(4, acctNo);
            ps.setString(5, type.code());
            if (amt == null) ps.setNull(6, Types.DECIMAL); else ps.setBigDecimal(6, amt);
            ps.setObject(7, requestDtm);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1L;
            }
        } catch (SQLException e) {
            throw new DataAccessException("채널 요청 적재 실패: " + requestUniqueNo, e);
        }
    }

    @Override
    public void insertDecision(Connection conn, long requestId, Decision d,
                               LocalDateTime decisionDtm, String txnUniqueNo) {
        try (PreparedStatement ps = conn.prepareStatement(INS_DEC)) {
            ps.setLong(1, requestId);
            ps.setString(2, d.result().code());
            if (d.appliedRuleId() == null) ps.setNull(3, Types.BIGINT);
            else                           ps.setLong(3, d.appliedRuleId());
            if (d.blockStartDtm() == null) ps.setNull(4, Types.TIMESTAMP);
            else                           ps.setObject(4, d.blockStartDtm());
            ps.setObject(5, decisionDtm);
            if (txnUniqueNo == null) ps.setNull(6, Types.VARCHAR);
            else                     ps.setString(6, txnUniqueNo);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("판정이력 적재 실패: request_id=" + requestId, e);
        }
    }

    @Override
    public void insertResponse(Connection conn, long requestId, String rspCd,
                               LocalDateTime responseDtm, int elapsedMs) {
        try (PreparedStatement ps = conn.prepareStatement(INS_RSP)) {
            ps.setLong(1, requestId);
            ps.setObject(2, responseDtm);
            ps.setInt(3, elapsedMs);
            ps.setString(4, rspCd);

            int inserted = ps.executeUpdate();
            if (inserted != 1) {
                // rsp_code 에 없는 응답코드를 쓰려 한 경우. SELECT 가 0건이라 INSERT 도 0건이 된다.
                // 조용히 지나가면 응답 기록이 비어 버리므로 분명히 알린다.
                throw new DataAccessException(
                        "등록되지 않은 응답코드입니다: " + rspCd);
            }
        } catch (SQLException e) {
            throw new DataAccessException("채널 응답 적재 실패: request_id=" + requestId, e);
        }
    }
}
