package io.spacedog.client.bulk;

import com.fasterxml.jackson.databind.JsonNode;

public class ServiceResponse {

	public boolean success;
	public int status;
	public JsonNode content;

	public ServiceResponse() {
	}
}
