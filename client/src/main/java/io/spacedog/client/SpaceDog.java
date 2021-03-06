package io.spacedog.client;

import java.util.Optional;
import java.util.Set;

import org.joda.time.DateTime;

import com.google.common.base.Strings;
import com.google.common.collect.Sets;

import io.spacedog.client.admin.AdminClient;
import io.spacedog.client.bulk.BulkClient;
import io.spacedog.client.credentials.CredentialsClient;
import io.spacedog.client.data.DataClient;
import io.spacedog.client.email.EmailClient;
import io.spacedog.client.file.FileClient;
import io.spacedog.client.http.SpaceBackend;
import io.spacedog.client.http.SpaceEnv;
import io.spacedog.client.http.SpaceFields;
import io.spacedog.client.http.SpaceParams;
import io.spacedog.client.http.SpaceRequest;
import io.spacedog.client.job.JobClient;
import io.spacedog.client.log.LogClient;
import io.spacedog.client.push.PushClient;
import io.spacedog.client.schema.SchemaClient;
import io.spacedog.client.settings.SettingsClient;
import io.spacedog.client.sms.SmsClient;
import io.spacedog.client.snapshot.SnapshotClient;
import io.spacedog.client.stripe.StripeClient;
import io.spacedog.utils.Check;

public class SpaceDog implements SpaceFields, SpaceParams {

	private SpaceBackend backend;
	private String username;
	private String password;
	private String accessToken;
	private DateTime expiresAt;

	private SpaceDog(SpaceBackend backend) {
		this.backend = backend;
	}

	public SpaceBackend backend() {
		return backend;
	}

	public String username() {
		return username;
	}

	public boolean isGuest() {
		return Strings.isNullOrEmpty(username);
	}

	public SpaceDog username(String username) {
		this.username = username;
		return this;
	}

	public String id() {
		return credentials().me().id();
	}

	public String group() {
		return isGuest() ? null : credentials().me().group();
	}

	public Set<String> groups() {
		if (isGuest())
			return Sets.newHashSet();
		return credentials().me().groups();
	}

	public Optional<String> accessToken() {
		return Optional.ofNullable(accessToken);
	}

	public SpaceDog accessToken(String accessToken) {
		this.accessToken = accessToken;
		return this;
	}

	public DateTime expiresAt() {
		return this.expiresAt;
	}

	public SpaceDog expiresAt(DateTime plus) {
		this.expiresAt = plus;
		return this;
	}

	public Optional<String> password() {
		return Optional.ofNullable(password);
	}

	public SpaceDog password(String password) {
		this.password = password;
		return this;
	}

	@Override
	public String toString() {
		return String.format("SpaceDog[%s]", username());
	}

	//
	// Factory methods
	//

	public static SpaceDog dog() {
		return dog(SpaceEnv.env().apiBackend());
	}

	public static SpaceDog dog(String backend) {
		return dog(SpaceBackend.valueOf(backend));
	}

	public static SpaceDog dog(SpaceBackend backend) {
		return new SpaceDog(backend);
	}

	//
	// login/logout
	//

	public SpaceDog login() {
		return login(password().get());
	}

	public SpaceDog login(long lifetime) {
		return login(password().get(), lifetime);
	}

	public SpaceDog login(String password) {
		return login(password, 0);
	}

	public SpaceDog login(String password, long lifetime) {
		return credentials().login(password, lifetime);
	}

	public boolean isTokenStillValid() {
		Check.notNullOrEmpty(accessToken, "access token");
		return post("/2/credentials/_login").go(200, 401).asVoid().status() == 200;
	}

	public SpaceDog logout() {
		return credentials().logout();
	}

	//
	// Basic REST requests
	//

	public SpaceRequest get(String uri) {
		return auth(SpaceRequest.get(uri));
	}

	public SpaceRequest post(String uri) {
		return auth(SpaceRequest.post(uri));
	}

	public SpaceRequest put(String uri) {
		return auth(SpaceRequest.put(uri));
	}

	public SpaceRequest delete(String uri) {
		return auth(SpaceRequest.delete(uri));
	}

	public SpaceRequest options(String uri) {
		return auth(SpaceRequest.options(uri));
	}

	public SpaceRequest head(String uri) {
		return auth(SpaceRequest.head(uri));
	}

	private SpaceRequest auth(SpaceRequest request) {
		request.backend(backend());

		Optional<String> accessToken = accessToken();
		if (accessToken.isPresent())
			return request.bearerAuth(accessToken.get());

		Optional<String> password = password();
		if (password.isPresent())
			return request.basicAuth(username(), password.get());

		// if no password nor access token then no auth
		return request;
	}

	//
	// Services
	//

	DataClient dataClient;

	public DataClient data() {
		if (dataClient == null)
			dataClient = new DataClient(this);

		return dataClient;
	}

	SettingsClient settingsClient;

	public SettingsClient settings() {
		if (settingsClient == null)
			settingsClient = new SettingsClient(this);

		return settingsClient;
	}

	StripeClient stripeClient;

	public StripeClient stripe() {
		if (stripeClient == null)
			stripeClient = new StripeClient(this);

		return stripeClient;
	}

	PushClient pushClient;

	public PushClient push() {
		if (pushClient == null)
			pushClient = new PushClient(this);
		return pushClient;
	}

	CredentialsClient credentialsClient;

	public CredentialsClient credentials() {
		if (credentialsClient == null)
			credentialsClient = new CredentialsClient(this);
		return credentialsClient;
	}

	SchemaClient schemaClient;

	public SchemaClient schemas() {
		if (schemaClient == null)
			schemaClient = new SchemaClient(this);
		return schemaClient;
	}

	AdminClient adminClient;

	public AdminClient admin() {
		if (adminClient == null)
			adminClient = new AdminClient(this);
		return adminClient;
	}

	EmailClient emailClient;

	public EmailClient emails() {
		if (emailClient == null)
			emailClient = new EmailClient(this);
		return emailClient;
	}

	FileClient fileClient;

	public FileClient files() {
		if (fileClient == null)
			fileClient = new FileClient(this);
		return fileClient;
	}

	LogClient logClient;

	public LogClient logs() {
		if (logClient == null)
			logClient = new LogClient(this);
		return logClient;
	}

	SmsClient smsClient;

	public SmsClient sms() {
		if (smsClient == null)
			smsClient = new SmsClient(this);
		return smsClient;
	}

	BulkClient bulkClient;

	public BulkClient bulk() {
		if (bulkClient == null)
			bulkClient = new BulkClient(this);
		return bulkClient;
	}

	SnapshotClient snapshotClient;

	public SnapshotClient snapshots() {
		if (snapshotClient == null)
			snapshotClient = new SnapshotClient(this);
		return snapshotClient;
	}

	JobClient JobClient;

	public JobClient jobs() {
		if (JobClient == null)
			JobClient = new JobClient(this);
		return JobClient;
	}

}
