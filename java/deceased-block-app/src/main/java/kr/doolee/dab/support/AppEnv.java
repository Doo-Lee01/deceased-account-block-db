package kr.doolee.dab.support;

/**
 * 개발 환경과 운영 환경을 구분하는 스위치.
 *
 * <p><b>왜 필요한가.</b> 오류가 났을 때 개발 중에는 스택 트레이스를 전부 보고 싶지만,
 * 운영 중에는 보여 주면 안 된다. 스택 트레이스에는 클래스 이름, 패키지 구조,
 * 때로는 SQL 문장까지 그대로 찍힌다. 그것을 그대로 고객 화면에 띄우면
 * 시스템 내부 구조를 외부에 알려 주는 셈이 된다.
 *
 * <p>그래서 같은 예외를 두 가지로 다르게 다룬다.
 * <ul>
 *   <li>개발(DEV) — 스택 트레이스 전체 출력. 원인을 빨리 찾는 것이 목적</li>
 *   <li>운영(PROD) — 응답코드와 안내 문구만. 상세 내용은 서버 로그에만 남긴다</li>
 * </ul>
 *
 * <p>실무에서는 보통 실행 시 전달하는 설정값으로 정한다.
 * <pre>java -Dapp.env=PROD -jar app.jar</pre>
 *
 * @author Doo-Lee
 * @version 1.0
 */
public final class AppEnv {

    /** 실행 환경 구분. */
    public enum Mode { DEV, PROD }

    private static final Mode MODE =
            "PROD".equalsIgnoreCase(System.getProperty("app.env", "DEV"))
                ? Mode.PROD : Mode.DEV;

    private AppEnv() { }

    /**
     * @return 현재 실행 환경
     */
    public static Mode mode() { return MODE; }

    /**
     * @return 개발 환경이면 true — 스택 트레이스를 찍어도 되는 상황인지 판단에 쓴다
     */
    public static boolean isDev() { return MODE == Mode.DEV; }
}
