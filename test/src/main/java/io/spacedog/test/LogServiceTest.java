package io.spacedog.test;

import java.util.List;

import org.joda.time.DateTime;
import org.junit.Test;

import io.spacedog.client.SpaceDog;
import io.spacedog.client.credentials.Permission;
import io.spacedog.client.credentials.Roles;
import io.spacedog.client.data.DataSettings;
import io.spacedog.client.data.ObjectNodeWrap;
import io.spacedog.client.elastic.ESQueryBuilders;
import io.spacedog.client.elastic.ESSearchSourceBuilder;
import io.spacedog.client.elastic.ESSortBuilders;
import io.spacedog.client.elastic.ESSortOrder;
import io.spacedog.client.http.SpaceEnv;
import io.spacedog.client.http.SpaceHeaders;
import io.spacedog.client.log.LogClient.LogItem;
import io.spacedog.client.log.LogClient.LogSearchResults;
import io.spacedog.utils.Json;

public class LogServiceTest extends SpaceTest {

	@Test
	public void purgeBackendLogs() throws InterruptedException {

		// prepare
		prepareTest();
		SpaceDog superadmin = clearServer();
		SpaceDog fred = createTempDog(superadmin, "fred").login();

		fred.data().getAllRequest().go();
		fred.data().getAllRequest().go();

		// superadmin checks everything is in place
		LogSearchResults log = superadmin.logs().get(10, true);

		assertEquals(6, log.results.size());
		assertEquals("/1/data", log.results.get(0).path);
		assertEquals("/1/data", log.results.get(1).path);
		assertEquals("/1/login", log.results.get(2).path);
		assertEquals("/1/credentials", log.results.get(3).path);
		assertEquals("/1/credentials", log.results.get(4).path);
		assertEquals("/1/admin/_clear", log.results.get(5).path);

		DateTime before = log.results.get(1).receivedAt;

		// superadmin deletes all logs before GET /data requests
		superadmin.logs().delete(before);

		// superadmin checks all test backend logs are deleted but ...
		log = superadmin.logs().get(10, true);

		assertEquals(4, log.total);
		assertEquals("DELETE", log.results.get(0).method);
		assertEquals("/1/log", log.results.get(0).path);
		assertEquals("GET", log.results.get(1).method);
		assertEquals("/1/log", log.results.get(1).path);
		assertEquals("GET", log.results.get(2).method);
		assertEquals("/1/data", log.results.get(2).path);
		assertEquals("GET", log.results.get(3).method);
		assertEquals("/1/data", log.results.get(3).path);

		before = log.results.get(1).receivedAt;

		// superdog deletes all logs before GET /log requests
		superdog().logs().delete(before);

		// superadmin checks all test backend logs are deleted but ...
		log = superdog().logs().get(10, true);

		assertEquals(4, log.total);
		assertEquals("DELETE", log.results.get(0).method);
		assertEquals("/1/log", log.results.get(0).path);
		assertEquals("GET", log.results.get(1).method);
		assertEquals("/1/log", log.results.get(1).path);
		assertEquals("DELETE", log.results.get(2).method);
		assertEquals("/1/log", log.results.get(2).path);
		assertEquals("GET", log.results.get(3).method);
		assertEquals("/1/log", log.results.get(3).path);
	}

