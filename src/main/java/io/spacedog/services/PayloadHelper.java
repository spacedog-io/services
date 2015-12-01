/**
 * © David Attias 2015
 */
package io.spacedog.services;

import java.util.concurrent.ExecutionException;

import org.elasticsearch.action.ShardOperationFailedException;
import org.elasticsearch.index.engine.VersionConflictEngineException;
import org.elasticsearch.index.mapper.MergeMappingException;
import org.elasticsearch.indices.IndexMissingException;
import org.elasticsearch.rest.RestStatus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.spacedog.services.Account.InvalidAccountException;
import io.spacedog.services.SchemaValidator.InvalidSchemaException;
import net.codestory.http.constants.HttpStatus;
import net.codestory.http.payload.Payload;

public class PayloadHelper {

	public static final String JSON_CONTENT = "application/json;charset=UTF-8";
	public static final String HEADER_OBJECT_ID = "x-spacedog-object-id";

	public static Payload error(Throwable t) {

		if (t instanceof VersionConflictEngineException) {
			return error(HttpStatus.CONFLICT, t);
		}
		if (t instanceof AuthenticationException) {
			return error(HttpStatus.UNAUTHORIZED, t);
		}
		if (t instanceof InvalidAccountException) {
			return error(HttpStatus.BAD_REQUEST, t);
		}
		if (t instanceof NotFoundException) {
			return error(HttpStatus.NOT_FOUND, t);
		}
		if (t instanceof IndexMissingException) {
			return error(HttpStatus.NOT_FOUND, t);
		}
		if (t instanceof IllegalArgumentException) {
			return error(HttpStatus.BAD_REQUEST, t);
		}
		if (t instanceof NumberFormatException) {
			return error(HttpStatus.BAD_REQUEST, t);
		}
		if (t instanceof InvalidSchemaException) {
			return error(HttpStatus.BAD_REQUEST, t);
		}
		if (t instanceof ExecutionException) {
			if (t.getCause() instanceof MergeMappingException)
				return error(HttpStatus.BAD_REQUEST, t.getCause());
			else
				return error(HttpStatus.INTERNAL_SERVER_ERROR, t);
		}

		return error(HttpStatus.INTERNAL_SERVER_ERROR, t);
	}

	public static Payload error(int httpStatus, Throwable throwable) {
		JsonBuilder<ObjectNode> builder = Json.objectBuilder().put("success", false);
		if (throwable != null)
			builder.node("error", Json.toJson(throwable));
		return json(builder, httpStatus);
	}

	public static Payload error(int httpStatus, String message, Object... args) {
		return error(httpStatus, new RuntimeException(String.format(message, args)));
	}

	public static Payload error(int httpStatus) {
		return error(httpStatus, null);
	}

	/**
	 * @param parameters
	 *            triples with parameter name, value and message
	 * @return a bad request http payload with a json listing invalid parameters
	 */
	protected static Payload invalidParameters(String... parameters) {
		JsonBuilder<ObjectNode> builder = Json.objectBuilder().put("success", false);
		if (parameters.length > 0 && parameters.length % 3 == 0) {
			builder.object("invalidParameters");
			for (int i = 0; i < parameters.length; i += 3)
				builder.object(parameters[0])//
						.put("value", parameters[1])//
						.put("message", parameters[2]);
		}
		return json(builder, HttpStatus.BAD_REQUEST);
	}

	public static Payload toPayload(RestStatus status, ShardOperationFailedException[] failures) {

		if (status.getStatus() == 200)
			return success();

		JsonBuilder<ObjectNode> builder = Json.objectBuilder().put("success", false)//
				.array("error");

		for (ShardOperationFailedException failure : failures)
			builder.object().put("type", failure.getClass().getName()).put("message", failure.reason())
					.put("shardId", failure.shardId()).end();

		return json(builder, status.getStatus());
	}

	public static Payload success() {
		return json("{\"success\":true}");
	}

	public static JsonBuilder<ObjectNode> savedBuilder(String uri, String type, String id, long version) {
		JsonBuilder<ObjectNode> builder = Json.objectBuilder() //
				.put("success", true) //
				.put("id", id) //
				.put("type", type) //
				.put("location", AbstractResource.toUrl(AbstractResource.BASE_URL, uri, type, id));

		if (version > 0) //
			builder.put("version", version);

		return builder;
	}

	public static Payload saved(boolean created, String uri, String type, String id) {
		return saved(created, uri, type, id, 0);
	}

	public static Payload saved(boolean created, String uri, String type, String id, long version) {
		JsonBuilder<ObjectNode> builder = PayloadHelper.savedBuilder(uri, type, id, version);
		return json(builder, created ? HttpStatus.CREATED : HttpStatus.OK)//
				.withHeader(HEADER_OBJECT_ID, id);
	}

	public static <N extends JsonNode> Payload json(JsonBuilder<N> content) {
		return json(content.build());
	}

	public static Payload json(JsonNode content) {
		return json(content.toString());
	}

	public static Payload json(String content) {
		return json(content.getBytes(Utils.UTF8));
	}

	public static Payload json(byte[] content) {
		return json(content, HttpStatus.OK);
	}

	public static <N extends JsonNode> Payload json(JsonBuilder<N> content, int httpStatus) {
		return json(content.build(), httpStatus);
	}

	public static Payload json(JsonNode content, int httpStatus) {
		return json(content.toString(), httpStatus);
	}

	public static Payload json(String content, int httpStatus) {
		return json(content.getBytes(Utils.UTF8), httpStatus);
	}

	public static Payload json(byte[] content, int httpStatus) {
		return new Payload(JSON_CONTENT, content, httpStatus);
	}
}
