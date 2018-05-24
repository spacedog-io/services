/**
 * © David Attias 2015
 */
package io.spacedog.test;

import org.joda.time.DateTime;
import org.junit.Test;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import io.spacedog.client.SpaceDog;
import io.spacedog.client.credentials.Credentials;
import io.spacedog.client.credentials.CredentialsSettings;
import io.spacedog.client.credentials.Passwords;
import io.spacedog.client.email.EmailTemplate;
import io.spacedog.client.http.SpaceRequest;
import io.spacedog.client.http.SpaceRequestException;
import io.spacedog.utils.Exceptions;
import io.spacedog.utils.Json;
import io.spacedog.utils.Optional7;

public class CredentialsServiceTest extends SpaceTest {

	@Test
	public void deleteSuperAdminCredentials() {

		// prepare
		prepareTest();
		SpaceDog superadmin = clearServer().login();
		SpaceDog superdog = superdog();

		// forbidden to delete superadmin if last superadmin of backend
		superadmin.delete("/1/credentials/" + superadmin.id()).go(403);
		superdog.delete("/1/credentials/" + superadmin.id()).go(403);

		// superadmin test can create another superadmin (test1)
		SpaceDog superfred = superadmin.credentials()//
				.create("superfred", "hi superfred", "superfred@test.com", "superadmin")//
				.login("hi superfred");

		// superadmin test can delete superadmin superfred
		superadmin.credentials().delete(superfred.id());

		// superfred can no longer login
		superfred.get("/1/login").go(401);

		// superdog can not be deleted
		superdog.delete("/1/credentials/me").go(404);
	}

	@Test
	public void superdogCanDoAnything() {

		// prepare
		prepareTest();
		clearServer();

		// superdog with root backend id
		SpaceDog superdog = superdog();

		// superdog can access anything in root backend
		superdog.credentials().getByUsername("fred");
		superdog.settings().get(CredentialsSettings.class);
	}

	@Test
	public void testEnableAfterAndDisableAfter() {

		// prepare
		prepareTest();
		SpaceDog superadmin = clearServer();
		SpaceDog fred = createTempDog(superadmin, "fred");

		// fred logs in
		fred.login();

		// fred gets data
		fred.data().getAllRequest().go();

		// only admins are allowed to update credentials enable after date
		assertHttpError(403, () -> fred.credentials().prepareUpdate()//
				.enableAfter(Optional7.of(DateTime.now())).go());

		// only admins are allowed to update credentials disable after date
		assertHttpError(403, () -> fred.credentials().prepareUpdate()//
				.disableAfter(Optional7.of(DateTime.now())).go());

		// only admins are allowed to update credentials enabled status
		assertHttpError(403, () -> fred.credentials().prepareUpdate().enabled(true).go());

		// superadmin can update fred's credentials disable after date
		// before now so fred's credentials are disabled
		superadmin.credentials().prepareUpdate(fred.id())//
				.disableAfter(Optional7.of(DateTime.now().minus(100000))).go();

		// fred's credentials are disabled so he fails to gets any data
		fred.get("/1/data").go(401)//
				.assertEquals("disabled-credentials", "error.code");

		// fred's credentials are disabled so he fails to log in
		fred.get("/1/login").go(401);

		// superadmin can update fred's credentials enable after date
		// before now and after disable after date so fred's credentials
		// are enabled again
		superadmin.credentials().prepareUpdate(fred.id())//
				.enableAfter(Optional7.of(DateTime.now().minus(100000))).go();

		// fred's credentials are enabled again so he gets data
		// with his old access token from first login
		SpaceRequest.get("/1/data").bearerAuth(fred).go(200);

		// fred's credentials are enabled again so he can log in
		fred.login();

		// superadmin updates fred's credentials disable after date
		// before now but after enable after date so fred's credentials
		// are disabled again
		superadmin.credentials().prepareUpdate(fred.id())//
				.disableAfter(Optional7.of(DateTime.now().minus(100000))).go();

		// fred's credentials are disabled so he fails to gets any data
		fred.get("/1/data").go(401)//
				.assertEquals("disabled-credentials", "error.code");

		// fred's credentials are disabled so he fails to log in
		fred.get("/1/login").go(401);

		// superadmin updates fred's credentials to remove enable and
		// disable after dates so fred's credentials are enabled again
		superadmin.credentials().prepareUpdate(fred.id())//
				.enableAfter(Optional7.empty()).disableAfter(Optional7.empty()).go();

		// fred's credentials are enabled again so he gets data
		// with his old access token from first login
		SpaceRequest.get("/1/data").bearerAuth(fred).go(200);

		// fred's credentials are enabled again so he can log in
		fred.login();

		// superadmin fails to update fred's credentials enable after date
		// since invalid format
		superadmin.put("/1/credentials/{id}").routeParam("id", fred.id())//
				.bodyJson(ENABLE_AFTER_FIELD, "XXX").go(400);
	}

