package io.github.kwp6zgnrsh.boardkitapi.common.mybatis;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class JsonNodeTypeHandlerTest {

	private final JsonNodeTypeHandler handler = new JsonNodeTypeHandler();
	private final JsonMapper mapper = JsonMapper.builder().build();

	@Test
	@DisplayName("본문 문서를 JSON 문자열로 저장한다")
	void writeDocumentAsJson() throws SQLException {
		PreparedStatement statement = mock(PreparedStatement.class);
		JsonNode document = mapper.readTree("""
			{"type":"doc","content":[]}
			""");

		handler.setNonNullParameter(statement, 1, document, JdbcType.VARCHAR);

		verify(statement).setString(1, mapper.writeValueAsString(document));
	}

	@Test
	@DisplayName("저장된 JSON을 본문 문서로 읽는다")
	void readJsonAsDocument() throws SQLException {
		ResultSet resultSet = mock(ResultSet.class);
		when(resultSet.getString("contentDocument"))
			.thenReturn("""
				{"type":"doc","content":[]}
				""");

		JsonNode document = handler.getNullableResult(resultSet, "contentDocument");

		assertEquals("doc", document.path("type").asString());
		assertTrue(document.path("content").isArray());
	}

	@Test
	@DisplayName("기존 글의 NULL 본문 문서는 그대로 NULL로 읽는다")
	void readNullDocument() throws SQLException {
		ResultSet resultSet = mock(ResultSet.class);
		when(resultSet.getString("contentDocument")).thenReturn(null);

		assertNull(handler.getNullableResult(resultSet, "contentDocument"));
	}

	@Test
	@DisplayName("잘못된 JSON을 읽으면 오류를 발생시킨다")
	void rejectMalformedJson() throws SQLException {
		ResultSet resultSet = mock(ResultSet.class);
		when(resultSet.getString("contentDocument")).thenReturn("{invalid");

		assertThrows(
			SQLException.class,
			() -> handler.getNullableResult(resultSet, "contentDocument")
		);
	}
}