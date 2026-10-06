package kr.doolee.dab.support;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * 커넥션 풀(connection pool)을 관리하는 단 하나의 창구.
 *
 * <h2>커넥션 풀이 왜 필요한가</h2>
 * {@code DriverManager.getConnection()} 은 호출할 때마다 새 커넥션을 만든다.
 * 그 한 번에 아래가 전부 일어난다.
 * <ol>
 *   <li>TCP 3-way handshake (네트워크 왕복)</li>
 *   <li>MySQL 인증 — 아이디/비밀번호 검증 (왕복 한 번 더)</li>
 *   <li>세션 변수 설정 (문자셋, 타임존, autocommit)</li>
 *   <li>서버 쪽에 스레드 하나 생성</li>
 * </ol>
 * 다 합쳐 보통 <b>수십 ms</b> 가 든다. 정작 우리 쿼리는 1~2 ms 다.
 * 즉 <b>일하는 시간보다 준비하는 시간이 10배 이상</b> 길다.
 *
 * <p>커넥션 풀은 커넥션을 <b>미리 몇 개 만들어 두고 빌려 주는</b> 방식이다.
 * 빌려 쓰고 돌려주면 그 커넥션은 닫히지 않고 다음 요청을 기다린다.
 * 준비 비용을 애플리케이션이 뜰 때 한 번만 내는 셈이다.
 *
 * <table border="1">
 *   <caption>차이</caption>
 *   <tr><th></th><th>DriverManager (이전)</th><th>HikariCP (지금)</th></tr>
 *   <tr><td>커넥션 획득</td><td>매번 새로 생성 — 수십 ms</td><td>풀에서 꺼냄 — 0.x ms</td></tr>
 *   <tr><td>close()</td><td>실제로 끊는다</td><td><b>풀에 반납한다</b></td></tr>
 *   <tr><td>동시 접속 수</td><td>제한 없음 — DB 가 먼저 터진다</td><td>maximumPoolSize 로 상한</td></tr>
 *   <tr><td>반납 안 하면</td><td>서버 커넥션이 쌓임</td><td>풀이 비고 대기 → 경고 로그</td></tr>
 * </table>
 *
 * <h2>가장 헷갈리는 부분: close() 가 닫지 않는다</h2>
 * 풀에서 받은 Connection 의 {@code close()} 는 실제로 끊는 것이 아니라
 * <b>풀에 돌려주는</b> 동작이다 (HikariCP 가 Connection 을 감싼 프록시를 주기 때문이다).
 * 그래서 코드는 전혀 바뀌지 않는다 — try-with-resources 를 그대로 쓰면 된다.
 *
 * <pre>
 * try (Connection conn = Db.getConnection()) {   // 풀에서 빌림
 *     ...
 * }                                              // 블록을 나가며 풀에 반납
 * </pre>
 *
 * <p>반대로 <b>반납하지 않으면 더 위험해진다.</b> 풀에 커넥션이 10개뿐이라면,
 * 반납을 빠뜨린 코드가 10번 실행되는 순간 애플리케이션 전체가 멈춘다.
 * 그래서 {@code leakDetectionThreshold} 를 켜 뒀다 — 빌린 채
 * 일정 시간이 지나면 "어디서 빌려 갔는지" 스택 트레이스를 경고로 찍어 준다.
 *
 * <h2>이 클래스만 바뀌었다</h2>
 * {@code getConnection()} 의 시그니처를 그대로 유지했기 때문에
 * repository · service · TxTemplate 중 <b>단 한 줄도 고치지 않았다</b>.
 * "커넥션을 어디서 얻는가"를 처음부터 한 곳에 모아 둔 효과다.
 *
 * @author Doo-Lee
 * @version 2.0
 */
public final class Db {

    private static final HikariDataSource DS;

