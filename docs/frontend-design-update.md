# 프론트 디자인 변경 기록

2026-10-08. 제공된 커뮤니티 게시판 시안을 기준으로 기존 10개 페이지와 공통 컴포넌트의 스타일을 변경했다.

## 적용 기준

- 흰색 본문, 연한 회색 사이드바, 파란색 활성 메뉴와 주요 버튼.
- 1px 구분선과 작은 모서리 반경. 굵은 검은 테두리, 기울어진 장식, 캐릭터 배너, 입체 그림자를 제거했다. 이미지 모달에만 배경과 구분하기 위한 일반 그림자를 사용한다.
- 페이지별 scoped CSS를 Tailwind CSS v4 유틸리티로 옮겼다. `main.css`에는 Tailwind 불러오기, 기본 글꼴과 기본 요소 스타일을 둔다.
- 게시판 메뉴는 API에서 받은 게시판을 표시한다. 시안의 게시판 이름·글·추천 수·프로필 사진을 실제 서비스에 고정 데이터로 추가하지 않았다.

## 수정한 위치

| 위치 | 변경 내용 |
| --- | --- |
| `boardkit-web/src/App.vue` | BoardKit 로고, 상단 메뉴, 공통 사이드바, 인증·관리 버튼, 푸터. 모바일에서는 사이드바 대신 가로 스크롤 메뉴를 표시한다. |
| `boardkit-web/src/assets/main.css` | 점무늬 배경과 장식용 글꼴을 제거하고 Tailwind 기본 스타일을 적용했다. |
| `components/ui/BaseButton.vue` | 주요·보조·삭제 버튼의 공통 스타일. 기본 높이 36px, 작은 버튼 32px인 `size` 속성을 추가했다. |
| `components/ui/BaseInput.vue`, `BaseSelect.vue`, `BaseTextarea.vue`, `BaseCheckbox.vue` | 글꼴·테두리·포커스·비활성 상태 통일. 도움말 유무와 무관하게 같은 행의 입력칸 상단을 맞췄다. |
| `components/ui/BasePagination.vue` | 이전·다음·숫자 버튼의 높이를 통일했다. 모바일 32px, 데스크톱 36px. 기존 5개 페이지 표시 계산은 유지한다. |
| `components/ui/CommunityIcon.vue` | 공통 SVG 선 아이콘. 별도 아이콘 라이브러리는 추가하지 않았다. |
| `features/board/components/BoardMenu.vue` | 헤더·사이드바 두 가지 표시 방식. 메뉴 조회는 `App.vue` 한 곳에서 하고 결과를 전달한다. |
| `features/board/components/BoardHero.vue`, `BoardHeader.vue`, `BoardCategoryList.vue`, `BoardPolicyPanel.vue` | 큰 배너 대신 제목과 설명을 표시한다. 게시판 이용 정책은 목록 아래 펼치기 영역에서 확인한다. |
| `views/BoardDetailView.vue`, `features/post/components/PostList.vue` | 카테고리 탭, 정렬, 공지 표시, 제목 옆 댓글 수, 촘촘한 표, 하단 검색. 모바일에서는 작성자·날짜를 제목 아래 표시한다. |
| `views/HomeView.vue`, `features/home/components/*` | 간결한 커뮤니티 소개, 최근 글, 게시판별 최신 글 목록. 각 게시판 최신 글 10개 조회를 유지한다. |
| `views/LoginView.vue`, `MemberCreateView.vue`, 각 로그인·회원가입 폼 | 공통 입력 스타일을 사용한 중앙 정렬 폼으로 통일했다. |
| `views/AdminDashboardView.vue`, `AdminBoardCreateView.vue`, `AdminBoardEditView.vue`, `features/board/components/BoardSettingsForm.vue` | 게시판 관리 목록과 생성·설정 폼을 동일한 스타일로 변경했다. 폼에 섞인 불필요한 `-70` 텍스트도 제거했다. |
| `views/PostCreateView.vue`, `PostEditView.vue`, 각 게시글 작성·수정 폼 | 제목·카테고리·본문·비밀글 설정·첨부파일·등록 버튼 스타일을 통일했다. |
| `views/PostDetailView.vue`, `features/post/components/PostDetailSidebar.vue`, `PostContent.vue`, `SecretPostPasswordForm.vue` | 게시글·메타 정보·첨부파일·관리 버튼·작성자 정보·비밀번호 입력 폼. 미구현 관련 글·인기 글 안내 상자는 제거했다. |
| `features/post/components/PostContentEditor.vue`, `PostImageModal.vue`, `PostAttachmentPicker.vue` | Tiptap 본문 입력 영역, 이미지 모달, 다운로드 첨부파일 선택 영역 스타일 변경. |
| `features/comment/components/*` | 댓글·대댓글·삭제된 댓글·작성·수정 폼과 버튼 스타일 통일. |

