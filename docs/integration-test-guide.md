# MySQL Mapper·파일 통합 테스트

작성일: 2026-10-09
최종 갱신일: 2026-10-11

## 채택한 방식

사용자는 V1 구성 비교 후 처음 제안한 방식을 채택했다.

- 전용 로컬 MySQL `boardkit_test` DB와 테이블을 한 번 준비한다.
- 기존 `src/test` 안에 Mapper 통합 테스트를 작성하고 `@ActiveProfiles("test")`로 전용 DB에 연결한다.
- Mapper 테스트의 데이터는 테스트 트랜잭션 롤백으로 정리한다. 파일 서비스 테스트는 실제 서비스 커밋·롤백을 검증한 뒤 전용 fixture를 명시적으로 정리한다. 테이블은 유지한다.
- Docker나 별도 Gradle `integrationTest` source set은 도입하지 않는다.
- 첫 단계는 Mapper 검증이며, 실제 서비스 트랜잭션·파일 정리 검증은 이후 별도로 진행한다.

인사이트: 테스트 데이터를 롤백하는 것과 스키마를 재생성하는 것은 다르다. 이 방식은 테스트 실행 비용을 줄이지만, DDL이 변경되면 테스트 DB에도 변경 사항을 반영해야 한다.

현재는 적용할 설정·테스트 코드를 정리한 문서다. 기능 소스와 테스트 소스는 사용자가 직접 적용한다. 이번 문서 작성에서 테스트 DB 생성, DDL 실행, Java 컴파일 및 테스트 실행은 하지 않았다.

진행 상태: 사용자가 전용 테스트 DB 준비와 계정 연결 확인, Mapper 통합 테스트 6개 및 파일 서비스 통합 테스트 6개(등록 2개·수정 실패 1개·삭제 2개·파일 삭제 재시도 1개)의 실행 완료를 보고했다. 게시글 삭제 시 cleanup task 등록 누락도 보완했다. 테스트 설정은 공통 파일과 로컬 비밀번호 파일로 분리한다. 계정 연결 오류 이후 TCP 연결을 명시하여 접속에 성공했다. 에이전트는 소스를 확인했으며 테스트를 직접 실행하지 않았다.

## 범위

- 기존 Mock 기반 정책·권한 테스트를 유지한다.
- 첫 단계는 `PostMapperIntegrationTest`로 실제 MySQL의 JSON 저장·조회, 여러 파일 INSERT의 생성 ID, 대표 이미지 JOIN, 버전 조건 UPDATE, FK를 확인한다.
- Mapper 테스트는 실제 파일을 만들지 않는다. `storage_key`, `thumbnail_key`는 DB 메타데이터로만 사용한다.
- 각 테스트는 `@MybatisTest`의 테스트 트랜잭션에서 실행하고 종료 후 롤백한다. AUTO_INCREMENT 값은 롤백되어 되돌아간다고 가정하지 않는다.
- 중첩 테스트 설정으로 필요한 Mapper와 `JsonNodeTypeHandler`만 등록한다. 애플리케이션의 JWT·파일 저장소·스케줄러를 로드하지 않는다.
- 이미 MyBatis 테스트 의존성이 있으므로 의존성을 추가하지 않는다. Spring Boot 4의 `AutoConfigureTestDatabase`는 `org.springframework.boot.jdbc.test.autoconfigure` 패키지를 사용한다.

## 전용 DB 준비

MySQL 관리자 연결에서 다음을 한 번 실행한다. 비밀번호 자리에는 개발 DB와 구분되는 테스트용 값을 사용한다.

```sql
CREATE DATABASE boardkit_test
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER 'boardkit_test'@'127.0.0.1'
    IDENTIFIED BY '테스트용 비밀번호로 변경';

GRANT SELECT, INSERT, UPDATE, DELETE
    ON boardkit_test.* TO 'boardkit_test'@'127.0.0.1';
```

