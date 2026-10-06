package kr.doolee.dab.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** core_bank.acct_txn 에 새로 적재할 거래원장 한 행. */
public final class AccountTxn {

    private final String        txnUniqueNo;
    private final String        acctNo;
    private final LocalDateTime txnDtm;
    private final TxnType       txnType;
    private final BigDecimal    txnAmt;
    private final BigDecimal    balanceAfterAmt;
    private final String        chnlCd;
    private final String        terminalId;
    private final String        rspCd;
    private final String        memo;

    public AccountTxn(String txnUniqueNo, String acctNo, LocalDateTime txnDtm,
                      TxnType txnType, BigDecimal txnAmt, BigDecimal balanceAfterAmt,
                      String chnlCd, String terminalId, String rspCd, String memo) {
        this.txnUniqueNo     = txnUniqueNo;
        this.acctNo          = acctNo;
        this.txnDtm          = txnDtm;
        this.txnType         = txnType;
        this.txnAmt          = txnAmt;
        this.balanceAfterAmt = balanceAfterAmt;
        this.chnlCd          = chnlCd;
        this.terminalId      = terminalId;
        this.rspCd           = rspCd;
        this.memo            = memo;
    }

    public String        txnUniqueNo()     { return txnUniqueNo; }
    public String        acctNo()          { return acctNo; }
    public LocalDateTime txnDtm()          { return txnDtm; }
    public TxnType       txnType()         { return txnType; }
    public BigDecimal    txnAmt()          { return txnAmt; }
    public BigDecimal    balanceAfterAmt() { return balanceAfterAmt; }
    public String        chnlCd()          { return chnlCd; }
    public String        terminalId()      { return terminalId; }
    public String        rspCd()           { return rspCd; }
    public String        memo()            { return memo; }
}
