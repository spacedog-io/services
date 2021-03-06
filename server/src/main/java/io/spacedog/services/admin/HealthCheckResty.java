/**
 * © David Attias 2015
 */
package io.spacedog.services.admin;

import io.spacedog.services.JsonPayload;
import io.spacedog.services.Server;
import io.spacedog.services.Services;
import io.spacedog.services.SpaceResty;
import net.codestory.http.annotations.Get;
import net.codestory.http.payload.Payload;

public class HealthCheckResty extends SpaceResty {

	@Get("")
	@Get("/")
	public Payload getPing() {
		if (elastic().exists(Services.credentials().index()))
			return JsonPayload.ok()//
					.withContent(Server.get().info())//
					.build();

		return Payload.notFound();
	}

}