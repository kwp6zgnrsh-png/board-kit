package io.github.kwp6zgnrsh.boardkitapi.post.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import io.github.kwp6zgnrsh.boardkitapi.post.exception.InvalidPostRequestException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

@Component
public class PostDocumentValidator {

	private static final JsonNodeFactory NODES = JsonNodeFactory.instance;
	private static final Set<String> TEXT_MARKS =
		Set.of("bold", "italic", "underline", "strike", "code", "link");
	private static final int MAX_BLOCK_DEPTH = 64;

	public ValidatedDocument validate(JsonNode source) {
		if (source == null || !source.isObject()
			|| !"doc".equals(source.path("type").asString(""))
			|| !source.path("content").isArray()) {
			throw new InvalidPostRequestException("본문 형식이 올바르지 않습니다.");
		}

		ValidationContext context = new ValidationContext();
		ObjectNode document = NODES.objectNode().put("type", "doc");
		ArrayNode blocks = document.putArray("content");

		for (JsonNode block : source.path("content")) {
			blocks.add(normalizeBlock(block, "doc", context, 1));
		}

		return new ValidatedDocument(
			document,
			String.join("\n", context.texts),
			List.copyOf(context.images)
		);
	}

	private ObjectNode normalizeBlock(
		JsonNode source, String parentType, ValidationContext context, int depth
	) {
		if (depth > MAX_BLOCK_DEPTH) {
			throw new InvalidPostRequestException("본문 구조가 너무 깊게 중첩되었습니다.");
		}
		if (!source.isObject()) {
			throw new InvalidPostRequestException("본문 요소가 올바르지 않습니다.");
		}

		String type = source.path("type").asString("");
		boolean parentIsList =
			"bulletList".equals(parentType) || "orderedList".equals(parentType);

		if (parentIsList && !"listItem".equals(type)) {
			throw new InvalidPostRequestException("목록에는 목록 항목만 사용할 수 있습니다.");
		}
		if ("listItem".equals(type) && !parentIsList) {
			throw new InvalidPostRequestException("목록 항목은 목록 안에서만 사용할 수 있습니다.");
		}

		ObjectNode normalized = NODES.objectNode().put("type", type);

		switch (type) {
			case "paragraph" ->
				normalizeTextContent(source, normalized, context);

			case "heading" -> {
				JsonNode level = source.path("attrs").path("level");
				if (!level.isIntegralNumber() || !level.canConvertToInt()
					|| (level.intValue() != 2 && level.intValue() != 3)) {
					throw new InvalidPostRequestException("본문 제목은 H2 또는 H3만 사용할 수 있습니다.");
				}

				normalized.putObject("attrs").put("level", level.intValue());
				normalizeTextContent(source, normalized, context);
			}

			case "codeBlock" -> {
				JsonNode language = source.path("attrs").get("language");
				if (language != null && !language.isNull()) {
					if (!language.isString()) {
						throw new InvalidPostRequestException("코드 언어 정보가 올바르지 않습니다.");
					}
					normalized.putObject("attrs").put("language", language.asString());
				}

				normalizeTextContent(source, normalized, context);
			}

			case "blockquote", "bulletList", "orderedList", "listItem" -> {
				JsonNode children = source.get("content");
				if (children == null || !children.isArray() || children.isEmpty()) {
					throw new InvalidPostRequestException("목록 또는 인용문 구조가 올바르지 않습니다.");
				}
				if ("listItem".equals(type)
					&& !"paragraph".equals(children.get(0).path("type").asString(""))) {
					throw new InvalidPostRequestException("목록 항목은 문단으로 시작해야 합니다.");
				}

				if ("orderedList".equals(type)) {
					JsonNode start = source.path("attrs").get("start");
					int startNumber = 1;

					if (start != null && !start.isNull()) {
						if (!start.isIntegralNumber() || !start.canConvertToInt()
							|| start.intValue() < 1) {
							throw new InvalidPostRequestException("목록 시작 번호가 올바르지 않습니다.");
						}
						startNumber = start.intValue();
					}

					normalized.putObject("attrs").put("start", startNumber);
				}

				ArrayNode content = normalized.putArray("content");
				for (JsonNode child : children) {
					content.add(normalizeBlock(child, type, context, depth + 1));
				}
			}

			case "image" -> {
				ImageReference reference = readImageReference(source);
				boolean duplicate = reference.fileId() != null
					? !context.usedFileIds.add(reference.fileId())
					: !context.usedFileIndexes.add(reference.fileIndex());

				if (duplicate) {
					throw new InvalidPostRequestException(
						"같은 첨부 이미지가 본문에 반복되었습니다. 이미지를 다시 확인해 주세요."
					);
				}

				context.images.add(reference);
				ObjectNode attrs = normalized.putObject("attrs");

				if (reference.fileId() != null) {
					attrs.put("fileId", reference.fileId());
				} else {
					attrs.put("fileIndex", reference.fileIndex());
				}
			}

			case "horizontalRule" -> {
				JsonNode children = source.get("content");
				if (children != null && (!children.isArray() || !children.isEmpty())) {
					throw new InvalidPostRequestException("구분선에는 내용을 넣을 수 없습니다.");
				}
			}

			default ->
				throw new InvalidPostRequestException("지원하지 않는 본문 요소가 있습니다.");
		}

		return normalized;
	}

