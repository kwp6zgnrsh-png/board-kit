# MySQL Mapper·파일·인증 테스트

작성일: 2026-10-09
최종 갱신일: 2026-10-19

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

현재 초기 DDL은 `revoked_token`을 포함한 14개 테이블을 생성한다. SQL 실행 오류가 없었는지 함께 확인한다. 이어서 테스트 계정의 TCP 연결을 검증한다. 이미 준비한 테스트 DB에는 초기 DDL 전체를 재실행하지 않고 신규 테이블 DDL만 적용한다.

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

## 인증 고도화 테스트 — 단계별 검증

- `JwtTokenProviderTest` 3개: 회원 정보·토큰 식별자·만료 시각 파싱, 같은 회원에 대한 서로 다른 토큰 식별자 발급, 서명은 유효하지만 식별자가 없는 토큰 거절을 검증한다.
- `RevokedTokenMapperIntegrationTest` 3개: 실제 MySQL에서 무효화 등록·조회, 중복 등록 시 기존 만료 시각 보존, 만료된 기록만 정리를 검증한다. 테스트 보고서에서 실패·오류 0건을 확인했다.
- 무효화 Mapper 테스트는 `test` 프로필과 전용 DB를 사용하며 인증 Mapper XML만 로드한다. 테스트 종료 시 DB 변경은 롤백한다.
- `AuthCookieFactoryTest` 4개: JWT 남은 수명과 쿠키 보안 속성, 로컬 HTTP의 Secure 해제, 동일 범위에서의 쿠키 만료, 만료된 JWT의 쿠키 발급 거절을 검증한다. 사용자가 통과를 보고했고 테스트 보고서에서도 실패·오류 0건을 확인했다.
- 쿠키 테스트는 Spring 컨텍스트나 DB 없이 실행하는 단위 테스트다. 실제 브라우저의 쿠키 저장·전송 및 서버 로그인·로그아웃의 동작까지 검증한 것은 아니다.
- `AuthServiceTest` 8개: 로그인 성공·실패와 현재 토큰의 UTC 만료 시각 저장, 중복 무효화 결과 0건 허용, DB 저장 실패 전달, 현재 회원 정보 반환과 조회 결과가 없는 회원의 거절을 검증한다. 회원 조회는 MemberMapper Mock을 사용하므로 삭제 여부 필터링 SQL을 실제 DB에서 검증한 것은 아니다.
- `JwtAuthenticationFilterTest` 7개: MEMBER·ADMIN 쿠키 인증과 JWT 정보 보관, 무효화·잘못된 토큰 거절, 쿠키 없는 요청과 Bearer 헤더만 있는 요청, 무효화 조회 DB 오류 시 요청 진행 차단을 검증한다.
- `AuthApiTest` 14개: 로그인 응답에서 JWT 제외 및 쿠키 발급, 로그인 실패·잘못된 입력, CSRF 쿠키 발급·헤더 누락 거절, 로그아웃 쿠키 만료, 미인증·CSRF 누락·무효화된 토큰 거절, DB 저장 실패 시 쿠키 미만료, 현재 회원 조회 성공·민감 정보 제외와 미인증·무효화 토큰·삭제된 회원의 거절을 검증한다. 실제 SecurityConfig·인증 필터·쿠키 팩토리와 Mock 서비스·JWT 제공자·무효화 Mapper를 사용한다.
- CSRF 쿠키의 SameSite는 MockMvc 응답 쿠키 객체의 attribute로 검사한다. 현재 Spring Test는 일반 Servlet Cookie의 SameSite를 Set-Cookie 문자열로 출력하지 않는 경우가 있어 문자열 검사만으로 판단하지 않는다.
- 2026-10-11 12:25 UTC 보고서에서 총 33개, 현재 회원 조회를 추가한 12:34 UTC 보고서에서 총 39개 테스트의 실패·오류 0건을 확인했다. 에이전트가 직접 실행한 결과는 아니다.
- 프론트 쿠키·CSRF 연결, 앱 시작 시 현재 회원 정보 복원과 서버 로그아웃 연결을 구현했다. 기존 JWT sessionStorage 저장과 Bearer 전송은 제거했으며 회원 정보는 메모리에 보관한다. 인증 상태 변경 시 메뉴·홈 목록·작성 및 수정·삭제 버튼을 갱신한다.
- 사용자는 프론트 연결 후 아래 수동 검증 절차의 완료를 보고했다. 에이전트가 브라우저에서 요청을 실행하거나 응답·쿠키를 직접 확인한 결과는 아니다. Mapper 통합 테스트와 Mock 기반 API 테스트를 합쳐 전체 로그아웃 자동 통합 검증으로 간주하지 않는다.
- 만료된 무효화 기록의 주기적 정리와 실제 DB·HTTP를 연결한 자동 인증 통합 테스트는 후속 범위다.
- 이번 문서 갱신에서 에이전트는 테스트를 직접 실행하거나 DB를 변경하지 않았다.

### 인증 브라우저·요청 수동 검증 절차

1. 로그인 후 새로고침하거나 관리자 페이지를 직접 열어 회원 정보와 권한에 맞는 화면이 복원되는지 확인한다. 쿠키 만료 전에는 브라우저 재실행 후에도 복원할 수 있다.
2. 로그아웃 버튼으로 `POST /api/auth/logout`을 호출하여 204 응답과 인증 쿠키의 `Max-Age=0`을 확인한다. Console에서 API만 직접 호출하면 메모리의 회원 정보는 자동으로 지워지지 않으므로 화면 확인 전 새로고침한다.
3. 새로고침 후 현재 회원 조회가 401을 반환하고 비로그인 화면으로 시작하는지 확인한다. 홈에서 로그아웃하면 회원 전용 게시판·글이 기존 목록에 남지 않아야 한다.
4. 로그아웃 전에 보관한 JWT를 인증 쿠키로 다시 보내 현재 회원 조회가 401을 반환하는지 확인한다. 로그아웃 화면과 쿠키 삭제만으로는 서버의 JWT 무효화를 증명할 수 없다.
5. 서버 연결 실패 시 로그아웃 오류를 표시하고 화면만 정상 로그아웃 처리하지 않는지 확인한다. 앱 시작 시 복원 요청의 서버 오류도 비로그인 상태와 구분한다.

## Tiptap 서식 검증 — 2026-10-14

- `PostDocumentValidatorTest`에 글자 서식 보존·복합 서식·잘못된 구조·중복 서식, http·https 링크 보존 및 다른 방식 거절, 불필요한 링크 속성 제거 테스트가 추가되었다.
- 문단 서식 테스트는 H2·H3, 중첩 목록·번호 목록 시작 번호·인용문·코드 블록·구분선의 보존과 검색용 텍스트 추출을 검사한다. 잘못된 제목·목록 구조, 코드 블록의 서식·hardBreak, 인라인 코드와 다른 서식의 조합도 거절하는지 확인한다.
- 이미지 테스트는 중첩 구조 전체의 참조 순서·중복 검사와 검증 요청 사이의 상태 분리를 검사한다. 재귀 처리의 기술적 상한을 넘는 중첩도 거절하는지 확인한다.
- `PostServiceTest.createPostWithNestedBodyImage`는 실제 검증기와 업로드 검증을 사용하고 Mapper·파일 저장소·Converter는 Mock으로 구성한다. 게시글 작성 호출을 통해 인용문 안의 새 이미지가 실제 파일 ID로 치환되어 저장 요청에 전달되는지, 이미지 필수 조건과 파일 용도·순서가 반영되는지 확인한다. 실제 DB·파일 저장 검증과는 구분한다.
- 사용자는 단계별 완료와 프론트 연결 후 작성·상세·수정 및 파일 확인 절차의 완료를 보고했다. 이번 문서 갱신에서 에이전트는 테스트·빌드·브라우저 요청을 직접 실행하지 않았다. 이전 테스트 보고서를 이번 서식 변경 전체의 실행 결과로 간주하지 않는다.
- 수동 확인: 서식·이미지·첨부파일을 넣어 작성 → 상세의 표시·링크 이동·다운로드 확인 → 수정에서 기존 서식·이미지 복원 → 이미지 재정렬·서식 변경 후 상세 재확인. 비밀글 파일 조회와 JSON 본문이 없는 기존 글의 호환 표시도 해당 데이터가 있으면 확인한다.
- 프론트는 테스트 코드를 추가하지 않고 TypeScript·프로덕션 빌드와 사용자 브라우저 확인으로 검증한다.

