package io.spacedog.services;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import org.elasticsearch.action.Action;
import org.elasticsearch.action.ActionFuture;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionRequestBuilder;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.indices.alias.Alias;
import org.elasticsearch.action.admin.indices.close.CloseIndexResponse;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexResponse;
import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsResponse;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.deletebyquery.DeleteByQueryAction;
import org.elasticsearch.action.deletebyquery.DeleteByQueryRequest;
import org.elasticsearch.action.deletebyquery.DeleteByQueryResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.get.MultiGetResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchScrollRequestBuilder;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.action.support.QuerySourceBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.ClusterAdminClient;
import org.elasticsearch.client.Requests;
import org.elasticsearch.cluster.health.ClusterHealthStatus;
import org.elasticsearch.cluster.metadata.MappingMetaData;
import org.elasticsearch.common.Priority;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.IndexNotFoundException;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;

import io.spacedog.utils.Check;
import io.spacedog.utils.Exceptions;
import io.spacedog.utils.Json;
import io.spacedog.utils.Schema;
import io.spacedog.utils.SpaceParams;
import io.spacedog.utils.Utils;

public class ElasticClient implements SpaceParams {

	private Client internalClient;

	public ElasticClient(Client client) {
		this.internalClient = client;
	}

	public void close() {
		internalClient.close();
	}

	//
	// prepare methods
	//

	public IndexRequestBuilder prepareIndex(String backend, String type) {
		return internalClient.prepareIndex(toAlias(backend, type), type);
	}

	public IndexRequestBuilder prepareIndex(String backend, String type, String id) {
		return internalClient.prepareIndex(toAlias(backend, type), type, id);
	}

	public UpdateRequestBuilder prepareUpdate(String backendId, String type, String id) {
		return internalClient.prepareUpdate(toAlias(backendId, type), type, id);
	}

	public SearchRequestBuilder prepareSearch() {
		// forbid any non very specific index list
		// to avoid the risk of mixing indices from different backend
		// to avoid for example empty index list resulting to
		// indices of all backend
		return internalClient.prepareSearch()//
				.setIndicesOptions(IndicesOptions.fromOptions(false, false, false, false));
	}

	public SearchRequestBuilder prepareSearch(String backendId, String type) {
		Check.notNullOrEmpty(backendId, "backendId");
		Check.notNullOrEmpty(type, "type");
		return internalClient.prepareSearch(toAlias(backendId, type))//
				.setIndicesOptions(IndicesOptions.fromOptions(false, false, false, false));
	}

	public SearchScrollRequestBuilder prepareSearchScroll(String scrollId) {
		return internalClient.prepareSearchScroll(scrollId);
	}

	//
	// shortcut methods
	//

	public IndexResponse index(String backend, String type, String source) {
		return index(backend, type, source, false);
	}

	public IndexResponse index(String backend, String type, String source, boolean refresh) {
		return prepareIndex(backend, type).setSource(source).setRefresh(refresh).get();
	}

	public IndexResponse index(String backend, String type, String id, String source) {
		return index(backend, type, id, source, false);
	}

	public IndexResponse index(String backend, String type, String id, String source, boolean refresh) {
		return prepareIndex(backend, type, id).setSource(source).setRefresh(refresh).get();
	}

	public IndexResponse index(String backend, String type, String id, byte[] source) {
		return index(backend, type, id, source, false);
	}

	public IndexResponse index(String backend, String type, String id, byte[] source, boolean refresh) {
		return prepareIndex(backend, type, id).setSource(source).setRefresh(refresh).get();
	}

	public GetResponse get(String backend, String type, String id) {
		return get(backend, type, id, false);
	}

	public GetResponse get(String backend, String type, String id, boolean throwNotFound) {
		GetResponse response = internalClient.prepareGet(toAlias(backend, type), type, id).get();

		if (!response.isExists() && throwNotFound)
			throw Exceptions.notFound(backend, type, id);

		return response;
	}

	public Optional<SearchHit> get(String backend, String type, QueryBuilder query) {
		try {
			SearchHits hits = internalClient.prepareSearch(toAlias(backend, type))//
					.setTypes(type)//
					.setQuery(query)//
					.get()//
					.getHits();

			if (hits.getTotalHits() == 0)
				return Optional.empty();
			else if (hits.getTotalHits() == 1)
				return Optional.of(hits.getAt(0));

			throw Exceptions.runtime(//
					"unicity violation in [%s] data collection", type);

		} catch (IndexNotFoundException e) {
			return Optional.empty();
		}
	}

	public boolean exists(String backend, String type, String id) {
		return internalClient.prepareGet(toAlias(backend, type), type, id)//
				.setFetchSource(false).get().isExists();
	}

	public boolean exists(String backend, String type, QueryBuilder query) {
		return internalClient.prepareSearch(toAlias(backend, type))//
				.setTypes(type)//
				.setQuery(query)//
				.setFetchSource(false)//
				.get()//
				.getHits()//
				.getTotalHits() > 0;
	}

