package io.spacedog.client;

import java.util.Optional;

import org.joda.time.DateTime;

import com.fasterxml.jackson.databind.node.ObjectNode;

import io.spacedog.utils.Json;
import io.spacedog.utils.Passwords;
import io.spacedog.utils.Schema;
import io.spacedog.utils.Settings;
import io.spacedog.utils.SpaceFields;
import io.spacedog.utils.SpaceParams;
import io.spacedog.utils.Utils;

public class SpaceClient implements SpaceFields, SpaceParams {

	public static class User {
		public String id;
		public String backendId;
		public String username;
		public String password;
		public String email;
		public String accessToken;
		public DateTime expiresAt;

		public User(String backendId, String username) {
			this.backendId = backendId;
			this.username = username;
		}

		public User(String backendId, String username, String password) {
			this(backendId, username);
			this.password = password;
		}

		public User(String backendId, String username, String password, String email) {
			this(backendId, username, password);
			this.email = email;
		}

		public User(String backendId, String id, String username, String password, String email) {
			this(backendId, username, password, email);
			this.id = id;
		}

		public User(String backendId, String id, String username, String password, String email, //
				String accessToken, DateTime expiresAt) {
			this(backendId, id, username, password, email);
			this.accessToken = accessToken;
			this.expiresAt = expiresAt;
		}
	}

	public static class Backend {

		public String backendId;
		public User adminUser;

		public Backend(String backendId, String username, String password, String email) {
			this.backendId = backendId;
			this.adminUser = new User(backendId, username, username, password, email);
		}
	}

	public static void initPushDefaultSchema(Backend backend) {
		SpaceRequest.post("/1/schema/installation").adminAuth(backend).go(201);
	}

	public static User signUp(Backend backend, String username, String password) {
		return signUp(backend.backendId, username, password);
	}

	public static User signUp(String backendId, String username, String password) {
		return signUp(backendId, username, password, "david@spacedog.io");
	}

	public static User signUp(Backend backend, String username, String password, String email) {
		return signUp(backend.backendId, username, password, email);
	}

	public static User signUp(User user) {
		return signUp(user.backendId, user.username, user.password, user.email);
	}

	public static User signUp(String backendId, String username, String password, String email) {
		return login(createCredentials(backendId, username, password, email));
	}

	public static User createTempCredentials(String backendId, String username) {
		return createCredentials(backendId, username, Passwords.random());
	}

	public static User createCredentials(Backend backend, String username, String password) {
		return createCredentials(backend.backendId, username, password);
	}

	public static User createCredentials(String backendId, String username, String password) {
		return createCredentials(backendId, username, password, "david@spacedog.io");
	}

	public static User createCredentials(String backendId, String username, String password, String email) {

		String id = SpaceRequest.post("/1/credentials").backendId(backendId)//
				.body(FIELD_USERNAME, username, FIELD_PASSWORD, password, FIELD_EMAIL, email)//
				.go(201).getString(FIELD_ID);

		return new User(backendId, id, username, password, email);
	}

	public static User createAdminCredentials(Backend backend, String username, String password, String email) {
		ObjectNode node = Json.object(FIELD_USERNAME, username, //
				FIELD_PASSWORD, password, FIELD_EMAIL, email, FIELD_LEVEL, "ADMIN");

		String id = SpaceRequest.post("/1/credentials")//
				.adminAuth(backend).body(node).go(201).getString(FIELD_ID);

		return new User(backend.backendId, id, username, password, email);
	}

	public static User login(String backendId, String username, String password) {
		return login(new User(backendId, username, password));
	}

	public static Optional<User> login(String backendId, String username, String password, int... statuses) {
		return login(new User(backendId, username, password), statuses);
	}

	public static User login(User user) {
		return login(user, 200).get();
	}

	public static Optional<User> login(User user, int... statuses) {
		return login(Optional.empty(), user, statuses);
	}

	public static User login(long lifetime, User user) {
		return login(Optional.of(lifetime), user, 200).get();
	}

	public static Optional<User> login(long lifetime, User user, int... statuses) {
		return login(Optional.of(lifetime), user, statuses);
	}

	private static Optional<User> login(Optional<Long> lifetime, User user, int... statuses) {
		SpaceRequest request = SpaceRequest.get("/1/login").basicAuth(user);

		if (lifetime.isPresent())
			request.queryParam(PARAM_LIFETIME, lifetime.get().toString());

		SpaceResponse response = request.go(statuses);

		if (response.httpResponse().getStatus() != 200)
			return Optional.empty();

		user.id = response.get("credentials").get(FIELD_ID).asText();
		user.email = response.get("credentials").get(FIELD_EMAIL).asText();
		user.accessToken = response.get(FIELD_ACCESS_TOKEN).asText();
		user.expiresAt = DateTime.now().plus(response.get(FIELD_EXPIRES_IN).asLong());

		return Optional.of(user);
	}

	public static User logout(User user) {
		return logout(user, 200);
	}

	public static User logout(User user, int... statuses) {
		logout(user.backendId, user.accessToken, statuses);
		user.accessToken = null;
		user.expiresAt = null;
		return user;
	}

	public static void logout(String backendId, String accessToken) {
		logout(backendId, accessToken, 200);
	}

	public static void logout(String backendId, String accessToken, int... statuses) {
		SpaceRequest.get("/1/logout").backendId(backendId)//
				.bearerAuth(accessToken).go(statuses);
	}

	public static void deleteCredentials(String username, Backend backend) {
		SpaceRequest.delete("/1/credentials/" + username).adminAuth(backend).go(200, 404);
	}

