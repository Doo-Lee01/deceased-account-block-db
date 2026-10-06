package kr.doolee.dab;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Scanner;

import kr.doolee.dab.domain.Account;
import kr.doolee.dab.domain.Decision;
import kr.doolee.dab.domain.TxnType;
import kr.doolee.dab.repository.AccountRepository;
import kr.doolee.dab.repository.AccountTxnRepository;
import kr.doolee.dab.repository.BlockRuleRepository;
import kr.doolee.dab.repository.ChannelLogRepository;
import kr.doolee.dab.repository.DeathBlockRepository;
import kr.doolee.dab.repository.jdbc.JdbcAccountRepository;
import kr.doolee.dab.repository.jdbc.JdbcAccountTxnRepository;
import kr.doolee.dab.repository.jdbc.JdbcBlockRuleRepository;
import kr.doolee.dab.repository.jdbc.JdbcChannelLogRepository;
import kr.doolee.dab.repository.jdbc.JdbcDeathBlockRepository;
import kr.doolee.dab.service.BlockDecisionService;
import kr.doolee.dab.service.TransactionService;
import kr.doolee.dab.service.TxnResult;
import kr.doolee.dab.support.AppEnv;
import kr.doolee.dab.support.BusinessException;
import kr.doolee.dab.support.DataAccessException;
import kr.doolee.dab.support.Db;

/**
 * 콘솔 실행 진입점.
 *
 * <h2>조립은 한 곳에서</h2>
 * {@code new JdbcAccountRepository()} 같은 <b>구현체 선택</b>이 이 파일에만 나온다.
 * 서비스 클래스들은 인터페이스만 알고 있으므로, DB 를 바꾸거나
 * 테스트용 가짜 객체를 쓸 때 고칠 곳이 여기 한 군데다.
 * 스프링의 {@code @Configuration} 이 해 주는 일을 손으로 하는 것이다.
 *
 * <h2>예외를 여기서 처리하는 이유</h2>
 * 아래 계층들은 예외를 <b>잡지 않고 올려 보낸다</b>. 중간에서 잡아 봐야
 * 할 수 있는 일이 없기 때문이다. 예외를 처리할 수 있는 곳은
 * <b>사용자에게 무언가 말해 줄 수 있는 지점</b>, 즉 화면을 가진 이곳뿐이다.
 *
 * <h2>커넥션 풀</h2>
 * {@link Db} 가 HikariCP 풀을 관리한다. 이 클래스에서 바뀐 것은
 * 메뉴 6번(풀 상태 보기)과 종료 시 {@link Db#shutdown()} 호출뿐이다.
 * 거래 처리 코드는 커넥션을 어디서 얻는지 알지 못하므로 손댈 필요가 없었다.
 *
 * <p>그래서 여기서 세 갈래로 나눈다.
 * <ol>
 *   <li>{@link BusinessException} — 규칙대로 거절한 것. 안내 문구만 보여 준다</li>
 *   <li>{@link DataAccessException} — 시스템 장애. 개발 환경에서는 스택 트레이스까지 찍는다</li>
 *   <li>그 밖의 {@code RuntimeException} — 우리가 예상 못한 버그. 가장 자세히 남긴다</li>
 * </ol>
 *
 * @author Doo-Lee
 * @version 1.1
 */
public class Main {

