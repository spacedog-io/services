package io.spacedog.client.credentials;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.spacedog.client.settings.SettingsBase;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CredentialsSettings extends SettingsBase {

	// default token lifetime is 24h
	public static final long SESSION_DEFAULT_LIFETIME = 60 * 60 * 24;

	public boolean guestSignUpEnabled = false;
	public String usernameRegex = Usernames.USERNAME_DEFAULT_REGEX;
	public String passwordRegex = Passwords.PASSWORD_DEFAULT_REGEX;
	public long sessionMaximumLifetimeInSeconds = SESSION_DEFAULT_LIFETIME;
	public int maximumInvalidChallenges = 0;
	public int resetInvalidChallengesAfterMinutes = 60;

	public OAuthSettings linkedin;

	public String usernameRegex() {
		return usernameRegex == null ? Usernames.USERNAME_DEFAULT_REGEX : usernameRegex;
	}

	public String passwordRegex() {
		return passwordRegex == null ? Passwords.PASSWORD_DEFAULT_REGEX : passwordRegex;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class OAuthSettings {
		public String backendUrl;
		public String clientId;
		public String clientSecret;
		public boolean useExpiresIn = true;
		public String finalRedirectUri;
	}
}