## 게시글·댓글 좋아요 검증 — 2026-10-18

- `PostLikeServiceTest` / `CommentLikeServiceTest`는 활성 회원, 제재 시 등록 차단·취소 허용, 기능 OFF 시 양쪽 차단, 게시판 읽기 권한과 비밀글 비밀번호 검사를 검증한다. 댓글 테스트는 게시글 댓글 작성 잠금과 관계없이 등록·취소를 허용하고 사용할 수 없는 댓글을 거절하는지도 검사한다.
- `PostLikeApiTest` / `CommentLikeApiTest`는 인증·CSRF·요청 전달과 상태 코드를 검사한다. 현재 SPA CSRF 설정의 MockMvc 요청에는 `.with(csrf())`를 사용한다. 브라우저는 `XSRF-TOKEN` 쿠키 값을 `X-XSRF-TOKEN` 헤더로 전송한다.
- `PostLikeMapperIntegrationTest` / `CommentLikeMapperIntegrationTest`는 전용 MySQL에서 중복 등록 시 기록·최초 시각 보존, 본인 기록만 취소, 반복 취소와 FK 제약을 검사한다. 댓글 테스트는 부모·대댓글의 독립적인 좋아요, 로그인 여부에 따른 선택 상태, 삭제된 부모의 정보 숨김과 대댓글로 시작하는 페이지의 조회도 검사한다. DB 변경은 테스트 트랜잭션으로 롤백한다.
- `PostSecretAccessTest`와 각 서비스 테스트는 공유 비밀글 접근 검사를 검증한다. Mock 기반 정책 테스트와 실제 DB Mapper 검증을 전체 HTTP·DB 통합 검증으로 간주하지 않는다.
- 사용자는 단계별 테스트 완료 및 댓글 작성 OFF·댓글 좋아요 ON 설정의 브라우저 확인 완료를 보고했다. 이번 문서 갱신에서 에이전트는 테스트·빌드를 재실행하지 않았다.

수동 확인 항목:

1. 게시글·댓글·대댓글의 하트를 눌러 추가·취소하고 새로고침 후 개수와 내 선택 상태를 확인한다. 미선택은 회색, 선택은 파란색으로 표시한다.
2. 댓글 작성 OFF·댓글 좋아요 ON으로 저장하면 새 댓글 작성은 차단되고 기존 댓글의 좋아요는 변경할 수 있어야 한다. 게시글별 댓글 작성 잠금도 좋아요를 막지 않아야 한다.
3. 좋아요 기능 OFF에서는 기존 개수를 유지하되 추가·취소를 모두 막고 기능 OFF 안내 문구는 별도로 표시하지 않는다.
4. 삭제된 부모의 하트·개수는 숨기고 활성 대댓글의 좋아요는 유지한다. 댓글 하트 클릭으로 대댓글 작성 폼이 열리지 않아야 한다.

## 프로필·인증 버전 검증 — 2026-10-18

- `MemberServiceTest`는 프로필 조회, 닉네임 정규화·중복·수정 대상 소멸, 현재 비밀번호·새 비밀번호 확인·기존과 같은 비밀번호 거절, 버전 충돌과 DB 오류 전달을 Mock으로 검사한다.
- `MemberProfileApiTest`는 실제 보안 설정과 쿠키 팩토리, Mock 서비스를 사용한다. 프로필의 민감 정보 제외·캐시 금지, 닉네임·비밀번호 변경의 인증·CSRF·입력 검증·상태 코드와 성공 시 쿠키 만료를 검사한다. 회원가입용 `MemberApiTest`에도 쿠키 팩토리 의존성을 등록한다.
- `JwtTokenProviderTest`는 인증 버전 발급·파싱과 누락·음수 버전 거절을 검사한다. `JwtAuthenticationFilterTest`는 활성 회원의 버전 일치 여부와 DB 조회 실패를 검사하며, `AuthApiTest`는 이전 버전으로 현재 회원 API에 접근하면 401을 반환하는지 검사한다. Mock 검증을 실제 DB·HTTP 전체 흐름 검증으로 간주하지 않는다.
- `MemberMapperIntegrationTest`는 실제 MySQL에서 회원 ID·로그인 ID 조회의 인증 버전 매핑, 비밀번호 해시와 버전의 동시 변경, 이전 버전 UPDATE 차단, 탈퇴 회원 거절, 닉네임 변경 시 인증 유지 조건을 검사한다. fixture의 인증 버전을 3으로 두어 필드의 기본값 0 때문에 매핑 누락이 가려지지 않게 한다.
- 해당 Mapper 테스트는 기존 `test` 프로필과 테스트 트랜잭션을 사용하며 종료 시 fixture를 롤백한다. 테스트 시작 시 `SELECT DATABASE()`로 `boardkit_test`인지 확인한 뒤 데이터를 추가한다. 이전 버전 UPDATE 검증은 순차 실행이며 실제 병렬 요청 검증은 아니다.
- 내가 쓴 글 검증 3개를 추가하여 다른 회원·삭제 글의 제외와 개수 일치, 보관·관리자 전용 게시판의 내 글 포함, 생성 시각·ID 정렬과 페이지 분할을 검사한다. 고정된 작성 시각과 생성 ID의 순서를 다르게 구성하여 ID만으로 정렬하는 오류도 확인한다.
- `MemberProfileApiTest`에는 내 글 목록의 기본·명시적 페이지 조건, 개인 정보 캐시 금지, 인증된 회원 ID 사용, 빈 목록·잘못된 입력·탈퇴 회원의 응답 검증이 추가되었다.
- 내가 쓴 댓글의 Mapper 검증 3개를 추가했다. 다른 회원·삭제 댓글 제외와 삭제된 부모 아래 본인 대댓글 유지, 삭제된 원글·보관·관리자 전용 게시판의 본인 댓글 포함, 작성 시각·ID 정렬과 페이지 분할을 검사한다. 원글 작성자와 댓글 작성자를 다르게 구성하여 댓글 작성자를 기준으로 필터링하는지도 확인한다.
- `MemberProfileApiTest`에는 내 댓글·대댓글 응답과 원글 삭제 여부 전달, 인증된 회원 ID 사용, 기본·명시적 페이지 조건, 빈 목록·잘못된 입력·탈퇴 회원의 응답 검증을 추가했다. 실제 SQL의 원글 삭제 처리·작성자 필터와 API의 응답 검증을 구분한다.
- 2026-10-18 03:46:33 UTC의 `MemberMapperIntegrationTest` 보고서에서 총 11개, 03:44:12 UTC의 `MemberProfileApiTest` 보고서에서 총 52개 테스트의 실패·오류 0건을 확인했다. 에이전트가 직접 테스트를 실행한 것은 아니다. 이 보고서는 이후 프론트 연결의 빌드·브라우저 검증 결과를 포함하지 않는다.

### 마이페이지 프론트 연결 — 2026-10-18

- `/me`에 프로필 카드, 닉네임·비밀번호 변경 폼, 내 게시글·댓글 목록의 연결을 소스에서 확인했다. 사용자는 비밀번호 변경 확인과 목록 연결 단계의 완료를 보고했다. 이번 문서 갱신에서 에이전트는 빌드·테스트·브라우저 요청을 실행하지 않았다. 전체 기기 로그아웃을 실제 복수 브라우저에서 확인했다는 보고와 구분한다.
- 프론트 테스트 코드는 추가하지 않는다. 프론트 디렉토리의 `npm run build`와 다음 브라우저 절차로 확인한다. 아래 절차 전체를 에이전트가 실행했거나 사용자가 개별 결과를 제출한 것으로 간주하지 않는다.