    static {
        Properties p = loadProperties();

        HikariConfig cfg = new HikariConfig();

        /* ---------- 접속 정보 ---------- */
        cfg.setJdbcUrl (p.getProperty("db.url"));
        cfg.setUsername(p.getProperty("db.user"));
        cfg.setPassword(p.getProperty("db.password"));
        cfg.setPoolName("dab-pool");          // 로그에 찍히는 풀 이름

        /*
         * ---------- 풀 크기 ----------
         * 흔한 오해: 크게 잡으면 빨라진다. 사실은 반대다.
         * 커넥션 하나당 DB 서버에 스레드가 하나 붙으므로, 풀을 100개로 잡으면
         * DB 가 100개 스레드를 번갈아 처리하며 문맥 전환에 시간을 쓴다.
         *
         * HikariCP 문서가 권하는 출발점:  (CPU 코어 수 x 2) + 디스크 수
         * 4코어라면 9~10 정도. 이 프로젝트는 콘솔 앱이라 10으로 충분하다.
         */
        cfg.setMaximumPoolSize(10);

        /*
         * 평소에 비워 두지 않고 유지할 최소 개수.
         * 0 으로 두면 한동안 안 쓰다가 요청이 오면 다시 만들어야 해서 첫 요청이 느려진다.
         */
        cfg.setMinimumIdle(2);

        /* ---------- 시간 설정 (단위: ms) ---------- */

        /*
         * 풀이 다 찼을 때 얼마나 기다릴지. 넘으면 SQLTransientConnectionException.
         * 무한히 기다리게 두면 창구 직원이 화면 앞에서 영원히 멈춘다.
         * 차라리 3초 뒤에 "지금 혼잡하다"고 응답하는 쪽이 낫다.
         */
        cfg.setConnectionTimeout(3_000);

        /* 안 쓰는 커넥션을 정리하기 전까지 두는 시간 (minimumIdle 까지만 줄인다) */
        cfg.setIdleTimeout(600_000);          // 10분

        /*
         * 커넥션 하나의 최대 수명. 아무 문제 없어도 때가 되면 버리고 새로 만든다.
         * MySQL 의 wait_timeout(기본 8시간)보다 반드시 짧아야 한다.
         * 그렇지 않으면 서버가 이미 끊은 커넥션을 풀이 멀쩡한 줄 알고 빌려 줘서
         * "Communications link failure" 가 간헐적으로 터진다.
         */
        cfg.setMaxLifetime(1_800_000);        // 30분

        /*
         * 빌려 간 커넥션이 이 시간 넘게 반납되지 않으면 경고 + 스택 트레이스를 찍는다.
         * try-with-resources 를 빠뜨린 코드를 잡아내는 장치다.
         * 운영에서는 끄거나 길게 잡는다 (0 = 끔).
         */
        cfg.setLeakDetectionThreshold(AppEnv.isDev() ? 5_000 : 0);

        /*
         * ---------- autoCommit 기본값 ----------
         * 중요하다. TxTemplate 이 setAutoCommit(false) 로 트랜잭션을 시작하는데,
         * 반납할 때 그 상태가 그대로 남아 있으면 다음에 빌려 가는 쪽이
         * 자기도 모르게 트랜잭션 안에서 작업하게 된다.
         * HikariCP 는 반납 시 이 기본값으로 되돌려 주므로 true 로 명시한다.
         */
        cfg.setAutoCommit(true);

        /*
         * ---------- MySQL 전용 최적화 ----------
         * PreparedStatement 를 캐시해 같은 SQL 을 다시 파싱하지 않게 한다.
         * 우리 repository 는 SQL 을 static final 상수로 두고 반복 실행하므로 효과가 크다.
         */
        cfg.addDataSourceProperty("cachePrepStmts",           "true");
        cfg.addDataSourceProperty("prepStmtCacheSize",        "250");
        cfg.addDataSourceProperty("prepStmtCacheSqlLimit",    "2048");
        cfg.addDataSourceProperty("useServerPrepStmts",       "true");
        cfg.addDataSourceProperty("rewriteBatchedStatements", "true");

        DS = new HikariDataSource(cfg);

        /*
         * JVM 이 종료될 때 풀을 닫는다.
         * 풀은 살아 있는 TCP 커넥션과 관리용 스레드를 들고 있어서,
         * 닫지 않으면 프로그램이 끝나도 JVM 이 바로 빠져나가지 못할 수 있다.
         */
        Runtime.getRuntime().addShutdownHook(new Thread(Db::shutdown, "db-shutdown"));
    }

