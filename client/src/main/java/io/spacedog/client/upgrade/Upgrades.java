/**
 * © David Attias 2015
 */
package io.spacedog.client.upgrade;

import java.util.List;

import com.google.common.collect.Lists;

import io.spacedog.client.SpaceDog;
import io.spacedog.client.admin.BackendSettings;
import io.spacedog.utils.Check;
import io.spacedog.utils.Exceptions;
import io.spacedog.utils.Utils;

public class Upgrades {

	private Upgrades() {
	}

	@SafeVarargs
	public static <T extends UpgradeEnv> void upgrade(T env, Upgrade<T>... upgrades) {
		upgrade(env, Lists.newArrayList(upgrades));
	}

	public static <T extends UpgradeEnv> void upgrade(T env, List<Upgrade<T>> upgrades) {

		String version = null;
		boolean upgradeStarted = false;
		SpaceDog superadmin = env.superadmin();

		if (superadmin.admin().checkMyBackendExists())
			version = superadmin.settings()//
					.get(BackendSettings.class, "version", String.class);

		for (Upgrade<T> upgrade : upgrades) {

			if (isFromEqualTo(upgrade, version)) {

				upgradeStarted = true;
				version = upgrade(env, upgrade);

			} else if (upgradeStarted) {
				throw Exceptions.runtime("upgrade [%s] is invalid from [%s]", //
						upgrade.getClass().getSimpleName(), upgrade.from());
			}
		}
	}

	private static <T extends UpgradeEnv> String upgrade(T env, Upgrade<T> upgrade) {

		Check.notNullOrEmpty(upgrade.to(), "upgrade.to");

		Utils.info();
		Utils.info("===> Upgrading from [%s] to [%s] ...", //
				upgrade.from(), upgrade.to());
		Utils.info();

		upgrade.upgrade(env);

		env.superadmin().settings()//
				.save(BackendSettings.class, "version", upgrade.to());

		return upgrade.to();
	}

	private static boolean isFromEqualTo(Upgrade<?> upgrade, String version) {
		if (version == null)
			return upgrade.from() == null;

		return version.equals(upgrade.from());
	}
}
