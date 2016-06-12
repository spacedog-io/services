/**
 * © David Attias 2015
 */
package io.spacedog.services;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.common.Strings;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.spacedog.utils.Exceptions;
import io.spacedog.utils.Json;
import io.spacedog.utils.NotFoundException;
import io.spacedog.utils.SpaceParams;
import net.codestory.http.Context;
import net.codestory.http.annotations.Delete;
import net.codestory.http.annotations.Get;
import net.codestory.http.annotations.Post;
import net.codestory.http.annotations.Put;
import net.codestory.http.constants.HttpStatus;
import net.codestory.http.payload.Payload;

//@Prefix("/1/data")
public class DataResource extends Resource {

	//
	// Routes
	//

	@Get("/v1/data")
	@Get("/v1/data/")
	@Get("/1/data")
	@Get("/1/data/")
	public Payload getAll(Context context) {
		Credentials credentials = SpaceContext.checkCredentials();
		boolean refresh = context.query().getBoolean(SpaceParams.REFRESH, false);
		DataStore.get().refreshBackend(refresh, credentials.backendId());
		ObjectNode result = SearchResource.get()//
				.searchInternal(credentials, null, null, context);
		return JsonPayload.json(result);
	}

	@Delete("/v1/data")
	@Delete("/v1/data/")
	@Delete("/1/data")
	@Delete("/1/data/")
	public Payload deleteAll(Context context) {
		// TODO not implemented since it would also delete super admins
		// return SearchResource.get().deleteAllTypes(null, context);
		return JsonPayload.error(HttpStatus.NOT_IMPLEMENTED);
	}

	@Get("/v1/data/:type")
	@Get("/v1/data/:type/")
	@Get("/1/data/:type")
	@Get("/1/data/:type/")
	public Payload getByType(String type, Context context) {
		Credentials credentials = SpaceContext.checkCredentials();
		boolean refresh = context.query().getBoolean(SpaceParams.REFRESH, false);
		DataStore.get().refreshType(refresh, credentials.backendId(), type);
		ObjectNode result = SearchResource.get()//
				.searchInternal(credentials, type, null, context);
		return JsonPayload.json(result);
	}

	@Post("/v1/data/:type")
	@Post("/v1/data/:type/")
	@Post("/1/data/:type")
	@Post("/1/data/:type/")
	public Payload post(String type, String body, Context context) {

		Credentials credentials = SpaceContext.checkCredentials();
		ObjectNode schema = Start.get().getElasticClient()//
				.getSchema(credentials.backendId(), type);
		ObjectNode object = Json.readObject(body);

		/*
		 * 3 cases: (1) id is not provided and generated by ES when object is
		 * indexed, (2) id is a property of the object and the _id schema field
		 * contains the property path, (3) id is provided with the 'id' query
		 * parameter
		 */
		Optional<String> id = Optional.empty();
		JsonNode idPropertyName = schema.path(type).path("_id");

		if (idPropertyName.isTextual()) {
			JsonNode idPropertyValue = Json.get(object, idPropertyName.asText());

			if (idPropertyValue == null)
				throw Exceptions.illegalArgument("id property [%s] of type [%s] is null or missing", //
						idPropertyName.asText(), type);

			id = Optional.of(idPropertyValue.asText());

		} else if (!Strings.isNullOrEmpty(context.get("id")))
			id = Optional.of(context.get("id"));

		IndexResponse response = DataStore.get().createObject(//
				credentials.backendId(), type, id, object, credentials.name());

		return JsonPayload.saved(true, credentials.backendId(), "/1/data", response.getType(), response.getId(),
				response.getVersion());

	}

	@Delete("/v1/data/:type")
	@Delete("/v1/data/:type/")
	@Delete("/1/data/:type")
	@Delete("/1/data/:type/")
	public Payload deleteByType(String type, Context context) {
		SpaceContext.checkAdminCredentials();
		return SearchResource.get().deleteSearchForType(type, null, context);
	}

