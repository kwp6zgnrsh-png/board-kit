package io.github.kwp6zgnrsh.boardkitapi.post.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.kwp6zgnrsh.boardkitapi.post.exception.InvalidPostRequestException;
import io.github.kwp6zgnrsh.boardkitapi.post.service.PostDocumentValidator.ImageReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

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
		assertEquals(List.of(new ImageReference(null, 0), new ImageReference(12L, null)), result.images());
		assertEquals("image", result.document().path("content").get(1).path("type").asString());
		assertFalse(result.document().path("content").get(1).path("attrs").has("src"));
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

	@Test
	@DisplayName("허용한 글자 서식을 보존하고 불필요한 속성은 제거한다")
	void preserveSupportedTextMarks() {
		for (String type : List.of("bold", "italic", "underline", "strike", "code")) {
			ObjectNode source = documentWithMarks(type);
			ObjectNode sourceMark = (ObjectNode)source.at("/content/0/content/0/marks/0");
			sourceMark.putObject("attrs").put("style", "color:red");

			var result = validator.validate(source);
			JsonNode savedMark = result.document().at("/content/0/content/0/marks/0");

			assertEquals(type, savedMark.path("type").asString());
			assertFalse(savedMark.has("attrs"));
			assertEquals("서식 있는 내용", result.text());
		}
	}

	@Test
	@DisplayName("같은 텍스트에 굵게와 기울임을 함께 적용할 수 있다")
	void preserveCombinedTextMarks() {
		var result = validator.validate(documentWithMarks("bold", "italic"));
		JsonNode marks = result.document().at("/content/0/content/0/marks");

		assertEquals(2, marks.size());
		assertEquals("bold", marks.get(0).path("type").asString());
		assertEquals("italic", marks.get(1).path("type").asString());
		assertEquals("서식 있는 내용", result.text());
	}

	@Test
	@DisplayName("같은 글자 서식의 중복 지정은 거절한다")
	void rejectDuplicateTextMarks() {
		ObjectNode source = documentWithMarks("bold", "bold");

		assertThrows(InvalidPostRequestException.class, () -> validator.validate(source));
	}

	@Test
	@DisplayName("허용하지 않은 글자 서식은 거절한다")
	void rejectUnsupportedTextMark() {
		ObjectNode source = documentWithMarks("textStyle");

		assertThrows(InvalidPostRequestException.class, () -> validator.validate(source));
	}

	@Test
	@DisplayName("잘못된 글자 서식 구조는 거절한다")
	void rejectMalformedTextMarks() {
		for (String invalidMarks : List.of("{}", "null", "[null]", "[\"bold\"]")) {
			ObjectNode source = documentWithMarks();
			ObjectNode text = (ObjectNode)source.at("/content/0/content/0");
			text.set("marks", mapper.readTree(invalidMarks));

			assertThrows(InvalidPostRequestException.class, () -> validator.validate(source));
		}
	}

	@Test
	@DisplayName("빈 서식 배열은 제거하고 텍스트를 유지한다")
	void normalizeEmptyTextMarks() {
		var result = validator.validate(documentWithMarks());

		assertFalse(result.document().at("/content/0/content/0").has("marks"));
		assertEquals("서식 있는 내용", result.text());
	}

	private ObjectNode documentWithMarks(String... types) {
		ObjectNode source = (ObjectNode)mapper.readTree("""
			{
			  "type": "doc",
			  "content": [{
			    "type": "paragraph",
			    "content": [{"type": "text", "text": "서식 있는 내용"}]
			  }]
			}
			""");

		ObjectNode text = (ObjectNode)source.at("/content/0/content/0");
		ArrayNode marks = text.putArray("marks");
		for (String type : types) {
			marks.addObject().put("type", type);
		}

		return source;
	}

	@Test
	@DisplayName("http와 https 링크를 보존하고 검색용 텍스트를 유지한다")
	void preserveWebLinks() {
		for (String href : List.of("http://example.com", "https://example.com/posts/1?sort=latest#comments",
			"HTTPS://example.com", "  https://example.com  ")) {
			var result = validator.validate(documentWithLink(href));
			JsonNode mark = result.document().at("/content/0/content/0/marks/0");

			assertEquals("link", mark.path("type").asString());
			assertEquals(href.strip(), mark.path("attrs").path("href").asString());
			assertEquals("서식 있는 내용", result.text());
		}
	}

	@Test
	@DisplayName("웹 주소 이외의 링크 방식은 거절한다")
	void rejectNonWebLinks() {
		for (String href : List.of("javascript:alert(1)", "data:text/html,test", "mailto:user@example.com",
			"ftp://example.com/file")) {
			ObjectNode source = documentWithLink(href);

			assertThrows(InvalidPostRequestException.class, () -> validator.validate(source));
		}
	}

	@Test
	@DisplayName("불완전하거나 잘못된 링크 주소는 거절한다")
	void rejectMalformedWebLinks() {
		for (String href : List.of("", "   ", "/posts/1", "//example.com", "https://", "https:///posts/1",
			"https://example.com/a b", "https://exa\nmple.com")) {
			ObjectNode source = documentWithLink(href);

			assertThrows(InvalidPostRequestException.class, () -> validator.validate(source));
		}
	}

	@Test
	@DisplayName("누락되거나 문자열이 아닌 링크 주소는 거절한다")
	void rejectInvalidLinkAttributes() {
		for (String attrs : List.of("{}", "{\"href\":null}", "{\"href\":123}")) {
			ObjectNode source = documentWithLink("https://example.com");
			ObjectNode mark = (ObjectNode)source.at("/content/0/content/0/marks/0");
			mark.set("attrs", mapper.readTree(attrs));

			assertThrows(InvalidPostRequestException.class, () -> validator.validate(source));
		}
	}

	@Test
	@DisplayName("링크 주소만 저장하고 클라이언트의 다른 속성은 제거한다")
	void removeUnnecessaryLinkAttributes() {
		ObjectNode source = documentWithLink("https://example.com");
		ObjectNode attrs = (ObjectNode)source.at("/content/0/content/0/marks/0/attrs");
		attrs.put("target", "_blank");
		attrs.put("rel", "opener");
		attrs.put("onclick", "alert(1)");

		var result = validator.validate(source);
		JsonNode saved = result.document().at("/content/0/content/0/marks/0/attrs");

		assertEquals(1, saved.size());
		assertEquals("https://example.com", saved.path("href").asString());
	}

	@Test
	@DisplayName("문단 서식을 유지하고 중첩 본문의 텍스트와 이미지를 순서대로 추출한다")
	void preserveBlockFormatsAndExtractNestedContent() {
		JsonNode source = mapper.readTree("""
			{
			  "type":"doc",
			  "content":[
			    {
			      "type":"heading",
			      "attrs":{"level":2},
			      "content":[{"type":"text","text":"큰 제목"}]
			    },
			    {
			      "type":"bulletList",
			      "content":[{
			        "type":"listItem",
			        "content":[
			          {
			            "type":"paragraph",
			            "content":[{"type":"text","text":"첫 항목"}]
			          },
			          {
			            "type":"orderedList",
			            "attrs":{"start":3},
			            "content":[{
			              "type":"listItem",
			              "content":[{
			                "type":"paragraph",
			                "content":[{"type":"text","text":"중첩 항목"}]
			              }]
			            }]
			          }
			        ]
			      }]
			    },
			    {
			      "type":"blockquote",
			      "content":[
			        {
			          "type":"paragraph",
			          "content":[{"type":"text","text":"인용 내용"}]
			        },
			        {"type":"image","attrs":{"fileIndex":0,"src":"blob:preview"}}
			      ]
			    },
			    {
			      "type":"codeBlock",
			      "attrs":{"language":"java"},
			      "content":[{"type":"text","text":"int a = 1;\\nint b = 2;"}]
			    },
			    {"type":"horizontalRule"},
			    {
			      "type":"heading",
			      "attrs":{"level":3},
			      "content":[{"type":"text","text":"작은 제목"}]
			    }
			  ]
			}
			""");

		var result = validator.validate(source);

		assertEquals(
			"큰 제목\n첫 항목\n중첩 항목\n인용 내용\nint a = 1;\nint b = 2;\n작은 제목",
			result.text()
		);
		assertEquals(List.of(new ImageReference(null, 0)), result.images());
		assertEquals(2, result.document().at("/content/0/attrs/level").intValue());
		assertEquals(
			3,
			result.document().at("/content/1/content/0/content/1/attrs/start").intValue()
		);
		assertFalse(result.document().at("/content/2/content/1/attrs").has("src"));
		assertEquals("java", result.document().at("/content/3/attrs/language").asString());
		assertEquals("horizontalRule", result.document().at("/content/4/type").asString());
		assertEquals(3, result.document().at("/content/5/attrs/level").intValue());
	}

	@Test
	@DisplayName("H2와 H3 이외의 본문 제목은 거절한다")
	void rejectUnsupportedHeadingLevels() {
		for (String level : List.of("0", "1", "4", "6", "2.5", "\"2\"", "null")) {
			JsonNode source = documentWithBlock(
				"{\"type\":\"heading\",\"attrs\":{\"level\":" + level + "}}"
			);

			assertThrows(InvalidPostRequestException.class, () -> validator.validate(source));
		}
	}

	@Test
	@DisplayName("잘못된 목록과 문단 및 코드 블록 구조는 거절한다")
	void rejectInvalidBlockStructures() {
		for (String block : List.of(
			"""
				{"type":"listItem","content":[{"type":"paragraph"}]}
				""",
			"""
				{"type":"bulletList","content":[{"type":"paragraph"}]}
				""",
			"""
				{"type":"orderedList","content":[]}
				""",
			"""
				{"type":"blockquote","content":[]}
				""",
			"""
				{"type":"bulletList","content":[{"type":"listItem","content":[]}]}
				""",
			"""
				{
				  "type":"bulletList",
				  "content":[{
					"type":"listItem",
					"content":[{"type":"heading","attrs":{"level":2}}]
				  }]
				}
				""",
			"""
				{"type":"paragraph","content":[{"type":"image","attrs":{"fileIndex":0}}]}
				""",
			"""
				{"type":"codeBlock","content":[{"type":"hardBreak"}]}
				""",
			"""
				{
				  "type":"codeBlock",
				  "content":[{"type":"text","text":"code","marks":[{"type":"bold"}]}]
				}
				"""
		)) {
			JsonNode source = documentWithBlock(block);
			assertThrows(InvalidPostRequestException.class, () -> validator.validate(source));
		}
	}

	@Test
	@DisplayName("번호 목록의 잘못된 시작 번호는 거절한다")
	void rejectInvalidOrderedListStart() {
		for (String start : List.of("0", "-1", "1.5", "\"2\"")) {
			JsonNode source = documentWithBlock("""
				{
				  "type":"orderedList",
				  "attrs":{"start":%s},
				  "content":[{"type":"listItem","content":[{"type":"paragraph"}]}]
				}
				""".formatted(start));

			assertThrows(InvalidPostRequestException.class, () -> validator.validate(source));
		}
	}

	@Test
	@DisplayName("번호 목록의 시작 번호를 생략하면 1로 정규화한다")
	void defaultOrderedListStartToOne() {
		var result = validator.validate(documentWithBlock("""
			{
			  "type":"orderedList",
			  "content":[{"type":"listItem","content":[{"type":"paragraph"}]}]
			}
			"""));

		assertEquals(1, result.document().at("/content/0/attrs/start").intValue());
	}

	@Test
	@DisplayName("최상위와 인용문 안에서 같은 이미지를 반복하면 거절한다")
	void rejectDuplicateImageAcrossContainers() {
		JsonNode source = mapper.readTree("""
			{
			  "type":"doc",
			  "content":[
			    {"type":"image","attrs":{"fileIndex":0}},
			    {
			      "type":"blockquote",
			      "content":[{"type":"image","attrs":{"fileIndex":0}}]
			    }
			  ]
			}
			""");

		assertThrows(InvalidPostRequestException.class, () -> validator.validate(source));
	}

	@Test
	@DisplayName("인라인 코드와 다른 글자 서식의 동시 적용은 거절한다")
	void rejectCodeCombinedWithOtherMarks() {
		assertThrows(
			InvalidPostRequestException.class,
			() -> validator.validate(documentWithMarks("code", "bold"))
		);
	}

	@Test
	@DisplayName("지나치게 깊게 중첩된 본문은 거절한다")
	void rejectExcessiveBlockDepth() {
		ObjectNode source = (ObjectNode)mapper.readTree("""
			{"type":"doc"}
			""");
		ObjectNode parent = source;

		for (int index = 0; index < 65; index++) {
			parent = parent.putArray("content").addObject().put("type", "blockquote");
		}
		parent.putArray("content").addObject().put("type", "paragraph");

		assertThrows(InvalidPostRequestException.class, () -> validator.validate(source));
	}

	@Test
	@DisplayName("다른 검증 요청 사이에 이미지 중복 검사 상태가 남지 않는다")
	void keepValidationStateIsolated() {
		JsonNode source = documentWithBlock("""
			{"type":"image","attrs":{"fileIndex":0}}
			""");

		assertEquals(1, validator.validate(source).images().size());
		assertEquals(1, validator.validate(source).images().size());
	}

	private JsonNode documentWithBlock(String block) {
		return mapper.readTree(
			"{\"type\":\"doc\",\"content\":[" + block + "]}"
		);
	}

	private ObjectNode documentWithLink(String href) {
		ObjectNode source = documentWithMarks("link");
		ObjectNode mark = (ObjectNode)source.at("/content/0/content/0/marks/0");
		mark.putObject("attrs").put("href", href);
		return source;
	}
}