1. 닉네임 변경 시 기본 정보·상단 닉네임 갱신과 새로고침 후 유지, 중복 닉네임 거절을 확인한다.
2. 현재 비밀번호 오류 시 로그인 유지, 변경 성공 시 로그인 화면의 완료 안내, 이전 비밀번호 거절·새 비밀번호 로그인을 확인한다. 다른 브라우저에서 미리 로그인한 뒤 변경 후 인증 요청이 401인지도 별도로 확인한다.
3. 내 게시글·댓글이 각각 최신순 10개씩 표시되고 다른 회원·삭제된 본인 내역은 제외되는지 확인한다. 보관·접근 제한 게시판의 본인 내역은 목록에 포함하되 상세 요청은 기존 권한 검사를 따르는지 확인한다.
4. 삭제된 부모 아래 활성 본인 대댓글과 삭제된 원글 아래 활성 본인 댓글을 확인한다. 원글이 삭제되었으면 원문 링크가 없어야 한다. 활성 원문 링크는 게시글 상세로 이동하며 특정 댓글 위치·페이지로 자동 이동하지 않는다.
5. `postPage`와 `commentPage`를 각각 변경하고 서로의 페이지 유지, 새로고침·브라우저 뒤로가기 복원, 삭제 등으로 사라진 마지막 페이지의 보정을 확인한다.

### 기존 테스트 DB의 인증 버전 컬럼 반영

이미 준비한 테스트 DB에는 초기 DDL 전체를 재실행하지 않는다. 먼저 다음으로 컬럼 존재 여부를 확인한다.

```sql
SHOW COLUMNS FROM boardkit_test.member LIKE 'auth_version';
```

컬럼이 없는 경우에만 ALTER 권한이 있는 계정으로 적용한다.

```sql
ALTER TABLE boardkit_test.member
    ADD COLUMN auth_version BIGINT UNSIGNED NOT NULL DEFAULT 0
    AFTER password_hash;
```

개발 DB와 테스트 DB는 별도이므로 스키마 변경도 각각 반영해야 한다. 일반 테스트 계정에 ALTER 권한을 추가할 필요는 없다.

```bash
./gradlew test --tests '*MemberMapperIntegrationTest'
```

## 관리자 공식 답변 검증 — 2026-10-18

- `PostReplyMapperIntegrationTest`는 전용 MySQL에서 생성 ID·JSON 매핑, 게시글당 하나의 UNIQUE 제약, 버전 증가·이전 버전 거절, 소속 불일치·삭제된 게시글 접근 차단, 물리 삭제 후 재등록 시 이전 답변 ID의 요청 차단을 검사한다. 이 클래스의 fixture는 기존 테스트 트랜잭션으로 롤백한다.
- `PostDocumentValidatorTest`에 공식 답변의 서식 보존·2,000자 경계, 빈 내용·중첩 이미지·안전하지 않은 링크 거절을 추가했다. 2026-10-18 05:50:51 UTC 보고서에서 기존 게시글 검증을 포함한 33개 테스트의 실패·오류 0건을 확인했다.
- `PostReplyServiceTest`는 Mapper를 Mock으로 두고 Converter·본문 검증기·비밀글 검사를 실제 객체로 사용한다. 기능 OFF의 기존 답변 조회·모든 변경 차단, ADMIN 및 활성 회원 검사, 댓글 잠금과의 독립성, 저장·버전 충돌·DB 오류 전달을 검사한다. 2026-10-18 06:12:34 UTC 보고서에서 16개 테스트의 실패·오류 0건을 확인했다. 이 테스트만으로 실제 DB 잠금·롤백을 검증했다고 간주하지 않는다.
- `PostReplyLockIntegrationTest`는 별도 DB 연결의 중첩 `REQUIRES_NEW` 트랜잭션으로 잠금이 겹치는 상태를 만든다. 같은 게시판의 서로 다른 게시글에서 공유 잠금과 답변 등록이 함께 가능하고, 게시판 배타 잠금은 차단되며 공유 잠금 해제 후 획득되는지 검사한다. 별도 스레드를 병렬 실행하는 테스트와 구분한다.
- 잠금 테스트는 다른 연결에서 fixture를 조회해야 하므로 테스트 자동 트랜잭션을 끄고 준비 데이터를 커밋한다. 시작 시 `boardkit_test`를 확인하며 종료 시 해당 fixture만 FK 순서에 맞게 삭제한다. 답변 변경 트랜잭션은 롤백한다. 해당 연결의 잠금 대기 시간을 1초로 설정한 뒤 원래 값으로 복원한다. 2026-10-18 06:12:31 UTC 보고서에서 2개 테스트의 실패·오류 0건을 확인했다.
- `PostReplyApiTest`는 실제 보안 필터·메서드 권한 설정과 Mock 서비스를 사용한다. 조회·등록·수정·삭제 전달, 204와 404 구분, 인증·ADMIN 권한·CSRF·입력 검증 및 403·409 응답을 검사한다. 2026-10-18 06:31:46 UTC 보고서에서 21개 테스트의 실패·오류 0건을 확인했다.
- 관리자 목록의 Mapper 통합 테스트는 기능 ON·OFF 포함 조건, 미답변·답변 있음 필터, 보관·DRAFT 게시판 및 삭제된 게시글 제외, 정렬·페이지 분할을 추가했다. 2026-10-18 07:15:29 UTC 보고서에서 9개 테스트의 실패·오류 0건을 확인했다. 이 결과는 당시 확인한 보고서 기록이며 이후 테스트 실행으로 해당 XML이 교체될 수 있다.
- 목록 fixture는 `JdbcTemplate`으로 변경하므로, 기존 MyBatis 조회 캐시가 남지 않도록 테스트의 `readAdminReplyTargets()`에서 `SqlSessionTemplate.clearCache()`를 호출한다. 운영 캐시 설정이나 SQL 조건을 테스트 통과를 위해 변경하지 않는다.
- 관리자 목록 Service 테스트는 필터 전달·페이지 계산·빈 결과·범위 밖 페이지 및 비관리자·비활성 회원 거절을 추가했다. 2026-10-18 07:20:54 UTC 보고서에서 기존 답변 관리 테스트를 포함한 21개 테스트의 실패·오류 0건을 확인했다. 이 결과도 당시 확인한 보고서 기록이다.
- `AdminPostReplyApiTest`는 실제 보안 설정과 Mock 서비스를 사용하여 기본 미답변·1페이지·10개 조건, 세 필터 전달, 응답 JSON·no-store, 인증·ADMIN 권한 및 잘못된 필터·페이지 입력 거절을 검사한다. 2026-10-18 07:24:16 UTC 보고서에서 15개 테스트의 실패·오류 0건을 확인했다.
- 프론트 상세의 패널·폼·편집기와 관리자 목록·라우터·메뉴 연결을 소스에서 확인했으며 사용자는 해당 단계의 완료를 보고했다. 이번 관리자 목록 문서 갱신에서 에이전트가 `boardkit-web`의 `npm run build`를 실행하여 타입 검사·프로덕션 빌드 성공을 확인했다. 주 번들의 500 kB 초과 경고는 남아 있다. 백엔드 테스트 재실행·브라우저 요청은 수행하지 않았으므로 다음 수동 항목의 개별 결과를 직접 검증한 것으로 간주하지 않는다.

수동 확인 항목:

