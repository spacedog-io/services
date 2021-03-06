package io.spacedog.services.db.elastic;

import java.util.Collections;

import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.DocWriteResponse.Result;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.DeprecationHandler;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.search.SearchModule;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import com.fasterxml.jackson.databind.node.ObjectNode;

import io.spacedog.utils.Exceptions;
import io.spacedog.utils.Json;

public class ElasticUtils {

	public static boolean isCreated(DocWriteResponse response) {
		return response.getResult() == Result.CREATED;
	}

	public static boolean isUpdated(DocWriteResponse response) {
		return response.getResult() == Result.UPDATED;
	}

	public static boolean isDeleted(DocWriteResponse response) {
		return response.getResult() == Result.DELETED;
	}

	public static boolean isNotFound(DocWriteResponse response) {
		return response.getResult() == Result.NOT_FOUND;
	}

	public static RefreshPolicy toPolicy(Boolean refresh) {
		return refresh == null || !refresh ? RefreshPolicy.NONE : RefreshPolicy.IMMEDIATE;
	}

	private static NamedXContentRegistry contentRegistry = new NamedXContentRegistry(//
			new SearchModule(Settings.EMPTY, false, Collections.emptyList())//
					.getNamedXContents());

	public static SearchSourceBuilder toSearchSourceBuilder(String source) {

		try {
			SearchSourceBuilder builder = new SearchSourceBuilder();
			XContentParser parser = XContentFactory.xContent(XContentType.JSON)//
					.createParser(contentRegistry, DeprecationHandler.THROW_UNSUPPORTED_OPERATION, source);
			builder.parseXContent(parser);
			return builder;

		} catch (Exception e) {
			throw Exceptions.illegalArgument(e, //
					"error parsing search source [%s]", source);
		}
	}

	public static QueryBuilder toQueryBuilder(String query) {
		return QueryBuilders.wrapperQuery(query);
	}

	public static ObjectNode toJson(BulkByScrollResponse response) {

		return Json.object(//
				"timedOut", response.isTimedOut(), //
				"created", response.getCreated(), //
				"updated", response.getUpdated(), //
				"deleted", response.getDeleted(), //
				"reasonCancelled", response.getReasonCancelled(), //
				"searchFailures", response.getSearchFailures(), //
				"bulkFailures", response.getBulkFailures());
	}

	public static ObjectNode toJson(IndexResponse response) {
		return Json.object(//
				"id", response.getId(), //
				"type", ElasticIndex.valueOf(response.getIndex()).type(), //
				"version", response.getVersion());
	}

}