	public boolean delete(String backendId, String type, String id, boolean refresh, boolean throwNotFound) {
		DeleteResponse response = internalClient.prepareDelete(//
				toAlias(backendId, type), type, id).setRefresh(refresh).get();

		if (response.isFound())
			return true;
		if (throwNotFound)
			throw Exceptions.notFound(backendId, type, id);

		return false;
	}

	public BulkRequestBuilder prepareBulk() {
		return internalClient.prepareBulk();
	}

	public DeleteByQueryResponse deleteByQuery(String backendId, QueryBuilder query, String... types) {
		Check.notNullOrEmpty(backendId, "backendId");

		DeleteByQueryRequest request = new DeleteByQueryRequest(toAliases(backendId, types))//
				.timeout(new TimeValue(60000))//
				.source(new QuerySourceBuilder().setQuery(query));

		try {
			return Start.get().getElasticClient().execute(//
					DeleteByQueryAction.INSTANCE, request).get();
		} catch (ExecutionException | InterruptedException e) {
			throw Exceptions.runtime(e);
		}
	}

	public DeleteByQueryResponse deleteByQuery(String query, String backendId, String... types) {

		Check.notNullOrEmpty(backendId, "backendId");

		if (Strings.isNullOrEmpty(query))
			query = Json.objectBuilder().object("query").object("match_all").toString();

		String[] indices = types == null ? toIndices(backendId) : toAliases(backendId, types);

		DeleteByQueryRequest delete = new DeleteByQueryRequest(indices)//
				.timeout(new TimeValue(60000))//
				.source(query);

		try {
			return Start.get().getElasticClient().execute(DeleteByQueryAction.INSTANCE, delete).get();
		} catch (ExecutionException | InterruptedException e) {
			throw Exceptions.runtime(e);
		}
	}

	public MultiGetResponse multiGet(String backend, String type, Set<String> ids) {
		return internalClient.prepareMultiGet().add(toAlias(backend, type), type, ids).get();
	}

	public <Request extends ActionRequest<?>, Response extends ActionResponse, RequestBuilder extends ActionRequestBuilder<Request, Response, RequestBuilder>> ActionFuture<Response> execute(
			final Action<Request, Response, RequestBuilder> action, //
			final Request request) {
		return internalClient.execute(action, request);
	}

	//
	// admin methods
	//

	public void createIndex(String backendId, String type, String mapping, boolean async) {
		createIndex(backendId, type, mapping, async, PARAM_SHARDS_DEFAULT, PARAM_REPLICAS_DEFAULT);
	}

	public void createIndex(String backendId, String type, String mapping, boolean async, int shards, int replicas) {

		Settings settings = Settings.builder()//
				.put("number_of_shards", shards)//
				.put("number_of_replicas", replicas)//
				.build();

		CreateIndexResponse createIndexResponse = internalClient.admin().indices()//
				.prepareCreate(toIndex0(backendId, type))//
				.addMapping(type, mapping)//
				.addAlias(new Alias(toAlias(backendId, type)))//
				.setSettings(settings)//
				.get();

		if (!createIndexResponse.isAcknowledged())
			throw Exceptions.runtime(//
					"index [%s] creation not acknowledged by the whole cluster", //
					toIndex0(backendId, type));

		if (!async)
			ensureGreen(backendId, type);
	}

	public void ensureAllIndicesGreen() {
		ensureGreen("_all");
	}

	public void ensureGreen(String backendId, String type) {
		ensureGreen(toAlias(backendId, type));
	}

	public void ensureGreen(String... indices) {
		String indicesString = Arrays.toString(indices);
		Utils.info("[SpaceDog] Ensure indices %s are green ...", indicesString);

		ClusterHealthResponse response = this.internalClient.admin().cluster()
				.health(Requests.clusterHealthRequest(indices)//
						.timeout(TimeValue.timeValueSeconds(30))//
						.waitForGreenStatus()//
						.waitForEvents(Priority.LOW)//
						.waitForRelocatingShards(0))//
				.actionGet();

		if (response.isTimedOut())
			throw Exceptions.runtime("ensure indices %s status are green timed out", //
					indicesString);

		if (!response.getStatus().equals(ClusterHealthStatus.GREEN))
			throw Exceptions.runtime("indices %s failed to turn green", //
					indicesString);

		Utils.info("[SpaceDog] indices %s are green!", indicesString);
	}

	public void refreshType(String backendId, String type) {
		refreshIndex(toAlias(backendId, type));
	}

	public void refreshBackend(String backendId) {
		toIndicesStream(backendId).forEach(indexName -> refreshIndex(indexName));
	}

	public void deleteAllIndices(String backendId) {

		String[] indices = toIndices(backendId);

		if (indices != null && indices.length > 0) {
			DeleteIndexResponse deleteIndexResponse = internalClient.admin().indices().prepareDelete(indices).get();

			if (!deleteIndexResponse.isAcknowledged())
				throw Exceptions.runtime(//
						"backend [%s] deletion not acknowledged by the whole cluster", //
						backendId);
		}
	}