1. ADMIN이 서식을 넣어 등록·수정·삭제하고 삭제 후 재등록할 수 있는지 확인한다. 새로고침 후 본문과 버전을 확인한다.
2. 일반 회원·비로그인 사용자는 기존 답변을 조회하고 관리 버튼은 사용할 수 없어야 한다. 비밀글은 기존 접근 권한을 따라야 한다.
3. 공식 답변 기능 OFF에서는 기존 답변을 표시하되 등록·수정·삭제 버튼을 제공하지 않아야 한다. 일반 댓글 작성 잠금은 공식 답변 관리를 막지 않아야 한다.
4. 두 관리자 화면에서 같은 답변을 수정하여 나중 요청의 409와 입력 내용 유지 여부를 확인한다. 삭제·재등록 전 답변 ID의 요청으로 새 답변을 변경하지 못해야 한다.
5. 빈 내용·2,000자 초과·이미지나 첨부파일 사용을 차단하고, 정상 서식·웹 링크·기존 JSON 없는 답변의 일반 텍스트 표시를 확인한다.
6. 수정 성공 뒤 재조회만 실패한 경우 성공 안내와 조회 오류를 구분하고, 공식 답변 새로고침이 게시글 조회수를 증가시키지 않는지 확인한다.
7. 관리자 메뉴에서 공식 답변 관리로 진입하면 미답변·첫 페이지·10개 조건으로 요청하는지 확인한다. 필터 변경 시 첫 페이지로 이동하고 페이지 이동·새로고침·브라우저 뒤로가기에서 조건을 유지해야 한다.
8. 관리자 목록에서 제목을 선택하여 기존 상세 화면에서 답변을 등록·수정·삭제한 뒤 브라우저 뒤로가기로 돌아와 목록을 다시 조회하는지 확인한다. 기능 OFF의 기존 답변은 전체·답변 있음에 조회 전용으로 표시하고, 보관·DRAFT 게시판과 삭제된 게시글은 제외해야 한다.
9. 목록의 필터를 빠르게 변경했을 때 이전 응답이 현재 목록을 덮어쓰지 않는지 확인한다. 요청 페이지가 마지막 페이지를 넘으면 현재 마지막 페이지로 이동하고, 결과가 없으면 빈 목록을 표시해야 한다.

## 게시글·댓글 신고 등록 검증 — 2026-10-18

- 앞선 단계에서 `ReportCreateRequestTest` 9개, `ReportMapperIntegrationTest` 13개, `ReportServiceTest` 14개의 실패·오류 0건인 보고서를 확인했다. 현재 실행 결과 폴더는 후속 테스트 실행으로 교체될 수 있으므로 당시 확인한 기록과 구분한다.
- DTO 테스트는 신고 사유 필수, 기타 사유의 상세 내용 필수, 2,000자 경계를 실제 Bean Validation으로 검사한다.
- Mapper 통합 테스트는 전용 `boardkit_test`에서 생성 ID·초기 PENDING·enum 저장, 게시글과 댓글의 별도 신고, 다른 회원의 동일 대상 신고, 처리 상태와 관계없는 중복 차단, 정확히 하나의 대상만 허용하는 CHECK, 조회 소속과 삭제 표시 제외를 검사한다. fixture는 테스트 트랜잭션으로 롤백한다. 공유 잠금 조회의 실행은 포함하지만 별도 연결에서 잠금 경합을 재현하는 테스트는 아니다.
- Service 테스트는 Mapper를 Mock으로 두고 실제 `PostSecretAccess`를 사용한다. 신고 대상별 저장, 자기 콘텐츠·비활성 회원·제재·읽기 권한·비밀글·사용 불가능한 대상의 차단, 댓글 작성 OFF·잠금과 신고의 독립성, 중복 예외 변환·DB 장애 전달·저장 결과 검사를 확인한다. 실제 트랜잭션 롤백이나 공유 잠금 동작을 검증하는 테스트와 구분한다.
- `ReportApiTest`의 2026-10-18 12:23:51 UTC 보고서에서 29개 테스트의 실패·오류 0건을 확인했다. 실제 보안 설정과 Mock 서비스로 회원·관리자 정보 및 비밀번호 전달, 201·생성 ID, 인증·CSRF·DTO·경로 ID 검증, 401·403·404·409 응답을 검사한다. JWT를 직접 발급·검증하는 테스트는 아니다.
- 프론트 신고 버튼·공통 폼·모달·API 연결을 소스에서 확인했고 사용자는 연결 단계 완료를 보고했다. 에이전트가 `boardkit-web`의 `npm run build`를 실행하여 타입 검사·프로덕션 빌드 성공을 확인했다. 주 번들 500 kB 초과 경고는 남아 있다. 이번 문서 갱신에서는 백엔드 테스트와 브라우저 요청을 직접 실행하지 않았으며 아래 항목의 개별 수동 결과를 직접 검증한 것으로 간주하지 않는다.
- 관리자 신고 목록·상세 조회와 인정·반려 처리도 구현했다. 후속 검증 기록은 아래 관리자 신고 절에 정리한다.

수동 확인 항목:

1. 다른 회원의 게시글·댓글·대댓글에 신고 버튼이 표시되고 자기 콘텐츠와 삭제된 댓글에서는 표시되지 않는지 확인한다.
2. 기타 사유는 상세 내용이 필수이고 2,000자 초과 입력을 차단하는지 확인한다. 정상 등록은 Network에서 201과 생성 ID를 확인한다.
3. 같은 대상을 다시 신고하면 409와 중복 안내를 표시하는지 확인한다. 등록 또는 중복 확인 후 버튼 비활성화도 확인한다.
4. 댓글 작성 OFF·잠금 상태에서도 기존 댓글 신고가 가능하고, 제재 중에는 서버가 403으로 차단하는지 확인한다.
5. 비밀글 신고 요청에 `X-Post-Password`가 전달되는지 확인한다. 틀린 비밀번호·읽기 권한 변경·대상 삭제 시 서버의 403 또는 404를 확인한다.
6. 모달 취소·Esc, 등록 중 중복 클릭·취소 차단을 확인한다. 댓글 신고 버튼 클릭이 대댓글 작성 폼을 열지 않아야 한다.
7. 요청 중 게시글·댓글 페이지 이동이나 계정 변경 시 이전 응답이 새 화면 상태를 바꾸지 않는지 확인한다. 통신 오류는 결과 확인 실패로 안내한다. 동일 대상·계정이 유지되는 400·403·404·통신 오류에서는 입력 내용을 유지하고, 인증 만료·계정 변경 시에는 모달과 입력 상태를 초기화해야 한다.

실행 예시:

```bash
./gradlew test --tests '*Report*Test'
```

## 관리자 신고 조회·처리 검증 — 2026-10-18

- 앞선 단계에서 `ReportReviewRequestTest` 12개, 관리자 조회·처리 추가 후 `ReportMapperIntegrationTest` 24개, `AdminReportServiceTest` 12개의 실패·오류 0건인 보고서를 확인했다. 이후 개별 테스트 실행으로 XML이 교체될 수 있으므로 당시 확인한 기록과 구분한다.
- 처리 DTO는 인정·반려만 허용하고, 처리 메모 필수·공백 거절·2,000자 경계를 검사한다. Mapper 테스트는 필터·정렬·페이지 분할, 보관·삭제 대상 포함, 게시글·댓글별 현재 원문과 처리 정보 매핑, 없는 신고 및 처리 완료 신고의 조건부 UPDATE 차단을 검사한다. 순차적인 재처리 차단 검사와 실제 여러 연결의 동시 요청 검증은 구분한다.
- 관리자 Service 테스트는 ADMIN·활성 회원 검사, 필터 전달·페이지 계산, 없는 신고 404 및 이미 처리된 신고 409를 검사한다. ADMIN에게 별도의 제재 여부 검사를 추가하지 않는다.
- `AdminReportApiTest`의 2026-10-18 13:07:36 UTC 보고서에서 33개 테스트의 실패·오류 0건을 확인했다. 실제 보안 설정과 Mock 서비스로 기본 대기·1페이지·10개 조건, 필터·상세 응답·no-store, 인정·반려 전달 및 204, 인증·ADMIN 권한·CSRF·입력 검증과 404·409 응답을 검사한다. 이 문서 갱신 시 에이전트가 백엔드 테스트를 다시 실행한 것은 아니다.
- 사용자는 관리자 프론트 적용 완료를 보고했다. 소스에서 관리자 메뉴·목록·상세 라우터, 필터·페이지 보존, 처리 메모 검증, 처리 완료 시 조회 전용 표시, 충돌·통신 오류 이후 재조회 요구, 처리 성공과 재조회 실패의 별도 안내를 확인했다. 에이전트가 `npm run build`를 실행하여 타입 검사·프로덕션 빌드 성공을 확인했다. 주 번들은 629.64 kB이며 기존 500 kB 초과 경고가 남아 있다. 프론트 테스트 코드는 추가하지 않았다.
- 관리자 상세는 현재 텍스트 원문을 표시한다. 반환받은 `contentDocument`의 서식·이미지와 첨부파일은 현재 화면에 표시하지 않는다. 현재 내용은 신고 당시 내용과 다를 수 있다.
- 아래 브라우저 항목은 수동 확인 목록이며 에이전트가 브라우저에서 실행한 결과가 아니다.