빈 `boardkit_test`를 명시적으로 선택하고 관리자 권한으로 기존 초기 DDL을 한 번 실행한다. 먼저 `SELECT DATABASE()`가 `boardkit_test`이고 `SHOW TABLES` 결과가 비어 있는지 확인한다. 기존 테이블이 있는 DB에 같은 DDL을 재실행하지 않는다.

```sql
USE boardkit_test;
SELECT DATABASE();
SHOW TABLES;
```

빈 DB임을 확인한 뒤 실행한다.

```sql
SOURCE /Users/yun/Desktop/boardKit/docs/schema.sql;
SHOW TABLES;
```

현재 초기 DDL은 13개 테이블을 생성한다. SQL 실행 오류가 없었는지 함께 확인한다. 이어서 테스트 계정의 TCP 연결을 검증한다.

```bash
mysql --protocol=TCP -h 127.0.0.1 -P 3306 -u boardkit_test -p -D boardkit_test \
  -e "SELECT DATABASE(), CURRENT_USER(); SHOW TABLES;"
```

테스트 계정에는 스키마 생성·삭제 권한을 부여하지 않는다. 테스트 시작 시 `SELECT DATABASE()`로 DB 이름을 확인한 뒤 fixture를 INSERT한다. 기존 개발 DB의 데이터를 삭제하거나 FK 검사를 끄지 않는다.

## 테스트 설정

사용자가 생성할 파일: `/Users/yun/Desktop/boardKit/boardKitApi/src/test/resources/application-test.yaml`

```yaml
spring:
  config:
    import: optional:classpath:application-local-test.yaml
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/boardkit_test
    username: boardkit_test
    password: ${TEST_DB_PASSWORD}
  sql:
    init:
      mode: never

mybatis:
  mapper-locations: classpath*:mapper/**/*.xml
  configuration:
    map-underscore-to-camel-case: true
```

공통 `application-test.yaml`은 Git에 올린다. 실제 비밀번호는 아래 로컬 파일에만 기록한다.

사용자가 생성할 파일: `/Users/yun/Desktop/boardKit/boardKitApi/src/test/resources/application-local-test.yaml`

```yaml
spring:
  datasource:
    password: "테스트용_비밀번호"
```

계정 생성 시 지정한 실제 비밀번호를 사용한다. `spring.config.import`로 읽은 파일의 비밀번호가 공통 설정의 값을 덮어쓴다. `test` 프로필만 사용하며 개발용 `local` 프로필을 추가로 활성화하지 않는다. 기존 루트 `.gitignore`의 `application-local*.yaml` 규칙이 이 로컬 파일을 제외한다. 공통 테스트 파일 자체는 제외하지 않는다.

`optional:`은 로컬 파일이 없어도 설정 파일 로딩을 허용한다. 이 경우에는 터미널 또는 IDE 실행 설정의 `TEST_DB_PASSWORD`가 필요하다. 로컬 파일을 준비했다면 환경변수 없이 실행할 수 있다. 테스트에서는 세션 시간대를 UTC로 설정한다.

## 테스트 코드

사용자가 생성할 파일: `/Users/yun/Desktop/boardKit/boardKitApi/src/test/java/io/github/kwp6zgnrsh/boardkitapi/post/mapper/PostMapperIntegrationTest.java`