    private Db() { }   // 인스턴스를 만들 이유가 없는 유틸리티 클래스

    /**
     * 설정 파일을 읽는다. 클래스가 처음 쓰일 때 한 번만 호출된다.
     *
     * @return 접속 정보
     * @throws IllegalStateException 파일이 없거나 읽을 수 없는 경우
     */
    private static Properties loadProperties() {
        Properties p = new Properties();
        try (InputStream in = Db.class.getResourceAsStream("/db.properties")) {
            if (in == null) {
                throw new IllegalStateException(
                    "src/main/resources/db.properties 가 없습니다. "
                  + "db.properties.example 을 복사해서 만들어 주세요.");
            }
            p.load(in);
        } catch (IOException e) {
            // IOException 은 체크 예외다. 설정을 못 읽으면 프로그램이 돌 수 없으므로
            // 복구를 시도하지 않고 언체크 예외로 바꿔 즉시 중단시킨다.
            throw new IllegalStateException("db.properties 를 읽지 못했습니다.", e);
        }
        return p;
    }

    /**
     * 풀에서 커넥션을 빌린다. <b>반드시 try-with-resources 로 반납해야 한다.</b>
     *
     * <p>여기서 돌려주는 것은 HikariCP 의 프록시 객체다.
     * {@code close()} 를 호출하면 끊지 않고 풀로 돌아간다.
     *
     * @return 빌린 커넥션 (autoCommit = true 상태)
     * @throws DataAccessException 풀이 다 차서 connectionTimeout 을 넘겼거나,
     *                             DB 에 접속할 수 없는 경우
     */
    public static Connection getConnection() {
        try {
            return DS.getConnection();
        } catch (SQLException e) {
            throw new DataAccessException("커넥션 획득 실패 " + poolStatus(), e);
        }
    }

    /**
     * 풀의 현재 상태를 한 줄로 돌려준다. 커넥션 풀을 눈으로 확인하는 용도다.
     *
     * <ul>
     *   <li><b>total</b> — 풀이 들고 있는 전체 커넥션 수</li>
     *   <li><b>active</b> — 지금 누군가 빌려 가서 쓰는 중</li>
     *   <li><b>idle</b> — 대기 중 (빌려 갈 수 있음)</li>
     *   <li><b>waiting</b> — 커넥션이 없어 기다리는 스레드 수. 0 이 아니면 풀이 작다는 신호</li>
     * </ul>
     *
     * @return 예: {@code [dab-pool total=2 active=0 idle=2 waiting=0 max=10]}
     */
    public static String poolStatus() {
        if (DS.isClosed()) return "[dab-pool closed]";
        var mx = DS.getHikariPoolMXBean();
        if (mx == null) return "[dab-pool 통계 없음]";
        return String.format("[%s total=%d active=%d idle=%d waiting=%d max=%d]",
                DS.getPoolName(),
                mx.getTotalConnections(),
                mx.getActiveConnections(),
                mx.getIdleConnections(),
                mx.getThreadsAwaitingConnection(),
                DS.getMaximumPoolSize());
    }

    /**
     * 풀을 닫고 들고 있던 커넥션을 모두 실제로 끊는다.
     * 프로그램을 끝낼 때 한 번 호출한다. 종료 훅에도 걸려 있으므로 두 번 불러도 안전하다.
     */
    public static void shutdown() {
        if (!DS.isClosed()) {
            DS.close();
        }
    }
}