	@Test
	public void searchInLogs() {

		// prepare
		prepareTest();

		// creates test backend and user
		SpaceDog guest = SpaceDog.dog();
		SpaceDog superadmin = clearServer();
		guest.get("/1/data").go(200);
		guest.get("/1/data/user").go(403);
		SpaceDog vince = createTempDog(superadmin, "vince").login();
		vince.credentials().get(vince.id());

		// superadmin search for test backend logs with status 400 and higher
		ESSearchSourceBuilder query = ESSearchSourceBuilder.searchSource()//
				.query(ESQueryBuilders.rangeQuery("status").gte(400))//
				.sort(ESSortBuilders.fieldSort("receivedAt").order(ESSortOrder.DESC));

		LogSearchResults results = superadmin.logs().search(query, true);

		assertEquals(1, results.results.size());
		assertEquals("/1/data/user", results.results.get(0).path);
		assertEquals(403, results.results.get(0).status);

		// superadmin search for backend logs
		// with credentials roles superadmin or user
		query = ESSearchSourceBuilder.searchSource()//
				.query(ESQueryBuilders.termsQuery("credentials.roles", "superadmin", "user"))//
				.sort(ESSortBuilders.fieldSort("receivedAt").order(ESSortOrder.DESC));

		results = superadmin.logs().search(query, true);
		assertEquals(4, results.results.size());
		assertEquals("/1/log/_search", results.results.get(0).path);
		assertEquals("/1/credentials/" + vince.id(), results.results.get(1).path);
		assertEquals("/1/login", results.results.get(2).path);
		assertEquals("/1/credentials", results.results.get(3).path);

		// superadmin search for test backend log to only get user and lower logs
		query = ESSearchSourceBuilder.searchSource()//
				.query(ESQueryBuilders.termsQuery("credentials.roles", "user"))//
				.sort(ESSortBuilders.fieldSort("receivedAt").order(ESSortOrder.DESC));

		results = superadmin.logs().search(query, true);
		assertEquals(2, results.results.size());
		assertEquals("/1/credentials/" + vince.id(), results.results.get(0).path);
		assertEquals("/1/login", results.results.get(1).path);

		// superadmin search for test backend log to only get guest logs
		query = ESSearchSourceBuilder.searchSource()//
				.query(ESQueryBuilders.boolQuery().mustNot(//
						ESQueryBuilders.existsQuery("credentials.roles")))//
				.sort(ESSortBuilders.fieldSort("receivedAt").order(ESSortOrder.DESC));

		results = superadmin.logs().search(query, true);
		assertEquals(2, results.results.size());
		assertEquals("/1/data/user", results.results.get(0).path);
		assertEquals("/1/data", results.results.get(1).path);

		// superadmin gets all test backend logs
		query = ESSearchSourceBuilder.searchSource()//
				.query(ESQueryBuilders.matchAllQuery())//
				.sort(ESSortBuilders.fieldSort("receivedAt").order(ESSortOrder.DESC))//
				.size(20);

		results = superadmin.logs().search(query, true);
		assertEquals(11, results.results.size());
		assertEquals("/1/log/_search", results.results.get(0).path);
		assertEquals("/1/log/_search", results.results.get(1).path);
		assertEquals("/1/log/_search", results.results.get(2).path);
		assertEquals("/1/log/_search", results.results.get(3).path);
		assertEquals("/1/credentials/" + vince.id(), results.results.get(4).path);
		assertEquals("/1/login", results.results.get(5).path);
		assertEquals("/1/credentials", results.results.get(6).path);
		assertEquals("/1/data/user", results.results.get(7).path);
		assertEquals("/1/data", results.results.get(8).path);
		assertEquals("/1/credentials", results.results.get(9).path);
		assertEquals("/1/admin/_clear", results.results.get(10).path);

		// superadmin gets logs with q = ...
		assertEquals(2, superadmin.logs().get("vince", 10, false).total);
		assertEquals(2, superadmin.logs().get("vin*", 10, true).total);
		assertEquals(1, superadmin.logs().get("403", 10, false).total);
		assertEquals(2, superadmin.logs().get("/1/credentials", 10, false).total);
	}

	@Test
	public void pingRequestsAreNotLogged() {

		// prepare
		prepareTest();
		SpaceDog guest = SpaceDog.dog();
		SpaceDog superadmin = clearServer();

		// guest (or load balancer) pings his backend
		guest.get("").go(200);
		guest.get("/").go(200);

		// check those pings are not logged
		LogSearchResults results = superadmin.logs().get(10, true);
		assertEquals(2, results.total);
		assertEquals("/1/credentials", results.results.get(0).path);
		assertEquals("/1/admin/_clear", results.results.get(1).path);
	}

	@Test
	public void webRootRequestsAreLogged() {

		// prepare
		prepareTest();
		SpaceDog wwwGuest = SpaceDog.dog(SpaceEnv.env().wwwBackend());
		SpaceDog superadmin = clearServer();

		// wwwGuest fails to loads root page since doesn't exist
		wwwGuest.get("").go(404);
		wwwGuest.get("/").go(404);

		// check those web requests are logged
		LogSearchResults results = superadmin.logs().get(10, true);
		assertEquals(4, results.total);
		assertEquals("/", results.results.get(0).path);
		assertEquals("/", results.results.get(1).path);
		assertEquals("/1/credentials", results.results.get(2).path);
		assertEquals("/1/admin/_clear", results.results.get(3).path);
	}

	@Test
	public void doAFewThingsAndGetTheLogs() {

		// prepare
		prepareTest();
		SpaceDog superadmin = clearServer();

		// superadmin sets schemas
		superadmin.schemas().set(Message.schema());

		// superadmin sets data acls
		DataSettings settings = new DataSettings();
		settings.acl().put(Message.TYPE, Roles.user, Permission.create, Permission.search);
		superadmin.settings().save(settings);

		// create a user in test backend
		SpaceDog user = createTempDog(superadmin, "user").login();

		// create message in test backend
		ObjectNodeWrap message = user.data()//
				.save("message", Json.object("text", "What's up boys?"));

		// find message by id in test backend
		user.data().fetch(message);

		// find all messages in test backend
		user.data().getAllRequest().type("message").go();

		// get all test backend logs
		// the delete request is not part of the logs
		// since log starts with the backend creation
		List<LogItem> results = superadmin.logs().get(10, true).results;
		assertEquals(9, results.size());
		assertEquals("GET", results.get(0).method);
		assertEquals("/1/data/message", results.get(0).path);
		assertEquals("GET", results.get(1).method);
		assertEquals("/1/data/message/" + message.id(), results.get(1).path);
		assertEquals("POST", results.get(2).method);
		assertEquals("/1/data/message", results.get(2).path);
		assertEquals("GET", results.get(3).method);
		assertEquals("/1/login", results.get(3).path);
		assertEquals("POST", results.get(4).method);
		assertEquals("/1/credentials", results.get(4).path);
		assertEquals("PUT", results.get(5).method);
		assertEquals("/1/settings/data", results.get(5).path);
		assertEquals("PUT", results.get(6).method);
		assertEquals("/1/schemas/message", results.get(6).path);
		assertEquals("POST", results.get(7).method);
		assertEquals("/1/credentials", results.get(7).path);
		assertEquals("POST", results.get(8).method);
		assertEquals("/1/admin/_clear", results.get(8).path);
	}

