/**
 * © David Attias 2015
 */
package io.spacedog.services;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.elasticsearch.action.deletebyquery.DeleteByQueryResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.InternalAggregations;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;

import io.spacedog.core.Json8;
import io.spacedog.model.CsvRequest;
import io.spacedog.model.DataPermission;
import io.spacedog.utils.Check;
import io.spacedog.utils.Credentials;
import io.spacedog.utils.Exceptions;
import io.spacedog.utils.JsonBuilder;
import io.spacedog.utils.Utils;
import net.codestory.http.Context;
import net.codestory.http.annotations.Delete;
import net.codestory.http.annotations.Get;
import net.codestory.http.annotations.Post;
import net.codestory.http.annotations.Prefix;
import net.codestory.http.payload.Payload;

@Prefix("/1/search")
public class SearchResource extends Resource {

	//
	// Routes
	//

	@Get("")
	@Get("/")
	public Payload getSearchAllTypes(Context context) {
		return postSearchAllTypes(null, context);
	}

	@Post("")
	@Post("/")
	public Payload postSearchAllTypes(String body, Context context) {
		Credentials credentials = SpaceContext.getCredentials();
		String[] types = DataAccessControl.types(DataPermission.search, credentials);
		refreshBackendIfNecessary(context, false);
		ObjectNode result = searchInternal(body, credentials, context, types);
		return JsonPayload.json(result);
	}

	@Delete("")
	@Delete("/")
	public Payload deleteAllTypes(String query, Context context) {
		// TODO delete special types like user the right way
		// credentials and user data at the same time
		Credentials credentials = SpaceContext.checkAdminCredentials();
		String[] types = DataAccessControl.types(DataPermission.delete_all, credentials);
		refreshBackendIfNecessary(context, true);
		DeleteByQueryResponse response = Start.get().getElasticClient()//
				.deleteByQuery(query, credentials.backendId(), types);
		return JsonPayload.json(response);
	}

	@Get("/:type")
	@Get("/:type/")
	public Payload getSearchForType(String type, Context context) {
		return postSearchForType(type, null, context);
	}

	@Post("/:type")
	@Post("/:type/")
	public Payload postSearchForType(String type, String body, Context context) {
		Credentials credentials = SpaceContext.getCredentials();
		if (!DataAccessControl.check(credentials, type, DataPermission.search))
			throw Exceptions.forbidden("forbidden to search [%s] objects", type);

		refreshTypeIfNecessary(type, context, false);
		ObjectNode result = searchInternal(body, credentials, context, type);
		return JsonPayload.json(result);
	}

	@Delete("/:type")
	@Delete("/:type/")
	public Payload deleteSearchForType(String type, String query, Context context) {
		Credentials credentials = SpaceContext.checkAdminCredentials();
		if (!DataAccessControl.check(credentials, type, DataPermission.delete_all))
			throw Exceptions.forbidden("forbidden to delete [%s] objects", type);

		refreshTypeIfNecessary(type, context, true);

		DeleteByQueryResponse response = Start.get().getElasticClient()//
				.deleteByQuery(query, credentials.backendId(), type);

		return JsonPayload.json(response);
	}

	@Post("/:type/_csv")
	@Post("/:type/_csv/")
	public Payload postSearchForTypeToCsv(String type, CsvRequest request, Context context) {

		Credentials credentials = SpaceContext.getCredentials();
		if (!DataAccessControl.check(credentials, type, DataPermission.search))
			throw Exceptions.forbidden("forbidden to search [%s] objects", type);

		DataStore.get().refreshType(request.refresh, SpaceContext.backendId(), type);
		Locale requestLocale = getRequestLocale(context);
		ElasticClient elastic = Start.get().getElasticClient();

		SearchResponse response = elastic.prepareSearch()//
				.setIndices(elastic.toAliases(credentials.backendId(), type))//
				.setTypes(type)//
				.setSource(request.query)//
				.setScroll(TimeValue.timeValueSeconds(60))//
				.setSize(1000)//
				.get();

		return new Payload("text/plain;charset=utf-8;", //
				new CsvStreamingOutput(request, response, requestLocale));

	}

	//
	// implementation
	//

	private void refreshTypeIfNecessary(String type, Context context, boolean defaultValue) {
		boolean refresh = context.query().getBoolean(PARAM_REFRESH, defaultValue);
		DataStore.get().refreshType(refresh, SpaceContext.backendId(), type);
	}

	private void refreshBackendIfNecessary(Context context, boolean defaultValue) {
		boolean refresh = context.query().getBoolean(PARAM_REFRESH, defaultValue);
		DataStore.get().refreshBackend(refresh, SpaceContext.backendId());
	}