	private void normalizeTextContent(
		JsonNode source, ObjectNode normalized, ValidationContext context
	) {
		boolean codeBlock = "codeBlock".equals(source.path("type").asString(""));
		ArrayNode content = normalized.putArray("content");
		StringBuilder text = new StringBuilder();
		JsonNode children = source.get("content");

		if (children != null) {
			if (!children.isArray()) {
				throw new InvalidPostRequestException("문단 형식이 올바르지 않습니다.");
			}

			for (JsonNode child : children) {
				ObjectNode inline = NODES.objectNode();
				String type = child.path("type").asString("");

				switch (type) {
					case "text" -> {
						JsonNode value = child.get("text");
						if (value == null || !value.isString() || value.asString().isEmpty()) {
							throw new InvalidPostRequestException("본문 텍스트가 올바르지 않습니다.");
						}

						inline.put("type", "text");
						inline.put("text", value.asString());
						text.append(value.asString());
					}

					case "hardBreak" -> {
						if (codeBlock) {
							throw new InvalidPostRequestException(
								"코드 블록의 줄바꿈은 텍스트로 전달해야 합니다."
							);
						}

						inline.put("type", "hardBreak");
						text.append('\n');
					}

					default ->
						throw new InvalidPostRequestException(
							"문단 안에 사용할 수 없는 요소가 있습니다."
						);
				}

				ArrayNode marks = normalizeMarks(child.get("marks"));
				if (codeBlock && !marks.isEmpty()) {
					throw new InvalidPostRequestException(
						"코드 블록 안에는 글자 서식을 적용할 수 없습니다."
					);
				}
				if (!marks.isEmpty()) {
					inline.set("marks", marks);
				}

				content.add(inline);
			}
		}

		context.texts.add(text.toString());
	}

	private ArrayNode normalizeMarks(JsonNode source) {
		ArrayNode normalized = NODES.arrayNode();
		if (source == null) {
			return normalized;
		}
		if (!source.isArray()) {
			throw new InvalidPostRequestException("글자 서식 정보가 올바르지 않습니다.");
		}

		Set<String> usedTypes = new HashSet<>();

		for (JsonNode mark : source) {
			String type = mark.path("type").asString("");

			if (!mark.isObject() || !TEXT_MARKS.contains(type)) {
				throw new InvalidPostRequestException("지원하지 않는 글자 서식입니다.");
			}
			if (!usedTypes.add(type)) {
				throw new InvalidPostRequestException("글자 서식 정보가 중복되었습니다.");
			}

			ObjectNode normalizedMark = normalized.addObject().put("type", type);
			if ("link".equals(type)) {
				normalizedMark.putObject("attrs").put("href", validateLinkHref(mark));
			}
		}

		if (usedTypes.contains("code") && usedTypes.size() > 1) {
			throw new InvalidPostRequestException(
				"인라인 코드는 다른 글자 서식과 함께 적용할 수 없습니다."
			);
		}

		return normalized;
	}

	private String validateLinkHref(JsonNode mark) {
		JsonNode value = mark.path("attrs").path("href");
		if (!value.isString() || value.asString().isBlank()) {
			throw new InvalidPostRequestException("링크 주소를 입력해 주세요.");
		}

		String href = value.asString().strip();

		try {
			URI uri = new URI(href);
			if (!"http".equalsIgnoreCase(uri.getScheme())
				&& !"https".equalsIgnoreCase(uri.getScheme())) {
				throw new InvalidPostRequestException(
					"링크는 http 또는 https 주소만 사용할 수 있습니다."
				);
			}
			if (uri.getHost() == null || uri.getHost().isBlank()) {
				throw new InvalidPostRequestException("링크 주소가 올바르지 않습니다.");
			}

			return href;
		} catch (URISyntaxException exception) {
			throw new InvalidPostRequestException("링크 주소가 올바르지 않습니다.");
		}
	}

	private ImageReference readImageReference(JsonNode image) {
		JsonNode attrs = image.get("attrs");
		if (attrs == null || !attrs.isObject()) {
			throw new InvalidPostRequestException("첨부 이미지 정보를 확인할 수 없습니다.");
		}

		JsonNode fileId = attrs.get("fileId");
		JsonNode fileIndex = attrs.get("fileIndex");
		boolean existing = fileId != null && !fileId.isNull();
		boolean newlyUploaded = fileIndex != null && !fileIndex.isNull();

		if (existing == newlyUploaded) {
			throw new InvalidPostRequestException("첨부 이미지 정보를 확인할 수 없습니다.");
		}

		if (existing) {
			if (!fileId.isIntegralNumber() || !fileId.canConvertToLong()
				|| fileId.longValue() <= 0) {
				throw new InvalidPostRequestException("첨부 이미지 정보를 확인할 수 없습니다.");
			}
			return new ImageReference(fileId.longValue(), null);
		}

		if (!fileIndex.isIntegralNumber() || !fileIndex.canConvertToInt()
			|| fileIndex.intValue() < 0) {
			throw new InvalidPostRequestException("첨부 이미지 정보를 확인할 수 없습니다.");
		}
		return new ImageReference(null, fileIndex.intValue());
	}

	private static class ValidationContext {
		private final List<String> texts = new ArrayList<>();
		private final List<ImageReference> images = new ArrayList<>();
		private final Set<Long> usedFileIds = new HashSet<>();
		private final Set<Integer> usedFileIndexes = new HashSet<>();
	}

	public record ImageReference(Long fileId, Integer fileIndex) {
	}

	public record ValidatedDocument(
		JsonNode document, String text, List<ImageReference> images
	) {
	}
}