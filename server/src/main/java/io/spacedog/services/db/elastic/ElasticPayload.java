/**
 * © David Attias 2015
 */
package io.spacedog.services.db.elastic;

import org.elasticsearch.action.index.IndexResponse;

import io.spacedog.client.data.DataWrap;
import io.spacedog.client.http.SpaceFields;
import io.spacedog.services.JsonPayload;

public class ElasticPayload implements SpaceFields {

	public static JsonPayload saved(String uriBase, DataWrap<?> object) {
		return JsonPayload.saved(object.isCreated(), uriBase, object.type(), //
				object.id()).withVersion(object.version());
	}

	public static JsonPayload saved(String uriBase, IndexResponse response) {
		final boolean created = ElasticUtils.isCreated(response);
		String version = ElasticVersion.toString(response.getSeqNo(), response.getPrimaryTerm());
		return JsonPayload.saved(created, uriBase, //
				ElasticIndex.valueOf(response.getIndex()).type(), //
				response.getId()).withVersion(version);
	}
}
