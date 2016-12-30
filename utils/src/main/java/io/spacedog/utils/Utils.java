package io.spacedog.utils;

import java.nio.charset.Charset;
import java.util.Collection;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Strings;

public class Utils {

	public static final Charset UTF8 = Charset.forName("UTF-8");

	//
	// Exceptions utils
	//

	public static StackTraceElement getStackTraceElement() {
		return new Exception().getStackTrace()[1];
	}

	public static StackTraceElement getParentStackTraceElement() {
		return new Exception().getStackTrace()[2];
	}

	public static StackTraceElement getGrandParentStackTraceElement() {
		return new Exception().getStackTrace()[3];
	}

	//
	// Strings utils
	//

	public static String concat(Object... objects) {
		StringBuilder builder = new StringBuilder();
		for (Object object : objects)
			builder.append(object.toString());
		return builder.toString();
	}

	public static String[] splitByDot(String propertyPath) {
		propertyPath = propertyPath.trim();
		if (propertyPath.startsWith("."))
			propertyPath = propertyPath.substring(1);
		if (propertyPath.endsWith("."))
			propertyPath = propertyPath.substring(0, propertyPath.length() - 1);
		return propertyPath.split("\\.");
	}

	public static String[] splitBySlash(String propertyPath) {
		propertyPath = propertyPath.trim();
		if (propertyPath.startsWith("/"))
			propertyPath = propertyPath.substring(1);
		if (propertyPath.endsWith("/"))
			propertyPath = propertyPath.substring(0, propertyPath.length() - 1);
		return propertyPath.split("/");
	}

	public static String removePreffix(String s, String preffix) {
		if (Strings.isNullOrEmpty(preffix))
			return s;
		return s.startsWith(preffix) ? s.substring(preffix.length()) : s;
	}

	public static String removeSuffix(String s, String suffix) {
		if (Strings.isNullOrEmpty(suffix))
			return s;
		return s.endsWith(suffix) ? s.substring(0, s.length() - suffix.length()) : s;
	}

	//
	// Others
	//

	public static String toUri(String[] uriTerms) {
		StringBuilder builder = new StringBuilder();
		for (String term : uriTerms)
			builder.append('/').append(term);
		return builder.toString();
	}

	public static boolean isDigit(char c) {
		return c == '1' || c == '2' || c == '3' || c == '4' || c == '5' //
				|| c == '6' || c == '7' || c == '8' || c == '9';
	}

	public static <T> boolean isNullOrEmpty(Collection<T> collection) {
		return collection == null || collection.isEmpty();
	}

	public static <T> boolean isNullOrEmpty(T[] array) {
		return array == null || array.length == 0;
	}

	public static void info() {
		System.out.println();
	}

	public static void info(String message, Object... arguments) {
		System.out.println(String.format(message, arguments));
	}

	public static void infoNoLn(String message, Object... arguments) {
		System.out.print(String.format(message, arguments));
	}

	public static void info(String nodeName, JsonNode node) {
		Utils.info("%s = %s", nodeName, Json.toPrettyString(node));
	}

	public static void warn(String message, Throwable t) {
		System.err.println("[SpaceDog Warning] " + message);
		t.printStackTrace();
	}

	//
	// Array utils
	//

	public static String[] toArray(String... parts) {
		return parts;
	}

	public static Object[] toArray(Object... parts) {
		return parts;
	}

}