	ObjectNode searchInternal(String jsonQuery, Credentials credentials, Context context, String... types) {

		SearchRequestBuilder search = null;
		ElasticClient elastic = Start.get().getElasticClient();
		String[] aliases = elastic.toAliases(credentials.backendId(), types);

		if (aliases.length == 0)
			return Json8.object("took", 0, "total", 0, "results", Json8.array());

		search = elastic.prepareSearch().setIndices(aliases).setTypes(types);

		if (Strings.isNullOrEmpty(jsonQuery)) {

			int from = context.query().getInteger(PARAM_FROM, 0);
			int size = context.query().getInteger(PARAM_SIZE, 10);
			Check.isTrue(from + size <= 1000, "from + size must be less than or equal to 1000");

			search.setFrom(from)//
					.setSize(size)//
					.setFetchSource(context.query().getBoolean("fetch-contents", true))//
					.setQuery(QueryBuilders.matchAllQuery())//
					.setVersion(true);

			String queryText = context.get("q");
			if (!Strings.isNullOrEmpty(queryText))
				search.setQuery(QueryBuilders.simpleQueryStringQuery(queryText));

		} else {
			search.setExtraSource(SearchSourceBuilder.searchSource().version(true).buildAsBytes());
			search.setSource(jsonQuery);
		}

		return extractResults(search.get(), context, credentials);
	}

	private ObjectNode extractResults(SearchResponse response, Context context, Credentials credentials) {

		String propertyPath = context.request().query().get("fetch-references");
		boolean fetchReferences = !Strings.isNullOrEmpty(propertyPath);
		List<String> references = null;
		if (fetchReferences)
			references = new ArrayList<>();

		List<JsonNode> objects = new ArrayList<>();
		for (SearchHit hit : response.getHits().getHits()) {

			// check if source is null is necessary
			// when the data is not requested
			// fetch-source = false for GET requests
			// or _source = false for POST requests
			String source = hit.sourceAsString();
			ObjectNode object = source == null ? Json8.object() : Json8.readObject(source);

			ObjectNode meta = object.with("meta");
			meta.put("id", hit.id()).put("type", hit.type()).put("version", hit.version());

			if (Float.isFinite(hit.score()))
				meta.put("score", hit.score());

			if (!Utils.isNullOrEmpty(hit.sortValues())) {
				ArrayNode array = Json8.array();
				for (Object value : hit.sortValues())
					array.add(Json8.toValueNode(value));
				meta.set("sort", array);
			}

			objects.add(object);

			if (fetchReferences)
				references.add(Json8.get(object, propertyPath).asText());
		}

		if (fetchReferences) {
			Map<String, ObjectNode> referencedObjects = getReferences(references, credentials);

			for (int i = 0; i < objects.size(); i++) {
				String reference = references.get(i);
				if (!Strings.isNullOrEmpty(reference))
					Json8.set(objects.get(i), propertyPath, referencedObjects.get(reference));
			}
		}

		JsonBuilder<ObjectNode> builder = Json8.objectBuilder()//
				.put("took", response.getTookInMillis())//
				.put("total", response.getHits().getTotalHits())//
				.array("results");

		objects.forEach(object -> builder.node(object));
		builder.end();

		if (response.getAggregations() != null) {
			// TODO find a safe and efficient solution to add aggregations to
			// payload.
			// Direct json serialization from response.getAggregations().asMap()
			// results in errors because of getters like:
			// InternalTerms$Bucket.getDocCountError(InternalTerms.java:83) that
			// can throw state exceptions.
			// The following solution is safer but inefficient. It fixes issue
			// #1.
			try {
				InternalAggregations aggs = (InternalAggregations) response.getAggregations();
				XContentBuilder jsonXBuilder = JsonXContent.contentBuilder();
				jsonXBuilder.startObject();
				aggs.toXContentInternal(jsonXBuilder, ToXContent.EMPTY_PARAMS);
				jsonXBuilder.endObject();
				// TODO this is so inefficient
				// SearchResponse -> Json String -> JsonNode -> Payload
				builder.node("aggregations", jsonXBuilder.string());
			} catch (IOException e) {
				throw Exceptions.runtime("failed to convert aggregations into json", e);
			}
		}

		return builder.build();
	}

	private Map<String, ObjectNode> getReferences(List<String> references, Credentials credentials) {
		Set<String> set = new HashSet<>(references);
		set.remove(null);
		Map<String, ObjectNode> results = new HashMap<>();
		set.forEach(reference -> {
			ObjectNode object = DataStore.get().getObject(credentials.backendId(), //
					getReferenceType(reference), getReferenceId(reference));
			results.put(reference, object);
		});
		return results;
	}

	//
	// singleton
	//

	private static SearchResource singleton = new SearchResource();

	static SearchResource get() {
		return singleton;
	}

	private SearchResource() {
	}

}
