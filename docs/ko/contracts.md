# 계약

바뀌면 이미 설치된 환경이 깨지는 값들.

여기 적힌 모든 것의 원본은 코드입니다. 이 문서는 어떤 값이 중요한지, 어떤 파일들이 함께 바뀌어야 하는지를
알려줄 뿐이고, 혼자 고쳐도 되는 사본이 아닙니다.

대부분은 `src/test/kotlin/com/agentellij/architecture`의 `ContractSpec`과 `LayeringSpec`이 자동으로
확인합니다. 사람이 직접 확인해야 하는 것은 그렇다고 적어두었습니다.

## 저장된 설정

설정은 클래스 위치와 아무 상관 없는 컴포넌트 이름과 파일 이름으로 저장됩니다. 클래스가 다른 패키지로
옮겨갈 때도 둘 다 그대로 두었습니다.

| 값 | 유지해야 하는 것 |
|---|---|
| 컴포넌트 이름 | `com.agentellij.settings.AgentellIJSettings` |
| 저장 파일 | `AgentellIJSettings.xml` |
| 필드 이름 | `mode`, `activeAgent`, `agentPath`, `claudeAgentPath`, `codexAgentPath`, `customArgs` |

하나라도 바꾸면 기존 사용자의 설정이 조용히 사라집니다. IDE가 맞는 컴포넌트를 못 찾고 기본값을 쓰기
때문입니다.

컴포넌트 이름에는 지금 존재하지 않는 `com.agentellij.settings`가 들어 있습니다. 일부러 남긴 것이고,
`LayeringSpec`의 소스 검사가 문자열을 건너뛰므로 잔재로 보고되지 않습니다.

설정 모양을 바꾸려면 `platform/config/AgentellIJSettings.kt`, `core/settings/AgentPaths.kt`,
`core/settings/AgentModePolicy.kt`를 함께 고쳐야 합니다.

## 플러그인 등록

`src/main/resources/META-INF/plugin.xml`은 클래스, 식별자, 단축키를 문자열로 적습니다. 컴파일러가
어긋남을 잡아주지 못합니다.

| 값 | 유지해야 하는 것 |
|---|---|
| 설정 화면 식별자 | `com.agentellij.settings` |
| 액션 식별자 | `com.agentellij.AddDirectoryToContext`, `com.agentellij.AddToContext`, `com.agentellij.AddLinesToContext` |
| 메뉴 그룹 | `ProjectViewPopupMenu`, `EditorPopupMenu`, `EditorTabPopupMenu` |
| 단축키 | 기본 키맵은 `ctrl shift I`, macOS 키맵 둘은 `meta shift I`, 액션 셋 모두에 |
| 번들 키 | `src/main/resources/messages/AgentellIJBundle.properties`의 일곱 개 |

설정 화면 식별자는 지금 화면이 있는 패키지와 다릅니다. 맞춰서 바꾸면 설정 검색 기록과 그 화면을 여는
저장된 링크가 사라지고, 얻는 것은 없습니다.

등록된 클래스를 옮기면 같은 커밋에서 `plugin.xml`도 고쳐야 합니다. 나누면 플러그인이 로딩되지 않는
중간 커밋이 생겨, 그 지점에서는 아무것도 확인할 수 없습니다.

## 브리지 프로토콜

`src/main/resources/webui`에 들어 있는 웹 화면이 이 계약의 반대편입니다. 여기를 바꾸면 양쪽을 바꾸는
것입니다.

| 항목 | 값 |
|---|---|
| 주소 | 임의의 로컬 포트에서 `/idebridge/{세션}/{동작}?token={토큰}` |
| 동작 | 이벤트 구독은 `events`, 메시지 전송은 `send` |
| 메시지 종류 | `openFile`, `openUrl`, `reloadPath`, `kv.get`, `kv.update`, `model.get`, `model.update`, `settings.get`, `settings.update`, `agent.turnCompleted`, `agent.inputRequested` |
| 이벤트 봉투 | `type`, `payload`, `timestamp` |
| 응답 봉투 | `replyTo`, `ok`, 그리고 `error` 또는 `payload`, `timestamp` |
| 응답 없음 | 식별자 없는 메시지에는 아무 응답도 보내지 않는다 |
| 허용 출처 | http 또는 https의 로컬 호스트만 |
| 자원 | `/ui` 아래에서 제공. 자원 폴더를 벗어나는 경로는 거부 |

메시지 종류를 바꾸려면 `core/bridge/BridgeRoutes.kt`, `platform/bridge/MessageHandler.kt`,
`src/main/resources/webui/js/core/ide-bridge.js`를 함께 고쳐야 합니다.
두 에이전트 알림 메시지는 터미널 어댑터를 만드는 `core/agent/AgentCompletionHooks.kt`에서도 보냅니다.

