/**
 * © David Attias 2015
 */
package io.spacedog.server;

import java.util.Map;
import java.util.Optional;

import org.elasticsearch.action.index.IndexResponse;

import com.fasterxml.jackson.databind.node.ObjectNode;

import io.spacedog.http.SpaceRequest;
import io.spacedog.http.SpaceResponse;
import io.spacedog.model.SmsBasicRequest;
import io.spacedog.model.SmsRequest;
import io.spacedog.model.SmsSettings;
import io.spacedog.model.SmsSettings.TwilioSettings;
import io.spacedog.model.SmsTemplate;
import io.spacedog.model.SmsTemplateRequest;
import io.spacedog.utils.Exceptions;
import io.spacedog.utils.Json;
import io.spacedog.utils.NotFoundException;
import net.codestory.http.Context;
import net.codestory.http.annotations.Delete;
import net.codestory.http.annotations.Get;
import net.codestory.http.annotations.Post;
import net.codestory.http.annotations.Put;
import net.codestory.http.payload.Payload;

public class SmsService extends SpaceService {

	//
	// Routes
	//

	@Post("/1/sms")
	@Post("/1/sms/")
	public Payload postSms(SmsRequest request, Context context) {

		if (request instanceof SmsBasicRequest) {
			SpaceContext.credentials().checkRoles(smsSettings().authorizedRoles);
			return send((SmsBasicRequest) request);
		}

		if (request instanceof SmsTemplateRequest) {
			SmsTemplateRequest templateRequest = (SmsTemplateRequest) request;

			SmsTemplate template = getTemplate(templateRequest.templateName)//
					.orElseThrow(() -> new NotFoundException(//
							"sms template [%s] not found", templateRequest.templateName));

			SpaceContext.credentials().checkRoles(template.roles);
			return send(toBasicRequest(templateRequest, template));
		}

		throw Exceptions.illegalArgument("invalid sms request type [%s]", //
				request.getClass().getSimpleName());
	}

	@Get("/1/sms/:messageId")
	@Get("/1/sms/:messageId")
	public Payload getSms(String messageId, Context context) {
		SpaceContext.credentials().checkRoles(smsSettings().authorizedRoles);
		return fetch(messageId);
	}

	@Put("/1/sms/templates/:name")
	@Put("/1/sms/templates/:name/")
	public Payload putTemplate(String templateName, SmsTemplate template, Context context) {
		SpaceContext.credentials().checkAtLeastSuperAdmin();
		template.name = templateName;
		IndexResponse response = SettingsService.get()//
				.doSave(toSettingsId(templateName), Json.toString(template));

		return JsonPayload.status(response.isCreated())//
				.withFields("id", templateName, "type", "SmsTemplate")//
				.withLocation("/1/sms/templates/" + templateName)//
				.build();
	}

	@Delete("/1/sms/templates/:name")
	@Delete("/1/sms/templates/:name/")
	public Payload deleteTemplate(String templateName) {
		SpaceContext.credentials().checkAtLeastSuperAdmin();
		SettingsService.get().doDelete(toSettingsId(templateName));
		return JsonPayload.ok().build();
	}

	//
	// SmsTemplateRequest Interface and Implementation
	//

	private String toSettingsId(String templateName) {
		return "internal-sms-template-" + templateName;
	}

	public Optional<SmsTemplate> getTemplate(String name) {
		return SettingsService.get().doGet(toSettingsId(name))//
				.map(source -> Json.toPojo(source, SmsTemplate.class));
	}

	private SmsBasicRequest toBasicRequest(SmsTemplateRequest request, SmsTemplate template) {
		PebbleTemplating pebble = PebbleTemplating.get();
		Map<String, Object> context = pebble.createContext(template.model, request.parameters);

		SmsBasicRequest message = new SmsBasicRequest();
		message.from = pebble.render("from", template.from, context);
		message.to = pebble.render("to", template.to, context);
		message.body = pebble.render("body", template.body, context);
		return message;
	}

	//
	// SmsBasicRequest Interface and Implementation
	//

	public Payload send(SmsBasicRequest request) {

		SmsSettings settings = smsSettings();

		if (settings.twilio != null)
			return smsViaTwilio(request, settings.twilio);

		throw Exceptions.illegalArgument("no sms provider settings set");
	}

	private Payload smsViaTwilio(SmsBasicRequest request, TwilioSettings twilio) {

		if (request.from == null)
			request.from = twilio.defaultFrom;

		SpaceResponse response = SpaceRequest//
				.post("/2010-04-01/Accounts/{id}/Messages.json")//
				.backend("https://api.twilio.com")//
				.routeParam("id", twilio.accountSid)//
				.basicAuth(twilio.accountSid, twilio.authToken)//
				.formField("To", request.to)//
				.formField("From", request.from)//
				.formField("Body", request.body)//
				.go();

		return toPayload(response);
	}

	public Payload fetch(String messageId) {

		SmsSettings settings = smsSettings();

		if (settings.twilio != null)
			return fetchFromTwilio(messageId, settings.twilio);

		throw Exceptions.illegalArgument("no sms provider settings set");
	}

	private Payload fetchFromTwilio(String messageId, TwilioSettings twilio) {

		SpaceResponse response = SpaceRequest//
				.get("/2010-04-01/Accounts/{accountId}/Messages/{messageId}.json")//
				.backend("https://api.twilio.com")//
				.routeParam("accountId", twilio.accountSid)//
				.routeParam("messageId", messageId)//
				.basicAuth(twilio.accountSid, twilio.authToken)//
				.go();

		return toPayload(response);
	}

	//
	// implementation
	//

	private Payload toPayload(SpaceResponse response) {
		JsonPayload payload = JsonPayload.status(response.status());
		ObjectNode twilio = response.asJsonObject();

		if (response.status() >= 400) {
			ObjectNode error = Json.object(//
					"code", "twilio:" + twilio.get("code").asText(), //
					"message", twilio.get("message").asText(), //
					"twilio", twilio);
			return payload.withError(error).build();
		}

		return payload.withObject(twilio).build();
	}

	private SmsSettings smsSettings() {
		return SettingsService.get().getAsObject(SmsSettings.class);
	}

	//
	// singleton
	//

	private static SmsService singleton = new SmsService();

	public static SmsService get() {
		return singleton;
	}

	private SmsService() {
		SettingsService.get().registerSettings(SmsSettings.class);
	}
}
