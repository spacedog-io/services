package io.spacedog.utils;

import org.junit.Assert;
import org.junit.Test;

import com.fasterxml.jackson.databind.node.ObjectNode;

public class JsonGeneratorTest extends Assert {

	@Test
	public void test() {

		ObjectNode schema = SchemaBuilder3.builder("person")//
				.string("name")//
				.object("address")//
				.string("street")//
				.string("city")//
				.string("name")//
				.close()//
				.build();

		JsonGenerator generator = new JsonGenerator();
		generator.reg("name", "vince", "william");
		generator.reg("address.name", "vince", "william");
		ObjectNode person = generator.gen(schema);
		Utils.info(Json.toPrettyString(person));
	}
}
