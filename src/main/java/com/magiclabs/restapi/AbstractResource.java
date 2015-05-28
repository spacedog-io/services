package com.magiclabs.restapi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import net.codestory.http.constants.HttpStatus;
import net.codestory.http.payload.Payload;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.mapper.MergeMappingException;
import org.elasticsearch.search.SearchHit;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.magiclabs.restapi.Account.InvalidAccountException;
import com.magiclabs.restapi.MetaResource.NotFoundException;
import com.magiclabs.restapi.SchemaValidator.InvalidSchemaException;

public abstract class AbstractResource {

	public static final String JSON_CONTENT = "application/json;charset=UTF-8";

	public static final String HEADER_OBJECT_ID = "X-magiclabs-object-id";

	private static ObjectMapper objectMapper = new ObjectMapper();

	protected static ObjectMapper getObjectMapper() {
		return objectMapper;
	}

	public static String toJsonError(Throwable e) {
		return add(new JsonObject(), e).toString();
	}

	private static JsonObject add(JsonObject error, Throwable t) {
		JsonArray stack = new JsonArray();
		error.add("type", t.getClass().getName()) //
				.add("message", t.getMessage()) //
				.add("trace", stack);

		for (StackTraceElement element : t.getStackTrace()) {
			stack.add(element.toString());
		}

		if (t.getCause() != null) {
			JsonObject cause = new JsonObject();
			add(cause, t.getCause());
			error.add("cause", cause);
		}
		return error;
	}

	public static Payload done() {
		return new Payload(JSON_CONTENT, "{\"done\":true}", HttpStatus.OK);
	}

	public static Payload internalServerError(Throwable throwable) {
		return new Payload(JSON_CONTENT, toJsonError(throwable),
				HttpStatus.INTERNAL_SERVER_ERROR);
	}

	public static Payload internalServerError(String message, Object... args) {
		return new Payload(JSON_CONTENT, toJsonError(new RuntimeException(
				String.format(message, args))),
				HttpStatus.INTERNAL_SERVER_ERROR);
	}

	public static Payload badRequest(Throwable throwable) {
		return new Payload(JSON_CONTENT, toJsonError(throwable),
				HttpStatus.BAD_REQUEST);
	}

	public static Payload forbidden(Throwable throwable) {
		return new Payload(JSON_CONTENT, toJsonError(throwable),
				HttpStatus.FORBIDDEN);
	}

	public static Payload unauthorized(Throwable throwable) {
		return new Payload(JSON_CONTENT, toJsonError(throwable),
				HttpStatus.UNAUTHORIZED);
	}

	public static Payload notFound(String message, Object... args) {
		return new Payload(JSON_CONTENT, toJsonError(new RuntimeException(
				String.format(message, args))), HttpStatus.NOT_FOUND);
	}

	public static Payload notFound(Throwable throwable) {
		return new Payload(JSON_CONTENT, toJsonError(throwable),
				HttpStatus.NOT_FOUND);
	}

	protected Payload unsupportedOperation() {
		return new Payload(HttpStatus.NOT_IMPLEMENTED);
	}

	protected Payload created(String type, String id) {
		return new Payload(JSON_CONTENT, Json.builder().add("done", true)
				.add("id", id).add("type", type).build().toString(),
				HttpStatus.CREATED).withHeader(
				AbstractResource.HEADER_OBJECT_ID, id);
	}

	protected Payload extractResults(SearchResponse response) {
		List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();

		for (SearchHit hit : response.getHits().getHits()) {
			Map<String, Object> map = new HashMap<String, Object>();
			map.put("id", hit.getId());
			map.put("type", hit.getType());
			map.put("version", hit.getVersion());
			// if (returnContents) {
			map.put("object", hit.getSource());
			// }
			results.add(map);
		}

		long total = response.getHits().getTotalHits();
		Map<String, Object> payload = new HashMap<String, Object>();
		payload.put("timeSpent", response.getTookInMillis());
		payload.put("total", total);
		payload.put("results", results);
		// if (results.size() > 0) {
		// payload.put("from", from);
		// payload.put("to", from + results.size() - 1);
		// }

		return new Payload(JSON_CONTENT, payload, HttpStatus.OK);
	}

	public static Payload toPayload(Throwable t) {

		if (t instanceof AuthenticationException) {
			return unauthorized(t);
		}
		if (t instanceof InvalidAccountException) {
			return badRequest(t);
		}
		if (t instanceof NotFoundException) {
			return notFound(t);
		}
		if (t instanceof IllegalArgumentException) {
			return badRequest(t);
		}
		if (t instanceof InvalidSchemaException) {
			return badRequest(t);
		}
		if (t instanceof ExecutionException) {
			if (t.getCause() instanceof MergeMappingException)
				return badRequest(t.getCause());
			else
				return internalServerError(t);
		}

		return internalServerError(t);
	}
}
