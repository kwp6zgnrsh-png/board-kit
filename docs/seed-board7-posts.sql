-- MySQL 8.0.16+, InnoDB; post.id/comment.id must be AUTO_INCREMENT.
-- Run against an explicitly selected development/test database, in a dedicated session.
-- Replaces the old procedure: first argument is now POST COUNT, not START DATE.
-- Dates: UTC, from one calendar year ago through yesterday (365 or 366 days).
-- Each call adds new data; existing rows are preserved.
-- Each batch commits independently; failures roll back ONLY the current batch.
SET NAMES utf8mb4;
SET SESSION time_zone = '+00:00';

DROP PROCEDURE IF EXISTS seed_board7_posts;
DELIMITER $$
CREATE PROCEDURE seed_board7_posts(
    IN p_post_count INT UNSIGNED,
    IN p_author_id BIGINT UNSIGNED,
    IN p_comments_per_post INT,
    OUT p_seed_prefix VARCHAR(64)
)
SQL SECURITY INVOKER
BEGIN
    DECLARE v_start_date DATE;
    DECLARE v_days INT;
    DECLARE v_offset BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_batch_size INT DEFAULT 10000;
    DECLARE v_batch_count INT;
    DECLARE v_inserted_posts INT;
    DECLARE v_inserted_comments INT;
    DECLARE v_category_count INT;
    DECLARE v_first_post_id BIGINT UNSIGNED;
    DECLARE v_last_post_id BIGINT UNSIGNED;
    DECLARE v_is_secret BOOLEAN DEFAULT FALSE;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        DROP TEMPORARY TABLE IF EXISTS tmp_bk7_categories;
        DROP TEMPORARY TABLE IF EXISTS tmp_bk7_posts;
        RESIGNAL;
    END;

    IF p_post_count IS NULL OR p_post_count = 0 OR p_author_id IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '게시글 수와 작성자 ID를 입력하세요.';
    END IF;
    IF p_comments_per_post IS NULL OR p_comments_per_post NOT BETWEEN 0 AND 5 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '게시글당 댓글 수는 0~5개로 지정하세요.';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM board b JOIN board_policy bp ON bp.board_id = b.id
        WHERE b.id = 7 AND b.status = 'ACTIVE'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '설정이 있는 활성 게시판 7번이 필요합니다.';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM member WHERE id = p_author_id AND deleted_at IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '존재하는 활성 회원 ID를 지정하세요.';
    END IF;
    IF EXISTS (
        SELECT 1 FROM board_policy WHERE board_id = 7 AND categories_enabled = FALSE
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '게시판 7번의 카테고리 기능이 꺼져 있습니다.';
    END IF;
    IF EXISTS (
        SELECT 1 FROM board_policy WHERE board_id = 7 AND image_required = TRUE
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '이미지 필수 게시판에는 텍스트 데이터만 생성할 수 없습니다.';
    END IF;
    IF p_comments_per_post > 0 AND EXISTS (
        SELECT 1 FROM board_policy WHERE board_id = 7 AND comments_enabled = FALSE
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '댓글 기능을 켜거나 댓글 수를 0으로 지정하세요.';
    END IF;

    SELECT secret_mode = 'REQUIRED' INTO v_is_secret
    FROM board_policy WHERE board_id = 7;
    SET v_start_date = DATE_SUB(UTC_DATE(), INTERVAL 1 YEAR);
    SET v_days = DATEDIFF(UTC_DATE(), v_start_date);
    SET p_seed_prefix = CONCAT('[SEED7-', UUID(), '] ');

    DROP TEMPORARY TABLE IF EXISTS tmp_bk7_categories;
    DROP TEMPORARY TABLE IF EXISTS tmp_bk7_posts;
    CREATE TEMPORARY TABLE tmp_bk7_categories (
        category_no INT PRIMARY KEY,
        category_id BIGINT UNSIGNED NOT NULL
    ) ENGINE = InnoDB;
    INSERT INTO tmp_bk7_categories (category_no, category_id)
    SELECT ROW_NUMBER() OVER (ORDER BY id), id
    FROM board_category WHERE board_id = 7 AND active = TRUE;
    SELECT COUNT(*) INTO v_category_count FROM tmp_bk7_categories;
    IF v_category_count = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '게시판 7번에 활성 카테고리가 없습니다.';
    END IF;
    CREATE TEMPORARY TABLE tmp_bk7_posts (
        seq_no BIGINT UNSIGNED PRIMARY KEY,
        category_id BIGINT UNSIGNED NOT NULL,
        title VARCHAR(200) NOT NULL,
        body TEXT NOT NULL,
        created_at DATETIME(6) NOT NULL,
        UNIQUE KEY uq_seed_title (title)
    ) ENGINE = InnoDB
      DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

    WHILE v_offset < p_post_count DO
        SET v_batch_count = LEAST(v_batch_size, p_post_count - v_offset);
        START TRANSACTION;
        DELETE FROM tmp_bk7_posts;

        INSERT INTO tmp_bk7_posts (seq_no, category_id, title, body, created_at)
        WITH digits AS (
            SELECT 0 AS d UNION ALL SELECT 1 UNION ALL SELECT 2
            UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
            UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
        ), numbers AS (
            SELECT v_offset + a.d + b.d * 10 + c.d * 100 + d.d * 1000 AS n
            FROM digits a CROSS JOIN digits b CROSS JOIN digits c CROSS JOIN digits d
            WHERE a.d + b.d * 10 + c.d * 100 + d.d * 1000 < v_batch_count
        ), topics AS (
            SELECT 0 AS topic_no, 'Spring Boot' AS keyword,
                '스프링 부트에서 인증과 트랜잭션을 구현했습니다.' AS body
            UNION ALL SELECT 1, 'Java', '자바 컬렉션과 스트림, 객체 설계 방법을 정리했습니다.'
            UNION ALL SELECT 2, 'Vue', '뷰 컴포넌트의 상태 관리와 화면 재사용 방법을 실습했습니다.'
            UNION ALL SELECT 3, 'TypeScript', '타입스크립트의 인터페이스와 제네릭, 타입 추론을 살펴봤습니다.'
            UNION ALL SELECT 4, 'MySQL', '마이에스큐엘 인덱스와 실행 계획, 검색 쿼리를 비교했습니다.'
            UNION ALL SELECT 5, 'MyBatis', '마이바티스 동적 SQL과 매퍼의 조회 결과를 확인했습니다.'
            UNION ALL SELECT 6, 'Redis', '레디스 캐시와 TTL, 만료 데이터 관리 방법을 조사했습니다.'
            UNION ALL SELECT 7, 'Elasticsearch', '엘라스틱서치 색인과 노리 분석기로 한국어를 검색했습니다.'
            UNION ALL SELECT 8, 'Ubuntu', '우분투 서버에 엔진엑스와 HTTPS를 설정하고 배포했습니다.'
            UNION ALL SELECT 9, '여행', '제주 여행의 숙소와 맛집, 산책 코스를 공유합니다.'
        )
        SELECT n.n + 1, c.category_id,
            CONCAT(p_seed_prefix, t.keyword, ' ',
                ELT(1 + MOD(n.n DIV 10, 5),
                    '입문 정리', '오류 해결', '실습 후기', '성능 비교', '질문 모음'),
                ' #', n.n + 1),
            CONCAT(t.body, ' 커뮤니티에서 경험과 의견을 공유합니다. ',
                REPEAT('실행 결과와 개선 과정을 기록하고 다른 설정과 비교했습니다. ',
                    1 + MOD(n.n DIV 10, 7)),
                CASE WHEN MOD(n.n, 97) = 0
                    THEN '희귀키워드 오로라연구소를 포함한 테스트입니다.' ELSE '' END),
            -- Reserve the final five minutes of each day for the comments.
            TIMESTAMPADD(SECOND,
                (MOD(n.n * v_days, p_post_count) * 86100) DIV p_post_count,
                TIMESTAMPADD(DAY, (n.n * v_days) DIV p_post_count,
                    CAST(v_start_date AS DATETIME)))
        FROM numbers n
        JOIN topics t ON t.topic_no = MOD(n.n, 10)
        JOIN tmp_bk7_categories c ON c.category_no = 1 + MOD(
            CRC32(CONCAT(p_seed_prefix, ':category:', n.n)), v_category_count);

        INSERT INTO post (
            board_id, author_id, category_id, title, content, content_document,
            is_secret, secret_password_hash, is_pinned, comments_locked,
            representative_file_id, view_count, version, created_at, updated_at,
            deleted_at, blinded_at, blind_report_base_count
        )
        SELECT 7, p_author_id, s.category_id, s.title, s.body,
            JSON_OBJECT('type', 'doc', 'content', JSON_ARRAY(
                JSON_OBJECT('type', 'paragraph', 'content', JSON_ARRAY(
                    JSON_OBJECT('type', 'text', 'text', s.body))))),
            v_is_secret, NULL, FALSE, FALSE, NULL, 0, 0,
            s.created_at, s.created_at, NULL, NULL, 0
        FROM tmp_bk7_posts s ORDER BY s.seq_no;

        SET v_inserted_posts = ROW_COUNT();
        SET v_first_post_id = LAST_INSERT_ID();
        SELECT MAX(id) INTO v_last_post_id FROM post;
        IF v_inserted_posts <> v_batch_count THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = '배치의 게시글 생성 건수가 예상과 다릅니다.';
        END IF;

        -- Restrict to this batch's ID range, then match exact unique titles.
        -- IDs need not be consecutive; concurrent unrelated rows cannot match the titles.
        INSERT INTO comment (
            post_id, author_id, parent_id, content, version, created_at, updated_at,
            deleted_at, blinded_at, blind_report_base_count
        )
        WITH comment_numbers AS (
            SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3
            UNION ALL SELECT 4 UNION ALL SELECT 5
        )
        SELECT p.id, p_author_id, NULL,
            CONCAT(SUBSTRING(p.title, CHAR_LENGTH(p_seed_prefix) + 1), ' — ',
                ELT(c.n, '정리 감사합니다. 실습하면서 참고하겠습니다.',
                    '같은 주제를 공부하고 있어 도움이 됐습니다.',
                    '다른 설정과 비교한 결과도 궁금합니다.',
                    '문제 원인과 해결 과정을 공유해 주세요.',
                    '추가 사례가 생기면 다시 이야기해 보겠습니다.')),
            0, TIMESTAMPADD(MINUTE, c.n, s.created_at),
            TIMESTAMPADD(MINUTE, c.n, s.created_at), NULL, NULL, 0
        FROM post p
        JOIN tmp_bk7_posts s ON s.title = p.title
        CROSS JOIN comment_numbers c
        WHERE p.id BETWEEN v_first_post_id AND v_last_post_id
            AND p.board_id = 7 AND c.n <= p_comments_per_post;

        SET v_inserted_comments = ROW_COUNT();
        IF v_inserted_comments <> v_batch_count * p_comments_per_post THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = '배치의 댓글 생성 건수가 예상과 다릅니다.';
        END IF;
        COMMIT;
        SET v_offset = v_offset + v_batch_count;
    END WHILE;

    DROP TEMPORARY TABLE tmp_bk7_categories;
    DROP TEMPORARY TABLE tmp_bk7_posts;
    SELECT p_seed_prefix AS seed_prefix, p_post_count AS posts_created,
        p_post_count * p_comments_per_post AS comments_created,
        v_start_date AS first_date_utc,
        DATE_ADD(v_start_date, INTERVAL (v_days - 1) DAY) AS last_date_utc,
        v_days AS days;
END$$
DELIMITER ;

-- Add 130,000 posts and 260,000 comments in 13 batches.
-- Author ID 5 must be an existing active member. Invoke this CALL once.
-- SET @seed_started_at = UTC_TIMESTAMP(6);
-- CALL seed_board7_posts(130000, 5, 2, @seed_prefix);
-- SET @seed_finished_at = UTC_TIMESTAMP(6);
-- SELECT ROUND(TIMESTAMPDIFF(MICROSECOND, @seed_started_at,
--     @seed_finished_at) / 1000000, 3) AS elapsed_seconds;

-- Verify this run only (same connection; @seed_prefix is an OUT parameter):
-- SELECT DATE(created_at) AS utc_date, COUNT(*) AS post_count
-- FROM post WHERE board_id = 7 AND title LIKE CONCAT(@seed_prefix, '%')
-- GROUP BY DATE(created_at) ORDER BY utc_date;
