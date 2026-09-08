"""종목 필터 — ETF / ETN / 리츠 / 스팩 / 우선주 제외 판별 (순수 함수, 단위 테스트 대상).

네이버 KOSPI 목록에는 일반 기업 외에 ETF·ETN·리츠·스팩이 모두 포함되어 있어
(약 2,300종목) 그대로 스크리닝하면 파킹형 ETF 처럼 매일 조금씩 오르는 상품이
정배열 상위를 채웁니다. 실제 KOSPI 상장 기업은 800~900개 수준입니다.
"""
from __future__ import annotations

import re

# --------------------------------------------------------------------------- ETF
# 국내 ETF 운용사 브랜드. 종목명이 이 이름으로 시작하면 ETF 로 간주합니다.
# 새 브랜드가 생기면 이 목록에 추가하세요.
# 영문 브랜드: 뒤에 공백이 없어도 ETF 로 본다 (예: "TIGER미국나스닥100").
#   영문 브랜드는 일반 기업명과 겹칠 일이 거의 없다.
ETF_BRANDS_LATIN: tuple[str, ...] = (
    "KODEX", "TIGER", "ACE", "RISE", "KBSTAR", "PLUS", "ARIRANG", "HANARO",
    "KOSEF", "KINDEX", "SOL", "TIMEFOLIO", "WOORI", "BNK", "VITA", "HK",
    "TREX", "FOCUS", "ITF", "UNICORN", "1Q", "KIWOOM", "KCGI", "DAISHIN",
)
# 한글 브랜드: 일반 기업명의 앞부분과 겹칠 수 있으므로(파워로직스, 하이록코리아,
#   흥국화재 …) 브랜드 뒤에 반드시 공백이 와야 ETF 로 본다.
ETF_BRANDS_HANGUL: tuple[str, ...] = (
    "히어로즈", "마이티", "네비게이터", "에셋플러스", "마이다스", "파워",
    "다올", "라이프", "흥국", "브이아이", "하이", "IBK",
)
ETF_BRANDS: tuple[str, ...] = ETF_BRANDS_LATIN + ETF_BRANDS_HANGUL

_ETF_PREFIX_RE = re.compile(
    r"^(?:(?:%s)|(?:%s)(?=\s))"
    % (
        "|".join(re.escape(b) for b in ETF_BRANDS_LATIN),
        "|".join(re.escape(b) for b in ETF_BRANDS_HANGUL),
    )
)

# --------------------------------------------------------------------------- 기타
_ETN_RE = re.compile(r"\bETN\b|ETN\(|ETN$")
_REIT_RE = re.compile(r"리츠$")
_SPAC_RE = re.compile(r"스팩")


def is_etf(name: str) -> bool:
    """ETF 여부 (운용사 브랜드로 시작하는지)."""
    return bool(_ETF_PREFIX_RE.match(name.strip().upper()))


def is_etn(name: str) -> bool:
    return bool(_ETN_RE.search(name.strip().upper()))


def is_reit(name: str) -> bool:
    return bool(_REIT_RE.search(name.strip()))


def is_spac(name: str) -> bool:
    """기업인수목적회사(스팩) — 예: '엔에이치스팩29호'."""
    return bool(_SPAC_RE.search(name.strip()))


def is_preferred(code: str, name: str = "") -> bool:
    """우선주 여부.

    국내 상장 보통주의 종목코드는 끝자리가 '0' 입니다. 우선주·신형우선주는
    끝자리가 5/7/9/K/L/M 등으로 끝나므로 코드로 판별하는 편이 정확합니다.
    ('미래에셋대우'처럼 이름이 '우'로 끝나는 보통주를 잘못 거르지 않습니다.)
    """
    c = code.strip()
    if len(c) == 6 and c.isdigit():
        return c[-1] != "0"
    # 코드 형식이 예상과 다르면 이름으로 보조 판별
    return bool(re.search(r"우[A-Z]?$|우\(전환\)$", name.strip()))


def is_common_stock(code: str, name: str) -> bool:
    """스크리닝 대상(일반 기업 보통주)이면 True."""
    return not (
        is_etf(name) or is_etn(name) or is_reit(name) or is_spac(name) or is_preferred(code, name)
    )


def exclusion_reason(code: str, name: str) -> str | None:
    """제외 사유 문자열 (로그·통계용). 대상이면 None."""
    if is_etf(name):
        return "ETF"
    if is_etn(name):
        return "ETN"
    if is_reit(name):
        return "REIT"
    if is_spac(name):
        return "SPAC"
    if is_preferred(code, name):
        return "PREFERRED"
    return None
