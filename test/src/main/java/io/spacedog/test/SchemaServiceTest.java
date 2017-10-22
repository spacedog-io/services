/**
 * © David Attias 2015
 */
package io.spacedog.test;

import org.junit.Test;

import com.google.common.collect.Sets;

import io.spacedog.client.SpaceDog;
import io.spacedog.http.SpaceTest;
import io.spacedog.model.Schema;
import io.spacedog.utils.SchemaBuilder;

public class SchemaServiceTest extends SpaceTest {

	@Test
	public void deletePutAndGetSchemas() {

		// prepare
		prepareTest();
		SpaceDog superadmin = resetTestBackend();
		SpaceDog guest = SpaceDog.backend(superadmin);
		SpaceDog bob = createTempDog(superadmin, "bob");

		// anonymous gets all backend schema
		// if no schema returns empty object
		guest.schema().getAll().isEmpty();

		// admin creates car, home and sale schemas
		Schema carSchema = buildCarSchema().build();
		superadmin.schema().set(carSchema);
		Schema homeSchema = buildHomeSchema().build();
		superadmin.schema().set(homeSchema);
		Schema saleSchema = buildSaleSchema().build();
		superadmin.schema().set(saleSchema);

		// anonymous gets car, home and sale schemas
		assertEquals(carSchema, guest.schema().get(carSchema.name()));
		assertEquals(homeSchema, guest.schema().get(homeSchema.name()));
		assertEquals(saleSchema, guest.schema().get(saleSchema.name()));

		// anonymous gets all schemas
		assertEquals(Sets.newHashSet(carSchema, homeSchema, saleSchema), //
				guest.schema().getAll());

		// anonymous is not allowed to delete schema
		guest.delete("/1/schema/sale").go(403);

		// user is not allowed to delete schema
		bob.delete("/1/schema/sale").go(403);

		// admin fails to delete a non existing schema
		superadmin.delete("/1/schema/XXX").go(404);

		// admin deletes a schema and all its objects
		superadmin.delete(saleSchema.name());

		// admin fails to create an invalid schema
		superadmin.put("/1/schema/toto")//
				.bodyString("{\"toto\":{\"_type\":\"XXX\"}}").go(400);

		// admin fails to update car schema color property type
		carSchema.node().with("car").with("color").put("_type", "date");
		superadmin.put("/1/schema/car").bodySchema(carSchema).go(400);

		// fails to remove the car schema color property
		// json = buildCarSchema();
		// json.with("car").remove("color");
		// SpaceRequest.put("/1/schema/car").adminAuth(testBackend).body(json).go(400);
	}

	private static SchemaBuilder buildHomeSchema() {
		return Schema.builder("home") //
				.enumm("type")//
				.string("phone")//
				.geopoint("location")//

				.object("address") //
				.integer("number")//
				.text("street")//
				.string("city")//
				.string("country")//
				.close();

	}

	public static SchemaBuilder buildSaleSchema() {
		return Schema.builder("sale") //
				.string("number") //
				.timestamp("when") //
				.geopoint("where") //
				.bool("online")//
				.date("deliveryDate") //
				.time("deliveryTime")//

				.object("items").array() //
				.string("ref")//
				.text("description").english()//
				.integer("quantity")//
				.enumm("type")//
				.close();
	}

	public static SchemaBuilder buildCarSchema() {
		return Schema.builder("car") //
				.string("serialNumber")//
				.date("buyDate")//
				.time("buyTime")//
				.timestamp("buyTimestamp") //
				.enumm("color")//
				.bool("techChecked") //
				.geopoint("location") //

				.object("model")//
				.text("description").french()//
				.integer("fiscalPower")//
				.floatt("size")//
				.close();
	}

	@Test
	public void saveMetaDataInSchema() {

		// prepare
		prepareTest();
		SpaceDog superadmin = resetTestBackend();

		Schema schemaClient = Schema.builder("home") //
				.extra("global-scope", true)//
				.enumm("type").required().extra("global-scope", false)//
				.object("address").required().extra("global-scope", false)//
				.text("street").required().extra("global-scope", false) //
				.string("owner").required().refType("user")//
				.build();

		superadmin.schema().set(schemaClient);

		// superadmin gets the schema from backend
		// and check it is unchanged
		Schema schemaServer = superadmin.schema().get(schemaClient.name());
		assertEquals(schemaClient, schemaServer);
	}

	@Test
	public void schemaNameSettingsIsReserved() {

		// prepare
		prepareTest();
		SpaceDog superadmin = resetTestBackend();
		SpaceDog guest = SpaceDog.backend(superadmin);

		// settings is a reserved schema name
		guest.get("/1/schema/settings").go(400);
		superadmin.put("/1/schema/settings").go(400);
	}

}
