/**
 * © David Attias 2015
 */
package io.spacedog.examples;

import org.joda.time.DateTime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.spacedog.client.SpaceClient;
import io.spacedog.client.SpaceRequest;
import io.spacedog.client.SpaceResponse;
import io.spacedog.utils.Json;
import io.spacedog.utils.Schema;

public class Birdee extends SpaceClient {

	private static final String BACKEND_ID = "birdee-dev";
	private static final String ADMIN_PASSWORD = "hi birdee";
	private static final String ADMIN_USERNAME = "birdee";

	private static Backend adminAccount;

	private static User fred;
	private static User maelle;
	private static User vincent;

	static Schema buildDeviceSchema() {
		return Schema.builder("device")//
				.string("id")//
				.string("userId")//
				.build();
	}

	static Schema buildStatusSchema() {
		return Schema.builder("status") //
				.string("newsId")//
				.string("userId")//
				// I need portfolio ids for users to filter
				.string("portfolioId").array()//
				.string("url")//
				.enumm("type")//
				.bool("read")//
				.bool("seen")//
				.bool("favorite")//
				.bool("processed")//
				.build();
	}

	static Schema buildNewsAllSchema() {
		return Schema.builder("news") //
				.string("newsId")//
				// portfolio not required
				.string("portfolioId").array()//
				.enumm("type")//
				.string("url")//
				.bool("processed")//
				.build();
	}

	public JsonNode getNews(String userId) throws Exception {

		String query = "{'from':0, 'size':10,"//
				+ "'sort':[{'meta.updatedAt':{'order':'asc'}}],"//
				+ "'query':{'match_all':{}}"//
				+ "}";

		JsonNode news = SpaceRequest.post("/1/search/news").refresh()//
				.backend(adminAccount).body(query).go(200).jsonNode();

		query = "{'from':0, 'size':10,"//
				+ "'sort':[{'meta.updatedAt':{'order':'asc'}}],"//
				+ "'query':{'filtered':{"//
				+ "'query':{'match_all':{}},"//
				+ "'filter':{'term':{'userId':'%s'}}"//
				+ "}";

		JsonNode statuses = SpaceRequest.post("/1/search/status").refresh()//
				.backend(adminAccount)//
				.body(String.format(query, userId))//
				.go(200)//
				.jsonNode();

		return news;
	}

	public void createStatus(String userId, String newsId, String url, //
			boolean read, boolean seen, boolean favorite) throws Exception {

		JsonNode status = Json.objectBuilder()//
				.put("userId", userId)//
				.put("newsId", newsId)//
				.put("url", url)//
				.put("read", read)//
				.put("seen", seen)//
				.put("favorite", favorite)//
				.put("processed", favorite)//
				.build();

		SpaceRequest.post("/1/data/status")//
				.backend(adminAccount).body(status).go(201);
	}

	public void seen(String statusId) throws Exception {
		SpaceRequest.put("/1/data/status/" + statusId)//
				.backend(adminAccount)//
				.body("{'seen':true}").go(200);
	}

	public void seen(String userId, String newsId, String url) throws Exception {
		createStatus(userId, newsId, url, false, true, false);
	}

	public void read(String statusId) throws Exception {
		SpaceRequest.put("/1/data/status/" + statusId)//
				.backend(adminAccount)//
				.body("{'read':true}").go(200);
	}

	public void read(String userId, String newsId, String url) throws Exception {
		createStatus(userId, newsId, url, true, false, false);
	}

	public void favorite(String statusId) throws Exception {
		SpaceRequest.put("/1/data/status/" + statusId)//
				.backend(adminAccount)//
				.body("{'favorite':true}").go(200);
	}

	public void newNews(ObjectNode[] articles) throws Exception {

		for (ObjectNode article : articles) {

			article.set("pushed", BooleanNode.FALSE);

			SpaceRequest.post("/1/data/news")//
					.backend(adminAccount)//
					.body(article)//
					.go(201);
		}
	}