## 터미널 알림 어댑터

AgentellIJ는 IDE 시스템 폴더의 `agentellij/completion` 아래에 크기가 고정된 어댑터 파일을 씁니다. 파일에는
토큰, 프로젝트 경로, 대화 상태, 결정된 에이전트 실행 파일이 들어가지 않습니다. 터미널마다 다른 값은 자식
프로세스 환경변수로 전달하고, 모드를 닫으면 완료 주소를 인증하던 브리지 세션을 없앱니다.

| 에이전트 | 메인 턴 완료 신호 | 구조화된 질문 신호 |
|---|---|---|
| Codex CLI | 세션 래퍼가 설정한 `notify`. 지원 이벤트는 `agent-turn-complete` | `request_user_input`과 일치하는 `PreToolUse` 수명 훅 |
| Claude Code | 추가 설정 파일에 든 시간 제한 `Stop` 명령 훅 | `AskUserQuestion`과 일치하는 `PreToolUse` 훅 |
| OpenCode | `session.idle`을 듣는 인라인 실행 플러그인 | `question.asked`를 듣는 같은 플러그인 |

Codex 명령 훅에는 Codex의 훅 신뢰 정책이 적용됩니다. 안정된 AgentellIJ 훅을 `/hooks`에서 한 번 검토해야
합니다. AgentellIJ는 훅 신뢰를 우회하거나 사용자의 전역 Codex 설정을 고치지 않습니다.

Terminal 화면은 대화형 셸이 시작된 뒤 지원 에이전트 세 개의 래퍼를 검색 경로 앞에 둡니다. 직접 선택한
TUI는 그 래퍼를 명시적으로 거칩니다. OpenCode 플러그인은 기존 인라인 설정을 덮지 않고 병합한
`OPENCODE_CONFIG_CONTENT`로 전달합니다. Terminal 프로필 자체는 AI 에이전트가 아니며 에이전트 알림
메시지를 보내지 않습니다.

## 줄 범위 표기

컨텍스트에 추가한 선택 영역은 `경로:시작-끝`으로 적히며, 줄 번호는 1부터입니다. 에이전트가 그 파일을
열어달라고 하면 브리지가 같은 표기를 되읽습니다.

`core/context/LineRangePath.kt`가 쓰고 `core/bridge/OpenFileRequest.kt`가 읽습니다. 속성 테스트가 둘을
왕복시키므로, 한쪽만 바뀌면 빌드가 실패합니다.

## 에이전트 상태 파일

이 파일들의 주인은 에이전트입니다. 플러그인은 웹 화면에 전달만 하고 아무것도 보관하지 않습니다.

| 항목 | 값 |
|---|---|
| 파일 | `kv.json`, `model.json`, `settings.json` |
| 위치 | `$XDG_STATE_HOME/opencode`, 변수가 없으면 `~/.local/state/opencode` |
| model 모양 | 언제나 `recent`, `favorite`, `variant` 세 키 |
| 병합 규칙 | `recent`와 `favorite`는 통째로 교체, `variant`와 키-값 상태는 키 단위 병합 |
| theme | `light` 또는 `dark` 문자열일 때만 유지 |
| 쓰기 | 임시 파일에 쓴 뒤 옮기고, 원자적 이동을 거부하는 곳에서는 일반 이동으로 |
| 읽을 수 없는 파일 | `.corrupt`, `.corrupt.1` 순으로 따로 복사하고 절대 덮어쓰지 않음 |

디스크에 상태를 두는 에이전트는 OpenCode뿐입니다. 나머지 셋은 빈 값을 돌려줍니다.

## 에이전트 기록

에이전트 계약에 필드를 더하려면 `core/agent/AgentProfile.kt`와 그 옆의 기록 넷을 함께 고쳐야 합니다.
`OpenCodeProfile`, `ClaudeCodeProfile`, `CodexCliProfile`, `TerminalProfile`입니다. 각 기록이 가진 값은
`AgentProfileContractSpec`이 고정합니다.

목록 순서도 중요합니다. 맞는 에이전트를 못 찾으면 첫 항목으로 떨어지므로, `core/agent/AgentCatalog.kt`의
순서를 바꾸면 새 사용자가 받는 에이전트가 바뀝니다.

## 환경 변수

