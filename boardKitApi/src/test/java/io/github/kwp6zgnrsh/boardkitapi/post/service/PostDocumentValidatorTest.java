package io.github.kwp6zgnrsh.boardkitapi.post.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.kwp6zgnrsh.boardkitapi.post.exception.InvalidPostRequestException;
import io.github.kwp6zgnrsh.boardkitapi.post.service.PostDocumentValidator.ImageReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class PostDocumentValidatorTest {

	private final PostDocumentValidator validator = new PostDocumentValidator();
	private final JsonMapper mapper = JsonMapper.builder().build();

	@Test
	@DisplayName("문단과 이미지 순서를 유지하고 검색용 텍스트를 추출한다")
	void preserveDocumentOrderAndExtractText() {
		JsonNode source = mapper.readTree("""
			{
			  "type": "doc",
			  "content": [
			    {"type":"paragraph","content":[{"type":"text","text":"앞 내용"}]},
			    {"type":"image","attrs":{"fileIndex":0,"src":"blob:preview"}},
			    {"type":"paragraph","content":[{"type":"text","text":"중간 내용"}]},
			    {"type":"image","attrs":{"fileId":12}}
			  ]
			}
			""");

		var result = validator.validate(source);

		assertEquals("앞 내용\n중간 내용", result.text());
		assertEquals(
			List.of(new ImageReference(null, 0), new ImageReference(12L, null)),
			result.images()
		);
		assertEquals(
			"image",
			result.document().path("content").get(1).path("type").asString()
		);
		assertFalse(
			result.document().path("content").get(1).path("attrs").has("src")
		);
	}

	@Test
	@DisplayName("본문 줄바꿈을 검색용 텍스트에도 반영한다")
	void extractHardBreak() {
		JsonNode source = mapper.readTree("""
			{
			  "type":"doc",
			  "content":[{
			    "type":"paragraph",
			    "content":[
			      {"type":"text","text":"첫 줄"},
			      {"type":"hardBreak"},
			      {"type":"text","text":"다음 줄"}
			    ]
			  }]
			}
			""");

		assertEquals("첫 줄\n다음 줄", validator.validate(source).text());
	}

	@Test
	@DisplayName("이미지에 기존 파일과 새 파일 참조를 동시에 지정할 수 없다")
	void rejectAmbiguousImageReference() {
		JsonNode source = mapper.readTree("""
			{
			  "type":"doc",
			  "content":[{"type":"image","attrs":{"fileId":12,"fileIndex":0}}]
			}
			""");

		assertThrows(InvalidPostRequestException.class, () -> validator.validate(source));
	}

	@Test
	@DisplayName("미리보기 주소만 있고 파일 참조가 없는 이미지는 거절한다")
	void rejectImageWithoutFileReference() {
		JsonNode source = mapper.readTree("""
			{
			  "type":"doc",
			  "content":[{"type":"image","attrs":{"src":"blob:preview"}}]
			}
			""");

		assertThrows(InvalidPostRequestException.class, () -> validator.validate(source));
	}

	@Test
	@DisplayName("같은 첨부 이미지의 중복 참조를 거절한다")
	void rejectDuplicateImageReference() {
		JsonNode source = mapper.readTree("""
			{
			  "type":"doc",
			  "content":[
			    {"type":"image","attrs":{"fileIndex":0}},
			    {"type":"image","attrs":{"fileIndex":0}}
			  ]
			}
			""");

		assertThrows(InvalidPostRequestException.class, () -> validator.validate(source));
	}

	@Test
	@DisplayName("서로 다른 첨부 이미지는 각각 본문에 배치할 수 있다")
	void acceptDistinctImageReferences() {
		JsonNode source = mapper.readTree("""
			{
			  "type":"doc",
			  "content":[
			    {"type":"image","attrs":{"fileIndex":0}},
			    {"type":"image","attrs":{"fileIndex":1}}
			  ]
			}
			""");

		assertEquals(2, validator.validate(source).images().size());
	}

	@Test
	@DisplayName("문자열로 전달한 파일 번호는 거절한다")
	void rejectStringFileReference() {
		JsonNode source = mapper.readTree("""
			{
			  "type":"doc",
			  "content":[{"type":"image","attrs":{"fileIndex":"0"}}]
			}
			""");

		assertThrows(InvalidPostRequestException.class, () -> validator.validate(source));
	}

	@Test
	@DisplayName("지원하지 않는 본문 요소는 거절한다")
	void rejectUnsupportedNode() {
		JsonNode source = mapper.readTree("""
			{"type":"doc","content":[{"type":"iframe"}]}
			""");

		assertThrows(InvalidPostRequestException.class, () -> validator.validate(source));
	}
}