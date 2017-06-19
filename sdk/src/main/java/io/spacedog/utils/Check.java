package io.spacedog.utils;

import java.util.Collection;
import java.util.regex.Pattern;

import com.google.common.base.Strings;

import io.spacedog.rest.SpaceStatus;

public class Check {

	public static Object notNull(Object value, String valueName) {
		if (value == null)
			throw Exceptions.illegalArgument("[%s] is null", valueName);
		return value;
	}

	public static void isNull(Object value, String valueName) {
		if (value != null)
			throw Exceptions.illegalArgument("[%s] is not null", valueName);
	}

	public static String notNullOrEmpty(String value, String valueName) {
		if (Strings.isNullOrEmpty(value))
			throw Exceptions.illegalArgument("[%s] is null or empty", valueName);
		return value;
	}

	public static void notNullOrEmpty(Collection<?> value, String valueName) {
		if (Utils.isNullOrEmpty(value))
			throw Exceptions.illegalArgument("[%s] is null or empty", valueName);
	}

	public static void isTrue(boolean condition, String message, Object... arguments) {
		isTrue(condition, SpaceStatus.BAD_REQUEST, message, arguments);
	}

	public static void isTrue(boolean condition, int httpStatus, String message, Object... arguments) {
		if (!condition)
			throw Exceptions.space(httpStatus, message, arguments);
	}

	public static void matchRegex(String regex, String value, String valueName) {
		notNull(value, valueName);
		if (!Pattern.matches(regex, value))
			throw Exceptions.illegalArgument(//
					"[%s] is invalid: does not comply [%s] regex", valueName, regex);
	}
}
