# KOSPI 정배열 스크리너

KOSPI 전종목 중 **10 · 20 · 60 · 120 · 200일 이동평균선이 정배열(MA10 > MA20 > MA60 > MA120 > MA200)** 인 종목을
매일 장 마감 후 자동으로 찾아 안드로이드 앱에서 확인하고, 신규 진입 종목이 있으면 앱이 알림을 띄우는 시스템입니다.
**데이터베이스나 푸시 서버 없이 GitHub 하나로 동작합니다.**

```
┌──────────────────┐ 평일 16:30 KST ┌──────────────────────┐        ┌──────────────┐
│ GitHub Actions   │ ─────────────▶ │ 같은 저장소의         │ ◀───── │ Android 앱   │
│ server/ (Python) │  JSON 저장·커밋 │ docs/data/latest.json │  읽기   │ (Kotlin)     │
└──────────────────┘                └──────────────────────┘        └──────────────┘
                                                             앱이 매일 17:00 스스로 확인 → 신규 진입 알림
```

| 폴더 | 내용 |
|---|---|
| `server/` | Python 스크리너 (`screener/`), 단위 테스트 (`tests/`) |
| `github-workflow/` | 자동 실행 워크플로 파일 — **push 전에 `.github/workflows/` 로 옮겨야 함** |
| `docs/data/` | 스크리닝 결과 JSON (Actions 가 매일 갱신) |
| `android/` | Android Studio 프로젝트 (Kotlin + Jetpack Compose) |

---

## 설정 절차 (전체 3단계)

### 1단계. PC 에서 스크리너 동작 확인

PowerShell 또는 CMD 에서:

```powershell
cd C:\Users\mouse\AndroidStudioProjects\KospiMaScreener\server
python -m venv .venv
.venv\Scripts\activate          # CMD 는 .venv\Scripts\activate.bat
pip install -r requirements-dev.txt
pytest                          # 39 passed 확인
python -m screener.main --limit 30
```

PowerShell 에서 "스크립트를 실행할 수 없으므로" 오류가 나면 `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned` 실행 후 다시.
마지막 명령이 성공하면 `docs\data\latest.json` 이 생기고 30종목 중 정배열 종목이 들어 있습니다.
(전체 실행은 `--limit` 없이. 약 900종목, 5~10분)

### 2단계. GitHub 에 올리고 자동 실행

1. 탐색기에서 `KospiMaScreener` 안에 `.github` 폴더 → 그 안에 `workflows` 폴더를 만들고, `github-workflow\daily-screen.yml` 을 `.github\workflows\` 로 옮깁니다. (폴더 이름을 `.github.` 으로 입력하면 점으로 시작하는 폴더가 만들어집니다.)
2. github.com → **New repository** → 이름 `kospi-ma-screener`, **Public** 선택 → Create.
   > 앱이 로그인 없이 결과 파일을 읽으려면 Public 이어야 합니다. 저장소에는 코드와 스크리닝 결과만 있고 비밀값은 없습니다.
3. PowerShell 에서 push:
   ```powershell
   cd C:\Users\mouse\AndroidStudioProjects\KospiMaScreener
   git init
   git add .
   git commit -m "KOSPI MA screener"
   git branch -M main
   git remote add origin https://github.com/<아이디>/kospi-ma-screener.git
   git push -u origin main
   ```
4. 저장소 **Settings → Actions → General → Workflow permissions** 에서 **Read and write permissions** 선택 → Save.
   (Actions 가 결과 JSON 을 저장소에 commit 하기 위해 필요)
5. **Actions** 탭 → `Daily KOSPI MA screen` → **Run workflow** (limit 은 0) → 5~10분 후 초록 체크.
   완료되면 저장소의 `docs/data/latest.json` 이 최신 결과로 바뀌어 있습니다.
6. 이후 평일 16:30(KST) 마다 자동 실행됩니다.

앱이 읽을 주소는 다음 형식입니다 (아이디만 바꾸면 됨):

```
https://raw.githubusercontent.com/<아이디>/kospi-ma-screener/main/docs/data
```

브라우저에서 `…/docs/data/latest.json` 을 열어 JSON 이 보이면 정상입니다.

### 3단계. 앱 빌드 및 설치

1. Android Studio → **Open** → `KospiMaScreener\android` 폴더 → Trust Project → Gradle Sync 완료 대기 (첫 회 5~15분, SDK 설치 안내가 뜨면 모두 Accept).
2. (선택) `android\local.properties` 에 `DATA_BASE_URL=https://raw.githubusercontent.com/<아이디>/kospi-ma-screener/main/docs/data` 한 줄을 추가하면 앱에 기본값으로 들어갑니다. 넣지 않아도 앱 첫 실행 때 입력 창이 뜹니다.
3. **Build → Make Project** → `BUILD SUCCESSFUL` 확인.
4. 폰 USB 연결(개발자 옵션 → USB 디버깅 켬) → 상단 기기 선택 → **▶ Run**. 또는 **Build → Build APK(s)** 로 `app-debug.apk` 를 만들어 폰에 복사해 설치.
5. 앱 첫 실행: 알림 권한 **허용** → 데이터 주소 입력 창에 위 주소 붙여넣기 → 저장.

