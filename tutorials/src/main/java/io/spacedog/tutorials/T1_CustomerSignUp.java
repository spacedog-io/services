package io.spacedog.tutorials;

import java.util.Collections;

import org.junit.Test;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import io.spacedog.client.SpaceDog;
import io.spacedog.client.credentials.CredentialsSettings;
import io.spacedog.client.credentials.Permission;
import io.spacedog.client.credentials.Roles;
import io.spacedog.client.data.DataWrap;
import io.spacedog.client.email.EmailTemplate;
import io.spacedog.client.email.EmailTemplateRequest;
import io.spacedog.client.file.FileBucket;
import io.spacedog.client.file.SpaceFile;
import io.spacedog.utils.ClassResources;

public class T1_CustomerSignUp extends DemoBase {

	@Test
	public void superadminAuthorizesGuestsToSignUp() {
		CredentialsSettings settings = new CredentialsSettings();
		settings.guestSignUpEnabled = true;
		superadmin().credentials().settings(settings);
	}

	@Test
	public void customerSignsUp() {

		SpaceDog david = SpaceDog.dog().username("dave").password("hi dave");
		guest().credentials().create(david.username(), david.password().get(), "david@spacedog.io");

		Customer source = new Customer();
		source.firstname = "David";
		source.lastname = "Attias";
		source.phone = "+33662627520";

		DataWrap<Customer> customer = DataWrap.wrap(source)//
				.id(david.id());

		david.data().save(customer);
	}

	@Test
	public void superadminAuthorizesUsersToShare() {
		FileBucket bucket = new FileBucket("shares");
		bucket.permissions.put(Roles.user, Permission.create);
		bucket.permissions.put(Roles.all, Permission.read);
		superadmin().files().setBucket(bucket);
	}

	@Test
	public void customerUploadsHisPhoto() {

		SpaceDog david = david();
		byte[] picture = ClassResources.loadAsBytes(this, "mapomme.jpg");
		SpaceFile file = david.files().share("shares", picture);

		DataWrap<Customer> customer = davidCustomer();
		customer.source().photo = "/1/files/shares" + file.getEscapedPath();
		customer = david.data().save(customer);
	}

	@Test
	public void superadminCreatesWelcomeEmailTemplate() {

		EmailTemplate template = new EmailTemplate();
		template.authorizedRoles = Collections.singleton(Roles.superadmin);

		template.name = "customer-welcome";
		template.from = "CAREMEN <no-reply@caremen.fr>";
		template.to = Lists.newArrayList("{{credentials.email}}");
		template.subject = "Bienvenue chez CAREMEN";
		template.html = ClassResources.loadAsString(this, "welcome.html");
		template.text = "Bienvenue à vous. Nous sommes heureux de pouvoir vous "
				+ "compter parmi les nouveaux utilisateurs de l’application CAREMEN.";

		template.model = Maps.newHashMap();
		template.model.put("credentials", "credentials");
		template.model.put("customer", "customer");

		superadmin().emails().saveTemplate(template);
	}

	@Test
	public void systemSendsWelcomeEmail() {

		SpaceDog david = david();
		SpaceDog superadmin = superadmin();

		EmailTemplateRequest emailTemplateRequest = new EmailTemplateRequest();
		emailTemplateRequest.templateName = "customer-welcome";
		emailTemplateRequest.parameters = Maps.newHashMap();
		emailTemplateRequest.parameters.put("credentials", david.id());
		emailTemplateRequest.parameters.put("customer", david.id());

		superadmin.emails().send(emailTemplateRequest);
	}

	@Test
	public void superadminDeletesCustomer() {

		SpaceDog david = david();
		superadmin().data().delete("customer", david.id(), false);
		superadmin().push().deleteInstallation(david.id(), false);
		superadmin().credentials().delete(david.id());
	}
}
