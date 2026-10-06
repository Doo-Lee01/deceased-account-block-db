package kr.doolee.dab.service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

import kr.doolee.dab.domain.Account;
import kr.doolee.dab.domain.AccountTxn;
import kr.doolee.dab.domain.Decision;
import kr.doolee.dab.domain.DrCr;
import kr.doolee.dab.domain.TxnType;
import kr.doolee.dab.repository.AccountRepository;
import kr.doolee.dab.repository.AccountTxnRepository;
import kr.doolee.dab.repository.ChannelLogRepository;
import kr.doolee.dab.support.BlockedException;
import kr.doolee.dab.support.BusinessException;
import kr.doolee.dab.support.DataAccessException;
import kr.doolee.dab.support.Db;
import kr.doolee.dab.support.TxTemplate;

/**
 * 거래 처리 — 판정, 잔액 변경, 원장 적재를 하나의 트랜잭션으로 묶는다.
 *
 * <h2>동시성 설계</h2>
 * {@code concurrency/} 실습에서 확인한 결론을 코드로 옮긴 부분이다.
 *
 * <p><b>(1) 잔액을 바꿀 계좌는 제일 먼저 {@code FOR UPDATE} 로 잠근다.</b>
 * <ul>
 *   <li>갱신 손실 방지 — 두 세션이 같은 잔액을 읽고 각자 차감하면
 *       나중에 쓴 값이 먼저 쓴 값을 덮는다. 오류는 나지 않는다</li>
 *   <li>데드락 방지 — {@code acct_txn} 에 INSERT 하면 외래키({@code fk_txn_acct})
 *       때문에 부모 {@code acct} 행에 <b>공유(S) 락</b>이 걸린다.
 *       두 세션이 S 락을 각자 쥔 채 UPDATE 로 <b>배타(X) 락</b>을 요구하면
 *       서로를 기다려 {@code ERROR 1213} 이 된다.
 *       처음부터 X 락으로 시작하면 한 세션이 줄을 서서 기다릴 뿐이다</li>
 * </ul>
 *
 * <p><b>(2) 그래도 데드락이 날 수 있으므로</b> {@link TxTemplate} 이 자동 재시도한다.
 *
 * <p><b>(3) 채널 로그는 별도 트랜잭션으로 적재한다.</b>
 * 업무 트랜잭션과 묶으면 '거절'이 롤백되면서 거절 기록까지 사라진다.
 * 거절도 남아야 하는 것이 감사 요건이다.
 *
 * @author Doo-Lee
 * @version 1.1
 */
public class TransactionService {

    private static final DateTimeFormatter SEQ_FMT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    /**
     * 번호 일련값. 여러 스레드가 동시에 번호를 받아도 겹치지 않도록
     * {@code AtomicInteger} 를 쓴다. 평범한 {@code int ++} 는 원자적이지 않아
     * 두 스레드가 같은 번호를 받을 수 있다.
     */
    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private final AccountRepository    accountRepository;
    private final AccountTxnRepository txnRepository;
    private final ChannelLogRepository channelRepository;
    private final BlockDecisionService decisionService;
    private final TxTemplate           tx = new TxTemplate();

    /**
     * @param accountRepository    계좌 저장소
     * @param txnRepository        거래원장 저장소
     * @param channelRepository    채널계 저장소
     * @param decisionService      차단 판정 서비스
     */
    public TransactionService(AccountRepository accountRepository,
                              AccountTxnRepository txnRepository,
                              ChannelLogRepository channelRepository,
                              BlockDecisionService decisionService) {
        this.accountRepository = accountRepository;
        this.txnRepository     = txnRepository;
        this.channelRepository = channelRepository;
        this.decisionService   = decisionService;
    }

    /**
     * 판정만 해 본다. 잔액을 건드리지 않고 락도 걸지 않는다.
     *
     * @param acctNo 계좌번호
     * @param type   거래유형
     * @param at     거래일시
     * @return 판정 결과
     * @throws BusinessException   계좌가 없는 경우
     * @throws DataAccessException DB 오류
     */
    public Decision simulate(String acctNo, TxnType type, LocalDateTime at) {
        try (Connection conn = Db.getConnection()) {
            Account acct = accountRepository.findByNo(conn, acctNo);
            if (acct == null) {
                throw new BusinessException("X999", "존재하지 않는 계좌입니다: " + acctNo);
            }
            return decisionService.judge(conn, acct.custNo(), type, at);
        } catch (SQLException e) {
            // try-with-resources 의 close() 가 던진 SQLException 이 여기로 온다.
            throw new DataAccessException("판정 시뮬레이션 실패: " + acctNo, e);
        }
    }

