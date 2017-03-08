package io.spacedog.admin;

import com.amazonaws.services.lambda.runtime.Context;

import io.spacedog.client.SpaceEnv;
import io.spacedog.client.SpaceRequest;
import io.spacedog.sdk.Job;

public class Snapshot extends Job {

	public String run(Context context) {
		addToDescription(context.getFunctionName());
		return run();
	}

	public String run() {

		try {
			SpaceEnv env = SpaceEnv.defaultEnv();
			addToDescription(env.target().host());

			// set high timeout to wait for server
			// since snapshot service is slow
			env.httpTimeoutMillis(120000);

			String password = env.get("spacedog_jobs_snapshotall_password");

			SpaceRequest.post("/1/snapshot")//
					.basicAuth("api", "snapshotall", password)//
					.go(202);

			SpaceRequest.get("/1/snapshot")//
					.basicAuth("api", "snapshotall", password)//
					.go(200)//
					.assertEquals("IN_PROGRESS", "results.0.state")//
					.assertEquals("SUCCESS", "results.1.state");

		} catch (Throwable t) {
			return error(t);
		}

		return okOncePerDay();
	}

	public static void main(String[] args) {
		Snapshot snapshot = new Snapshot();
		snapshot.addToDescription("snapshot");
		snapshot.run();
	}
}