	public void deleteIndex(String backendId, String type) {
		internalClient.admin().indices().prepareDelete(toAlias(backendId, type)).get();
	}

	public ImmutableOpenMap<String, ImmutableOpenMap<String, MappingMetaData>> getMappings(String backendId) {
		Check.notNullOrEmpty(backendId, "backendId");

		String[] indices = toIndices(backendId);
		if (indices.length == 0)
			return ImmutableOpenMap.of();

		return internalClient.admin().indices()//
				.prepareGetMappings(indices)//
				.get()//
				.getMappings();
	}

	public GetMappingsResponse getMappings(String backendId, String type) {
		Check.notNullOrEmpty(backendId, "backendId");
		Check.notNullOrEmpty(type, "type");

		return internalClient.admin().indices()//
				.prepareGetMappings(toAlias(backendId, type))//
				.setTypes(type)//
				.get();
	}

	public Schema getSchema(String backendId, String type) {

		// TODO what if this type is unknown?

		String source = getMappings(backendId, type).getMappings()//
				.iterator().next().value.get(type).source().toString();
		return new Schema(type, //
				(ObjectNode) Json.readObject(source).get(type).get("_meta"));
	}

	public boolean existsIndex(String backendId, String type) {
		try {
			return internalClient.admin().indices()//
					.prepareTypesExists(toAlias(backendId, type))//
					.setTypes(type)//
					.get()//
					.isExists();

		} catch (IndexNotFoundException e) {
			return false;
		}
	}

	public void putMapping(String backendId, String type, String mapping) {
		PutMappingResponse putMappingResponse = internalClient.admin().indices()//
				.preparePutMapping(toAlias(backendId, type))//
				.setType(type)//
				.setSource(mapping)//
				.setUpdateAllTypes(true)//
				.get();

		if (!putMappingResponse.isAcknowledged())
			throw Exceptions.runtime(//
					"mapping [%s] update not acknowledged by the whole cluster", //
					type);
	}

	public void deleteAllBackendIndices() {
		DeleteIndexResponse response = internalClient.admin().indices().prepareDelete("_all")//
				.setIndicesOptions(IndicesOptions.fromOptions(false, true, true, true))//
				.get();

		if (!response.isAcknowledged())
			throw Exceptions.runtime(//
					"delete all indices not acknowledged by the cluster");
	}

	public void closeAllBackendIndices() {
		CloseIndexResponse closeIndexResponse = internalClient.admin().indices().prepareClose("_all")//
				.setIndicesOptions(IndicesOptions.fromOptions(false, true, true, true))//
				.get();

		if (!closeIndexResponse.isAcknowledged())
			throw Exceptions.runtime(//
					"close all indices not acknowledged by the cluster");
	}

	public ClusterAdminClient cluster() {
		return internalClient.admin().cluster();
	}

	//
	// to index help methods
	//

	public Stream<String> indices() {
		// TODO if too many customers, my cluster might have too many indices
		// for this to work correctly
		return Arrays.stream(internalClient.admin().indices().prepareGetIndex().get().indices());
	}

	public static class Index {
		public String backendId;
		public String schemaName;

		public Index(String backendId, String schemaName) {
			this.backendId = backendId;
			this.schemaName = schemaName;
		}

		@Override
		public String toString() {
			return String.join("-", backendId, schemaName);
		}

		public static Index valueOf(String index) {
			String[] parts = index.split("-", 3);
			Check.isTrue(parts.length >= 3, "index [%s] is invalid", index);
			return new Index(parts[0], parts[1]);
		}
	}

	public Index[] toIndicesForSchema(String schemaName) {
		return indices()//
				.map(index -> Index.valueOf(index))//
				.filter(index -> schemaName.equals(index.schemaName))//
				.toArray(Index[]::new);
	}

	public Stream<String> toIndicesStream(String backendId) {
		String prefix = backendId + "-";
		return indices()//
				.filter(indexName -> indexName.startsWith(prefix));
	}

	public String[] toIndices(String backendId) {
		return toIndicesStream(backendId).toArray(String[]::new);
	}

	/**
	 * TODO use this in the future to distinguish data indices and internal
	 * backend indices. This means all data indices must be renamed with the
	 * following pattern: backendId-data-indexName
	 */
	public String[] toDataIndices(String backendId) {
		return toIndicesStream(backendId)//
				.filter(index -> "data".equals(index.split("-", 3)[1]))//
				.toArray(String[]::new);
	}

	public String toIndex0(String backendId, String type) {
		return toIndex(backendId, type, 0);
	}

	public String toIndex(String backendId, String type, int version) {
		return String.join("-", backendId, type, Integer.toString(version));
	}

	public String toAlias(String backendId, String type) {
		return String.join("-", backendId, type);
	}

	public String[] toAliases(String backendId, String... types) {
		return Arrays.stream(types)//
				.map(type -> toAlias(backendId, type))//
				.toArray(String[]::new);
	}

	//
	// implementation
	//

	private void refreshIndex(String indexName) {
		internalClient.admin().indices().prepareRefresh(indexName).get();
	}
}