	@Test
	public void changePasswordInvalidatesAllTokens() {

		// prepare
		prepareTest();
		SpaceDog superadmin = clearServer();
		SpaceDog fred = createTempDog(superadmin, "fred");

		// fred logs in
		fred.login();

		// fred logs in again creating a second session
		SpaceDog fred2 = SpaceDog.dog().username(fred.username())//
				.login(fred.password().get());

		// fred can access data with his first token
		fred.data().getAllRequest().go();

		// fred can access data with his second token
		fred2.data().getAllRequest().go();

		// superadmin updates fred's password
		String newPassword = Passwords.random();
		superadmin.credentials().setPassword(fred.id(), //
				superadmin.password().get(), newPassword);

		// fred can no longer access data with his first token now invalid
		assertHttpError(401, () -> fred.data().getAllRequest().go());

		// fred can no longer access data with his second token now invalid
		assertHttpError(401, () -> fred2.data().getAllRequest().go());

		// but fred can log in with his new password
		fred.login(newPassword);
	}

	@Test
	public void multipleInvalidPasswordChallengesDisableCredentials() {

		// prepare
		prepareTest();
		SpaceDog guest = SpaceDog.dog();
		SpaceDog superadmin = clearServer();
		SpaceDog fred = createTempDog(superadmin, "fred");

		Credentials credentials = superadmin.credentials().get(fred.id());//
		assertEquals(0, credentials.invalidChallenges());
		assertNull(credentials.lastInvalidChallengeAt());

		// fred tries to log in with an invalid password
		SpaceRequest.get("/1/login")//
				.backend(guest.backend())//
				.basicAuth(fred.username(), "XXX")//
				.go(401);

		// fred's invalid challenges count is still zero
		// since no maximum invalid challenges set in credentials settings
		credentials = superadmin.credentials().get(fred.id());//
		assertEquals(0, credentials.invalidChallenges());
		assertNull(credentials.lastInvalidChallengeAt());

		// superadmin sets maximum invalid challenges to 2
		CredentialsSettings settings = new CredentialsSettings();
		settings.maximumInvalidChallenges = 2;
		settings.resetInvalidChallengesAfterMinutes = 1;
		superadmin.settings().save(settings);

		// fred tries to log in with an invalid password
		SpaceRequest.get("/1/login")//
				.backend(guest.backend())//
				.basicAuth(fred.username(), "XXX")//
				.go(401);

		// superadmin gets fred's credentials
		// fred has 1 invalid password challenge
		credentials = superadmin.credentials().get(fred.id());//
		assertEquals(1, credentials.invalidChallenges());
		assertNotNull(credentials.lastInvalidChallengeAt());

		// fred tries to log in with an invalid password
		SpaceRequest.get("/1/login")//
				.backend(guest.backend())//
				.basicAuth(fred.username(), "XXX").go(401);

		// superadmin gets fred's credentials; fred has 2 invalid password
		// challenge; his credentials has been disabled since equal to settings
		// max
		credentials = superadmin.credentials().get(fred.id());//
		assertFalse(credentials.enabled());
		assertEquals(2, credentials.invalidChallenges());
		assertNotNull(credentials.lastInvalidChallengeAt());

		// fred's credentials are disabled since too many invalid
		// password challenges in a period of time of 1 minutes
		// he can no longer login
		fred.get("/1/login").go(401)//
				.assertEquals("disabled-credentials", "error.code");

		// superadmin enables fred's credentials
		superadmin.credentials().prepareUpdate(fred.id()).enabled(true).go();

		// fred can log in again
		credentials = fred.login().credentials().me();
		assertTrue(credentials.enabled());
		assertEquals(0, credentials.invalidChallenges());
		assertNull(credentials.lastInvalidChallengeAt());
	}