    /**
     * 입금/출금을 처리한다.
     *
     * <p>성공이든 거절이든 {@link TxnResult} 를 돌려주고, 채널 로그는 항상 남긴다.
     * 예외를 밖으로 던지는 것은 <b>시스템 장애</b>일 때뿐이다.
     *
     * @param acctNo 계좌번호
     * @param type   거래유형
     * @param amount 거래금액 (항상 양수)
     * @param chnlCd 채널코드 (10 창구 / 20 ATM / 30 인터넷뱅킹 / 40 모바일)
     * @param memo   적요
     * @return 처리 결과
     * @throws DataAccessException DB 장애이거나 데드락 재시도를 모두 소진한 경우
     */
    public TxnResult execute(String acctNo, TxnType type, BigDecimal amount,
                             String chnlCd, String memo) {

        long started = System.currentTimeMillis();
        final LocalDateTime now = LocalDateTime.now();
        final String txnUniqueNo = nextNo("T");
        final String reqUniqueNo = nextNo("R");

        // 채널 로그에 쓸 고객번호를 미리 읽어 둔다 (락 없는 단순 조회).
        String custNo = readCustNo(acctNo);

        TxnResult result;
        try {
            result = tx.execute(conn -> {

                /* ---- (1) 제일 먼저 계좌 행을 X 락으로 잠근다 ---- */
                Account acct = accountRepository.findByNoForUpdate(conn, acctNo);
                if (acct == null) {
                    throw new BusinessException("X999", "존재하지 않는 계좌입니다: " + acctNo);
                }

                /* ---- (2) 차단 판정 ---- */
                Decision decision = decisionService.judge(conn, acct.custNo(), type, now);
                if (decision.isBlocked()) {
                    // 판정 근거를 통째로 들고 올라가기 위해 전용 예외를 쓴다.
                    throw new BlockedException(decision);
                }

                /* ---- (3) 잔액 계산. 락을 쥔 상태이므로 읽은 값이 그대로 유효하다 ---- */
                BigDecimal after;
                if (type.drCr() == DrCr.I) {
                    after = acct.balanceAmt().add(amount);
                } else {
                    if (!acct.canAfford(amount)) {
                        throw new BusinessException("E030", "잔액이 부족합니다");
                    }
                    after = acct.balanceAmt().subtract(amount);
                }

                /* ---- (4) 잔액 수정 + 원장 적재 ---- */
                accountRepository.updateBalance(conn, acctNo, after);
                txnRepository.insert(conn, new AccountTxn(
                        txnUniqueNo, acctNo, now, type, amount, after,
                        chnlCd, null, "0000", memo));

                return TxnResult.ok(decision, txnUniqueNo, after);
            });

        } catch (BlockedException e) {
            // 차단 — 판정 근거가 예외 안에 들어 있다.
            result = TxnResult.fail(e.decision(), e.decision().rspCd(), e.decision().reason());

        } catch (BusinessException e) {
            // 그 밖의 업무 거절 (잔액부족, 계좌 없음)
            result = TxnResult.fail(Decision.allow("판정 통과 후 거절"), e.rspCd(), e.getMessage());
        }
        // DataAccessException 은 일부러 잡지 않는다.
        // 시스템 장애는 이 계층이 해결할 수 없고, Main 이 운영자에게 알려야 한다.

        int elapsed = (int) (System.currentTimeMillis() - started);
        writeChannelLog(reqUniqueNo, chnlCd, custNo, acctNo, type, amount, now, result, elapsed);
        return result;
    }

    /* ------------------------------------------------------------------ */

    /**
     * 채널 로그용 고객번호를 읽는다.
     *
     * <p><b>예외를 삼키지 않는다.</b> 예전에는 여기서
     * {@code catch (SQLException e) { return null; }} 로 조용히 넘겼는데,
     * 그러면 DB 가 죽었을 때도 "고객번호 모름"으로 처리되어
     * 진짜 원인이 스택 트레이스째로 사라진다.
     * 조회 실패와 "계좌가 없음"은 다른 상황이므로 구분해서 올려 보낸다.
     *
     * @param acctNo 계좌번호
     * @return 고객번호. 계좌가 없으면 {@code null}
     * @throws DataAccessException 조회 자체가 실패한 경우
     */
    private String readCustNo(String acctNo) {
        try (Connection conn = Db.getConnection()) {
            Account a = accountRepository.findByNo(conn, acctNo);
            return a == null ? null : a.custNo();
        } catch (SQLException e) {
            throw new DataAccessException("고객번호 조회 실패: " + acctNo, e);
        }
    }

    /**
     * 요청·판정·응답을 채널계에 적재한다. 업무 트랜잭션과 <b>분리된</b> 트랜잭션이다.
     *
     * <p>로그 적재가 실패해도 이미 확정된 업무 결과를 뒤집지는 않는다.
     * 그래서 여기서만 예외를 잡아 경고로 바꾼다. 다른 곳에서는 이렇게 하지 않는다.
     */
    private void writeChannelLog(String reqNo, String chnlCd, String custNo, String acctNo,
                                 TxnType type, BigDecimal amount, LocalDateTime now,
                                 TxnResult result, int elapsedMs) {
        try {
            tx.execute(conn -> {
                long reqId = channelRepository.insertRequest(conn, reqNo, chnlCd, custNo,
                        acctNo, type, amount, now);
                channelRepository.insertDecision(conn, reqId, result.decision(),
                        LocalDateTime.now(), result.txnUniqueNo());
                channelRepository.insertResponse(conn, reqId, result.rspCd(),
                        LocalDateTime.now(), elapsedMs);
                return null;
            });
        } catch (RuntimeException e) {
            System.out.println("  [경고] 채널 로그 적재 실패 — " + e.getMessage());
            // 운영에서는 여기서 로그 프레임워크로 스택 트레이스를 남기고
            // 모니터링에 알림을 보낸다. 삼키고 끝내면 아무도 모른다.
            if (kr.doolee.dab.support.AppEnv.isDev()) {
                e.printStackTrace(System.out);
            }
        }
    }

    /**
     * 거래고유번호를 만든다. {@code T + yyyyMMddHHmmssSSS + 3자리} = 21자
     * ({@code VARCHAR(30)} 안에 들어간다).
     *
     * @param prefix 'T'(거래) 또는 'R'(요청)
     * @return 고유번호
     */
    private static String nextNo(String prefix) {
        int seq = COUNTER.incrementAndGet() % 1000;
        return prefix + LocalDateTime.now().format(SEQ_FMT) + String.format("%03d", seq);
    }
}
