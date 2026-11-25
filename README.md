# PocketSpot

태블릿에서 버튼 한 번으로 주머니 속 폰의 핫스팟을 켜고, 바로 그 핫스팟에 붙는 안드로이드 앱. 루팅 불필요.

> **English summary.** PocketSpot turns on your phone's Wi-Fi hotspot from a tablet with one tap
> (quick settings tile), then joins it. The tablet sends a short signed Bluetooth LE beacon; the
> phone's background scan wakes the app, which starts tethering through a privileged helper (ADB
> or [Shizuku](https://shizuku.rikka.app/)). No pairing over Bluetooth, no internet permission
> beyond 127.0.0.1, no root. Tap again to turn the hotspot off. Install the same APK on both
> devices and enter the same pairing code. The UI is in Korean.

## 만든 이유

- SIM 없는 태블릿을 밖에서 쓰려면 매번 폰을 꺼내 핫스팟을 켜고 태블릿에서 연결해야 함
- 구글의 인스턴트 핫스팟은 버스나 지하철처럼 이동이 잦은 곳에서 20분 정도마다 끊김
- 직접 켠 일반 핫스팟은 같은 환경에서도 끊기지 않음 → 일반 핫스팟을 원격으로 켜는 방식으로 해결

## 특징

- **타일 한 번으로 켜고 끄기**: 태블릿 빠른 설정의 "폰 핫스팟" 타일을 누르면 폰 핫스팟이 켜짐. 폰 핫스팟에 연결된 동안 타일이 켜진 상태로 남고, 다시 누르면 핫스팟이 꺼짐
- **폰 핫스팟 우선 연결**: 태블릿이 다른 와이파이에 붙어 있어도 폰 핫스팟으로 옮겨 붙음. 누른 뒤 연결까지 실측 6-8초
- **폰은 그대로 주머니에**: 화면이 꺼진 폰이 1-2초 안에 신호를 받음. 앱을 켜 둘 필요 없음
- **블루투스 짝 맺기 불필요**: 서명된 짧은 신호만 주고받음. 두 기기에 같은 페어링 코드만 넣으면 됨
- **ADB와 Shizuku 둘 다 지원**: 일반 앱은 핫스팟을 켤 수 없어서 둘 중 하나의 권한을 빌려 씀
- **폰의 핫스팟 설정을 그대로 사용**: 이름, 비밀번호, 대역은 폰 설정에서 정한 그대로

## 동작 방식

```
태블릿                                   폰
[타일 탭] ──BLE 신호 10초 (서명)──▶ [블루투스 칩이 걸러서 앱을 깨움]
                                        │ 서명 확인
                                        ▼
                                   [권한 도우미 (ADB 또는 Shizuku)]
                                        │ 핫스팟 켜기
[권한 도우미] ◀──── 와이파이 ──── [핫스팟]
 3초 뒤 바로 접속 시작
```

| 단계 | 누른 뒤 걸린 시간 (Pixel 9 Pro, 화면 꺼짐) |
|---|---|
| 폰이 신호 받음 | 약 1.4초 |
| 폰 핫스팟 켜짐 | 약 3.8초 |
| 태블릿 연결 완료 (우선 연결 사용) | 약 6-8초 |

## 요구 사항

| | 조건 |
|---|---|
| 폰 | Android 11 이상, 블루투스 LE, 모바일 데이터와 핫스팟 사용 가능 |
| 태블릿 | Android 11 이상, 블루투스 LE 신호 보내기(advertising) 지원 |
| 권한 도우미 | 폰: ADB 또는 Shizuku 필수. 태블릿: 우선 연결과 타일 끄기를 쓸 때만 필요 |

- 확인한 조합: Pixel 9 Pro (Android 17) + OPPO Pad mini (ColorOS 17)
- 두 기기는 서로 블루투스가 닿는 거리에 있어야 함

## 설치

[Releases](../../releases)에서 `PocketSpot.apk`를 받아 **폰과 태블릿 둘 다** 설치. 처음 열 때 블루투스 권한(근처 기기)을 허용.

## 설정

### 1. 폰

1. PocketSpot을 열고 역할을 **핫스팟을 켜는 쪽 (폰)** 으로 선택
2. 페어링 코드가 자동으로 생김. 원하는 코드로 바꿔도 됨 (영문과 숫자 8자 이상)
3. 권한 도우미를 하나 켬 (아래 둘 중 하나)
4. 앱 화면에 `ADB 도우미: 실행 중` 또는 `Shizuku: 권한 있음`, `블루투스 감시: 감시 중`이 보이면 완료
5. 권장: 폰 설정의 핫스팟 메뉴에서 **연결된 기기가 없으면 자동으로 끄기** 켜기

#### 권한 도우미 A: ADB

PC에 연결해 한 번 실행. 폰에서 앱을 한 번 연 뒤에 실행해야 함 (앱이 시작 스크립트를 만들어 둠).

```sh
adb shell sh /sdcard/Android/data/io.github.dqot.pocketspot/files/start.sh
```

- 앱 화면의 **명령 복사** 버튼으로도 같은 명령을 복사할 수 있음
- 폰을 재부팅하면 다시 실행해야 함
- USB 디버깅을 끄거나 PC 파일 전송 모드로 바꾸면 ADB가 다시 시작되며 도우미가 꺼질 수 있음

#### 권한 도우미 B: Shizuku

1. [Shizuku](https://shizuku.rikka.app/)를 설치하고 시작 (무선 디버깅으로 PC 없이 시작 가능)
2. PocketSpot의 **Shizuku 권한 요청** 버튼 → 허용

- 재부팅 뒤 PC 없이 폰 안에서 Shizuku를 다시 시작할 수 있음
- 둘 다 켜져 있으면 ADB 도우미를 먼저 사용

### 2. 태블릿

1. PocketSpot을 열고 역할을 **부르는 쪽 (태블릿)** 으로 둠
2. 폰에 보이는 페어링 코드를 그대로 입력하고 **저장**
3. 빠른 설정 편집에서 **폰 핫스팟** 타일 추가
4. 선택: **폰 핫스팟 우선 연결**
   - 이 태블릿에도 권한 도우미(ADB 또는 Shizuku)를 켬
   - "사용"을 켜고 폰 핫스팟 이름과 비밀번호를 입력 (입력 즉시 저장됨)
   - 켜면 타일이 연결 상태를 보여 주고, 연결된 상태에서 누르면 핫스팟을 끔

도우미가 없으면 태블릿은 평소처럼 저장된 와이파이 중에서 자동으로 붙음. 집처럼 이미 다른 와이파이에 붙어 있으면 그대로 머묾.

## 배터리

| | 평소 | 누를 때 |
|---|---|---|
| 태블릿 | 아무것도 실행 안 함 | 10초 동안 블루투스 신호 |
| 폰 | 블루투스 칩의 저전력 필터 스캔 (약 5초마다 0.5초), 15분마다 스캔 등록 확인, 도우미는 대기 | 앱이 잠깐 깨어나 도우미에 요청 |

체감할 만한 배터리 소모 없음.

## 보안

- 신호와 도우미 요청은 페어링 코드에서 만든 키로 서명(HMAC-SHA256). 같은 코드를 넣은 기기만 서로 알아봄
- 신호에 시각과 일회용 번호가 들어 있어, 녹음해 두었다가 다시 틀어도 동작하지 않음
- ADB 도우미는 127.0.0.1에서만 받고, 서명이 맞는 요청만 처리. 키는 앱의 `KeyProvider`에서 읽는데, 이 provider는 shell 사용자에게만 답함
- 페어링 코드는 길고 무작위일수록 안전. 앱의 **새 코드 만들기**(16자) 권장. `1111-1111`처럼 쉬운 코드는 근처의 누군가가 핫스팟을 켜고 끌 수 있음 (핫스팟 비밀번호 없이는 접속 불가)
- 인터넷 권한은 127.0.0.1의 도우미와 통신하는 데만 사용. 외부로 아무것도 보내지 않음
- 태블릿에 저장한 핫스팟 비밀번호는 앱 내부 저장소에만 있고, 도우미의 `cmd wifi connect-network`에만 전달됨

## 알려진 한계

- 권한 도우미는 재부팅하면 꺼짐 (ADB: PC로 다시 실행, Shizuku: 폰에서 다시 시작)
- USB 디버깅(개발자 옵션)을 끄면 ADB 도우미와 Shizuku 모두 대부분 멈춤. 일부 금융 앱은 개발자 옵션이 켜져 있으면 실행을 거부함
- 제조사가 핫스팟이나 블루투스 백그라운드 스캔을 막는 기기에서는 동작하지 않을 수 있음
- 신호는 블루투스 시험용 제조사 ID(0xFFFF)를 사용
- 앱 화면은 한국어만 지원

## 비슷한 프로젝트

- [EasySpot](https://github.com/EasySpotApp/EasySpot): 폰 쪽을 같은 아이디어로 구현(Shizuku, BLE GATT). 부르는 쪽은 PC용 파이썬 프로그램이나 nRF Connect 사용
- [Tetherbug](https://f-droid.org/packages/ie.xav.tetherbug/): 맥이 와이파이를 잃으면 안드로이드 폰 핫스팟을 켬
- 구글 인스턴트 핫스팟, 삼성 자동 핫스팟: 기본 기능

PocketSpot은 안드로이드 태블릿에서 탭 한 번, 우선 연결, ADB 지원에 초점.

## 빌드

Gradle 없이 Android SDK 도구만 사용.

```sh
./build.sh            # build/PocketSpot.apk
./build.sh install    # 빌드 후 adb install -r
```

- JDK 17 이상, Android SDK `platforms/android-36`과 build-tools 필요. 위치 지정은 `build.sh` 첫머리 참고
- 첫 빌드 때 `keys/release.jks` 서명 키를 만듦 (git에서 제외)
- 앱을 업데이트한 뒤에는 ADB 도우미 시작 명령을 다시 실행해야 새 코드로 바뀜

## 라이선스

[MIT](LICENSE). 포함된 Shizuku 클라이언트 라이브러리도 MIT ([libs/README.md](libs/README.md)).
