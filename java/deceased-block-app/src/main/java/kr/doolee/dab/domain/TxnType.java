package kr.doolee.dab.domain;

/**
 * 거래유형. comm_code.code_detail 의 group_cd='TXN_TP' 와 1:1 대응한다.
 *
 * [OOP 포인트] DB 의 CHAR(4) 코드값을 자바에서 String 으로 들고 다니면
 * "WDR" 을 "WDW" 로 오타 내도 컴파일이 통과한다. enum 으로 감싸면
 * 잘못된 값은 컴파일 단계에서 걸린다. 이것이 '타입으로 막는다' 의 예다.
 */
public enum TxnType {
    DEP("DEP", "입금",       DrCr.I),
    TRI("TRI", "이체입금",   DrCr.I),
    WDR("WDR", "출금",       DrCr.O),
    TRO("TRO", "이체출금",   DrCr.O),
    ATO("ATO", "자동이체출금", DrCr.O),
    CLS("CLS", "계좌해지",   DrCr.O),
    LON("LON", "대출실행",   DrCr.O),
    INQ("INQ", "조회",       DrCr.O);

    private final String code;
    private final String label;
    private final DrCr drCr;

    TxnType(String code, String label, DrCr drCr) {
        this.code = code;
        this.label = label;
        this.drCr = drCr;
    }

    public String code()  { return code; }
    public String label() { return label; }
    /** 거래유형이 입금인지 출금인지는 유형 자체가 알고 있다. 호출자가 매번 판단하지 않는다. */
    public DrCr drCr()    { return drCr; }

    public static TxnType of(String code) {
        for (TxnType t : values()) {
            if (t.code.equals(code)) return t;
        }
        throw new IllegalArgumentException("알 수 없는 거래유형 코드: " + code);
    }
}