	public void newNews(ObjectNode[] articles, String[] userIds) throws Exception {

		for (String userId : userIds) {

			for (ObjectNode article : articles) {

				article.set("pushed", BooleanNode.FALSE);

				createStatus(userId, //
						article.get("id").asText(), //
						article.get("url").asText(), //
						false, false, false);
			}
		}
	}

	public void registerDevice(String userId, String token) throws Exception {

		// device schema must have been customized to accept userId field

		JsonNode device = Json.objectBuilder()//
				.put("protocol", "application")//
				.put("endpoint", token)//
				.put("appName", "birdee")//
				.put("userId", userId)//
				.build();

		SpaceRequest.post("/1/device")//
				.backend(adminAccount)//
				.body(device)//
				.go(201);
	}

	public void cmsJob() throws Exception {

		// récupération de tous les articles du CMS
		ObjectNode[] articles = null;

		for (ObjectNode article : articles) {

			SpaceResponse response = SpaceRequest.get("/1/data/news/" + article.get("id").asText())//
					.backend(adminAccount)//
					.body(article)//
					.go(200, 404);

			if (response.httpResponse().getStatus() == 404) {

				// convert article to news
				ObjectNode news = null;

				SpaceRequest.post("/1/data/news")//
						.backend(adminAccount)//
						.body(news)//
						.go(201);
			}

			if (response.httpResponse().getStatus() == 200) {

				ObjectNode news = response.objectNode();

				// check if changed after last update in news
				if (DateTime.parse(news.get("meta").get("updatedAt").asText()).isBefore(//
						DateTime.parse(article.get("updatedAt").asText()))) {

					// update news from article
					// news.set(...)

					SpaceRequest.put("/1/data/news/" + article.get("id").asText())//
							.backend(adminAccount)//
							.body(news)//
							.go(200);
				}

			}

		}

	}

	public void pushJob() throws Exception {

		String query = "{'from':0, 'size':100,"//
				+ "'query':{'filtered':{"//
				+ "'query':{'match_all':{}},"//
				+ "'filter':{'term':{'pushed':false}}"//
				+ "}";

		JsonNode news = SpaceRequest.post("/1/search/news").refresh()//
				.backend(adminAccount).body(query).go(200).jsonNode();

		JsonNode statuses = SpaceRequest.post("/1/search/status").refresh()//
				.backend(adminAccount)//
				.body(query)//
				.go(200)//
				.jsonNode();

		// for all non pushed status or news, decide what to do
		// ...

		String message = null;

		// if message to be pushed is for all

		SpaceRequest.post("/1/topic/all/push")//
				.backend(adminAccount)//
				.body(Json.objectBuilder().put("message", message).toString())//
				.go(200);

		// if message to be pushed is for some users
		// get users from status object
		// or get users from portfolio via PMS
		// then get devices from userIds

		String userId = null;

		query = "{'from':0, 'size':1,"//
				+ "'query':{'filtered':{"//
				+ "'query':{'match_all':{}},"//
				+ "'filter':{'term':{'userId':'%s'}}"//
				+ "}";

		JsonNode device = SpaceRequest.post("/1/search/device").refresh()//
				.backend(adminAccount).body(String.format(query, userId)).go(200).jsonNode();

		// push to device

		SpaceRequest.post("/1/device/{id}/push")//
				.backend(adminAccount)//
				.routeParam("id", device.get("id").asText())//
				.body(Json.objectBuilder().put("message", message).toString())//
				.go(200);

		// set pushed to true for all processed object

		String statusId = null;

		SpaceRequest.put("/1/data/status/" + statusId)//
				.backend(adminAccount)//
				.body("{'processed':true}").go(200);

		String newsId = null;

		SpaceRequest.put("/1/data/news/" + newsId)//
				.backend(adminAccount)//
				.body("{'processed':true}").go(200);
	}

}