수동 확인 항목:

1. 관리자 메뉴에서 신고 목록에 진입하면 대기 목록이 표시되고, 전체·대기·인정·반려 필터 및 페이지가 URL·새로고침·뒤로가기에 유지되는지 확인한다. 상세의 목록으로 버튼도 같은 조건을 복원해야 한다.
2. 보관된 게시판·삭제된 게시글·삭제된 댓글의 신고가 목록과 상세에 남고, 현재 대상 텍스트·수정 시각·삭제 상태가 표시되는지 확인한다. 활성·미삭제 게시글만 원본 이동 링크를 제공해야 한다.
3. 메모 없이 인정·반려를 누르면 프론트에서 차단하고, 정상 메모로 처리하면 PATCH 204와 처리자·메모·시각이 표시되는지 확인한다. 인정으로 콘텐츠가 자동 삭제되거나 회원 제재가 생성되지 않아야 한다.
4. 처리 완료된 신고에서 처리 폼이 사라지는지 확인한다. 두 탭에서 같은 대기 신고를 연 뒤 각각 처리하면 두 번째는 409가 되고 최초 처리 결과가 보존되어야 한다.
5. 처리 요청의 통신 결과가 불확실하면 메모를 유지하고 재처리를 막으며, 다시 조회하여 최신 상태를 확인할 수 있는지 확인한다. 처리 성공 뒤 GET만 실패시키면 성공 안내와 조회 실패 안내가 함께 표시되어야 한다.
6. 처리 중 중복 클릭, 다른 신고로 이동, 로그아웃·계정 변경 후 늦게 도착한 응답이 현재 화면을 덮어쓰지 않는지 확인한다. 일반 회원·비로그인 사용자의 관리자 API 접근도 각각 403·401인지 확인한다.

## 신고 기반 블라인드 준비 검증 — 2026-10-18

- 사용자가 DB 컬럼 추가 확인을 보고한 뒤, 초기 DDL·ERD·컬럼 목록을 동기화했다. 소스에서 `Post`·`Comment` 모델의 블라인드 필드와 기존 댓글 조회 매핑, 게시글·댓글별 ACCEPTED 신고 집계 쿼리를 확인했다.
- `ReportMapperIntegrationTest`의 2026-10-18 13:47:15 UTC 보고서에서 총 28개 테스트의 실패·오류 0건을 확인했다. 추가된 검사는 세 신고 상태 중 ACCEPTED만 집계, 없는 대상 0건, 여러 회원의 신고 합산 및 게시글·댓글 건수 분리를 포함한다. 에이전트가 직접 실행한 결과는 아니다.
- 이 결과는 인정 신고 집계 단계의 검증이다. 블라인드·해제 상태 변경, 자동 판정·동시 처리 및 일반 조회·파일 API 접근 차단까지 검증한 것으로 간주하지 않는다.
- 후속 `ContentModerationMapperIntegrationTest`의 2026-10-18 13:57:19 UTC 보고서에서 4개 테스트의 실패·오류 0건을 확인했다. 게시글·댓글의 블라인드·해제, 기준 건수 저장과 반복 해제 차단, 버전 충돌·반복 블라인드·댓글 소속 조건, 삭제된 대상의 관리 조회 및 변경 차단을 검사한다. 원문·파일 메타데이터·기존 대댓글과 본문 수정 시각의 보존도 확인한다. 실제 파일시스템이나 여러 연결의 잠금 경합 테스트는 아니다. 앞선 실패는 게시글 SQL이 ReportMapper namespace에 들어가 있었기 때문이며 PostMapper XML로 이동 후 통과한 보고서를 확인했다.

- `AdminReportServiceTest`의 2026-10-18 14:04:41 UTC 보고서에서 29개 테스트의 실패·오류 0건을 확인했다. 자동 판정의 기준 미달·도달·초과와 해제 기준 이후 재집계, 대상별 잠금·신고 처리·집계 호출 순서, 이미 블라인드·삭제된 대상의 생략, 처리 충돌과 DB 장애·블라인드 실패 예외 전파를 검사한다. 이 결과는 실제 Spring 트랜잭션 롤백이나 여러 DB 연결의 동시 실행을 검증한 결과가 아니다. 에이전트가 직접 실행하지 않고 기존 보고서를 확인했다.

- 후속 `AdminReportServiceIntegrationTest`의 2026-10-18 14:10:16 UTC 보고서에서 6개 테스트의 실패·오류 0건을 확인했다. 테스트 자동 트랜잭션을 끄고 실제 Service 프록시와 MySQL을 사용한다. 게시글·댓글 각각에 대해 서로 다른 신고의 동시 인정 시 3건 도달·블라인드, 동일 신고 동시 처리 시 단일 성공·단일 집계, 블라인드 실패 주입 시 신고 상태·처리 정보 롤백을 검증한다. 두 요청이 신고 상세 SQL을 읽은 뒤 대상 잠금을 얻기 전에 barrier에서 만나도록 조정한다. 준비 데이터는 커밋하고 종료 후 해당 fixture만 FK 순서로 삭제한다. 에이전트가 직접 실행하지 않고 보고서를 확인했다. 일반 조회·파일 접근 차단과 관리자 수동 조치 검증은 포함하지 않는다.

- 게시글 상세·파일 접근 제한 후 2026-10-18 14:25:53~56 UTC 보고서에서 `PostServiceTest` 57개, `PostLikeServiceTest` 12개, `PostMapperIntegrationTest` 9개, `PostApiTest` 19개의 실패·오류 0건을 확인했다(총 97개). 추가된 검사는 블라인드 원문·원본·썸네일의 작성자·관리자 열람 및 다른 사용자 차단, 비밀번호를 알아도 우회 불가, 수정 차단·본인 삭제 유지, SQL 블라인드 시각 매핑과 실제 MapStruct 응답 변환을 포함한다. 이 결과는 아직 댓글·좋아요·신고 등 모든 경로의 제한이나 브라우저 표시까지 완료됐다는 의미는 아니다. 에이전트는 기존 보고서를 확인했고 테스트를 직접 실행하지 않았다.

## 블라인드 조회·작업 제한 검증 — 2026-10-19

- 2026-10-19 00:14 UTC의 전체 테스트 결과 XML 47개 클래스·700개 테스트에서 실패·오류 0건을 확인했다. 이 기록은 사용자가 실행한 보고서 확인 결과다. 앞선 에이전트의 관련 89개 실행에서는 목록 SQL의 중복 `likeCount`로 1개가 실패했고, 사용자가 해당 중복을 제거한 뒤 전체 통과를 확인했다.
- 댓글 원문 가림은 작성자·관리자 외 본문 NULL 반환, 기존 대댓글과 페이지 행 유지, 삭제된 댓글의 관리자 원문 가림을 검증한다. 좋아요·신고 제한은 부모 게시글 블라인드 검사, 댓글 자체의 대상 조회 제외, 기존 좋아요 보존 및 해제 후 대상 조회 복구를 포함한다.
- `PostMapperIntegrationTest` 13개는 블라인드 목록의 안내 행 유지·제목/썸네일 가림, 작성자·관리자 예외, 제목·본문 검색과 개수 일치, 비밀글 썸네일 숨김을 포함한다. `PostApiTest` 19개와 `PostServiceTest` 57개도 통과했다. 이 결과는 관리자 수동 블라인드·해제·삭제 API나 프론트 상태 표시의 구현 완료를 의미하지 않는다.