	@Test
	public void passwordMustChange() {

		// prepare
		prepareTest();
		SpaceDog superadmin = clearServer();
		SpaceDog fred = createTempDog(superadmin, "fred").login();

		// fred can get data objects
		fred.data().getAllRequest().go();

		// superadmin forces fred to change his password
		superadmin.credentials().passwordMustChange(fred.id());

		// fred can no longer get data objects with his token
		// because he must first change his password
		SpaceRequest.get("/1/data").bearerAuth(fred).go(403)//
				.assertEquals("password-must-change", "error.code");

		// fred can no longer get data objects with his password
		// because he must first change his password
		SpaceRequest.get("/1/data").basicAuth(fred).go(403)//
				.assertEquals("password-must-change", "error.code");

		// fred can change his password
		String newPassword = Passwords.random();
		fred.credentials().setMyPassword(fred.password().get(), newPassword);
		fred.password(newPassword);

		// fred fails to get data objects
		// since old access token is no more valid
		SpaceRequest.get("/1/data").bearerAuth(fred).go(401);

		// but fred gets data with his new password
		SpaceRequest.get("/1/data").basicAuth(fred).go(200);

		// fred logs in with his new password
		fred.login(newPassword);

		// fred can get data objects again
		SpaceRequest.get("/1/data").bearerAuth(fred).go(200);
	}

	@Test
	public void forgotPassword() {

		// prepare
		prepareTest();
		SpaceDog superadmin = clearServer();
		SpaceDog fred = createTempDog(superadmin, "fred");

		// fred can get data objects
		fred.get("/1/data").go(200);

		// to declare that you forgot your password
		// you need to pass your username
		assertHttpError(404, () -> superadmin.credentials()//
				.sendPasswordResetEmail(""));

		// if invalid username, you get a 404
		assertHttpError(404, () -> superadmin.credentials()//
				.sendPasswordResetEmail("XXX"));

		// fred fails to declare "forgot password" if no
		// forgotPassword template set in mail settings
		assertHttpError(400, () -> fred.credentials()//
				.sendMePasswordResetEmail());

		// superadmin saves the forgotPassword email template
		EmailTemplate template = new EmailTemplate();
		template.name = "password_reset_email_template";
		template.from = "no-reply@api.spacedog.io";
		template.to = Lists.newArrayList("{{to}}");
		template.subject = "You've forgotten your password!";
		template.text = "{{passwordResetCode}}";
		superadmin.emails().saveTemplate(template);

		// fred declares he's forgot his password
		fred.credentials().sendMePasswordResetEmail();

		// fred can not pass any parameter unless they
		// are registered in the template model
		assertHttpError(400, () -> fred.credentials().sendMePasswordResetEmail(//
				Json.object("url", "http://localhost:8080")));

		// add an url parameter to the template model
		template.model = Maps.newHashMap();
		template.model.put("url", "string");
		template.text = "{{url}}?code={{passwordResetCode}}";
		superadmin.emails().saveTemplate(template);

		// fred declares he's forgot his password
		// passing an url parameter
		fred.credentials().sendMePasswordResetEmail(//
				Json.object("url", "http://localhost:8080"));

		// fred can still access services if he remembers his password
		// or if he's got a valid token
		fred.get("/1/data").go(200);
	}

	@Test
	public void changeUsername() {

		// prepare
		prepareTest();
		SpaceDog superadmin = clearServer();
		SpaceDog fred = createTempDog(superadmin, "fred");
		SpaceDog nath = createTempDog(superadmin, "nath");

		// fred fails to set his username to 'nath'
		SpaceRequestException exception = assertHttpError(400, () -> fred.credentials()//
				.prepareUpdate().username(nath.username()).go());

		assertEquals(Exceptions.ALREADY_EXISTS, exception.serverErrorCode());

		// fred sets his username to 'fred2'
		fred.credentials().prepareUpdate().username("fred2").go();

		// fred old username is no more valid
		assertHttpError(401, () -> fred.credentials().me(true));

		// fred new username is valid
		fred.username("fred2");
		Credentials credentials = fred.credentials().me(true);
		assertEquals("fred2", credentials.username());
	}
}
