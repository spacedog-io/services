/**
 * © David Attias 2015
 */
package io.spacedog.services;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.spacedog.core.Json8;
import io.spacedog.jobs.Internals;
import io.spacedog.model.BackendSettings;
import io.spacedog.rest.SpaceBackend;
import io.spacedog.utils.Credentials;
import io.spacedog.utils.Exceptions;
import io.spacedog.utils.Optional7;
import net.codestory.http.Context;
import net.codestory.http.annotations.Delete;
import net.codestory.http.annotations.Get;
import net.codestory.http.annotations.Post;
import net.codestory.http.payload.Payload;

public class BackendResource extends Resource {

	private static final String TYPE = "backend";

	//
	// Routes
	//

	@Get("")
	@Get("/")
	public Payload ping() {
		ObjectNode payload = (ObjectNode) Json8.toNode(Start.get().info());
		payload.put("success", true).put("status", 200);
		return JsonPayload.json(payload);
	}

	@Get("/1/backend")
	@Get("/1/backend/")
	public Payload getAll(Context context) {
		SpaceContext.credentials().checkAtLeastSuperAdmin();
		int from = context.query().getInteger(PARAM_FROM, 0);
		int size = context.query().getInteger(PARAM_SIZE, 10);

		SearchResults<Credentials> superAdmins = CredentialsResource.get()//
				.getAllSuperAdmins(from, size);
		return toPayload(superAdmins);
	}

	@Delete("/1/backend")
	@Delete("/1/backend/")
	public Payload delete(Context context) {
		SpaceContext.credentials().checkAtLeastSuperAdmin();

		if (SpaceContext.backend().isDefault())
			throw Exceptions.illegalArgument("default backend can not be deleted");

		elastic().deleteBackendIndices();

		if (isDeleteFilesAndShares()) {
			FileResource.get().deleteAll();
			ShareResource.get().deleteAll();
		}

		return JsonPayload.success();
	}

	@Post("/1/backend")
	@Post("/1/backend/")
	public Payload post(String body, Context context) {
		return post(SpaceContext.backendId(), body, context);
	}

	// TODO these routes are deprecated
	@Post("/1/backend/:id")
	@Post("/1/backend/:id/")
	public Payload post(String backendId, String body, Context context) {

		ServerConfiguration configuration = Start.get().configuration();
		SpaceBackend backend = configuration.apiBackend();
		if (!backend.multi())
			throw Exceptions.illegalArgument(//
					"backend [%s] does not allow sub backends", backend);

		SpaceBackend.checkIsValid(backendId);

		if (configuration.backendCreateRestricted())
			SpaceContext.credentials().checkSuperDog();

		Index index = CredentialsResource.credentialsIndex().backendId(backendId);
		if (elastic().exists(index))
			return JsonPayload.invalidParameters("backendId", backendId,
					String.format("backend [%s] not available", backendId));

		CredentialsResource credentialsResource = CredentialsResource.get();
		credentialsResource.initIndex(backendId);
		Credentials credentials = credentialsResource//
				.createCredentialsRequestToCredentials(body, Credentials.Type.superadmin);
		credentialsResource.create(credentials);

		LogResource.get().initIndex(backendId);

		if (context.query().getBoolean(PARAM_NOTIF, true)) {
			Optional7<String> topic = configuration.awsSuperdogNotificationTopic();
			if (topic.isPresent())
				Internals.get().notify(topic.get(), //
						String.format("New backend (%s)", spaceRootUrl()), //
						String.format("backend id = %s\nadmin email = %s", //
								backendId, credentials.email().get()));
		}

		return JsonPayload.saved(true, "/1/backend", TYPE, backendId, true);
	}

	//
	// Public interface
	//

	// public Stream<String[]> getAllBackendIndices() {
	// return elastic().indices().map(index -> index.split("-",
	// 2));
	// }

	//
	// Implementation
	//

	private Payload toPayload(SearchResults<Credentials> superAdmins) {
		ArrayNode results = Json8.array();

		for (Credentials superAdmin : superAdmins.results)
			results.add(Json8.object(FIELD_USERNAME, superAdmin.name(), //
					FIELD_EMAIL, superAdmin.email().get()));

		return JsonPayload.json(Json8.object("total", superAdmins.total, //
				"results", results));
	}

	private boolean isDeleteFilesAndShares() {
		ServerConfiguration configuration = Start.get().configuration();
		return !SpaceContext.isTest() //
				&& configuration.awsRegion().isPresent() //
				&& !configuration.isOffline();
	}

	//
	// Singleton
	//

	private static BackendResource singleton = new BackendResource();

	static BackendResource get() {
		return singleton;
	}

	private BackendResource() {
		SettingsResource.get().registerSettingsClass(BackendSettings.class);
	}
}