## 관리자 수동 조치 Service 검증 — 2026-10-19

- 2026-10-19 00:35 UTC 보고서에서 `AdminReportServiceTest` 29개와 `AdminReportServiceIntegrationTest` 10개의 실패·오류 0건을 확인했다. 에이전트가 직접 실행한 결과는 아니다.
- 새 통합 검증은 게시글·댓글 각각 보관된 게시판에서 기준 미달 수동 블라인드, 버전 충돌, 해제 기준 저장과 추가 인정 3건 재블라인드, 삭제와 삭제 후 조치 차단을 포함한다. 수동 조치 자체가 신고 판정을 변경하지 않는지도 검사한다.
- 게시글 삭제의 파일 삭제 표시·cleanup task 저장 성공과 작업 등록 실패 시 실제 DB 롤백을 검사한다. 파일시스템 삭제나 API·브라우저 조치 버튼까지 완료됐다는 의미는 아니다.
- 후속 2026-10-19 00:40 UTC 보고서에서 `AdminReportApiTest` 41개·`AdminReportServiceTest` 29개·`AdminReportServiceIntegrationTest` 10개, 총 80개의 실패 0건을 확인했다. 수동 조치 API의 세 action·버전 전달·204, 잘못된 입력 400, 상태 충돌 409와 기존 인증·관리자 권한·CSRF 검사를 포함한다. 관리자 프론트 연결과 일반 사용자 화면의 블라인드 안내·버튼 제한은 아직 검증 완료 범위가 아니다.

## 회원 제재 Mapper 통합 테스트 — 2026-10-19

- 소스에서 `MemberSanction` 모델·등록/해제 요청 DTO, `MemberSanctionMapper`와 XML, `MemberMapper.selectActiveMemberByIdForUpdate` 추가를 확인했다.
- 테스트는 `member/mapper/MemberSanctionMapperIntegrationTest.java` 한 클래스다. 기존 방식대로 `@MybatisTest`, `test` 프로필, `Replace.NONE`, `mapper/member/*.xml`과 member Mapper 패키지 스캔을 사용한다.
- 실행 전 `boardkit_test.member_sanction`에도 `revoked_by`, `revoke_reason`, 해제자 FK를 반영해야 한다. 기존 DB에 초기 DDL 전체를 다시 실행하지 않는다. 변경 SQL은 `schema.md`의 회원 제재 관리 절에 있다.
- `insertAndReadSanction`: 기간/영구 두 입력으로 INSERT 생성 ID, 컬럼 매핑, 초기 해제 정보 NULL, 현재 제재 판정을 확인한다. 같은 트랜잭션에서 회원의 잠금 조회 SQL도 실행한다.
- `ignoreExpiredSanctionWithoutChangingHistory`: 만료 기록은 현재 제재에서 제외하되 저장 이력과 미해제 상태를 유지하는지 확인한다.
- `preserveRevocationAuditOnRepeatedRequest`: 다른 ADMIN이 해제할 수 있고, 등록 관리자·원래 기간은 유지하면서 해제자·사유·시각이 저장되는지 확인한다. 반복 해제는 영향 행 0건이며 최초 해제 이력을 덮어쓰지 않아야 한다.
- 세 메서드·실행 사례 네 개다. 테스트 시작 시 DB 이름을 먼저 확인하고, DB의 UTC 시각으로 날짜 fixture를 만든다. 테스트별 회원·제재 데이터는 기본 테스트 트랜잭션 롤백으로 정리한다.
- 이 테스트는 실제 동시 등록 차단이나 Service의 관리자·대상 권한 검증을 보장하지 않는다. 해당 검증은 후속 Service 및 트랜잭션 테스트에서 수행한다.
- 사용자가 추가한 테스트 소스와 JUnit XML을 확인했다. 2026-10-19 05:19:16 UTC 보고서 기준 4개 실행 사례의 실패·오류·건너뜀은 0개다. 에이전트가 직접 재실행하거나 실제 DB 스키마를 확인한 결과는 아니다.
- 이후 만료된 제재의 해제 거절 정책이 확정되었고 해제 SQL에 현재 유효한 기간 조건이 반영된 것을 확인했다. 2026-10-19 14:30 KST 에이전트 선택 실행에서도 기존 4개 사례가 통과했다. 이후 소스에서 만료된 제재의 해제 결과 0건 검증 추가도 확인했으며, 추가 이후 14:50:16 KST JUnit XML에서 4개 사례의 실패·오류·건너뜀 0건을 확인했다. 최신 결과는 에이전트가 직접 재실행한 결과가 아니다.

실행 명령 (`boardKitApi` 디렉토리):

```bash
./gradlew test --tests '*MemberSanctionMapperIntegrationTest'
```

## 회원 제재 Service 검증 — 2026-10-19

- 등록·해제는 별도 `AdminMemberSanctionService`에서 처리한다. 기존 `MemberService`와 인터페이스 구현체 구조를 추가하지 않는다.
- 단위 테스트는 실제 Service 객체와 Mock Mapper로 정책·UTC 변환·잠금 이후 검사/쓰기 호출 순서·영향 행 수와 생성 ID 검사·중복 해제 충돌을 확인한다.
- 관리자 권한 및 활성 여부, 없는/탈퇴한 대상, 관리자 대상 등록, 현재 제재 중복, 지난 종료 일시, 만료·기해제 제재의 해제 거절을 구분한다. DTO의 2,000자 제한 등 Bean Validation은 실제 프록시 또는 후속 API 테스트에서 검증한다.
- Mock 테스트는 실제 행 잠금·트랜잭션 격리·롤백을 보장하지 않는다. 같은 회원의 동시 등록 거절 및 다른 회원의 독립 처리는 후속 트랜잭션 통합 테스트에서 확인한다.
- Service·단위 테스트·DTO 길이 제한·추가 SQL 조건의 소스 적용을 확인했다.
- 최초 Service 실행의 실패 3개는 테스트 코드 두 곳에서 발생한 같은 `ClassCastException`이었다. `ReflectionTestUtils.setField(invocation.getArgument(0), ...)`의 제네릭 타입 추론이 `Class` 인자를 받는 오버로드를 선택했다. 인자를 `MemberSanction` 지역 변수에 먼저 대입하여 객체 인자를 받는 오버로드를 사용하도록 두 곳만 보정했다. 기능 소스는 이 실패 보정에서 수정하지 않았다.
- 2026-10-19 14:30 KST 에이전트가 아래 명령으로 재실행했다. Service 16개·Mapper 통합 4개, 총 20개 실행 사례의 실패·오류·건너뜀은 0개다. 전체 테스트를 재실행한 결과는 아니다.

```bash
./gradlew test --tests '*AdminMemberSanctionServiceTest' \
  --tests '*MemberSanctionMapperIntegrationTest'
```

## 회원 제재 등록·해제 API 검증 — 2026-10-19