```java
package io.github.kwp6zgnrsh.boardkitapi.post.mapper;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import io.github.kwp6zgnrsh.boardkitapi.common.mybatis.JsonNodeTypeHandler;
import io.github.kwp6zgnrsh.boardkitapi.file.mapper.PostFileMapper;
import io.github.kwp6zgnrsh.boardkitapi.file.model.PostFile;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.request.PostSort;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.response.PostSummaryResponse;
import io.github.kwp6zgnrsh.boardkitapi.post.model.Post;
import io.github.kwp6zgnrsh.boardkitapi.post.model.PostDetail;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

@MybatisTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = PostMapperIntegrationTest.Config.class)
class PostMapperIntegrationTest {

    @Autowired PostMapper postMapper;
    @Autowired PostFileMapper postFileMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    private Long memberId;
    private Long boardId;

    @Configuration(proxyBeanMethods = false)
    @MapperScan(basePackageClasses = {PostMapper.class, PostFileMapper.class})
    @Import(JsonNodeTypeHandler.class)
    static class Config {
    }

    @BeforeEach
    void prepareFixtures() {
        assertEquals("boardkit_test",
            jdbcTemplate.queryForObject("SELECT DATABASE()", String.class));
        jdbcTemplate.execute("SET SESSION time_zone = '+00:00'");

        String key = UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.update("""
            INSERT INTO member (login_id, password_hash, display_name, role)
            VALUES (?, ?, ?, 'ADMIN')
            """, "it-" + key, "unused-in-mapper-tests", "테스트" + key.substring(0, 6));
        memberId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        jdbcTemplate.update("""
            INSERT INTO board (name, slug, display_type, status, created_by)
            VALUES (?, ?, 'LIST', 'ACTIVE', ?)
            """, "통합 테스트 게시판", "it-" + key, memberId);
        boardId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @Test
    @DisplayName("게시글 생성 ID와 JSON 본문을 실제 MySQL에서 저장하고 조회한다")
    void insertPostAndReadJsonDocument() {
        Post post = insertPost();
        PostDetail saved = postMapper.selectActivePostDetail(boardId, post.getId())
            .orElseThrow();

        assertAll(
            () -> assertNotNull(post.getId()),
            () -> assertTrue(post.getId() > 0),
            () -> assertEquals("테스트 제목", saved.title()),
            () -> assertEquals("앞 내용\n다음 내용", saved.content()),
            () -> assertEquals(post.getContentDocument(), saved.contentDocument())
        );
    }

    @Test
    @DisplayName("여러 파일을 한 번에 저장하면 각 객체에 올바른 생성 ID가 반영된다")
    void assignGeneratedIdsToEachInsertedFile() {
        Post post = insertPost();
        PostFile first = image(post.getId(), "a.png", 0, true);
        PostFile second = image(post.getId(), "b.png", 1, false);

        assertEquals(2, postFileMapper.insertPostFiles(List.of(first, second)));
        assertNotNull(first.getId());
        assertNotNull(second.getId());
        assertNotEquals(first.getId(), second.getId());

        for (PostFile file : List.of(first, second)) {
            PostFile saved = postFileMapper.selectActivePostFile(post.getId(), file.getId())
                .orElseThrow();
            assertAll(
                () -> assertEquals(file.getOriginalName(), saved.getOriginalName()),
                () -> assertEquals(file.getStorageKey(), saved.getStorageKey()),
                () -> assertEquals(file.getThumbnailKey(), saved.getThumbnailKey())
            );
        }
    }

    @Test
    @DisplayName("대표 이미지가 없는 글도 목록에 포함하고 첫 본문 이미지의 썸네일을 조회한다")
    void readRepresentativeImageFromPostSummaries() {
        Post post = insertPost();
        PostSummaryResponse withoutImage = summary();
        assertNull(withoutImage.representativeFileId());
        assertFalse(withoutImage.thumbnailAvailable());

        PostFile later = image(post.getId(), "later.png", 1, false);
        PostFile first = image(post.getId(), "first.png", 0, true);
        assertEquals(2, postFileMapper.insertPostFiles(List.of(later, first)));
        assertEquals(1, postMapper.refreshRepresentativeImage(boardId, post.getId()));

        PostSummaryResponse withImage = summary();
        assertEquals(first.getId(), withImage.representativeFileId());
        assertTrue(withImage.thumbnailAvailable());
    }

    @Test
    @DisplayName("대표 이미지가 있어도 썸네일 키가 없으면 썸네일 유무는 false다")
    void readRepresentativeImageWithoutThumbnail() {
        Post post = insertPost();
        PostFile file = image(post.getId(), "original.png", 0, false);
        assertEquals(1, postFileMapper.insertPostFiles(List.of(file)));
        assertEquals(1, postMapper.refreshRepresentativeImage(boardId, post.getId()));

        PostSummaryResponse result = summary();
        assertEquals(file.getId(), result.representativeFileId());
        assertFalse(result.thumbnailAvailable());
    }

    @Test
    @DisplayName("이미 사용한 버전으로 다시 수정하면 UPDATE 결과는 0건이다")
    void rejectUpdateWithStaleVersion() {
        Post post = insertPost();
        Post update = Post.builder()
            .id(post.getId()).boardId(boardId).authorId(memberId)
            .title("수정 제목").content("수정 내용")
            .contentDocument(document("수정 내용")).version(0)
            .build();

        assertEquals(1, postMapper.updatePost(update));
        assertEquals(0, postMapper.updatePost(update));

        PostDetail saved = postMapper.selectActivePostDetail(boardId, post.getId())
            .orElseThrow();
        assertEquals(Integer.valueOf(1), saved.version());
        assertEquals("수정 제목", saved.title());
    }

    @Test
    @DisplayName("존재하지 않는 게시글에 파일을 연결하면 FK 제약으로 거절된다")
    void rejectFileForMissingPost() {
        PostFile file = image(Long.MAX_VALUE, "missing.png", 0, false);

        assertThrows(DataIntegrityViolationException.class,
            () -> postFileMapper.insertPostFiles(List.of(file)));
    }

    private Post insertPost() {
        Post post = Post.builder()
            .boardId(boardId).authorId(memberId)
            .title("테스트 제목").content("앞 내용\n다음 내용")
            .contentDocument(document("앞 내용\n다음 내용"))
            .viewCount(0L).version(0)
            .build();
        assertEquals(1, postMapper.insertPost(post));
        return post;
    }

    private PostFile image(Long postId, String name, int order, boolean thumbnail) {
        String key = UUID.randomUUID().toString();
        return PostFile.builder()
            .postId(postId).originalName(name)
            .storageKey("posts/" + postId + "/" + key + ".png")
            .contentType("image/png").fileUsage("BODY_IMAGE")
            .sizeBytes(100L).sortOrder(order)
            .thumbnailKey(thumbnail ? "thumbnails/" + postId + "/" + key + ".png" : null)
            .build();
    }

    private PostSummaryResponse summary() {
        List<PostSummaryResponse> posts = postMapper.selectPostSummaries(
            boardId, null, null, PostSort.LATEST, 10, 0L);
        assertEquals(1, posts.size());
        return posts.getFirst();
    }

    private JsonNode document(String text) {
        var document = JsonNodeFactory.instance.objectNode();
        document.put("type", "doc");
        var paragraph = document.putArray("content").addObject();
        paragraph.put("type", "paragraph");
        paragraph.putArray("content").addObject()
            .put("type", "text").put("text", text);
        return document;
    }
}
```

