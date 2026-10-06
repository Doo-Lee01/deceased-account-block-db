package kr.doolee.dab.repository.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;

import kr.doolee.dab.domain.BlockStatus;
import kr.doolee.dab.domain.DeathBlock;
import kr.doolee.dab.repository.DeathBlockRepository;
import kr.doolee.dab.support.DataAccessException;

/**
 * {@link DeathBlockRepository} 의 JDBC 구현.
 *
 * @author Doo-Lee
 * @version 1.1
 */
public class JdbcDeathBlockRepository implements DeathBlockRepository {

    private static final String SELECT_BY_CUST =
        "SELECT block_id, cust_no, death_dt, death_report_dt, "
      + "       block_start_dtm, block_status_cd "
      + "  FROM core_bank.death_block "
      + " WHERE cust_no = ?";

    @Override
    public DeathBlock findByCustNo(Connection conn, String custNo) {
        try (PreparedStatement ps = conn.prepareStatement(SELECT_BY_CUST)) {
            ps.setString(1, custNo);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;          // 차단 이력 자체가 없음
                return new DeathBlock(
                    rs.getLong("block_id"),
                    rs.getString("cust_no"),
                    // 날짜·시간은 getObject(컬럼, 타입.class) 로 읽는다 (JDBC 4.2 방식).
                    // 옛날 getDate()/getTimestamp() 는 java.util.Date 기반이라
                    // 타임존이 끼어들어 하루가 밀리는 사고가 난다.
                    rs.getObject("death_dt", LocalDate.class),
                    rs.getObject("death_report_dt", LocalDate.class),
                    rs.getObject("block_start_dtm", LocalDateTime.class),
                    BlockStatus.of(rs.getString("block_status_cd")));
            }
        } catch (SQLException e) {
            throw new DataAccessException("차단정보 조회 실패: " + custNo, e);
        } catch (IllegalArgumentException e) {
            // BlockStatus.of() 가 모르는 코드를 만난 경우.
            // DB 에 코드가 추가됐는데 enum 을 안 고쳤다는 뜻이므로 원인을 분명히 남긴다.
            throw new DataAccessException(
                    "차단상태 코드를 해석할 수 없습니다 (cust_no=" + custNo + ")", e);
        }
    }
}
