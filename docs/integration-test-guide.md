# MySQL Mapper·파일·인증 테스트

작성일: 2026-10-09
최종 갱신일: 2026-10-18

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

## 참고

- [MyBatis 테스트 구성](https://mybatis.org/spring-boot-starter/mybatis-spring-boot-test-autoconfigure/)
- [Spring 테스트 트랜잭션과 롤백](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/tx.html)