`LAST_INSERT_ID()`는 이 테스트의 트랜잭션에 묶인 동일한 DB 연결에서 조회한다. 회원의 해시는 Mapper fixture 전용 값이며 이 계정으로 로그인 테스트를 수행하지 않는다.

## 실행

로컬 비밀번호 파일을 준비했다면 `boardKitApi` 디렉터리의 터미널에서 실행한다.

```bash
./gradlew test --tests '*PostMapperIntegrationTest'
```

로컬 파일을 만들지 않고 환경변수로 전달하려면 비밀번호를 화면에 표시하거나 셸 기록에 남기지 않고 입력한다.

```zsh
read -s 'TEST_DB_PASSWORD?테스트 DB 비밀번호: '
export TEST_DB_PASSWORD
./gradlew test --tests '*PostMapperIntegrationTest'
unset TEST_DB_PASSWORD
```

IDE에서도 로컬 파일을 준비했다면 환경변수는 필요하지 않다. 로컬 파일이 없을 때만 이 테스트 실행 설정에 `TEST_DB_PASSWORD`를 지정한다. `local` 프로필은 추가하지 않는다.

이 명령은 새 Mapper 통합 테스트만 실행한다. 기존 `BoardKitApiApplicationTests`는 `local` 프로필로 전체 애플리케이션을 띄우므로 전체 테스트를 실행하기 전에 개발 DB·파일 저장소·스케줄러의 격리를 별도로 정리해야 한다.

