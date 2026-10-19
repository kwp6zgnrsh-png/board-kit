-- Community V2 schema draft, 2026-09-19
-- Last synchronized with the agreed policies: 2026-10-11.
-- Target: MySQL 8.0.16+ / InnoDB (CHECK constraints must be enforced).
-- Apply once to an EMPTY database explicitly selected by the caller.
-- This file does not create/drop a database or disable FK checks.
-- Session time zone: UTC. Store and interpret DATETIME values as UTC.
-- Status values, field lengths, indexes and defaults are draft choices.
-- See schema.md for policies that still require application validation.

SET NAMES utf8mb4;
SET SESSION time_zone = '+00:00';

CREATE TABLE member (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    login_id VARCHAR(50) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    auth_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    display_name VARCHAR(50) NOT NULL,
    role VARCHAR(16) NOT NULL DEFAULT 'MEMBER',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_member_login_id (login_id),
    UNIQUE KEY uq_member_display_name (display_name),
    CONSTRAINT ck_member_role CHECK (role IN ('MEMBER', 'ADMIN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE board (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(100) NOT NULL,
    description TEXT NULL,
    display_type VARCHAR(16) NOT NULL DEFAULT 'LIST',
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    sort_order INT UNSIGNED NOT NULL DEFAULT 0,
    created_by BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_board_slug (slug),
    KEY ix_board_menu (status, sort_order, id),
    KEY ix_board_created_by (created_by),
    CONSTRAINT fk_board_creator FOREIGN KEY (created_by) REFERENCES member (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_board_display CHECK (display_type IN ('LIST', 'CARD')),
    CONSTRAINT ck_board_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE board_policy (
    board_id BIGINT UNSIGNED NOT NULL,
    read_permission VARCHAR(16) NOT NULL DEFAULT 'PUBLIC',
    write_permission VARCHAR(16) NOT NULL DEFAULT 'MEMBER',
    comment_permission VARCHAR(16) NOT NULL DEFAULT 'MEMBER',
    secret_mode VARCHAR(16) NOT NULL DEFAULT 'OFF',
    categories_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    category_required BOOLEAN NOT NULL DEFAULT FALSE,
    attachments_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    upload_profile VARCHAR(32) NOT NULL DEFAULT 'IMAGE',
    max_file_count INT UNSIGNED NOT NULL DEFAULT 0,
    max_file_size_bytes BIGINT UNSIGNED NOT NULL DEFAULT 0,
    image_required BOOLEAN NOT NULL DEFAULT FALSE,
    thumbnail_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    comments_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    comment_replies_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    post_likes_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    comment_likes_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    official_reply_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (board_id),
    CONSTRAINT fk_policy_board FOREIGN KEY (board_id) REFERENCES board (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_policy_read CHECK (read_permission IN ('PUBLIC', 'MEMBER', 'ADMIN')),
    CONSTRAINT ck_policy_write CHECK (write_permission IN ('MEMBER', 'ADMIN')),
    CONSTRAINT ck_policy_comment CHECK (comment_permission IN ('MEMBER', 'ADMIN')),
    CONSTRAINT ck_policy_secret CHECK (secret_mode IN ('OFF', 'OPTIONAL', 'REQUIRED')),
    CONSTRAINT ck_policy_upload CHECK (upload_profile IN ('IMAGE', 'IMAGE_AND_ZIP', 'IMAGE_AND_FILE')),
    CONSTRAINT ck_policy_booleans CHECK (
        categories_enabled IN (0, 1)
        AND category_required IN (0, 1) AND attachments_enabled IN (0, 1)
        AND image_required IN (0, 1) AND thumbnail_enabled IN (0, 1)
        AND comments_enabled IN (0, 1) AND comment_replies_enabled IN (0, 1)
        AND post_likes_enabled IN (0, 1) AND comment_likes_enabled IN (0, 1)
        AND official_reply_enabled IN (0, 1)
    ),
    CONSTRAINT ck_policy_category_required CHECK (category_required = 0 OR categories_enabled = 1),
    CONSTRAINT ck_policy_image_required CHECK (image_required = 0 OR attachments_enabled = 1),
    CONSTRAINT ck_policy_upload_limits CHECK (
        (attachments_enabled = 0 AND max_file_count = 0 AND max_file_size_bytes = 0)
        OR (attachments_enabled = 1 AND max_file_count > 0 AND max_file_size_bytes > 0)
    ),
    CONSTRAINT ck_policy_thumbnail_enabled CHECK (thumbnail_enabled = 0 OR attachments_enabled = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE board_category (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    board_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(50) NOT NULL,
    sort_order INT UNSIGNED NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    KEY ix_category_board_order (board_id, active, sort_order, id),
    CONSTRAINT fk_category_board FOREIGN KEY (board_id) REFERENCES board (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_category_active CHECK (active IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE post (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    board_id BIGINT UNSIGNED NOT NULL,
    author_id BIGINT UNSIGNED NOT NULL,
    category_id BIGINT UNSIGNED NULL,
    title VARCHAR(200) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    content_document JSON NULL,
    is_secret BOOLEAN NOT NULL DEFAULT FALSE,
    secret_password_hash VARCHAR(255) NULL,
    is_pinned BOOLEAN NOT NULL DEFAULT FALSE,
    comments_locked BOOLEAN NOT NULL DEFAULT FALSE,
    representative_file_id BIGINT UNSIGNED NULL,
    view_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    deleted_at DATETIME(6) NULL,
    blinded_at DATETIME(6) NULL,
    blind_report_base_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY ix_post_board_list (board_id, deleted_at, is_pinned, created_at, id),
    KEY ix_post_author_list (author_id, deleted_at, created_at, id),
    KEY ix_post_category (category_id),
    KEY ix_post_representative (representative_file_id),
    CONSTRAINT fk_post_board FOREIGN KEY (board_id) REFERENCES board (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_post_author FOREIGN KEY (author_id) REFERENCES member (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_post_category FOREIGN KEY (category_id) REFERENCES board_category (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_post_booleans CHECK (is_secret IN (0, 1) AND is_pinned IN (0, 1) AND comments_locked IN (0, 1)),
    CONSTRAINT ck_post_public_password CHECK (is_secret = 1 OR secret_password_hash IS NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE post_file (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    post_id BIGINT UNSIGNED NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    storage_key VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_usage VARCHAR(16) NOT NULL DEFAULT 'ATTACHMENT',
    size_bytes BIGINT UNSIGNED NOT NULL,
    thumbnail_key VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL,
    sort_order INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_file_storage_key (storage_key),
    UNIQUE KEY uq_file_thumbnail_key (thumbnail_key),
    KEY ix_file_post_order (post_id,deleted_at,sort_order,id),
    CONSTRAINT fk_file_post FOREIGN KEY (post_id) REFERENCES post (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_post_file_size CHECK (size_bytes > 0),
    CONSTRAINT ck_post_file_usage CHECK (file_usage IN ('BODY_IMAGE', 'ATTACHMENT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Insert post with representative_file_id = NULL, then insert its files.
-- Select the first active BODY_IMAGE by sort_order and id (body-document order).
-- Validate that the representative file belongs to the same post.
ALTER TABLE post
    ADD CONSTRAINT fk_post_representative FOREIGN KEY (representative_file_id)
    REFERENCES post_file (id) ON DELETE RESTRICT ON UPDATE RESTRICT;

CREATE TABLE comment (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    post_id BIGINT UNSIGNED NOT NULL,
    author_id BIGINT UNSIGNED NOT NULL,
    parent_id BIGINT UNSIGNED NULL,
    content TEXT NOT NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at DATETIME(6) NULL,
    blinded_at DATETIME(6) NULL,
    blind_report_base_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY ix_comment_post (post_id, created_at, id),
    KEY ix_comment_parent (parent_id, created_at, id),
    KEY ix_comment_author (author_id, deleted_at, created_at, id),
    CONSTRAINT fk_comment_post FOREIGN KEY (post_id) REFERENCES post (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_comment_author FOREIGN KEY (author_id) REFERENCES member (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_comment_parent FOREIGN KEY (parent_id) REFERENCES comment (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE post_like (
    post_id BIGINT UNSIGNED NOT NULL,
    member_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (post_id, member_id),
    KEY ix_post_like_member (member_id, created_at, post_id),
    CONSTRAINT fk_post_like_post FOREIGN KEY (post_id) REFERENCES post (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_post_like_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE comment_like (
    comment_id BIGINT UNSIGNED NOT NULL,
    member_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (comment_id, member_id),
    KEY ix_comment_like_member (member_id, created_at, comment_id),
    CONSTRAINT fk_comment_like_comment FOREIGN KEY (comment_id) REFERENCES comment (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_comment_like_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE post_reply (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    post_id BIGINT UNSIGNED NOT NULL,
    author_id BIGINT UNSIGNED NOT NULL,
    content TEXT NOT NULL,
    content_document JSON NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_post_reply_post (post_id),
    KEY ix_post_reply_author (author_id),
    CONSTRAINT fk_reply_post FOREIGN KEY (post_id) REFERENCES post (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_reply_author FOREIGN KEY (author_id) REFERENCES member (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE report (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    reporter_id BIGINT UNSIGNED NOT NULL,
    post_id BIGINT UNSIGNED NULL,
    comment_id BIGINT UNSIGNED NULL,
    reason VARCHAR(32) NOT NULL,
    detail TEXT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    reviewed_by BIGINT UNSIGNED NULL,
    review_note TEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    reviewed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_report_member_post (reporter_id, post_id),
    UNIQUE KEY uq_report_member_comment (reporter_id, comment_id),
    KEY ix_report_queue (status, created_at, id),
    KEY ix_report_post (post_id, status),
    KEY ix_report_comment (comment_id, status),
    KEY ix_report_reviewer (reviewed_by),
    CONSTRAINT fk_report_reporter FOREIGN KEY (reporter_id) REFERENCES member (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_report_post FOREIGN KEY (post_id) REFERENCES post (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_report_comment FOREIGN KEY (comment_id) REFERENCES comment (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_report_reviewer FOREIGN KEY (reviewed_by) REFERENCES member (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_report_target CHECK (
        (post_id IS NOT NULL AND comment_id IS NULL)
        OR (post_id IS NULL AND comment_id IS NOT NULL)
    ),
    CONSTRAINT ck_report_reason CHECK (reason IN ('SPAM', 'ABUSE', 'OTHER')),
    CONSTRAINT ck_report_status CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE member_sanction (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    member_id BIGINT UNSIGNED NOT NULL,
    admin_id BIGINT UNSIGNED NOT NULL,
    reason TEXT NOT NULL,
    starts_at DATETIME(6) NOT NULL,
    ends_at DATETIME(6) NULL,
    revoked_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY ix_sanction_member (member_id, revoked_at, ends_at, starts_at),
    KEY ix_sanction_admin (admin_id),
    CONSTRAINT fk_sanction_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_sanction_admin FOREIGN KEY (admin_id) REFERENCES member (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_sanction_period CHECK (ends_at IS NULL OR ends_at > starts_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 파일 메타데이터가 없어도 고아 파일을 정리할 수 있도록 FK를 두지 않는다.
-- target_key는 저장소 루트 기준 상대 경로다.
CREATE TABLE file_cleanup_task (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    target_key VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    attempts INT UNSIGNED NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_error TEXT NULL,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_cleanup_target_key (target_key),
    KEY ix_cleanup_due (next_attempt_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 로그아웃한 JWT의 jti와 원래 만료 시각만 저장한다. 회원 FK와 JWT 원문은 저장하지 않는다.
-- expires_at은 UTC이며, JWT 만료 이후에는 무효화 기록을 정리할 수 있다.
CREATE TABLE revoked_token (
    token_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    PRIMARY KEY (token_id),
    KEY ix_revoked_token_expiration (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