- `AdminMemberSanctionApiTest`는 기존 방식의 `@WebMvcTest`, 실제 `SecurityConfig`·`MethodSecurityConfig`, Mock Service를 사용한다. 보안 설정 생성에 필요한 JwtTokenProvider·RevokedTokenMapper·MemberMapper도 Mock Bean으로 등록한다.
- 기간/영구 제재의 201·생성 ID·no-store 및 로그인한 관리자 ID 전달, 해제의 204·빈 본문·제재 ID/사유 전달을 확인한다. 시간대가 있는 종료 일시는 동일한 순간으로 전달되는지 비교한다.
- 각 경로에서 비로그인·일반 회원·CSRF 누락·잘못된 CSRF의 거절과 Service 미호출을 확인한다. JWT 자체 검증의 상세 조합은 기존 인증 테스트를 재사용한다.
- 등록 본문의 대상 회원 필수/양수, 사유 필수/2,000자 상한, 종료 일시의 미래 여부/잘못된 형식과 해제 사유 필수/상한을 확인한다. 해제 경로 ID는 0 경계와 숫자 변환 실패를 확인한다. 같은 양수 검증의 음수 입력은 중복 추가하지 않는다.
- Service의 업무상 400, 비활성 관리자 401, 대상 없음 404, 상태 충돌 409가 HTTP 응답으로 전달되는지 확인한다. 실제 DB 트랜잭션이나 파일·프론트 동작을 확인하는 테스트는 아니다.
- API·응답 DTO·테스트의 소스 적용을 확인했다. JUnit XML의 2026-10-19 14:42:49 KST 보고서 기준 26개 사례의 실패·오류·건너뜀은 0개다. 이번 문서 갱신에서 에이전트가 API 테스트를 직접 재실행한 것은 아니다.

실행 명령 (`boardKitApi` 디렉토리):

```bash
./gradlew test --tests '*AdminMemberSanctionApiTest'
```

## 회원 제재 Service 트랜잭션 통합 테스트 검증 — 2026-10-19

- `AdminMemberSanctionServiceIntegrationTest`에서 실제 Spring Service 프록시·MyBatis·MySQL을 연결한다. `@MybatisTest`에 Service와 트랜잭션 설정을 Import하고 `mapper/member/*.xml`만 로드한다.
- 같은 회원에 대한 두 등록 요청은 실제 관리자 조회 SQL 직후 barrier에서 만나도록 한다. 이렇게 두 트랜잭션이 모두 잠금 이전 조회를 마친 상태에서 진행하여 `READ_COMMITTED`의 후속 조회가 앞선 커밋을 반영하는지 확인한다. 한 요청만 성공하고 다른 요청은 업무상 409여야 하며 실제 제재 행은 하나여야 한다.
- 서로 다른 회원은 첫 요청이 INSERT 후 커밋 전 대기 중이어도 다른 요청이 완료·커밋할 수 있어야 한다. latch로 순서를 제어하고 Thread.sleep은 사용하지 않는다.
- 실제 INSERT 후 영향 행 수를 테스트용으로 0으로 반환하여 Service의 실패를 발생시킨다. 실제 트랜잭션 활성 여부와 READ_COMMITTED 설정, 예외 이후 저장 행이 롤백되는지 확인한다.
- Mapper Spy는 SQL을 Mock 결과로 대체하지 않고 SqlSessionTemplate으로 실제 mapped statement를 실행한다. 도구는 실행 순서 제어와 INSERT 이후 실패 주입에만 사용한다. DB 오류나 잠금 시간 초과를 업무상 중복 409로 간주하지 않는다.
- 작업 스레드의 커밋을 확인하므로 테스트 기본 트랜잭션은 NOT_SUPPORTED로 둔다. UUID로 생성한 테스트 회원 ID만 추적하고, 모든 작업 스레드 종료 후 해당 회원의 제재 → 회원 순서로 명시적으로 정리한다. 테스트 DB 확인 실패 시 쓰기와 정리를 수행하지 않는다.
- 트랜잭션 밖의 fixture INSERT는 같은 PreparedStatement의 GeneratedKeyHolder로 ID를 얻는다. 커넥션 풀이 다른 연결을 반환할 수 있으므로 별도 LAST_INSERT_ID 조회는 사용하지 않는다.
- 신규 실행 사례는 3개이며, 기존 권한·입력 검증 조합은 반복하지 않는다. 사용자에게 제공한 코드의 소스 적용을 확인했고, 2026-10-19 14:50:17 KST JUnit XML에서 3개 사례의 실패·오류·건너뜀 0건을 확인했다. 함께 실행된 Mapper 4개 사례도 통과했다. 이번 문서 갱신에서 에이전트가 테스트를 직접 재실행하거나 전체 테스트를 검증한 것은 아니다.

실행 명령 (`boardKitApi` 디렉토리):

```bash
./gradlew test --tests '*AdminMemberSanctionServiceIntegrationTest' \
  --tests '*MemberSanctionMapperIntegrationTest'
```

## 관리자 회원 검색 API 검증 — 2026-10-19

- 기존 `AdminMemberSanctionApiTest`의 WebMvcTest 대상에 `AdminMemberApi`를 추가하고 Mock Service와 보안 설정을 재사용한다.
- 로그인 ID·닉네임 검색 항목 전달, 기본/지정 페이지, 제재 여부를 포함한 응답과 비밀번호 해시·인증 버전 미노출, no-store, 요청 관리자 ID 무시를 확인한다.
- 비로그인·일반 회원 거절과 필수 파라미터 누락, 공백 검색어, 잘못된 enum·페이지·크기의 400 및 Service 미호출을 확인한다. 조회 성공은 CSRF 없이 검증한다.
- 테스트 요청 빌더의 `params()` 무인자 호출은 지원되지 않는다. 소스에서 각 파라미터를 `.param(...)`으로 처음부터 구성하는 방식으로 수정된 것을 확인했다.
- 2026-10-19 15:06:31 KST JUnit XML에서 기존 등록·해제와 신규 검색을 포함한 38개 사례의 실패·오류·건너뜀 0건을 확인했다. 이번 갱신에서 에이전트가 테스트를 직접 실행한 것은 아니며, 실제 검색 SQL이나 전체 테스트의 실행 결과를 의미하지 않는다.

## 관리자 제재 이력 Mapper 조회 검증 — 2026-10-19

- 기존 `MemberSanctionMapperIntegrationTest`에 상태 필터 4개와 해제 감사 정보 유무 2개의 조회 사례를 추가한다. 공통 DB 기준 시각을 사용하며 테스트별 트랜잭션 롤백을 재사용한다.
- 기간/영구 제재의 진행 상태, 종료 시각과 기준 시각이 같은 만료 경계, 종료 시각이 지난 해제 기록의 분류 우선순위, 회원별/전체 건수, 등록 시각이 같을 때 ID 정렬과 LIMIT/OFFSET을 확인한다.
- 탈퇴한 대상 회원과 등록 관리자 정보가 이력 조회에서 유지되는지, 해제자·사유가 없는 과거 기록도 조회되는지, 없는 상세가 Optional.empty인지 확인한다.
- Mapper/XML 소스와 2026-10-19 15:25:52 KST JUnit XML에서 기존 등록·해제·회원 검색과 신규 이력 조회를 포함한 13개 사례의 실패·오류·건너뜀 0건을 확인했다. 이번 문서 갱신에서 에이전트가 직접 재실행한 것은 아니다. 이력 조회 Service/API는 후속 구현이므로 해당 계층 검증 완료를 의미하지 않는다.

## 관리자 제재 이력 Service 조회 검증 — 2026-10-19

- 기존 `AdminMemberSanctionServiceTest`에서 전체/회원별 목록의 공통 UTC 기준 시각 전달, 페이지 계산, 빈 결과·범위 초과 시 목록 쿼리 생략, 탈퇴 회원 상세 조회 및 없는 상세의 404를 확인한다. 기존 권한 거절 테스트에 목록·상세 조회도 포함한다.
- Service 소스 적용과 2026-10-19 15:29:48 KST JUnit XML에서 기존 등록·해제·회원 검색을 포함한 25개 사례의 실패·오류·건너뜀 0건을 확인했다. 에이전트가 직접 재실행한 결과는 아니다.
- Mock 단위 테스트는 실제 SQL의 상태 분류나 Spring의 메서드 입력 검증을 대신하지 않는다. 상태 분류는 기존 Mapper 통합 테스트에서 확인하며, GET API의 입력 변환·검증·관리자 권한·no-store·오류 응답 검증은 코드 제공 후 진행한다.

## 관리자 제재 이력 API 조회 검증 — 2026-10-19