## 검증의 경계와 다음 단계

- JSON 객체 구조를 비교한다. MySQL JSON의 직렬화 공백·객체 키 순서가 동일하다고 가정하지 않는다.
- `thumbnailAvailable`은 DB 키 존재 여부다. 이 테스트로 물리 썸네일의 생성·읽기 성공까지 검증하지 않는다.
- stale version 테스트는 UPDATE 조건의 동작을 확인한다. 동시 스레드 경쟁이나 Service의 HTTP 409 처리를 검증하는 테스트와 구분한다.
- 테스트 종료 롤백은 fixture 정리를 위한 동작이다. 실제 서비스 커밋 실패 검증은 아니다.
- 다음 단계는 전용 DB와 JUnit 임시 저장소를 사용하고 실제 서비스 트랜잭션이 끝나도록 구성한 Service 통합 테스트다. 등록·수정 실패의 DB 롤백, 새 파일 정리, 기존 파일 보존을 확인한다. 이 단계에서 추가할 코드와 실행 설정은 별도로 제시한다.

## 등록·파일 통합 테스트 — 사용자 검증 완료

사용자가 생성한 파일: `/Users/yun/Desktop/boardKit/boardKitApi/src/test/java/io/github/kwp6zgnrsh/boardkitapi/post/service/PostFileIntegrationTest.java`

- `@MybatisTest`에 실제 `PostService`, Converter, 본문·파일 Validator, `LocalFileStorage`를 명시적으로 등록한다. 전체 애플리케이션과 스케줄러는 로드하지 않는다.
- 테스트 클래스에 `@Transactional(propagation = Propagation.NOT_SUPPORTED)`를 선언하여 테스트용 외부 트랜잭션을 끈다. 서비스의 `@Transactional`이 실제 커밋·롤백을 끝낸 후 DB를 조회한다.
- 파일 경로는 JUnit `@TempDir`로 격리한다. 테스트용 회원·게시판·정책은 DB에 준비하고 `@AfterEach`에서 이 테스트의 ID로만 정리한다. 대표 이미지 FK를 해제한 뒤 파일→게시글→정책→게시판→회원 순서로 삭제한다.
- 정상 등록: 실제 PNG를 전송하고 커밋된 게시글·파일 메타데이터, 본문의 `fileId`, 대표 이미지, 원본 및 360×360 썸네일을 확인한다.
- 실패 등록: `@MockitoSpyBean PostMapper`로 대표 이미지 처리 단계만 예외를 발생시킨다. 해당 시점에 실제 파일·DB 행이 존재했음을 먼저 확인한다. 예외 이후 게시글·파일 행이 롤백되고 임시 파일·원본·썸네일이 남지 않는지 확인한다.
- 예외는 서비스 실행 도중 주입한다. DB 커밋 자체의 실패, 파일 삭제 실패의 재시도, 수정·삭제 흐름은 이번 두 테스트의 검증 범위에 포함하지 않는다.
- 등록 테스트 2개는 사용자가 작성 및 실행 완료를 보고했다. 에이전트는 소스를 읽어 확인했으며 직접 실행하지 않았다.

인사이트: 서비스의 실패 롤백을 확인할 때 테스트의 자동 롤백에만 의존하면 서비스 자체의 트랜잭션 경계가 잘못되어도 놓칠 수 있다. 파일 시스템의 정리는 DB 롤백과 별도로 검증해야 한다.

## 수정·파일 통합 테스트 — 사용자 검증 완료

