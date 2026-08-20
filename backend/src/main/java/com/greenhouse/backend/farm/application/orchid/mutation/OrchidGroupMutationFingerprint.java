package com.greenhouse.backend.farm.application.orchid.mutation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class OrchidGroupMutationFingerprint {

	private final ObjectMapper canonicalObjectMapper;

	public OrchidGroupMutationFingerprint() {
		this.canonicalObjectMapper = new ObjectMapper()
				.findAndRegisterModules()
				.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
	}

	public String calculate(Object semanticCommand) {
		if (semanticCommand == null) {
			throw new IllegalArgumentException("fingerprint를 계산할 semantic command가 필요합니다.");
		}
		try {
			byte[] canonicalBytes = canonicalObjectMapper.writeValueAsString(semanticCommand)
					.getBytes(StandardCharsets.UTF_8);
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonicalBytes));
		} catch (JsonProcessingException exception) {
			throw new IllegalArgumentException("Mutation command를 정규화할 수 없습니다.", exception);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 fingerprint를 사용할 수 없습니다.", exception);
		}
	}
}
