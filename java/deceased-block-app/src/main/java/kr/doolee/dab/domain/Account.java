package kr.doolee.dab.domain;

import java.math.BigDecimal;

/**
 * core_bank.acct 한 행에 대응하는 값 객체(value object).
 *
 * [OOP 포인트 1] 모든 필드가 final 이다 (불변 객체).
 *   DB 에서 읽어온 스냅샷을 자바 쪽에서 몰래 고치지 못하게 막는다.
 *   잔액을 바꾸려면 반드시 DAO 를 통해 UPDATE 해야 한다.
 *
 * [OOP 포인트 2] 금액은 double 이 아니라 BigDecimal 이다.
 *   DECIMAL(18,2) <-> BigDecimal 이 정확한 짝이다.
 *   double 을 쓰면 0.1 + 0.2 != 0.3 이 되어 잔액이 1원씩 어긋난다.
 */
public final class Account {

    private final String     acctNo;
    private final String     custNo;
    private final String     productCd;
    private final BigDecimal balanceAmt;
    private final String     acctStatusCd;

    public Account(String acctNo, String custNo, String productCd,
                   BigDecimal balanceAmt, String acctStatusCd) {
        this.acctNo       = acctNo;
        this.custNo       = custNo;
        this.productCd    = productCd;
        this.balanceAmt   = balanceAmt;
        this.acctStatusCd = acctStatusCd;
    }

    public String     acctNo()       { return acctNo; }
    public String     custNo()       { return custNo; }
    public String     productCd()    { return productCd; }
    public BigDecimal balanceAmt()   { return balanceAmt; }
    public String     acctStatusCd() { return acctStatusCd; }

    /** 계좌 스스로 "이 금액을 감당할 수 있는가" 를 안다. 서비스가 잔액을 꺼내 비교하지 않는다. */
    public boolean canAfford(BigDecimal amount) {
        return balanceAmt.compareTo(amount) >= 0;
    }

    public boolean isNormal() { return "10".equals(acctStatusCd); }

    @Override
    public String toString() {
        return String.format("계좌 %s (고객 %s) 잔액 %,.0f원 상태 %s",
                acctNo, custNo, balanceAmt, acctStatusCd);
    }
}
