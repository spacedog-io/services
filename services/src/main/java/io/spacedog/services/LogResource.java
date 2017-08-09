package io.spacedog.services;

import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

import org.elasticsearch.action.deletebyquery.DeleteByQueryResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.QuerySourceBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.joda.time.DateTime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;

import io.spacedog.core.Json8;
import io.spacedog.rest.SpaceBackend;
import io.spacedog.utils.Check;
import io.spacedog.utils.ClassResources;
import io.spacedog.utils.Credentials;
import io.spacedog.utils.Exceptions;
import io.spacedog.utils.JsonBuilder;
import io.spacedog.utils.SpaceHeaders;
import io.spacedog.utils.Utils;
import net.codestory.http.Context;
import net.codestory.http.annotations.Delete;
import net.codestory.http.annotations.Get;
import net.codestory.http.annotations.Post;
import net.codestory.http.annotations.Prefix;
import net.codestory.http.filters.PayloadSupplier;
import net.codestory.http.payload.Payload;

@Prefix("/1/log")
public class LogResource extends Resource {

	public static final String TYPE = "log";
	public static final String PURGE_ALL = "purgeall";

	//
	// init
	//

	public void initIndex(String backendId) {
		ElasticClient elastic = Start.get().getElasticClient();
		String mapping = ClassResources.loadToString(this, "log-mapping.json");
		Index index = logIndex().backendId(backendId);

		if (!elastic.exists(index))
			elastic.createIndex(index, mapping, false);
	}

	//
	// Routes
	//

	@Get("")
	@Get("/")
	public Payload getAll(Context context) {
		SpaceContext.credentials().checkAtLeastSuperAdmin();

		int from = context.query().getInteger(PARAM_FROM, 0);
		int size = context.query().getInteger(PARAM_SIZE, 10);
		Check.isTrue(from + size <= 1000, "from + size must be less than or equal to 1000");

		boolean refresh = context.query().getBoolean(PARAM_REFRESH, false);
		DataStore.get().refreshDataTypes(refresh, TYPE);

		SearchResponse response = doGetLogs(from, size);
		return extractLogs(response);
	}

	@Post("/search")
	@Post("/search/")
	public Payload search(String body, Context context) {

		SpaceContext.credentials().checkAtLeastAdmin();
		SearchSourceBuilder.searchSource().query(body);

		boolean refresh = context.query().getBoolean(PARAM_REFRESH, false);
		DataStore.get().refreshDataTypes(refresh, TYPE);

		SearchResponse response = Start.get().getElasticClient()//
				.prepareSearch(logIndex())//
				.setTypes(TYPE)//
				.setSource(body).get();

		return extractLogs(response);
	}

	@Delete("")
	@Delete("/")
	public Payload purge(Context context) {

		Credentials credentials = SpaceContext.credentials();
		Optional<String> optBackendId = null;

		if (hasPurgeAllRole(credentials))
			optBackendId = Optional.empty();

		else if (credentials.isAtLeastSuperAdmin())
			optBackendId = Optional.of(SpaceContext.backendId());

		else
			throw Exceptions.insufficientCredentials(credentials);

		String param = context.request().query().get(PARAM_BEFORE);
		DateTime before = param == null ? DateTime.now().minusDays(7) //
				: DateTime.parse(param);

		Optional<DeleteByQueryResponse> response = doPurgeBackend(//
				before, optBackendId);

		// no delete response means no logs to delete means success

		return response.isPresent()//
				? JsonPayload.json(response.get())
				: JsonPayload.success();
	}

	//
	// Filter
	//

	public static SpaceFilter filter() {

		return new SpaceFilter() {

			private static final long serialVersionUID = 5621427145724229373L;

			@Override
			public boolean matches(String uri, Context context) {

				// https://api.spacedog.io ping requests should not be logged
				if (uri.isEmpty() || uri.equals(SLASH))
					return !SpaceContext.backend().isDefault();

				return true;
			}

			@Override
			public Payload apply(String uri, Context context, PayloadSupplier nextFilter) throws Exception {
				Payload payload = null;
				DateTime receivedAt = DateTime.now();

				try {
					payload = nextFilter.get();
				} catch (Throwable t) {
					payload = JsonPayload.error(t);
				}

				if (payload == null)
					payload = JsonPayload.error(500, //
							"unexpected null payload for [%s] request to [%s]", context.method(), uri);

				try {
					get().log(uri, context, receivedAt, payload);
				} catch (Exception e) {
					// TODO: log platform unexpected error with a true logger
					e.printStackTrace();
				}

				return payload;
			}
		};
	}

	//
	// Implementation
	//

	private boolean hasPurgeAllRole(Credentials credentials) {
		return credentials.isSuperDog() || credentials.roles().contains(PURGE_ALL);
	}

