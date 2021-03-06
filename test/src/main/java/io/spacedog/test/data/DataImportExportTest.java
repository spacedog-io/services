/**
 * © David Attias 2015
 */
package io.spacedog.test.data;

import java.util.List;

import org.junit.Test;

import io.spacedog.client.SpaceDog;
import io.spacedog.client.data.DataWrap;
import io.spacedog.client.elastic.ESQueryBuilder;
import io.spacedog.client.elastic.ESQueryBuilders;
import io.spacedog.client.elastic.ESSearchSourceBuilder;
import io.spacedog.client.schema.Schema;
import io.spacedog.test.Message;
import io.spacedog.test.SpaceTest;

public class DataImportExportTest extends SpaceTest {

	@Test
	public void fullExportPreservingIds() {

		// prepare
		prepareTest();
		SpaceDog superadmin = clearServer();
		SpaceDog fred = createTempDog(superadmin, "fred");

		// superadmin creates message1 schema
		superadmin.schemas().set(messageSchema("message1"));

		// superadmin creates messages into message1 schema
		createMessages(superadmin);

		// superadmin exports message1 data objects
		String export = superadmin.data().prepareExport("message1")//
				.withRefresh(true).go().asString();

		// superadmin fails to export with invalid search query
		assertHttpError(400, () -> superadmin.data()//
				.prepareExport("message1").withQuery("XXX").go());

		// fred not authorized to export data
		// since only superadmins are allowed
		assertHttpError(403, () -> fred.data().prepareExport("message1").go());

		// superadmin creates message2 index
		superadmin.schemas().set(messageSchema("message2"));

		// superadmin imports previously exported messages
		// into message2 schema with id preservation
		superadmin.data().prepareImport("message2")//
				.withPreserveIds(true).go(export);

		// superadmin fails to import an incorrect data set
		assertHttpError(400, () -> superadmin.data()//
				.prepareImport("message2").go("XXX"));

		// fred not authorized to import data
		// since only superadmins are allowed
		assertHttpError(403, () -> fred.data()//
				.prepareImport("message2").go(""));

		// superamdin gets all message1 messages
		List<DataWrap<Message>> messages1 = superadmin.data().prepareSearch()//
				.source(ESSearchSourceBuilder.searchSource().toString())//
				.type("message1").refresh(true)//
				.go(Message.class).objects;

		// superamdin gets all message2 messages
		List<DataWrap<Message>> messages2 = superadmin.data().prepareSearch()//
				.source(ESSearchSourceBuilder.searchSource().toString())//
				.type("message2").refresh(true)//
				.go(Message.class).objects;

		// check message1 and message2 contains the same messages
		assertEquals(messages1.size(), messages2.size());
		for (DataWrap<Message> message : messages1)
			assertContains(messages2, message, true);

	}

	@Test
	public void partialExportWithoutIdPreservation() {

		// prepare
		prepareTest();
		SpaceDog superadmin = clearServer();

		// superadmin creates message1 schema
		superadmin.schemas().set(messageSchema("message1"));

		// superadmin creates messages into message1 schema
		createMessages(superadmin);

		// superadmin exports message1 data objects
		// starting with an 'h'
		ESQueryBuilder query = ESQueryBuilders.matchPhrasePrefixQuery("text", "h");
		String export = superadmin.data().prepareExport("message1")//
				.withQuery(query.toString()).withRefresh(true).go().asString();

		// superadmin creates message2 index
		superadmin.schemas().set(messageSchema("message2"));

		// superadmin imports previously exported messages
		// into message2 schema without id preservation
		superadmin.data().prepareImport("message2").go(export);

		// superadmin gets message1 messages but 'ola'
		ESSearchSourceBuilder source = ESSearchSourceBuilder.searchSource()//
				.query(query);
		List<DataWrap<Message>> messages1 = superadmin.data().prepareSearch()//
				.source(source.toString()).type("message1").refresh(true)//
				.go(Message.class).objects;

		// superadmin gets all message2 messages
		List<DataWrap<Message>> messages2 = superadmin.data().prepareSearch()//
				.source(ESSearchSourceBuilder.searchSource().toString())//
				.type("message2").refresh(true)//
				.go(Message.class).objects;

		// check message1 and message2 contains the same messages
		// minus 'ola' message and without id preservation
		assertEquals(4, messages2.size());
		for (DataWrap<Message> message : messages1)
			assertContains(messages2, message, false);
	}

	private void createMessages(SpaceDog superadmin) {
		superadmin.data().save("message1", new Message("hi"));
		superadmin.data().save("message1", new Message("hello"));
		superadmin.data().save("message1", new Message("ola"));
		superadmin.data().save("message1", new Message("hi \"friends\""));
		superadmin.data().save("message1", new Message("hi\nhello\nola"));
	}

	private void assertContains(List<DataWrap<Message>> messages, DataWrap<Message> message1, boolean compareId) {
		for (DataWrap<Message> message2 : messages) {
			if (isEqual(message1, message2, compareId))
				return;
		}
		fail(String.format("message [%s] not found", message1.source().text));
	}

	private boolean isEqual(DataWrap<Message> message1, DataWrap<Message> message2, boolean compareId) {
		return message1.source().text.equals(message2.source().text) //
				&& message1.source().createdAt().equals(message2.source().createdAt()) //
				&& message1.source().updatedAt().equals(message2.source().updatedAt()) //
				&& message1.source().owner().equals(message2.source().owner()) //
				&& message1.source().group().equals(message2.source().group()) //
				&& (compareId == false || message1.id().equals(message2.id()));
	}

	public static Schema messageSchema(String name) {
		return Schema.builder(name).text("text").build();
	}

}