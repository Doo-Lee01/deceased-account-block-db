package kr.doolee.dab.support;

/**
 * <b>업무 규칙에 걸려 거절된 상황</b>을 나타내는 예외.
 *
 * <p>시스템 장애({@link DataAccessException})와 반드시 구분해야 한다.
 * 둘 다 "거래가 안 됐다"지만 성격이 완전히 다르다.
 *
 * <table border="1">
 *   <caption>두 예외의 차이</caption>
 *   <tr><th></th><th>BusinessException</th><th>DataAccessException</th></tr>
 *   <tr><td>원인</td><td>규칙대로 거절한 것</td><td>시스템이 고장난 것</td></tr>
 *   <tr><td>예시</td><td>사망자 계좌 출금, 잔액 부족</td><td>DB 접속 끊김, 제약 위반</td></tr>
 *   <tr><td>응답</td><td>D001 / E030 안내</td><td>X999 시스템 오류</td></tr>
 *   <tr><td>재시도</td><td>해도 소용없다</td><td>데드락이면 의미 있다</td></tr>
 *   <tr><td>알림</td><td>불필요</td><td>운영자 호출 대상</td></tr>
 * </table>
 *
 * <p>구분하지 않으면 사망자 계좌 출금 거절 때마다 장애 알림이 울리거나,
 * 반대로 진짜 DB 장애가 "정상 거절"로 묻혀 아무도 모르게 된다.
 *
 * <p>{@code RuntimeException} 을 상속한 <b>언체크 예외</b>다.
 * 체크 예외로 만들면 거절 가능성이 있는 모든 메서드에 {@code throws} 를 달아야 해서
 * 시그니처가 지저분해진다. 업무 거절은 "예외적 상황"이지 "컴파일러가 강제할 일"은 아니다.
 *
 * @author Doo-Lee
 * @version 1.1
 */
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String rspCd;

    /**
     * @param rspCd   comm_code.rsp_code 에 등록된 응답코드 (예: "D001", "E030")
     * @param message 고객에게 보여 줄 수 있는 설명
     */
    public BusinessException(String rspCd, String message) {
        super(message);
        this.rspCd = rspCd;
    }

    /** @return 응답코드 */
    public String rspCd() { return rspCd; }
}