| 변수 | 읽는 이유 |
|---|---|
| `AGENTELLIJ_BIN` | 에이전트 실행 파일. 설정 경로 다음, 에이전트 전용 변수 앞 |
| `OPENCODE_BIN`, `CLAUDE_CODE_BIN`, `CODEX_BIN` | 해당 에이전트의 실행 파일 |
| `OPENCODE_INSTALL_DIR`, `CODEX_INSTALL_DIR`, `CODEX_HOME`, `XDG_BIN_DIR` | 뒤질 폴더 |
| `NVM_DIR`, `FNM_DIR`, `NPM_CONFIG_PREFIX`, `NPM_CONFIG_USERCONFIG`, `XDG_DATA_HOME` | Node 도구 위치 |
| `LOCALAPPDATA`, `APPDATA`, `USERPROFILE` | Windows 설치 위치 |
| `XDG_STATE_HOME` | OpenCode가 대화 기록을 두는 곳 |
| `SHELL`, `ComSpec`, `PATH` | 에이전트를 실행할 셸과 그 셸이 찾을 수 있는 것 |

아래 변수는 호스트 설정으로 읽지 않고 AgentellIJ 터미널 자식에만 씁니다.

| 변수 | 자식에 전달하는 이유 |
|---|---|
| `AGENTELLIJ_NOTIFY_URL` | 세션 토큰이 든 로컬 완료 주소 |
| `AGENTELLIJ_CODEX_BIN`, `AGENTELLIJ_CLAUDE_BIN`, `AGENTELLIJ_OPENCODE_BIN` | 고정 세션 래퍼 뒤의 실제 실행 파일 |
| `AGENTELLIJ_OPENCODE_CONFIG_CONTENT` | 셸 시작 뒤에도 보존할 병합된 OpenCode 실행 설정 |
| `OPENCODE_CONFIG_CONTENT` | 물려받은 인라인 설정에 AgentellIJ 실행 플러그인을 더한 값 |
| `PATH` | 물려받은 검색 경로 앞에 고정 지원 에이전트 래퍼를 두기 위해 |

## 일부러 남긴 결함

아래는 모두 잘못된 동작이고, 모두 그대로 두었습니다. 고치면 사용자가 이미 기대고 있을지 모르는 동작이
바뀌기 때문입니다. 다음 사람이 실수로 빠뜨린 것으로 오해하지 않도록 적어둡니다.

| 결함 | 위치 |
|---|---|
| 경로 조회의 5초 제한이 그 앞의 읽기를 제한하지 못해, 출력이 없는 조회는 멈춰 있을 수 있다 | `platform/env/PathLookup.kt` |
| `XDG_STATE_HOME`이 빈 문자열이면 값으로 취급되어, 상태 폴더가 홈이 아니라 `/opencode`가 된다 | `core/agent/AgentStateLocation.kt` |
| 잘못된 퍼센트 인코딩이 들어오면 정해진 응답 대신 예외가 난다 | `core/bridge/StaticAssets.kt`, `core/bridge/BridgeRequest.kt` |
| 브리지 정지 경로를 부르는 곳이 프로덕션 코드에 없다 | `platform/bridge/IdeBridge.kt` |
| 브리지 서버와 라우트 처리기가 서로를 참조한다 | `platform/bridge` |
| 조용한 도우미가 여전히 모든 실패를 잡는다. 이제 진단 통로로 기록하므로 조용히 사라지지는<br>않지만, 잡는 범위는 넓다 | `core/util/QuietHelpers.kt` |
| 에이전트가 주소를 알리지 않고 끝나면 화면이 5분 제한까지 "시작 중"으로 남는다 | `platform/surface/GuiModeContent.kt` |
| 경로를 부분 문자열로 비교해서 `/usr/local/bin-old`가 `/usr/local/bin`을 가린다 | `core/launch/ProcessEnvironment.kt` |
| 검색 경로가 아예 없으면 끝에 빈 항목이 붙는데, POSIX는 이를 현재 폴더로 읽는다 | `core/launch/ProcessEnvironment.kt` |
| 다른 에이전트의 경로를 편집하던 중 설정 화면을 되돌리면 그 값이 엉뚱한 에이전트에 저장된다 | `platform/config/AgentellIJConfigurable.kt` |
| 재시도해도 이전 브리지 세션이 모드가 끝날 때까지 남는다 | `platform/surface/GuiModeContent.kt` |
| 웹 화면은 세션을 전환할 때만 메시지 보관함을 줄이고 만들 때는 줄이지 않는다 | `src/main/resources/webui/js/core/state.js` |

앞의 두 개는 테스트로 고정해두었습니다. 좋은 뜻으로 고치더라도 사용자에게 조용히 전달되지 않고 빌드가
먼저 막습니다.