동일한 `PostFileIntegrationTest`에 `preserveExistingFilesWhenPostUpdateFails`를 구현했다.

- 기존 이미지 게시글을 정상 등록하고 원본·썸네일 바이트 및 게시글 데이터를 보관한다.
- 기존 파일을 제거하고 새 이미지를 추가하는 수정 요청을 보낸다.
- 대표 이미지 처리 직전에 예외를 주입한다. 그 시점에 기존 파일의 soft delete, 삭제 작업 등록 및 새 파일 저장이 실제로 이루어졌음을 먼저 확인한다.
- 예외 이후 제목·본문 JSON·버전·대표 이미지가 이전 값으로 돌아오는지, 기존 파일이 활성 상태이고 원본·썸네일 바이트가 그대로인지, 새 파일과 임시 파일이 남지 않는지 확인한다.
- 기존 파일용 cleanup task도 같은 트랜잭션에서 롤백되어야 한다. 실패한 수정의 삭제 작업이 남으면 추후 재시도가 보존해야 할 파일을 지울 수 있다.
- 테스트 종료 정리에는 이 테스트 게시판의 파일 키에 해당하는 cleanup task 삭제를 추가한다. 정상적으로는 실패 요청의 작업이 롤백되지만, 테스트 실패 시에도 다른 테스트를 오염시키지 않기 위한 정리다.
- 사용자가 테스트 실행 완료를 보고했다. 에이전트는 구현된 소스를 확인했으며 직접 실행하지 않았다.

인사이트: 파일 교체 실패에서 핵심은 새 파일 삭제와 기존 파일 보존을 함께 검증하는 것이다. DB 행이 롤백되더라도 기존 파일의 삭제 작업이 커밋되거나 물리 파일이 먼저 삭제되면 정합성이 깨진다.

## 게시글 삭제·파일 통합 테스트 — 사용자 검증 완료

동일한 `PostFileIntegrationTest`에 두 테스트를 구현했다. `PostFileMapper`는 실제 Mapper를 유지하고, 작업 등록 실패를 주입하기 위해 `FileCleanupTaskMapper`를 `@MockitoSpyBean`으로 등록한다. 기본 호출은 실제 Mapper를 사용한다.

- `deletePostFilesThroughCleanupBatch`: 게시글·파일 행을 유지하면서 `deleted_at`이 기록되고 활성 상세·파일 조회에서 제외되는지 확인한다. 삭제 요청 직후 원본·썸네일은 남고 두 키의 cleanup task가 등록되는지 확인한 뒤, `FileCleanupService.processDueTasks()`를 직접 실행하여 실제 원본·썸네일과 작업 행이 제거되는지 확인한다. 물리 파일 삭제 이후에도 soft delete된 게시글·파일 메타데이터는 유지된다.
- `rollbackPostDeletionWhenCleanupEnqueueFails`: 게시글·파일 soft delete 이후 작업 등록 단계에 예외를 주입한다. 서비스 호출 종료 후 게시글·파일의 활성 상태와 게시글 버전·대표 이미지가 복구되고, 정리 작업은 없으며 실제 파일은 유지되는지 확인한다.
- `PostService.deletePost()`는 게시글 삭제 표시 후 활성 파일을 잠금 조회하여 키를 확보한다. 파일 삭제 표시와 작업 등록을 같은 서비스 트랜잭션에서 처리하며, 파일이 없으면 빈 목록으로 작업을 등록하지 않는다. 기존 `PostServiceTest.deletePostByAdmin`은 파일 없는 삭제와 작업 미등록을 검증한다.
- 기존 `FileCleanupService`가 커밋된 작업의 물리 파일 삭제와 재시도를 담당한다. 별도 보관 기간이나 영구 보존을 가정하지 않는다.
- 파일 삭제 재시도 테스트를 포함하여 이 클래스의 테스트는 총 6개이며 사용자가 실행 완료를 보고했다. 에이전트는 소스를 확인했으며 직접 실행하지 않았다.
- 배치 검증을 위해 중첩 설정에 `FileCleanupService`를 등록하며 스케줄러는 로드하지 않는다. 실제 DB에서 이번 테스트 파일 키에 해당하는 작업만 읽고 `selectDueTasks`의 반환 목록을 Spy로 제한한다. 파일 삭제와 작업 행 DELETE는 실제 구현을 사용하여 다른 테스트의 작업을 처리하지 않는다.
- 사용자가 기존 정상 삭제 테스트를 확장한 배치 실행 검증의 완료를 보고했다. 에이전트는 변경된 소스를 확인했으며 직접 실행하지 않았다.
- 위 두 삭제 통합 테스트는 작업 등록·배치 물리 삭제·작업 행 제거와 작업 등록 실패 롤백을 검증한다. 파일 삭제 실패의 재시도는 아래 별도 테스트에서 검증한다. 작업 조회 SQL의 시간 조건·개수 제한, 스케줄러 실행, 동시 수정·삭제 경쟁 및 목록 API에서의 제외까지 검증한 것은 아니다.