    /**
     * @param args 쓰지 않는다. 환경 구분은 {@code -Dapp.env=PROD} 로 전달한다
     */
    public static void main(String[] args) {

        /* ---------- 조립 (Composition Root) ---------- */
        AccountRepository    accountRepository = new JdbcAccountRepository();
        AccountTxnRepository txnRepository     = new JdbcAccountTxnRepository();
        DeathBlockRepository blockRepository   = new JdbcDeathBlockRepository();
        BlockRuleRepository  ruleRepository    = new JdbcBlockRuleRepository();
        ChannelLogRepository channelRepository = new JdbcChannelLogRepository();

        BlockDecisionService decisionService =
                new BlockDecisionService(blockRepository, ruleRepository);
        TransactionService txnService = new TransactionService(
                accountRepository, txnRepository, channelRepository, decisionService);

        System.out.println("실행 환경: " + AppEnv.mode()
                + (AppEnv.isDev() ? "  (오류 시 스택 트레이스를 출력합니다)" : ""));
        // 첫 getConnection() 에서 풀이 만들어지므로, 여기서 한 번 호출해 미리 띄워 둔다.
        // 이렇게 하면 첫 거래가 풀 생성 시간까지 떠안지 않는다 (warm-up).
        System.out.println("커넥션 풀 : " + Db.poolStatus());

        /* ---------- 콘솔 ---------- */
        try (Scanner sc = new Scanner(System.in)) {
            while (true) {
                printMenu();
                String sel;
                try {
                    sel = sc.nextLine().trim();
                } catch (NoSuchElementException e) {
                    // 입력 스트림이 끊긴 경우(파이프로 실행하다 입력이 떨어짐).
                    // 무한 루프를 돌지 않도록 정상 종료한다.
                    System.out.println("입력이 종료되었습니다.");
                    return;
                }

                try {
                    switch (sel) {
                        case "1": showAccount(accountRepository, ask(sc, "계좌번호")); break;
                        case "2": simulate(txnService, sc);                           break;
                        case "3": run(txnService, sc, TxnType.DEP);                   break;
                        case "4": run(txnService, sc, TxnType.WDR);                   break;
                        case "5": concurrentTest(txnService, accountRepository, sc);   break;
                        case "6": System.out.println("  " + Db.poolStatus());          break;
                        case "0":
                            System.out.println("  종료 전 " + Db.poolStatus());
                            Db.shutdown();        // 풀이 들고 있던 커넥션을 실제로 끊는다
                            System.out.println("  커넥션 풀을 닫았습니다. 종료합니다.");
                            return;
                        default : System.out.println("잘못된 선택입니다.");
                    }

                } catch (BusinessException e) {
                    /*
                     * (1) 업무 거절. 정상적인 결과이므로 스택 트레이스를 찍지 않는다.
                     *     찍으면 로그가 거절 건으로 가득 차서 진짜 장애를 못 찾는다.
                     */
                    System.out.printf("  [거절] %s — %s%n", e.rspCd(), e.getMessage());

                } catch (DataAccessException e) {
                    /*
                     * (2) 시스템 장애. 운영자가 알아야 한다.
                     *     getMessage() 에 MySqlError 가 붙여 준 한글 설명이 들어 있다.
                     */
                    System.out.println("  [시스템 오류] " + e.getMessage());
                    if (e.errorCode() != 0) {
                        System.out.printf("  MySQL ERROR %d / SQLSTATE %s%n",
                                e.errorCode(), e.sqlState());
                    }
                    printTrace(e);

                } catch (NumberFormatException e) {
                    /*
                     * (3) 사용자가 금액에 숫자가 아닌 값을 넣었다.
                     *     언체크 예외지만 원인이 명확하므로 친절한 안내로 바꿔 준다.
                     */
                    System.out.println("  [입력 오류] 금액은 숫자로만 입력해 주세요: " + e.getMessage());

                } catch (RuntimeException e) {
                    /*
                     * (4) 예상하지 못한 예외 — NullPointerException 등.
                     *     여기 걸리는 것은 우리 쪽 버그라는 뜻이므로 가장 자세히 남긴다.
                     *     이 catch 가 없으면 프로그램이 그냥 죽어 버려서,
                     *     콘솔 앱이라면 사용자가 메뉴로 돌아오지 못한다.
                     */
                    System.out.println("  [예상치 못한 오류] "
                            + e.getClass().getSimpleName() + " — " + e.getMessage());
                    printTrace(e);
                }
            }
        }
    }

