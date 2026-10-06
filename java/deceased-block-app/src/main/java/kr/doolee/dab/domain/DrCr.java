package kr.doolee.dab.domain;

/** 입출금 구분. acct_txn.dr_cr_cd CHECK (dr_cr_cd IN ('I','O')) 와 대응한다. */
public enum DrCr {
    I("I", "입금"),
    O("O", "출금");

    private final String code;
    private final String label;

    DrCr(String code, String label) { this.code = code; this.label = label; }

    public String code()  { return code; }
    public String label() { return label; }

    public static DrCr of(String code) {
        for (DrCr d : values()) {
            if (d.code.equals(code)) return d;
        }
        throw new IllegalArgumentException("알 수 없는 입출금 코드: " + code);
    }
}