	@Test
	public void checkPasswordsAreNotLogged() {

		prepareTest();
		SpaceDog superadmin = clearServer();
		SpaceDog fred = createTempDog(superadmin, "fred");
		fred.login();

		// fred has forgotten his password
		fred.password(null);
		fred.accessToken(null);

		// fred ask an admin to reset his password
		String passwordResetCode = superadmin.credentials().resetPassword(fred.id());

		// fred sets his new password with reset code received by email
		fred.credentials().setMyPasswordWithCode("hi fred 2", passwordResetCode);
		fred.password("hi fred 2");

		// fred changes his password
		fred.credentials().setMyPassword("hi fred 2", "hi fred 3");

		// superadmin checks backend logs
		List<LogItem> results = superadmin.logs().get(10, true).results;
		assertEquals(7, results.size());
		assertEquals("POST", results.get(0).method);
		assertEquals("/1/credentials/me/_set_password", results.get(0).path);
		assertEquals("fred", results.get(0).credentials.username());
		assertEquals("********", results.get(0).payload.get(PASSWORD_FIELD).asText());

		assertEquals("POST", results.get(1).method);
		assertEquals("/1/credentials/" + fred.id() + "/_set_password", results.get(1).path);
		assertEquals("********", results.get(1).payload.get(PASSWORD_FIELD).asText());
		assertEquals(passwordResetCode, //
				results.get(1).payload.get(PASSWORD_RESET_CODE_FIELD).asText());

		assertEquals("POST", results.get(2).method);
		assertEquals("/1/credentials/" + fred.id() + "/_reset_password", results.get(2).path);
		assertEquals(passwordResetCode, //
				results.get(2).response.get(PASSWORD_RESET_CODE_FIELD).asText());

		assertEquals("GET", results.get(3).method);
		assertEquals("/1/login", results.get(3).path);

		assertEquals("POST", results.get(4).method);
		assertEquals("/1/credentials", results.get(4).path);
		assertEquals("fred", results.get(4).payload.get(USERNAME_FIELD).asText());
		assertEquals("********", results.get(4).payload.get(PASSWORD_FIELD).asText());

		assertEquals("POST", results.get(5).method);
		assertEquals("/1/credentials", results.get(5).path);
		assertEquals("superadmin", results.get(5).payload.get(USERNAME_FIELD).asText());
		assertEquals("********", results.get(5).payload.get(PASSWORD_FIELD).asText());

		assertEquals("POST", results.get(6).method);
		assertEquals("/1/admin/_clear", results.get(6).path);
	}

	@Test
	public void testSpecialCases() {

		// prepare
		prepareTest();
		SpaceDog superadmin = clearServer();

		// fails because invalid body
		superadmin.put("/1/schemas/toto").body("XXX").go(400);

		// but logs the failed request without the json content
		LogItem logItem = superadmin.logs().get(1, true).results.get(0);
		assertEquals("PUT", logItem.method);
		assertEquals("/1/schemas/toto", logItem.path);
		assertEquals(400, logItem.status);
		assertNull(logItem.payload);
		assertNull(logItem.parameters);

		// check that log response results are not logged
		superadmin.logs().get(10);
		logItem = superadmin.logs().get(1, true).results.get(0);
		assertEquals("GET", logItem.method);
		assertEquals("/1/log", logItem.path);
		assertNull(logItem.response.get("results"));

		// Headers are logged if not empty
		// and 'Authorization' header is not logged
		superadmin.get("/1/log")//
				.setHeader("x-empty", "")//
				.setHeader("x-blank", " ")//
				.setHeader("x-color", "YELLOW")//
				.addHeader("x-color-list", "RED")//
				.addHeader("x-color-list", "BLUE")//
				.addHeader("x-color-list", "GREEN")//
				.go(200);

		logItem = superadmin.logs().get(1, true).results.get(0);
		assertTrue(logItem.getHeader(SpaceHeaders.AUTHORIZATION).isEmpty());
		assertTrue(logItem.getHeader("x-empty").isEmpty());
		assertTrue(logItem.getHeader("x-blank").isEmpty());
		assertTrue(logItem.getHeader("x-color").contains("YELLOW"));
		assertTrue(logItem.getHeader("x-color-list").contains("RED"));
		assertTrue(logItem.getHeader("x-color-list").contains("BLUE"));
		assertTrue(logItem.getHeader("x-color-list").contains("GREEN"));
	}
}