    /**
     * 개발 환경에서만 스택 트레이스를 찍는다.
     *
     * <p><b>스택 트레이스 읽는 법.</b> 위에서부터 읽는다.
     * 맨 윗줄이 오류가 <b>직접 난 자리</b>이고, 아래로 갈수록
     * 그 메서드를 호출한 순서가 거꾸로 나온다 (나중에 호출된 것이 위).
     * <pre>
     * java.lang.NullPointerException
     *   at kr.doolee.dab.service.TransactionService.execute(TransactionService.java:118)  ← 여기서 터졌다
     *   at kr.doolee.dab.Main.run(Main.java:146)                                          ← 여기가 불렀다
     *   at kr.doolee.dab.Main.main(Main.java:71)                                          ← 시작점
     * Caused by: java.sql.SQLException: ...                                               ← 진짜 원인
     * </pre>
     * {@code Caused by:} 가 중요하다. 우리가 감싼 예외 아래에 원래 예외가 붙는다.
     * 그래서 {@code new DataAccessException("실패", e)} 처럼 원인을 꼭 넘겨야 한다.
     *
     * <p>운영 환경에서는 찍지 않는다. 클래스·패키지 구조와 SQL 이 그대로 노출된다.
     *
     * @param e 출력할 예외
     */
    private static void printTrace(Throwable e) {
        if (AppEnv.isDev()) {
            System.out.println("  --- 스택 트레이스 (개발 환경에서만 출력) ---");
            e.printStackTrace(System.out);
        } else {
            System.out.println("  자세한 내용은 서버 로그를 확인해 주세요.");
        }
    }

    private static void printMenu() {
        System.out.println();
        System.out.println("==========================================");
        System.out.println(" 사망자 명의 금융거래 신속차단 - 콘솔");
        System.out.println("==========================================");
        System.out.println(" 1. 계좌 조회");
        System.out.println(" 2. 차단 판정 시뮬레이션 (잔액 변경 없음)");
        System.out.println(" 3. 입금");
        System.out.println(" 4. 출금");
        System.out.println(" 5. 동시 출금 테스트 (스레드 2개)");
        System.out.println(" 6. 커넥션 풀 상태 보기");
        System.out.println(" 0. 종료");
        System.out.print  (" 선택 > ");
    }

    private static String ask(Scanner sc, String label) {
        System.out.print("  " + label + " > ");
        return sc.nextLine().trim();
    }

    /**
     * 금액 입력을 읽는다.
     *
     * @param sc 입력 스캐너
     * @return 금액
     * @throws NumberFormatException 숫자가 아닌 값이 들어온 경우
     */
    private static BigDecimal askAmount(Scanner sc) {
        String raw = ask(sc, "금액");
        BigDecimal amt = new BigDecimal(raw);      // 숫자가 아니면 여기서 예외
        if (amt.signum() <= 0) {
            // DB 의 ck_txn_amt CHECK (txn_amt > 0) 와 같은 조건을 미리 걸러 준다.
            // DB 까지 보내서 3819 를 받아도 되지만, 왕복을 아끼고 안내도 친절해진다.
            throw new BusinessException("X999", "금액은 0보다 커야 합니다");
        }
        return amt;
    }

    private static void showAccount(AccountRepository repo, String acctNo) {
        try (Connection conn = Db.getConnection()) {
            Account a = repo.findByNo(conn, acctNo);
            System.out.println("  " + (a == null ? "계좌가 없습니다." : a.toString()));
        } catch (SQLException e) {
            // 계층 체계를 지킨다. 여기서 generic RuntimeException 을 던지면
            // 위의 catch 들이 구분을 못 한다.
            throw new DataAccessException("계좌 조회 실패: " + acctNo, e);
        }
    }

    private static void simulate(TransactionService svc, Scanner sc) {
        String acctNo = ask(sc, "계좌번호");
        TxnType type  = readType(sc);
        Decision d    = svc.simulate(acctNo, type, LocalDateTime.now());
        System.out.println("  판정 결과 : " + d);
        if (d.appliedRuleId() != null) {
            System.out.println("  적용 규칙 : rule_id=" + d.appliedRuleId());
        }
        if (d.blockStartDtm() != null) {
            System.out.println("  차단 개시 : " + d.blockStartDtm());
        }
    }

