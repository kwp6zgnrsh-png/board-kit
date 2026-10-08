package io.github.kwp6zgnrsh.boardkitapi.post.service;

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

	public ValidatedDocument validate(JsonNode source) {
		if (source == null || !source.isObject()
			|| !"doc".equals(source.path("type").asString(""))
			|| !source.path("content").isArray()) {
			throw new InvalidPostRequestException("본문 형식이 올바르지 않습니다.");
		}

		ObjectNode document = NODES.objectNode();
		document.put("type", "doc");
		ArrayNode blocks = document.putArray("content");

		List<String> paragraphs = new ArrayList<>();
		List<ImageReference> images = new ArrayList<>();
		Set<Long> usedFileIds = new HashSet<>();
		Set<Integer> usedFileIndexes = new HashSet<>();

		for (JsonNode block : source.path("content")) {
			switch (block.path("type").asString("")) {
				case "paragraph" -> {
					StringBuilder text = new StringBuilder();
					blocks.add(normalizeParagraph(block, text));
					paragraphs.add(text.toString());
				}
				case "image" -> {
					ImageReference reference = readImageReference(block);

					boolean duplicate = reference.fileId() != null ? !usedFileIds.add(reference.fileId()) :
						!usedFileIndexes.add(reference.fileIndex());

					if (duplicate) {
						throw new InvalidPostRequestException("같은 첨부 이미지가 본문에 반복되었습니다. 이미지를 다시 확인해 주세요.");
					}

					images.add(reference);

					ObjectNode image = NODES.objectNode();
					image.put("type", "image");
					ObjectNode attrs = image.putObject("attrs");

					if (reference.fileId() != null) {
						attrs.put("fileId", reference.fileId());
					} else {
						attrs.put("fileIndex", reference.fileIndex());
					}

					blocks.add(image);
				}
				default -> throw new InvalidPostRequestException("지원하지 않는 본문 요소가 있습니다.");
			}
		}

		return new ValidatedDocument(document, String.join("\n", paragraphs), List.copyOf(images));
	}

	private ObjectNode normalizeParagraph(JsonNode paragraph, StringBuilder text) {
		ObjectNode normalized = NODES.objectNode();
		normalized.put("type", "paragraph");
		ArrayNode content = normalized.putArray("content");

		JsonNode children = paragraph.get("content");
		if (children == null) {
			return normalized;
		}
		if (!children.isArray()) {
			throw new InvalidPostRequestException("문단 형식이 올바르지 않습니다.");
		}

		for (JsonNode child : children) {
			ObjectNode inline = NODES.objectNode();

			switch (child.path("type").asString("")) {
				case "text" -> {
					JsonNode value = child.get("text");
					JsonNode marks = child.get("marks");

					if (value == null || !value.isString() || value.asString().isEmpty()) {
						throw new InvalidPostRequestException("본문 텍스트가 올바르지 않습니다.");
					}
					if (marks != null && (!marks.isArray() || !marks.isEmpty())) {
						throw new InvalidPostRequestException("현재 지원하지 않는 글자 서식입니다.");
					}

					inline.put("type", "text");
					inline.put("text", value.asString());
					text.append(value.asString());
				}
				case "hardBreak" -> {
					inline.put("type", "hardBreak");
					text.append('\n');
				}
				default -> throw new InvalidPostRequestException("문단 안에 사용할 수 없는 요소가 있습니다.");
			}

			content.add(inline);
		}

		return normalized;
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
			if (!fileId.isIntegralNumber() || !fileId.canConvertToLong() || fileId.longValue() <= 0) {
				throw new InvalidPostRequestException("첨부 이미지 정보를 확인할 수 없습니다.");
			}

			return new ImageReference(fileId.longValue(), null);
		}

		if (!fileIndex.isIntegralNumber() || !fileIndex.canConvertToInt() || fileIndex.intValue() < 0) {
			throw new InvalidPostRequestException("첨부 이미지 정보를 확인할 수 없습니다.");
		}

		return new ImageReference(null, fileIndex.intValue());
	}

	public record ImageReference(Long fileId, Integer fileIndex) {
	}

	public record ValidatedDocument(JsonNode document, String text, List<ImageReference> images) {
	}
}