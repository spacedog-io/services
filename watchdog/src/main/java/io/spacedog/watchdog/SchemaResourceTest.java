/**
 * © David Attias 2015
 */
package io.spacedog.watchdog;

import org.junit.Assert;
import org.junit.Test;

import com.fasterxml.jackson.databind.node.ObjectNode;

import io.spacedog.client.SpaceDogHelper;
import io.spacedog.client.SpaceDogHelper.Backend;
import io.spacedog.client.SpaceDogHelper.User;
import io.spacedog.client.SpaceRequest;
import io.spacedog.utils.Json;
import io.spacedog.utils.SchemaBuilder;
import io.spacedog.watchdog.SpaceSuite.TestOften;

@TestOften
public class SchemaResourceTest extends Assert {

	@Test
	public void deletePutAndGetSchemas() throws Exception {

		SpaceDogHelper.prepareTest();
		Backend testBackend = SpaceDogHelper.resetTestBackend();
		SpaceDogHelper.initUserDefaultSchema(testBackend);

		// should succeed to create a user

		User bob = SpaceDogHelper.createUser(testBackend, "bob", "hi bob", "bob@dog.com");

		// should succeed to reset all schemas
		SpaceDogHelper.setSchema(buildCarSchema(), testBackend);
		SpaceDogHelper.setSchema(buildHomeSchema(), testBackend);
		SpaceDogHelper.setSchema(buildSaleSchema(), testBackend);

		// should succeed to get schemas with simple backend key credentials
		assertEquals(buildCarSchema(), //
				SpaceRequest.get("/1/schema/car").backend(testBackend).go(200).jsonNode());
		assertEquals(buildHomeSchema(), //
				SpaceRequest.get("/1/schema/home").backend(testBackend).go(200).jsonNode());
		assertEquals(buildSaleSchema(), //
				SpaceRequest.get("/1/schema/sale").backend(testBackend).go(200).jsonNode());

		// admin gets the default user schema
		ObjectNode userSchema = SpaceRequest.get("/1/schema/user")//
				.adminAuth(testBackend).go(200).objectNode();

		// should succeed to get all schemas with simple backend key credentials
		SpaceRequest.get("/1/schema").adminAuth(testBackend).go(200)//
				.assertEquals(Json.merger() //
						.merge(buildHomeSchema()) //
						.merge(buildCarSchema()) //
						.merge(buildSaleSchema()) //
						.merge(userSchema) //
						.get());

		// should fail to delete schema with simple backend key credentials
		SpaceRequest.delete("/1/schema/toto").backend(testBackend).go(401);

		// should fail to delete schema with simple user credentials
		SpaceRequest.delete("/1/schema/toto").userAuth(bob).go(401);

		// should fail to delete a non existent schema
		SpaceRequest.delete("/1/schema/toto").adminAuth(testBackend).go(404);

		// should succeed to delete a schema and all its documents
		SpaceRequest.delete("/1/schema/sale").adminAuth(testBackend).go(200);

		// should fail to create an invalid schema
		SpaceRequest.put("/1/schema/toto").adminAuth(testBackend).body("{\"toto\":{\"_type\":\"XXX\"}}").go(400);

		// should fail to change the car schema color property type
		ObjectNode json = buildCarSchema();
		json.with("car").with("color").put("_type", "date");
		SpaceRequest.put("/1/schema/car").adminAuth(testBackend).body(json).go(400);
	}

	private static ObjectNode buildHomeSchema() {
		return SchemaBuilder.builder("home") //
				.property("type", "enum").required().end() //
				.objectProperty("address").required() //
				.property("number", "integer").end() //
				.property("street", "text").required().end() //
				.property("city", "string").required().end() //
				.property("country", "string").required().end() // /
				.end() //
				.property("phone", "string").end() //
				.property("location", "geopoint").required().end() //
				.build();
	}

	public static ObjectNode buildSaleSchema() {
		return SchemaBuilder.builder("sale") //
				.property("number", "string").required().end() //
				.property("when", "timestamp").required().end() //
				.property("where", "geopoint").end() //
				.property("online", "boolean").required().end() //
				.property("deliveryDate", "date").required().end() //
				.property("deliveryTime", "time").required().end() //
				.objectProperty("items").array().required() //
				.property("ref", "string").required().end() //
				.property("description", "text").language("english").required().end() //
				.property("quantity", "integer").end() //
				// .property("price", "amount").required().end() //
				.property("type", "enum").required().end() //
				.end() //
				.build();
	}

	public static ObjectNode buildCarSchema() {
		return SchemaBuilder.builder("car") //
				.property("serialNumber", "string").required().end() //
				.property("buyDate", "date").required().end() //
				.property("buyTime", "time").required().end() //
				.property("buyTimestamp", "timestamp").required().end() //
				.property("color", "enum").required().end() //
				.property("techChecked", "boolean").required().end() //
				.objectProperty("model").required() //
				.property("description", "text").language("french").required().end() //
				.property("fiscalPower", "integer").required().end() //
				.property("size", "float").required().end() //
				.end() //
				.property("location", "geopoint").required().end() //
				.build();
	}

}