    private static void run(TransactionService svc, Scanner sc, TxnType type) {
        String acctNo = ask(sc, "계좌번호");
        BigDecimal amt = askAmount(sc);
        TxnResult r = svc.execute(acctNo, type, amt, "30", type.label() + " (콘솔)");
        System.out.println("  " + r);
    }

    /**
     * 거래유형을 읽는다.
     *
     * @param sc 입력 스캐너
     * @return 거래유형
     * @throws IllegalArgumentException 알 수 없는 코드를 입력한 경우
     */
    private static TxnType readType(Scanner sc) {
        System.out.println("  거래유형: DEP 입금 / WDR 출금 / TRO 이체출금 / "
                + "ATO 자동이체출금 / CLS 해지 / LON 대출");
        String code = ask(sc, "거래유형 코드").toUpperCase();
        try {
            return TxnType.of(code);
        } catch (IllegalArgumentException e) {
            // enum 이 던지는 예외를 그대로 올리면 "알 수 없는 거래유형 코드: XXX" 만 나온다.
            // 사용자가 다음에 무엇을 해야 하는지까지 알려 주는 쪽이 낫다.
            throw new BusinessException("X999",
                    "거래유형 코드가 올바르지 않습니다: " + code + " (DEP/WDR/TRO/ATO/CLS/LON 중 하나)");
        }
    }

    /**
     * 같은 계좌에서 두 스레드가 동시에 출금한다.
     *
     * <p>{@code FOR UPDATE} 가 없으면 갱신 손실이 나고, 있으면 한 쪽이 기다렸다가
     * 정확히 처리된다. {@code concurrency/03_lost_update.sql} 과
     * {@code 04_withdraw_fixed.sql} 을 자바로 재현한 것이다.
     *
     * <p><b>스레드 안에서 생긴 예외는 밖으로 전파되지 않는다.</b>
     * 그래서 각 스레드가 자기 예외를 직접 잡아 출력해야 한다.
     * 잡지 않으면 조용히 죽고, 메인은 아무 일도 없었다고 생각한다.
     */
    private static void concurrentTest(TransactionService svc, AccountRepository repo, Scanner sc) {
        String acctNo = ask(sc, "계좌번호");
        BigDecimal amt = askAmount(sc);

        System.out.println("  --- 시작 전 ---");
        showAccount(repo, acctNo);
        System.out.println("  " + Db.poolStatus());

        Runnable job = () -> {
            String who = Thread.currentThread().getName();
            try {
                TxnResult r = svc.execute(acctNo, TxnType.WDR, amt, "30", who + " 동시출금");
                System.out.println("  [" + who + "] " + r);
            } catch (RuntimeException e) {
                System.out.println("  [" + who + "] 실패 — " + e.getMessage());
                printTrace(e);
            }
        };

        Thread a = new Thread(job, "A");
        Thread b = new Thread(job, "B");
        a.start();
        b.start();
        try {
            a.join();
            b.join();
        } catch (InterruptedException e) {
            // InterruptedException 은 체크 예외다. 삼키면 "중단 요청"이 사라지므로
            // 반드시 인터럽트 상태를 되돌려 놓는다.
            Thread.currentThread().interrupt();
            System.out.println("  대기 중 인터럽트가 걸렸습니다.");
        }

        System.out.println("  --- 종료 후 ---");
        showAccount(repo, acctNo);
        // 스레드 2개가 동시에 돌았으므로 total 이 2 이상으로 늘어나 있고,
        // 끝난 뒤에는 둘 다 반납되어 active=0 이 된다.
        System.out.println("  " + Db.poolStatus());
        System.out.println("  기대값: 시작잔액 - (출금액 x 성공한 건수)");
    }
}