- 기존 `AdminMemberSanctionApiTest`에서 기본 진행 중 필터·전체 회원 조회, 지정 회원/필터/페이지 전달, 탈퇴 회원 상세와 등록·해제 관리자 정보, no-store, 인증 회원 ID 전달을 확인한다. 성공 GET에는 CSRF 토큰을 넣지 않는다.
- 목록·상세의 비로그인 401·일반 회원 403, 잘못된 필터·회원 ID·페이지·크기의 400, 상세 경로 ID 검증과 없는 상세의 404를 확인한다. Mock Service 미호출 또는 실제 위임 여부를 구분한다.
- API 소스와 2026-10-19 15:33:50 KST JUnit XML에서 기존 등록·해제·회원 검색을 포함한 50개 사례의 실패·오류·건너뜀 0건을 확인했다. 에이전트가 직접 실행하거나 전체 테스트를 재실행한 결과는 아니다. 관리자 프론트와 브라우저 동작은 후속 검증이다.

## 관리자 회원 제재 화면 — 사용자 브라우저 검증 완료

- `AdminMemberSanctionView.vue`와 `/admin/member-sanctions` 관리자 라우트·대시보드 메뉴, 검색 컴포넌트의 `refreshKey` 연결을 소스에서 확인했다.
- 회원 검색·선택, 기간/영구 제재 등록, 중복 등록 거절, 이력/상세 조회와 해제, 처리 후 화면 갱신, 만료·해제·탈퇴 이력의 해제 폼 제한, 필터·페이지·상세의 새로고침/뒤로가기 확인 절차를 안내했다. 사용자가 해당 단계의 브라우저 테스트 완료를 보고했다.
- 브라우저 검증 완료는 사용자 보고 기준이다. 에이전트가 브라우저를 직접 조작하거나 전체 Vite 빌드·백엔드 전체 테스트를 이번 갱신에서 재실행한 것은 아니다. 별도 프론트 테스트 코드는 추가하지 않는다.

## 관리자 회원 제재 화면 — 에이전트 브라우저 검증, 2026-10-19

- 로그인된 앱 내 브라우저의 `/admin/member-sanctions`에서 직접 UI를 조작했다. 기존 `test` 회원의 제재는 변경하지 않고, 제재 이력이 없던 `test2`(회원 ID 7)를 사용했다.
- 정상 확인: 로그인 ID 검색, 닉네임 검색, 회원 선택, 과거 종료 일시 거절, 기간제/영구 제재 등록, 등록 후 검색·목록·상세 갱신, 해제 및 처리자·사유 표시, 해제 후 검색 결과의 `제재 없음`, 해제 필터, 새로고침 후 회원 범위·필터·상세 복원.
- **실패: 진행 중인 기간제 제재가 있는데 중복 등록이 성공했다.** 16:28 KST에 종료 시각 17:30 KST인 기간제 제재 ID 3을 등록한 직후, 같은 회원에게 영구 제재 ID 4가 추가되었다. 진행 중 목록에도 두 건이 함께 표시됐다. 기존의 사용자 검증 완료 보고 및 테스트 통과가 이 경로의 정상 동작을 보장하지 않는다.
- **실패: 등록 일시가 9시간 뒤로 표시됐다.** 시작 일시는 `2026. 10. 19. 16:28`, 등록 일시는 `2026. 10. 20. 01:28`이었다. 해제 일시는 실제 조작 시각인 `2026. 10. 19. 16:29`로 표시됐다.
- 소스상 시간 기준 불일치를 확인했다. `MemberMapper.existsActiveSanctionByMemberId`는 `CURRENT_TIMESTAMP(6)`, 검색·해제 SQL은 `UTC_TIMESTAMP(6)`, Service의 시작/종료/조회 시각은 UTC를 사용한다. `created_at`은 INSERT에서 생략되어 DDL의 `DEFAULT CURRENT_TIMESTAMP(6)`에 의존하고, 프론트는 UTC로 해석한다. 실제 운영 연결의 DB 세션 시간대는 이번 검증에서 직접 조회하지 않았으므로, 시간대 불일치를 유력 원인으로 기록한다.
- 기존 트랜잭션 통합 테스트는 연결 세션을 `time_zone = '+00:00'`으로 고정하고 중복 등록에 영구 제재를 사용한다. 비UTC 세션의 기간제 제재 중복과 등록 시각 일관성은 별도 회귀 검증이 필요하다.
- 정리: 에이전트가 만든 ID 4와 ID 3을 순서대로 해제했다. 두 건의 감사 이력은 유지되며 테스트 회원에게 남은 진행 중 제재는 0건이다. 기능 소스·DB 설정은 수정하지 않았다.
- 미검증: 회원 계정으로 로그인하여 실제 작성·좋아요·신고 제한 확인, 자연 만료 대기, 탈퇴 회원 처리, 다중 페이지 이동, 동시 관리자 조작. 이번 브라우저 결과는 이 범위까지 통과했다는 의미가 아니다.

## 회원 제재 시간 기준 수정 및 회귀 검증 — 2026-10-19

- `MemberMapper.existsActiveSanctionByMemberId`의 현재 시각 비교 두 곳을 `CURRENT_TIMESTAMP(6)`에서 `UTC_TIMESTAMP(6)`로 변경했다. DB 세션이 한국 시각이어도 UTC로 저장된 1시간짜리 제재를 이미 만료된 것으로 판단하지 않는다. 기존 작성·좋아요·신고 등의 제재 검사도 이 Mapper를 공유한다.
- `MemberSanctionMapper.insertSanction`에 `created_at = UTC_TIMESTAMP(6)` 값을 명시했다. DDL의 세션 의존 기본값에 맡기지 않으므로 DB 구조 변경은 필요 없다. 프론트의 UTC 해석은 유지한다. 과거에 잘못 저장된 등록 시각은 자동 보정하지 않는다.
- Mapper 통합 테스트에 `+09:00`·`-09:00` 세션에서 1시간짜리 제재의 유효 여부와 등록 시각의 UTC 범위를 확인하는 파라미터 사례 2개를 추가했다. 테스트 후 세션 시간대를 UTC로 복구하며 기존 테스트 트랜잭션으로 데이터는 롤백한다.
- 기존 Service 트랜잭션 통합 테스트의 풀 연결을 `+09:00`으로 설정하고 등록 fixture를 1시간짜리 기간제로 변경했다. 테스트 수를 늘리지 않고 동시 중복 차단·다른 회원의 독립 처리·롤백을 실제 비UTC 환경에서 검증한다.
- 기능 수정 전 신규 Mapper 2개 사례에서 현재 제재 여부와 등록 시각 검사가 실패했고, 기존 동시 등록 사례도 성공 요청이 기대한 1건이 아닌 2건으로 재현됐다.
- 기능 수정 후 에이전트가 아래 명령을 실행했다. Mapper 통합 15개·Service 트랜잭션 통합 3개·Service 단위 25개·API 50개, **총 93개가 통과**했다. 실패·오류·건너뜀은 모두 0건이다. 전체 프로젝트 테스트 실행 결과는 아니다.
- 수정 후 실제 앱 브라우저 검증은 아직 수행하지 않았다. 서버를 재시작한 뒤 종료가 약 1시간 남은 기간제 제재의 중복 등록 거절과 신규 제재의 등록/시작 시각 일치를 확인한다. 기존 잘못된 등록 시각은 수정 전 기록으로 구분한다.

실행 명령 (`boardKitApi` 디렉토리):

```bash
./gradlew test --tests '*MemberSanctionMapperIntegrationTest' \
  --tests '*AdminMemberSanctionServiceIntegrationTest' \
  --tests '*AdminMemberSanctionServiceTest' \
  --tests '*AdminMemberSanctionApiTest'
```

## 참고

- [MyBatis 테스트 구성](https://mybatis.org/spring-boot-starter/mybatis-spring-boot-test-autoconfigure/)
- [Spring 테스트 트랜잭션과 롤백](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/tx.html)
