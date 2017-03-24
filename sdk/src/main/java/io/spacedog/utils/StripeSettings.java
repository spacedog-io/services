package io.spacedog.utils;

import java.util.Set;

public class StripeSettings extends Settings {

	public String secretKey;
	public Set<String> rolesAllowedToCharge;
	public Set<String> rolesAllowedToPay;
}