위 표에서 `components`, `features`, `views`는 모두 `boardkit-web/src` 아래 경로다.

## 기능과 검증 범위

- API 경로와 요청 형식, 검색 조건 유지, 권한에 따른 버튼 표시, 비밀글 처리, Tiptap 문서와 업로드 로직은 유지했다. 백엔드와 DB 정책은 이번 작업에서 변경하지 않았다.
- `npm run build`: TypeScript 검사와 프로덕션 빌드 통과. 기존 500kB 초과 번들 경고는 남아 있다.
- 실제 개발 서버에서 공개 게시판 목록과 게시글 상세 화면을 확인했다.
- 관리자·게시판 설정·글쓰기·수정·댓글 수정·이미지 모달은 별도 로컬 미리보기 서버의 가상 데이터로 화면을 확인했다. 이 서버는 실제 백엔드에 요청을 전달하지 않으며 저장·삭제 API도 실행하지 않는다. 가상 데이터는 프로젝트 소스에 포함하지 않았다.
- 320px에서 전체 페이지의 가로 넘침이 없음을 확인했다. 390px에서는 게시판 목록과 글 수정 화면을, 데스크톱에서는 헤더·사이드바·표·폼 배치를 확인했다.
- 320px 게시판 목록에서 이전·다음·숫자 버튼 7개의 높이가 모두 32px이고 같은 행에 놓이는 것을 확인했다.
- 새 프론트 테스트 코드는 추가하지 않았다. 저장·삭제·파일 업로드의 전체 브라우저 동작을 이번 디자인 확인으로 재검증한 것은 아니다.

## 설계 인사이트

게시판을 동적으로 구성하는 데 필요한 것은 게시판마다 다른 화면을 만드는 것이 아니라, 같은 화면과 컴포넌트에 서버 데이터를 전달하는 것이다. 공통 메뉴는 데이터를 한 번 받아 여러 배치에서 사용하고, 공통 입력·버튼은 페이지에서 스타일을 강제로 덮어쓰지 않도록 정리했다. 이후 디자인을 바꿀 때도 기능 코드와 분리해서 수정할 수 있다.

## 카드형 목록·썸네일 연결

- `BoardDetailView.vue`에서 `board.displayType`이 `CARD`이면 `PostCardList`, 그 외에는 `PostList`를 표시하는 분기가 적용된 것을 확인했다. 이미지 유무로 목록 형태를 바꾸지 않는다.
- 기존 대표 이미지 선정, 360×360 썸네일 생성 및 파일 조회 API를 재사용한다. 이 작업에 새 DB 컬럼·테이블·이미지 조회 API는 필요하지 않다.
- 목록 응답과 조회 쿼리에 `representativeFileId`와 `thumbnailAvailable`이 추가된 것을 소스에서 확인했다. 실제 DB 매핑 및 테스트 실행 결과는 이번 문서 갱신에서 재검증하지 않았다.
- 목록 쿼리에서는 대표 파일이 같은 게시글 소속의 삭제되지 않은 `BODY_IMAGE`인지 확인한다. 저장소 키와 본문 문서는 목록 응답에 포함하지 않는다.
- 2026-10-09 소스 확인: 프론트 응답 타입, 카드 구조, 공통 `PostThumbnail.vue` 연결이 적용되어 있다. 이전 카드의 임시 아이콘 영역에 실제 썸네일 조회가 연결되었다. 이번 확인에서 프론트 빌드나 브라우저 검증은 재실행하지 않았다.
- 목록형은 제목 옆 32×32 썸네일, 카드형은 카드 이미지 영역에 썸네일을 표시한다. 두 화면 모두 대표 파일 ID가 있고 `thumbnailAvailable`이 true인 일반 글에 공통 컴포넌트를 사용한다. 스타일은 Tailwind CSS v4 유틸리티로 작성한다.
- 이미지 표시에는 기존 `getPostFileBlob`과 `thumbnail=true` 파일 API를 사용한다. JWT를 포함한 요청으로 Blob을 받아 Object URL로 표시하고, 대상 변경 및 컴포넌트 해제 시 URL을 해제한다. 파일 API의 게시판 읽기 권한·비밀글 검사는 유지한다.
- 2026-10-09 표시 정책 확정: `!post.secret` 조건을 유지한다. 작성자·관리자를 포함해 비밀글의 목록 썸네일은 요청하거나 표시하지 않는다. 카드형에서는 기본 이미지 아이콘을 표시한다. 이 규칙은 목록 미리보기에 적용되며 상세 본문·파일 조회의 기존 접근 권한은 유지한다.
- 썸네일이 없으면 목록형은 썸네일 영역을 생략하고 카드형은 기본 이미지 아이콘을 표시한다. 원본 이미지로 대체하지 않는다. 기존 이미지의 썸네일 일괄 생성 기능은 추가하지 않았다.