	@Get("/v1/data/:type/:id")
	@Get("/v1/data/:type/:id/")
	@Get("/1/data/:type/:id")
	@Get("/1/data/:type/:id/")
	public Payload getById(String type, String id, Context context) {
		Credentials credentials = SpaceContext.checkCredentials();
		Optional<ObjectNode> object = DataStore.get().getObject(credentials.backendId(), type, id);
		if (object.isPresent())
			return JsonPayload.json(object.get());
		else
			return JsonPayload.error(HttpStatus.NOT_FOUND);
	}

	@Put("/v1/data/:type/:id")
	@Put("/v1/data/:type/:id/")
	@Put("/1/data/:type/:id")
	@Put("/1/data/:type/:id/")
	public Payload put(String type, String id, String body, Context context) {
		Credentials credentials = SpaceContext.checkCredentials();

		// check if type is well defined
		// object should be validated before saved
		ObjectNode schema = Start.get().getElasticClient()//
				.getSchema(credentials.backendId(), type);

		ObjectNode object = Json.readObject(body);

		Optional<String> idPath = schema.has("_id") //
				? Optional.of(schema.get("_id").asText()) : Optional.empty();

		if (idPath.isPresent()) {
			JsonNode idValue = Json.get(object, idPath.get());
			if (!Json.isNull(idValue) && !id.equals(idValue.asText()))
				throw new IllegalArgumentException(String.format(//
						"property [%s/%s/%s] represents the object id and can not be updated to [%s]", //
						type, id, idPath.get(), idValue.asText()));
		}

		boolean strict = context.query().getBoolean(SpaceParams.STRICT, false);
		// TODO return better exception-message in case of invalid version
		// format
		long version = context.query().getLong(SpaceParams.VERSION, 0l);

		if (strict) {

			IndexResponse response = DataStore.get().updateObject(credentials.backendId(), type, id, version, object,
					credentials.name());
			return JsonPayload.saved(false, credentials.backendId(), "/1/data", response.getType(), response.getId(),
					response.getVersion());

		} else {

			UpdateResponse response = DataStore.get().patchObject(credentials.backendId(), type, id, version, object,
					credentials.name());
			return JsonPayload.saved(false, credentials.backendId(), "/1/data", response.getType(), response.getId(),
					response.getVersion());
		}
	}

	@Delete("/v1/data/:type/:id")
	@Delete("/v1/data/:type/:id/")
	@Delete("/1/data/:type/:id")
	@Delete("/1/data/:type/:id/")
	public Payload deleteById(String type, String id, Context context) {
		Credentials credentials = SpaceContext.checkCredentials();
		ElasticClient elastic = Start.get().getElasticClient();
		Optional<ObjectNode> object = DataStore.get().getObject(credentials.backendId(), type, id);

		if (object.isPresent()) {
			if (credentials.name().equals(Json.get(object.get(), "meta.createdBy").asText())) {
				DeleteResponse response = elastic.delete(credentials.backendId(), type, id);
				return response.isFound() //
						? JsonPayload.success() //
						: JsonPayload.error(HttpStatus.INTERNAL_SERVER_ERROR, //
								"failed to delete object of type [%s] and id [%s]", type, id);
			} else
				return JsonPayload.error(HttpStatus.FORBIDDEN, //
						"[%s] not owner of object of type [%s] and id [%s]", //
						credentials.name(), type, id);
		}

		return JsonPayload.error(HttpStatus.NOT_FOUND, "object of type [%s] and id [%s] not found", type, id);
	}

	@Post("/v1/data/search")
	@Post("/v1/data/search/")
	@Post("/1/data/search")
	@Post("/1/data/search/")
	@Deprecated
	public Payload searchAllTypes(String body, Context context) throws JsonParseException, JsonMappingException,
			NotFoundException, IOException, InterruptedException, ExecutionException {
		return SearchResource.get().postSearchAllTypes(body, context);
	}

	@Post("/v1/data/:type/search")
	@Post("/v1/data/:type/search/")
	@Post("/1/data/:type/search")
	@Post("/1/data/:type/search/")
	@Deprecated
	public Payload searchForType(String type, String body, Context context) throws JsonParseException,
			JsonMappingException, NotFoundException, IOException, InterruptedException, ExecutionException {
		return SearchResource.get().postSearchForType(type, body, context);
	}

	//
	// singleton
	//

	private static DataResource singleton = new DataResource();

	static DataResource get() {
		return singleton;
	}

	private DataResource() {
	}

}
