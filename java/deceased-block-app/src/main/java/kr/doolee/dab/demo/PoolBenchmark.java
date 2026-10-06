package kr.doolee.dab.demo;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Properties;

import kr.doolee.dab.support.Db;

/**
 * 커넥션 풀이 실제로 얼마나 빠른지 숫자로 확인하는 측정용 프로그램.
 *
 * <p>같은 쿼리를 같은 횟수만큼 두 가지 방법으로 실행해 비교한다.
 * <ol>
 *   <li>{@code DriverManager.getConnection()} — 매번 새 커넥션을 만든다</li>
 *   <li>{@link Db#getConnection()} — 풀에서 빌려 쓰고 반납한다</li>
 * </ol>
 *
 * <p>쿼리 자체는 양쪽이 완전히 같으므로, 차이는 <b>커넥션을 얻는 비용</b>뿐이다.
 * 로컬 MySQL 로도 차이가 분명히 나고, 네트워크를 건너가는 실제 운영 환경에서는
 * 격차가 훨씬 커진다 (TCP 왕복과 인증이 네트워크 지연을 그대로 타기 때문).
 *
 * <p>실행: {@code Run As → Java Application} (이 클래스를 선택)
 *
 * @author Doo-Lee
 * @version 1.0
 */
public class PoolBenchmark {

    /** 측정 횟수. 너무 적으면 JIT 컴파일 전 상태가 섞여 들어가 수치가 흔들린다. */
    private static final int LOOPS = 60;

    private static final String SQL =
        "SELECT balance_amt FROM core_bank.acct WHERE acct_no = '10200000000001'";

    public static void main(String[] args) throws Exception {

        Properties p = new Properties();
        try (InputStream in = PoolBenchmark.class.getResourceAsStream("/db.properties")) {
            p.load(in);
        }
        String url  = p.getProperty("db.url");
        String user = p.getProperty("db.user");
        String pass = p.getProperty("db.password");

        System.out.println("=== 커넥션 획득 방식 비교 (" + LOOPS + "회) ===\n");

        /* 워밍업: 양쪽 모두 한 번 실행해 클래스 로딩과 풀 생성을 미리 끝낸다.
         * 이걸 안 하면 먼저 측정한 쪽이 초기화 비용을 혼자 떠안아 불공정해진다. */
        try (Connection c = DriverManager.getConnection(url, user, pass)) { query(c); }
        try (Connection c = Db.getConnection())                          { query(c); }

        /* ---------- 1. DriverManager ---------- */
        long t1 = System.nanoTime();
        for (int i = 0; i < LOOPS; i++) {
            try (Connection c = DriverManager.getConnection(url, user, pass)) {
                query(c);           // 이 블록을 나가면 커넥션이 실제로 끊긴다
            }
        }
        long d1 = System.nanoTime() - t1;

        /* ---------- 2. HikariCP ---------- */
        long t2 = System.nanoTime();
        for (int i = 0; i < LOOPS; i++) {
            try (Connection c = Db.getConnection()) {
                query(c);           // 이 블록을 나가면 풀에 반납된다
            }
        }
        long d2 = System.nanoTime() - t2;

        /* ---------- 결과 ---------- */
        double ms1 = d1 / 1_000_000.0;
        double ms2 = d2 / 1_000_000.0;
        System.out.printf("DriverManager : %8.1f ms  (1회 평균 %5.2f ms)%n", ms1, ms1 / LOOPS);
        System.out.printf("HikariCP      : %8.1f ms  (1회 평균 %5.2f ms)%n", ms2, ms2 / LOOPS);
        System.out.printf("%n차이          : %.1f 배 빠름%n", ms1 / ms2);
        System.out.println("풀 상태       : " + Db.poolStatus());
        System.out.println();
        System.out.println("쿼리는 양쪽이 완전히 같다. 차이는 커넥션을 얻는 비용뿐이다.");

        Db.shutdown();
    }

    /** 측정 대상 쿼리. 양쪽에서 똑같이 호출한다. */
    private static void query(Connection c) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(SQL);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) rs.getBigDecimal(1);
        }
    }
}
