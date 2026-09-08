import pytest

from screener.filters import (
    exclusion_reason,
    is_common_stock,
    is_etf,
    is_etn,
    is_preferred,
    is_reit,
    is_spac,
)

# 실제 화면에 잘못 올라왔던 ETF 들
ETF_NAMES = [
    "1Q 단기금융액티브",
    "1Q 머니마켓액티브",
    "ACE 단기통안채",
    "ACE 머니마켓액티브",
    "HANARO 머니마켓액티브",
    "KIWOOM CD금리액티브(합성)",
    "KIWOOM 머니마켓액티브",
    "KODEX CD1년금리플러스액티브(합성)",
    "KODEX 200",
    "TIGER 미국S&P500",
    "RISE 200",
    "PLUS 고배당주",
    "SOL 미국배당다우존스",
    "KOSEF 국고채10년",
    "히어로즈 리츠이지스액티브",
]

# 일반 보통주 (걸러지면 안 되는 이름)
COMMON = [
    ("005930", "삼성전자"),
    ("000660", "SK하이닉스"),
    ("006800", "미래에셋대우"),      # 이름이 '우'로 끝나지만 보통주
    ("034730", "SK"),
    ("003490", "대한항공"),
    ("139480", "이마트"),
    ("011070", "LG이노텍"),
    ("128940", "한미약품"),
    ("001040", "CJ"),
    ("079160", "CJ CGV"),
    ("281820", "케이씨텍"),
    ("336260", "두산퓨얼셀"),
]


@pytest.mark.parametrize("name", ETF_NAMES)
def test_etf_detected(name):
    assert is_etf(name)
    assert not is_common_stock("069500", name)
    assert exclusion_reason("069500", name) == "ETF"


@pytest.mark.parametrize("code,name", COMMON)
def test_common_stock_kept(code, name):
    assert is_common_stock(code, name), f"{name} 이 잘못 제외됨"
    assert exclusion_reason(code, name) is None


def test_preferred_by_code():
    assert is_preferred("005935", "삼성전자우")
    assert is_preferred("005387", "현대차2우B")
    assert is_preferred("051915", "LG화학우")
    assert not is_preferred("005930", "삼성전자")
    assert exclusion_reason("005935", "삼성전자우") == "PREFERRED"


def test_etn_reit_spac():
    assert is_etn("삼성 레버리지 WTI원유 선물 ETN")
    assert is_etn("신한 인버스 2X 나스닥100 ETN(H)")
    assert is_reit("SK리츠")
    assert is_reit("롯데리츠")
    assert is_spac("엔에이치스팩29호")
    assert exclusion_reason("357120", "코람코라이프인프라리츠") == "REIT"
    assert exclusion_reason("456780", "하나금융스팩27호") == "SPAC"


def test_brand_prefix_does_not_overmatch():
    """브랜드 문자열이 이름 중간에 들어간 보통주는 ETF 로 보지 않는다."""
    assert not is_etf("에이스침대")     # ACE 로 시작하지 않음
    assert not is_etf("솔브레인")        # SOL 로 시작하지만 뒤에 문자가 이어짐
    assert not is_etf("파워로직스")      # 파워 뒤에 한글이 붙어 ETF 형식이 아님
    assert is_etf("파워 고배당저변동성")  # 브랜드 + 공백 형태만 ETF
