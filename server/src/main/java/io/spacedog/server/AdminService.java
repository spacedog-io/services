/**
 * © David Attias 2015
 */
package io.spacedog.server;

import io.spacedog.utils.Exceptions;
import net.codestory.http.Context;
import net.codestory.http.annotations.Get;
import net.codestory.http.annotations.Post;
import net.codestory.http.annotations.Prefix;
import net.codestory.http.payload.Payload;

@Prefix("/1/admin")
public class AdminService extends SpaceService {

	//
	// Routes
	//

	@Get("/return500")
	@Get("/return500/")
	public Payload getLog() {
		throw Exceptions.runtime("this route always returns http code 500");
	}

	@Post("/_clear")
	@Post("/_clear/")
	public void postClear(Context context) {
		Server.context().credentials().checkSuperDog();

		if (!Server.backend().host().endsWith("lvh.me"))
			throw Exceptions.forbidden("only allowed for [*.lvh.me] env");

		Server.get().clear(context.query().getBoolean("files", false));
	}

	//
	// Singleton
	//

	private static AdminService singleton = new AdminService();

	public static AdminService get() {
		return singleton;
	}

	private AdminService() {
	}

}