package kr.doolee.dab.domain;

/** 차단상태. comm_code group_cd='BLK_ST'. */
public enum BlockStatus {
    SCHEDULED("10", "차단예정"),
    BLOCKED  ("20", "차단중"),
    RELEASED ("30", "해제");

    private final String code;
    private final String label;

    BlockStatus(String code, String label) { this.code = code; this.label = label; }

    public String code()  { return code; }
    public String label() { return label; }

    public static BlockStatus of(String code) {
        for (BlockStatus s : values()) {
            if (s.code.equals(code)) return s;
        }
        throw new IllegalArgumentException("알 수 없는 차단상태 코드: " + code);
    }
}
