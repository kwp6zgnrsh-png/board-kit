# 게시판 목록 UI 변경 내역

## 작업 목표

- 참고 이미지의 네오 브루탈리즘 스타일을 게시판 목록 화면에 적용했다.
- 기존 게시판·정책·카테고리·게시글 API 연결과 라우팅은 유지했다.
- 게시판이 추가되어도 같은 화면을 재사용하도록 게시판별 전용 페이지를 만들지 않았다.

## 설치한 패키지

```bash
npm install -D tailwindcss@latest @tailwindcss/vite@latest
```

- `tailwindcss` 4.3.3
- `@tailwindcss/vite` 4.3.3

`vite.config.ts`에 Tailwind Vite 플러그인을 등록하고 `src/assets/main.css`에서 `@import "tailwindcss";`로 불러온다. Tailwind CSS v4 방식이므로 별도의 `tailwind.config.js`는 만들지 않았다.

## 파일별 변경 내용

### 설정 및 공통 레이아웃

- `package.json`, `package-lock.json`
  - Tailwind CSS v4와 Vite 플러그인을 개발 의존성으로 추가했다.
- `vite.config.ts`
  - `@tailwindcss/vite` 플러그인을 등록했다.
- `src/assets/main.css`
  - Tailwind CSS를 불러오고 전역 글꼴, 점무늬 배경, 제목용 글꼴을 정의했다.
- `src/App.vue`
  - 상단 게시판 메뉴, 관리자 게시판 생성 링크, 로그인 상태, 하단 문구를 굵은 테두리와 하드 섀도 스타일로 구성했다.
  - 로그인 세션은 라우트가 바뀔 때 다시 읽는다.

### 게시판 영역

- `src/features/board/components/BoardMenu.vue`
  - API로 받은 게시판 메뉴를 가로 스크롤 가능한 버튼 형태로 표시한다.
  - 현재 게시판을 파란색으로 강조한다.
- `src/features/board/components/BoardHero.vue`
  - 게시판 이름과 설명을 표시하는 배너를 추가했다.
  - 원·별·삼각형 장식은 별도 이미지 없이 CSS로 그렸다.
- `src/features/board/components/BoardPolicyPanel.vue`
  - 실제 게시판 정책에서 읽기 권한, 쓰기 권한, 비밀글 모드, 댓글 사용 여부를 표시한다.
  - 카테고리를 사용하는 게시판은 실제 카테고리 목록도 표시한다.
- `src/features/board/services/boardTheme.ts`
  - 게시판 slug로 색상 팔레트를 정해 새 게시판도 별도 디자인 설정 없이 일관된 색을 갖도록 했다.
- `src/views/BoardDetailView.vue`
  - 배너, 정책 패널, 게시글 목록을 참고 이미지와 유사한 2단 레이아웃으로 조합했다.
  - 기존 로딩·404·403·페이지 보정·글쓰기 권한 처리를 유지했다.
  - 작은 화면에서는 한 열로 배치된다.

### 게시글 목록

- `src/features/post/components/PostList.vue`
  - 번호, 카테고리, 제목, 작성자, 작성일, 조회수, 댓글 수를 표 형태로 표시한다.
  - 고정글과 비밀글에 `HOT`, `LOCK` 배지를 표시한다.
  - 행을 누르는 대신 기존과 같이 제목 링크로 상세 화면에 이동한다.
- `src/components/ui/BasePagination.vue`
  - 기존 페이지 이동 로직은 유지하고 새 화면 스타일에 맞게 버튼 디자인을 변경했다.
  - 이전·숫자·다음 버튼 높이를 32px로 통일하고 간격과 너비를 줄여 목록 아래에 정돈되게 배치했다.

## 데이터 및 디자인 기준

- 화면의 게시판 이름, 설명, 정책, 카테고리, 게시글 수와 게시글 목록은 모두 기존 API 응답을 사용한다.
- 게시판 디자인 설정은 DB에 추가하지 않았다. 색상은 프론트의 고정 팔레트와 slug 계산으로 결정한다.
- 처음 시안처럼 큰 배너·안내 패널·게시글 목록은 굵은 외곽선만 사용한다. 하드 섀도는 로고·버튼·스티커처럼 작은 강조 요소에만 적용했다.
- 참고 이미지에 있는 검색, 정렬, 좋아요, 인기 태그는 현재 목록 API에서 지원하지 않아 가짜 UI로 추가하지 않았다. 해당 기능의 API가 구현된 뒤 실제 동작과 함께 추가할 수 있다.

## 확인 결과

```bash
npm run build
```

- `vue-tsc --build` 통과
- `vite build` 통과
- `http://localhost:5173/boards/free`에서 게시판 정책과 실제 게시글 2개가 표시되는 것을 브라우저로 확인
- Git diff whitespace 검사 통과