---

## 앱 기능

- **리스트**: 기준일 · 정배열/신규 종목 수, 종목별 현재가·등락률·정배열 유지일수, `NEW` 배지, 아래로 당겨 새로고침
- **필터**: 신규 진입만 / 시가총액 하한 / 20일 평균 거래대금 하한 / 정렬(유지일수·등락률·시총·거래대금·종목명) — 설정은 앱에 저장
- **상세**: 최근 120일 캔들 + 5개 이동평균선 차트(드래그로 날짜별 값 확인), 이평 대비 이격률, 시총·거래대금
- **알림**: 매일 17:00 무렵 앱이 백그라운드에서 `latest.json` 을 확인해 신규 진입 종목이 있으면 알림. 앱을 직접 열어 새 데이터를 받았을 때도 같은 규칙으로 1회 알림
- **설정(톱니바퀴)**: 데이터 주소 변경

## 판별 로직 및 저장 규칙

- **정배열**: 당일 종가 기준 단순이동평균 `ma10 > ma20 > ma60 > ma120 > ma200` 모두 성립.
- **유지일수(`days_aligned`)**: 오늘까지 연속으로 정배열이 성립한 거래일 수. `= 1` 이면 **신규 진입(`is_new`)**.
- **거래대금(`avg_trading_value_20`)**: 종가 × 거래량의 20일 평균(근사, 원). **시가총액(`market_cap`)**: 네이버 종목 목록 기준(원).
- **스크리닝 대상은 일반 기업 보통주뿐입니다.** 네이버 KOSPI 목록에는 ETF·ETN·리츠·스팩이 모두 들어 있어(약 2,300종목) 그대로 두면 파킹형 ETF 처럼 매일 조금씩 오르는 상품이 정배열 상위를 채웁니다. `server/screener/filters.py` 가 다음을 제외합니다.
  - **ETF**: 운용사 브랜드로 시작하는 종목명 (KODEX, TIGER, ACE, RISE, KBSTAR, PLUS, HANARO, KOSEF, SOL, 1Q, KIWOOM, 히어로즈 …). 새 브랜드가 생기면 `ETF_BRANDS_LATIN` / `ETF_BRANDS_HANGUL` 목록에 추가하세요.
  - **ETN**(이름에 ETN), **리츠**(이름이 리츠로 끝남), **스팩**(이름에 스팩)
  - **우선주**: 종목코드 끝자리가 `0` 이 아닌 종목 (보통주 코드는 끝자리가 0). 이름이 아니라 코드로 판별하므로 `미래에셋대우` 처럼 이름이 '우'로 끝나는 보통주는 남습니다.
  - 제외 결과는 실행 로그와 `latest.json` 의 `summary.excluded` 에 사유별 개수로 기록됩니다. 전부 포함하려면 GitHub **Settings → Secrets and variables → Actions → Variables** 에 `EXCLUDE_NON_STOCK=false` 를 추가하세요.
- 기준일은 수집된 일봉 중 가장 최신 거래일. 휴장일에 실행되면 직전 거래일 결과로 갱신. 같은 날 재실행해도 신규 표시는 유지.
- `docs/data/prices/<code>.json` 에는 정배열 종목만 최근 320거래일 저장(차트에 MA200 까지 표시하기 위함). `docs/data/history/` 에 날짜별 결과 60일분 보관.

## 데이터 소스 관련 주의

- 기본은 **네이버 금융**(로그인 불필요): 종목 목록/시총은 `m.stock.naver.com` API, 일봉은 FinanceDataReader → 실패 시 `api.stock.naver.com` 차트 API.
  비공식 API 이므로 응답 형식이 바뀌면 `server/screener/data_sources.py` 의 `NaverProvider` 파서를 손봐야 할 수 있습니다.
- **pykrx 1.2.x 는 KRX 정보데이터시스템 로그인(`KRX_ID`/`KRX_PW`)이 필요**합니다. 네이버가 막힐 때의 폴백이며, GitHub Secrets 에 계정을 넣고 Variables 에 `DATA_PROVIDER=pykrx` 를 추가하면 강제할 수 있습니다.
- 개인 투자 참고용 도구이며 투자 판단의 책임은 사용자에게 있습니다.

## 문제 해결

| 증상 | 확인 |
|---|---|
| 앱 "아직 스크리닝 결과가 없습니다" | Actions 가 성공했는지, 브라우저에서 `latest.json` 에 `run_date` 가 채워져 있는지 |
| 앱 "HTTP 404" | 데이터 주소의 아이디/저장소 이름 오타, 저장소가 Private 인지 |
| Actions 에서 push 실패(403) | 2단계 4번 Workflow permissions 가 Read and write 인지 |
| Actions `모든 종목 조회 실패` | 네이버 API 변경 가능성 → PC 에서 `--limit 5` 로 재현 후 파서 수정 |
| 목록에 ETF 가 섞여 나옴 | 새 운용사 브랜드일 가능성 → `server/screener/filters.py` 의 브랜드 목록에 추가 후 push |
| 알림이 안 옴 | 알림 권한, 배터리 최적화(절전) 예외 설정, 신규 진입 종목이 실제로 있었는지 (`new_count`) |