	public static void deleteCredentialsBySuperdog(String backendId, String username) {
		Optional<User> user = getCredentialsBySuperdog(backendId, username);

		if (user.isPresent())
			SpaceRequest.delete("/1/credentials/{id}")//
					.routeParam("id", user.get().id)//
					.superdogAuth(backendId).go(200);
	}

	public static Optional<User> getCredentialsBySuperdog(String backendId, String username) {
		ObjectNode node = SpaceRequest.get("/1/credentials")//
				.queryParam(PARAM_USERNAME, username)//
				.superdogAuth(backendId).go(200).objectNode();

		if (node.get("total").asInt() == 0)
			return Optional.empty();

		User user = new User(backendId, username);
		user.id = Json.get(node, "results.0.id").asText();
		user.email = Json.get(node, "results.0.email").asText();
		return Optional.of(user);
	}

	public static void resetSchema(Schema schema, Backend backend) {
		deleteSchema(schema, backend);
		setSchema(schema, backend);
	}

	public static void deleteSchema(Schema schema, Backend backend) {
		deleteSchema(schema.name(), backend);
	}

	public static void deleteSchema(String schemaName, Backend backend) {
		SpaceRequest.delete("/1/schema/" + schemaName).adminAuth(backend).go(200, 404);
	}

	public static void setSchema(Schema schema, Backend backend) {
		SpaceRequest.put("/1/schema/" + schema.name())//
				.adminAuth(backend).bodySchema(schema).go(200, 201);
	}

	public static Schema getSchema(String name, Backend backend) {
		ObjectNode node = SpaceRequest.get("/1/schema/" + name)//
				.adminAuth(backend).go(200).objectNode();
		return new Schema(name, node);
	}

	public static Backend createBackend(Backend backend) {
		return createBackend(backend, false);
	}

	public static Backend createBackend(Backend backend, boolean notification) {
		return createBackend(backend.backendId, backend.adminUser.username, //
				backend.adminUser.password, backend.adminUser.email, notification);
	}

	public static Backend createBackend(String backendId, String username, String password, //
			String email, boolean notification) {

		SpaceRequest.post("/1/backend").backendId(backendId)//
				.queryParam(PARAM_NOTIF, Boolean.toString(notification))//
				.body(FIELD_USERNAME, username, FIELD_PASSWORD, password, FIELD_EMAIL, email)//
				.go(201);

		return new Backend(backendId, username, password, email);
	}

	public static void deleteTestBackend() {
		deleteBackend("test", "test", "hi test");
	}

	public static void deleteBackend(Backend backend) {
		deleteBackend(backend.backendId, backend.adminUser.username, backend.adminUser.password);
	}

	public static void deleteBackend(String backendId, String username, String password) {
		// 401 Unauthorized is valid since if this backend does not exist
		// delete returns 401 because admin username and password
		// won't match any backend
		SpaceRequest.delete("/1/backend")//
				.basicAuth(backendId, username, password).go(200, 401);
	}

	public static Backend resetTestBackend() {
		return resetBackend("test", "test", "hi test");
	}

	public static Backend resetTest2Backend() {
		return resetBackend("test2", "test2", "hi test2");
	}

	public static Backend resetBackend(String backendId, String username, String password) {
		return resetBackend(backendId, username, password, "platform@spacedog.io");
	}

	public static Backend resetBackend(Backend backend) {
		return resetBackend(backend.backendId, backend.adminUser.username, //
				backend.adminUser.password, backend.adminUser.email);
	}

	public static Backend resetBackend(String backendId, String username, String password, String email) {
		deleteBackend(backendId, username, password);
		return createBackend(backendId, username, password, email, false);
	}

	public static void prepareTest() {
		prepareTestInternal(true);
	}

	public static void prepareTest(boolean forTesting) {
		prepareTestInternal(forTesting);
	}

	private static void prepareTestInternal(boolean forTesting) {

		SpaceRequest.setForTestingDefault(forTesting);
		StackTraceElement grandParentStackTraceElement = Utils.getGrandParentStackTraceElement();

		Utils.info();
		Utils.info("--- %s.%s() ---", //
				grandParentStackTraceElement.getClassName(), //
				grandParentStackTraceElement.getMethodName());
	}

	public static void deleteAll(String type, Backend backend) {
		SpaceRequest.delete("/1/data/" + type).adminAuth(backend).go(200);
	}

	public static <K extends Settings> void saveSettings(Backend test, K settings) {
		SpaceRequest.put("/1/settings/" + settings.id()).adminAuth(test).bodySettings(settings).go(200, 201);
	}

	public static <K extends Settings> K loadSettings(Backend backend, Class<K> settingsClass) {
		return SpaceRequest.get("/1/settings/" + Settings.id(settingsClass))//
				.adminAuth(backend).go(200).toObject(settingsClass);
	}

	public static <K extends Settings> void deleteSettings(Backend backend, Class<K> settingsClass) {
		deleteSettings(backend, Settings.id(settingsClass));
	}

	public static void deleteSettings(Backend backend, String id) {
		SpaceRequest.delete("/1/settings/" + id).adminAuth(backend).go(200);
	}

	public static void setRole(User admin, User user, String role) {
		SpaceRequest.put("/1/credentials/{id}/roles/{role}").userAuth(admin)//
				.routeParam("id", user.id).routeParam("role", role).go(200);
	}

	public static void deleteAllCredentialsButSuperAdmins(Backend backend) {
		SpaceRequest.delete("/1/credentials").adminAuth(backend).go(200);
	}

}
