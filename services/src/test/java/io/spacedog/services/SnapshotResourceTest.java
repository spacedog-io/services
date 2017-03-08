package io.spacedog.services;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import com.fasterxml.jackson.databind.node.ObjectNode;

import io.spacedog.rest.SpaceRequest;
import io.spacedog.rest.SpaceResponse;
import io.spacedog.rest.SpaceTest;
import io.spacedog.sdk.SpaceDog;

public class SnapshotResourceTest extends SpaceTest {

	@Test
	public void snapshotAndRestoreMultipleTimes() throws InterruptedException, UnknownHostException {

		// prepare
		prepareTest();
		SpaceDog aaaa = SpaceDog.backend("aaaa").username("aaaa").password("hi aaaa");
		SpaceDog bbbb = SpaceDog.backend("bbbb").username("bbbb").password("hi bbbb");
		SpaceDog cccc = SpaceDog.backend("cccc").username("cccc").password("hi cccc");

		aaaa.backend().delete();
		bbbb.backend().delete();
		cccc.backend().delete();

		// superdog creates snapshotall user in root backend
		SpaceDog superdog = superdog();
		SpaceDog snapshotAll = getOrSignUp(superdog, //
				"snapshotAll", "hi snapshotAll", "platform@spacedog.io");
		superdog.credentials().setRole(snapshotAll.id(), SnapshotResource.SNAPSHOT_ALL);
		snapshotAll.login("hi snapshotAll");

		// creates backend and credentials
		aaaa.signUpBackend();
		SpaceDog vince = signUp(aaaa, "vince", "hi vince");
		vince.get("/1/login").go(200);

		// deletes the current repository to force repo creation by this test
		// use full url to avoid delete by mistake any prod repo
		String repository = DateTime.now().withZone(DateTimeZone.UTC).toString("yyyy-ww");
		String ip = InetAddress.getLocalHost().getHostAddress();
		SpaceRequest.delete("http://" + ip + ":9200/_snapshot/{repoId}")//
				.routeParam("repoId", repository)//
				.go(200, 404);

		// first snapshot
		// returns 202 since wait for completion false
		// snapshot authorized because superdog credentials and root backend
		String firstSnapId = superdog.post("/1/snapshot")//
				.go(202)//
				.getString("id");

		// fails since snapshot is not yet completed
		// returns 400 if not yet restorable or if restoreInfo is null
		SpaceResponse response = superdog.post("/1/snapshot/latest/restore").go(400);

		// poll and wait for snapshot to complete
		do {

			response = superdog.get("/1/snapshot/latest").go(200)//
					.assertEquals(firstSnapId, "id");

			// let server work a bit
			Thread.sleep(100);

		} while (!response.jsonNode().get("state").asText().equalsIgnoreCase("SUCCESS"));

		ObjectNode firstSnap = response.objectNode();

		// gets snapshot by id
		superdog.get("/1/snapshot/" + firstSnapId).go(200)//
				.assertEquals(firstSnap);

		// creates another backend and credentials
		bbbb.signUpBackend();
		SpaceDog fred = signUp(bbbb, "fred", "hi fred");
		fred.get("/1/login").go(200);

		// second snapshot
		// returns 201 since wait for completion true (202 otherwise)
		// Authorized since superdog even with non root backend
		response = SpaceRequest.post("/1/snapshot")//
				.superdogAuth("test")//
				.queryParam("waitForCompletion", "true")//
				.go(201)//
				.assertEquals(repository, "snapshot.repository")//
				.assertEquals("SUCCESS", "snapshot.state")//
				.assertEquals("all", "snapshot.type");

		ObjectNode secondSnap = (ObjectNode) response.get("snapshot");
		String secondSnapId = response.getString("id");

		superdog.get("/1/snapshot").go(200)//
				.assertEquals(secondSnap, "results.0")//
				.assertEquals(firstSnap, "results.1");

		// create another account and add a credentials
		cccc.signUpBackend();
		SpaceDog nath = signUp(cccc, "nath", "hi nath");
		nath.get("/1/login").go(200);

		// third snapshot
		// returns 201 since wait for completion true (202 otherwise)
		// Authorized since snapshotUser is a root backend user
		// and has the snapshotall role
		response = snapshotAll.post("/1/snapshot")//
				.queryParam("waitForCompletion", "true")//
				.go(201)//
				.assertEquals(repository, "snapshot.repository")//
				.assertEquals("SUCCESS", "snapshot.state")//
				.assertEquals("all", "snapshot.type");

		ObjectNode thirdSnap = (ObjectNode) response.get("snapshot");
		String thirdSnapId = response.getString("id");

		// snapshotAll can get snapshot info
		snapshotAll.get("/1/snapshot").go(200)//
				.assertEquals(thirdSnap, "results.0")//
				.assertEquals(secondSnap, "results.1")//
				.assertEquals(firstSnap, "results.2");

		// restore to oldest snapshot
		superdog.post("/1/snapshot/{id}/restore")//
				.routeParam("id", firstSnapId)//
				.queryParam("waitForCompletion", "true")//
				.go(200);

		// check only account aaaa and credentials vince are present
		vince.get("/1/login").go(200);
		fred.get("/1/login").go(401);
		nath.get("/1/login").go(401);

		// restore to second (middle) snapshot
		superdog.post("/1/snapshot/{id}/restore")//
				.routeParam("id", secondSnapId)//
				.queryParam("waitForCompletion", "true")//
				.go(200);

		// check only aaaa and bbbb accounts are present
		// check only vince and fred credentials are present
		vince.get("/1/login").go(200);
		fred.get("/1/login").go(200);
		nath.get("/1/login").go(401);

		// restore to latest (third) snapshot
		superdog.post("/1/snapshot/{id}/restore")//
				.routeParam("id", thirdSnapId)//
				.queryParam("waitForCompletion", "true")//
				.go(200);

		// check all accounts and credentials are present
		vince.get("/1/login").go(200);
		fred.get("/1/login").go(200);
		nath.get("/1/login").go(200);

		// delete all accounts and internal indices
		aaaa.delete("/1/backend").go(200);
		bbbb.delete("/1/backend").go(200);
		cccc.delete("/1/backend").go(200);

		// check all accounts are deleted
		vince.get("/1/login").go(401);
		fred.get("/1/login").go(401);
		nath.get("/1/login").go(401);

		// restore to latest (third) snapshot
		superdog.post("/1/snapshot/latest/restore")//
				.queryParam("waitForCompletion", "true")//
				.go(200);

		// check all accounts and credentials are back
		vince.get("/1/login").go(200);
		fred.get("/1/login").go(200);
		nath.get("/1/login").go(200);

		// check that restore to an invalid snapshot id fails
		superdog.post("/1/snapshot/xxxx/restore")//
				.queryParam("waitForCompletion", "true")//
				.go(404);

		// check account administrator can not restore the platform
		aaaa.post("/1/snapshot/latest/restore").go(403);

		// clean up
		aaaa.delete("/1/backend").go(200);
		bbbb.delete("/1/backend").go(200);
		cccc.delete("/1/backend").go(200);

		// check snapshot list did not change since last snapshot
		superdog.get("/1/snapshot").go(200)//
				.assertEquals(thirdSnap, "results.0")//
				.assertEquals(secondSnap, "results.1")//
				.assertEquals(firstSnap, "results.2");
	}
}