	private Optional<DeleteByQueryResponse> doPurgeBackend(DateTime before, //
			Optional<String> optBackendId) {

		BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery()//
				.filter(QueryBuilders.rangeQuery(FIELD_RECEIVED_AT).lt(before.toString()));

		if (optBackendId.isPresent())
			boolQueryBuilder.filter(//
					QueryBuilders.termQuery("credentials.backendId", optBackendId.get()));

		String query = new QuerySourceBuilder().setQuery(boolQueryBuilder).toString();

		ElasticClient elastic = Start.get().getElasticClient();
		DeleteByQueryResponse delete = elastic.deleteByQuery(query, logIndex());

		// TODO why return an optional?
		// return directly the response
		return Optional.of(delete);
	}

	private SearchResponse doGetLogs(int from, int size) {

		return Start.get().getElasticClient()//
				.prepareSearch(logIndex())//
				.setTypes(TYPE)//
				.setQuery(QueryBuilders.matchAllQuery())//
				.addSort(FIELD_RECEIVED_AT, SortOrder.DESC)//
				.setFrom(from)//
				.setSize(size)//
				.get();
	}

	private Payload extractLogs(SearchResponse response) {
		JsonBuilder<ObjectNode> builder = Json8.objectBuilder()//
				.put("took", response.getTookInMillis())//
				.put("total", response.getHits().getTotalHits())//
				.array("results");

		for (SearchHit hit : response.getHits().getHits())
			builder.node(hit.sourceAsString());

		return JsonPayload.json(builder);
	}

	private String log(String uri, Context context, DateTime receivedAt, Payload payload) {

		ObjectNode log = Json8.object(//
				"method", context.method(), //
				"path", uri, //
				FIELD_RECEIVED_AT, receivedAt.toString(), //
				"processedIn", DateTime.now().getMillis() - receivedAt.getMillis(), //
				"status", payload == null ? 500 : payload.code());

		addCredentials(log);
		addQuery(log, context);
		addHeaders(log, context.request().headers().entrySet());
		addRequestPayload(log, context);
		addResponsePayload(log, payload, context);

		// in case incoming request is targeting non existent backend
		// or backend without any log index, they should be logged to
		// default backend
		Index indexToLogTo = logIndex();
		ElasticClient elastic = Start.get().getElasticClient();

		if (!elastic.exists(indexToLogTo))
			indexToLogTo.backendId(SpaceBackend.defaultBackendId());

		return elastic.index(indexToLogTo, log.toString()).getId();
	}

	private void addResponsePayload(ObjectNode log, Payload payload, Context context) {
		if (payload != null) {
			if (payload.rawContent() instanceof ObjectNode) {
				ObjectNode node = (ObjectNode) payload.rawContent();
				ObjectNode response = log.putObject("response");
				// log the whole json payload but 'results'
				Iterator<Entry<String, JsonNode>> fields = node.fields();
				while (fields.hasNext()) {
					Entry<String, JsonNode> field = fields.next();
					if (!field.getKey().equals("results"))
						response.set(field.getKey(), field.getValue());
				}
			}
		}
	}

	private void addRequestPayload(ObjectNode log, Context context) {

		try {
			String content = context.request().content();

			if (!Strings.isNullOrEmpty(content)) {

				// TODO: fix the content type problem
				// String contentType = context.request().contentType();
				// log.put("contentType", contentType);
				// if (PayloadHelper.JSON_CONTENT.equals(contentType))
				// log.node("content", content);
				// else
				// log.put("content", content);

				if (Json8.isObject(content)) {
					JsonNode securedContent = Json8.fullReplaceTextualFields(//
							Json8.readNode(content), "password", "******");

					log.set("jsonContent", securedContent);
				}
			}
		} catch (Exception e) {
			log.set("jsonContent", Json8.object("error", JsonPayload.toJson(e, true)));
		}
	}

	private void addQuery(ObjectNode log, Context context) {
		if (context.query().keys().isEmpty())
			return;

		ObjectNode logQuery = log.putObject("query");
		for (String key : context.query().keys()) {
			String value = key.equals(FIELD_PASSWORD) //
					? "******"
					: context.get(key);
			logQuery.put(key, value);
		}
	}

	private void addCredentials(ObjectNode log) {
		Credentials credentials = SpaceContext.credentials();
		ObjectNode logCredentials = log.putObject("credentials");
		logCredentials.put("type", credentials.type().name());
		logCredentials.put("name", credentials.name());
	}

	private void addHeaders(ObjectNode log, Set<Entry<String, List<String>>> headers) {

		for (Entry<String, List<String>> header : headers) {

			String key = header.getKey();
			List<String> values = header.getValue();

			if (key.equalsIgnoreCase(SpaceHeaders.AUTHORIZATION))
				continue;

			if (Utils.isNullOrEmpty(values))
				continue;

			if (values.size() == 1)
				log.with("headers").put(key, values.get(0));

			else if (values.size() > 1) {
				ArrayNode array = log.with("headers").putArray(key);
				for (String string : values)
					array.add(string);
			}
		}
	}

	public static Index logIndex() {
		return Index.toIndex(TYPE);
	}

	//
	// Singleton
	//

	private static LogResource singleton = new LogResource();

	static LogResource get() {
		return singleton;
	}

	private LogResource() {
	}
}