인사이트: 삭제 표시 직후부터 배치 완료 전까지 파일이 디스크에 남아도 API로는 열람할 수 없어야 한다. 게시글·파일의 soft delete와 cleanup task 등록이 같은 트랜잭션에 있어야 실패한 삭제가 나중에 실제 파일을 지우지 않는다.

## 파일 삭제 실패·재시도 통합 테스트 — 사용자 검증 완료

동일한 `PostFileIntegrationTest`에 `retryFailedFileDeletionAndCompleteTask`를 구현했다. `LocalFileStorage`를 `@MockitoSpyBean`으로 등록하여 원본의 첫 삭제 호출만 예외를 발생시키고 다음 호출은 실제 구현을 실행한다. `readCleanupTasks` 헬퍼로 이번 테스트 파일 키의 작업과 실패 정보를 실제 DB에서 읽는다.

- 게시글 삭제 후 원본·썸네일 정리 작업 2개를 확인한다.
- 첫 배치 실행에서 원본 삭제를 실패시킨다. 배치는 오류를 기록하고 썸네일 작업을 계속 처리하여 실제 썸네일과 해당 작업 행을 제거한다.
- 원본 파일과 작업은 남아 있어야 한다. 실제 DB의 `attempts=1`, 저장된 오류 메시지, 미래의 `next_attempt_at`을 확인하고 첫 실패의 재시도 간격이 60초임을 검증한다.
- 실제 대기 없이 이번 테스트 작업의 `next_attempt_at`만 현재 DB 시각으로 변경하고 DB에서 작업을 다시 읽어 배치에 전달한다. 재시도 성공 후 실제 원본과 작업 행이 제거되는지 확인한다.
- 작업 조회 대상만 Spy로 제한하며 실패 기록 UPDATE, 작업 행 DELETE 및 재시도의 파일 삭제는 실제 구현을 사용한다. 실제 조회 SQL이 미래 작업을 제외하는지와 스케줄러 실행 주기는 검증하지 않는다. 운영체제 파일 권한으로 발생하는 실제 삭제 오류도 주입한 예외와 구분한다.
- 사용자가 통합 테스트와 기존 `FileCleanupServiceTest` 실행 완료를 보고했다. 에이전트는 적용된 소스를 확인했으며 테스트를 직접 실행하지 않았다.

인사이트: 한 파일의 삭제 실패가 다른 작업 처리를 막아서는 안 된다. 실패 작업은 재시도 정보를 보존하고 성공한 작업만 제거해야 하며, 재시도에서는 이미 완료한 썸네일 작업을 다시 처리할 필요가 없다.

## 참고

- [MyBatis 테스트 구성](https://mybatis.org/spring-boot-starter/mybatis-spring-boot-test-autoconfigure/)
- [Spring 테스트 트랜잭션과 롤백](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/tx.html)
