package com.greenhouse.backend.work.application.operation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/**
 * Request comparison ignores JSON object order and decimal scale, but keeps array order.
 */
@Component
public class WorkRequestFingerprint {

	private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();

	public String calculate(Object request) {
		JsonNode canonical = canonical(mapper.valueToTree(request));
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256")
					.digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
		}
	}

	private JsonNode canonical(JsonNode node) {
		if (node.isObject()) {
			var fields = new TreeMap<String, JsonNode>();
			node.fields().forEachRemaining(field -> {
				fields.put(field.getKey(), canonical(field.getValue()));
			});
			return JsonNodeFactory.instance.objectNode().setAll(fields);
		}
		if (node.isArray()) {
			var result = JsonNodeFactory.instance.arrayNode();
			node.forEach(value -> result.add(canonical(value)));
			return result;
		}
		return node.isNumber() ? JsonNodeFactory.instance.numberNode(node.decimalValue().stripTrailingZeros()) : node;
	}

}
