package com.greenhouse.backend.demo;

/** Endpoint protection rules, independent of servlet dispatch and request counters. */
final class DemoRequestPolicy {

	private DemoRequestPolicy() {
	}

	static boolean blocks(String method, String path) {
		if (path.equals("/api/auth/login") || path.equals("/api/auth/logout"))
			return true;
		if (!isMutation(method))
			return false;
		return path.startsWith("/api/work-types") || path.startsWith("/api/partner-settlement-settings")
				|| path.matches("/api/business-partners/[^/]+/settlement-settings(?:/.*)?");
	}

	static boolean isMutation(String method) {
		return switch (method) {
			case "POST", "PUT", "PATCH", "DELETE" -> true;
			default -> false;
		};
	}

}
