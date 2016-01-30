/**
 * © David Attias 2015
 */
package io.spacedog.services;

import java.util.Optional;
import java.util.concurrent.ExecutionException;

import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsResponse;
import org.elasticsearch.action.deletebyquery.DeleteByQueryRequestBuilder;
import org.elasticsearch.action.deletebyquery.DeleteByQueryResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.common.base.Strings;
import org.elasticsearch.index.query.AndFilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.joda.time.DateTime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.spacedog.utils.Json;
import net.codestory.http.Context;

public class ElasticHelper {

	//
	// help methods
	//

	public Optional<ObjectNode> getObject(String index, String type, String id) {
		GetResponse response = Start.get().getElasticClient().prepareGet(index, type, id).get();

		if (!response.isExists())
			return Optional.empty();

		ObjectNode object = Json.readObjectNode(response.getSourceAsString());
		object.with("meta").put("id", response.getId()).put("type", response.getType()).put("version",
				response.getVersion());
		return Optional.of(object);
	}

	IndexResponse createObject(String index, String type, ObjectNode object, String createdBy) {

		String now = DateTime.now().toString();

		// replace meta to avoid developers to
		// set any meta fields directly
		object.set("meta",
				Json.objectBuilder()//
						.put("createdBy", createdBy)//
						.put("updatedBy", createdBy)//
						.put("createdAt", now)//
						.put("updatedAt", now)//
						.build());

		return Start.get().getElasticClient().prepareIndex(index, type)//
				.setSource(object.toString()).get();
	}

	/**
	 * TODO do we need these two update methods or just one?
	 */
	public IndexResponse updateObject(String index, String type, String id, long version, ObjectNode object,
			String updatedBy) {

		object.with("meta").remove("id");
		object.with("meta").remove("version");
		object.with("meta").remove("type");

		Json.checkStringNotNullOrEmpty(object, "meta.createdBy");
		Json.checkStringNotNullOrEmpty(object, "meta.createdAt");

		object.with("meta").put("updatedBy", updatedBy);
		object.with("meta").put("updatedAt", DateTime.now().toString());

		IndexRequestBuilder builder = Start.get().getElasticClient().prepareIndex(index, type, id)
				.setSource(object.toString());
		if (version > 0)
			builder.setVersion(version);
		return builder.get();
	}

	public IndexResponse updateObject(String index, ObjectNode object, String updatedBy) {

		String id = Json.checkStringNotNullOrEmpty(object, "meta.id");
		String type = Json.checkStringNotNullOrEmpty(object, "meta.type");
		long version = Json.checkLongNode(object, "meta.version", true).get().asLong();

		Json.checkStringNotNullOrEmpty(object, "meta.createdBy");
		Json.checkStringNotNullOrEmpty(object, "meta.createdAt");

		object.with("meta").remove("id");
		object.with("meta").remove("version");
		object.with("meta").remove("type");

		object.with("meta").put("updatedBy", updatedBy);
		object.with("meta").put("updatedAt", DateTime.now().toString());

		return Start.get().getElasticClient().prepareIndex(index, type, id).setSource(object.toString())
				.setVersion(version).get();
	}

	public UpdateResponse patchObject(String index, String type, String id, long version, ObjectNode object,
			String updatedBy) {

		object.with("meta").removeAll()//
				.put("updatedBy", updatedBy)//
				.put("updatedAt", DateTime.now().toString());

		UpdateRequestBuilder update = Start.get().getElasticClient().prepareUpdate(index, type, id)
				.setDoc(object.toString());

		if (version > 0)
			update.setVersion(version);

		return update.get();
	}

	public DeleteByQueryResponse delete(String index, String query, String... types) {

		if (Strings.isNullOrEmpty(query))
			query = Json.objectBuilder().object("query").object("match_all").toString();

		DeleteByQueryRequestBuilder setSource = Start.get().getElasticClient().prepareDeleteByQuery(index)
				.setSource(query);

		if (types != null)
			setSource.setTypes(types);

		return setSource.get();
	}

	public SearchHits search(String index, String type, String... terms) {

		if (terms.length % 2 == 1)
			throw new RuntimeException(
					String.format("invalid search terms [%s]: missing term value", terms.toString()));

		AndFilterBuilder builder = new AndFilterBuilder();
		for (int i = 0; i < terms.length; i = i + 2) {
			builder.add(FilterBuilders.termFilter(terms[i], terms[i + 1]));
		}

		SearchResponse response = Start.get().getElasticClient().prepareSearch(index).setTypes(type)
				.setQuery(QueryBuilders.filteredQuery(QueryBuilders.matchAllQuery(), builder)).get();

		return response.getHits();
	}

	public FilteredSearchBuilder searchBuilder(String index, String type) {
		return new FilteredSearchBuilder(index, type);
	}

	public static class FilteredSearchBuilder {

		private SearchRequest searchRequest;
		private SearchSourceBuilder sourceBuilder;
		private QueryBuilder queryBuilder;
		private AndFilterBuilder filterBuilder;

		public FilteredSearchBuilder(String index, String type) {

			this.sourceBuilder = SearchSourceBuilder.searchSource();

			this.searchRequest = new SearchRequest(index);

			if (!Strings.isNullOrEmpty(type)) {
				// check if type is well defined
				// throws a NotFoundException if not
				ElasticHelper.get().getSchema(index, type);
				this.searchRequest.types(type);
			}

		}

		public FilteredSearchBuilder applyContext(Context context) {
			sourceBuilder.from(context.request().query().getInteger("from", 0))
					.size(context.request().query().getInteger("size", 10))
					.fetchSource(context.request().query().getBoolean("fetch-contents", true));

			String queryText = context.get("q");
			if (!Strings.isNullOrEmpty(queryText)) {
				queryBuilder = QueryBuilders.simpleQueryStringQuery(queryText);
			} else
				queryBuilder = QueryBuilders.matchAllQuery();

			return this;
		}

		public FilteredSearchBuilder applyFilters(JsonNode filters) {
			filterBuilder = new AndFilterBuilder();
			filters.fields().forEachRemaining(field -> filterBuilder
					.add(FilterBuilders.termFilter(field.getKey(), Json.toSimpleValue(field.getValue()))));
			return this;
		}

		public SearchResponse get() throws InterruptedException, ExecutionException {
			searchRequest.source(sourceBuilder.query(QueryBuilders.filteredQuery(queryBuilder, filterBuilder)));
			return Start.get().getElasticClient().search(searchRequest).get();
		}
	}

	public void refresh(boolean refresh, String... indices) {
		if (refresh) {
			Start.get().getElasticClient().admin().indices().prepareRefresh(indices).get();
		}
	}

	public ObjectNode getSchema(String index, String type) {

		GetMappingsResponse resp = Start.get().getElasticClient().admin().indices()//
				.prepareGetMappings(index)//
				.addTypes(type)//
				.get();

		String source = Optional.ofNullable(resp.getMappings())//
				.map(indexMap -> indexMap.get(index))//
				.map(typeMap -> typeMap.get(type))//
				.orElseThrow(() -> NotFoundException.type(type))//
				.source()//
				.toString();

		return (ObjectNode) Json.readObjectNode(source).get(type).get("_meta");
	}

	//
	// singleton
	//

	private static ElasticHelper singleton = new ElasticHelper();

	static ElasticHelper get() {
		return singleton;
	}

	private ElasticHelper() {
	}

}
