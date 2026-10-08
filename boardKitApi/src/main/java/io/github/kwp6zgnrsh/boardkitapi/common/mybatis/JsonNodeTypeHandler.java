package io.github.kwp6zgnrsh.boardkitapi.common.mybatis;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
@MappedTypes(JsonNode.class)
@MappedJdbcTypes(value = {JdbcType.VARCHAR, JdbcType.LONGVARCHAR}, includeNullJdbcType = true)
public class JsonNodeTypeHandler extends BaseTypeHandler<JsonNode> {

	private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

	@Override
	public void setNonNullParameter(PreparedStatement statement, int index, JsonNode parameter,
		JdbcType jdbcType) throws SQLException {
		try {
			statement.setString(index, JSON_MAPPER.writeValueAsString(parameter));
		} catch (JacksonException exception) {
			throw new SQLException("본문 문서를 JSON으로 변환하지 못했습니다.", exception);
		}
	}

	@Override
	public JsonNode getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
		return parse(resultSet.getString(columnName));
	}

	@Override
	public JsonNode getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
		return parse(resultSet.getString(columnIndex));
	}

	@Override
	public JsonNode getNullableResult(CallableStatement statement, int columnIndex) throws SQLException {
		return parse(statement.getString(columnIndex));
	}

	private JsonNode parse(String value) throws SQLException {
		if (value == null) {
			return null;
		}

		try {
			return JSON_MAPPER.readTree(value);
		} catch (JacksonException exception) {
			throw new SQLException("저장된 본문 문서를 읽지 못했습니다.", exception);
		}
	}
}