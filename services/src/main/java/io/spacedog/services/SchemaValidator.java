/**
 * © David Attias 2015
 */
package io.spacedog.services;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.StreamSupport;

import org.apache.http.HttpStatus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;

import io.spacedog.utils.SpaceException;

public class SchemaValidator {

	private static enum JsonType {
		OBJECT, ARRAY, BOOLEAN, STRING, NUMBER
	}

	public static JsonNode validate(String type, JsonNode schema) throws InvalidSchemaException {

		JsonNode rootObject = checkField(schema, type, true, JsonType.OBJECT).get();

		checkIfInvalidField(schema, false, type);

		String rootType = checkField(rootObject, "_type", false, JsonType.STRING).orElse(TextNode.valueOf("object"))
				.asText();

		// if (rootType.equals("stash")) {
		// checkStashProperty(type, rootObject);
		// } else

		if (rootType.equals("object")) {
			checkField(rootObject, "_id", false, JsonType.STRING);
			Optional<JsonNode> opt = checkField(rootObject, "_acl", false, JsonType.OBJECT);
			if (opt.isPresent()) {
				checkAcl(type, opt.get());
			}
			checkIfInvalidField(rootObject, true, "_acl", "_id", "_type");
			checkObjectProperties(type, rootObject);
		} else
			throw InvalidSchemaException.invalidSchemaType(type, rootType);

		return schema;
	}

	private static void checkAcl(String type, JsonNode json) throws InvalidSchemaException {
		// TODO implement this
	}

	private static void checkObjectProperties(String propertyName, JsonNode json) {
		Iterable<String> fieldNames = () -> json.fieldNames();

		StreamSupport.stream(fieldNames.spliterator(), false).filter(name -> name.charAt(0) != '_').findFirst()
				.orElseThrow(() -> InvalidSchemaException.noProperty(propertyName));

		StreamSupport.stream(fieldNames.spliterator(), false).filter(name -> name.charAt(0) != '_')
				.forEach(name -> checkProperty(name, json.get(name)));
	}

	private static void checkObjectProperty(String propertyName, JsonNode json) throws InvalidSchemaException {
		checkField(json, "_type", false, JsonType.STRING, TextNode.valueOf("object"));
		checkField(json, "_required", false, JsonType.BOOLEAN);
		checkField(json, "_array", false, JsonType.BOOLEAN);
		checkIfInvalidField(json, true, "_type", "_required", "_array");
		checkObjectProperties(propertyName, json);
	}

	private static void checkProperty(String propertyName, JsonNode jsonObject) throws InvalidSchemaException {

		if (!jsonObject.isObject())
			throw new InvalidSchemaException(
					String.format("invalid value [%s] for object property [%s]", jsonObject, propertyName));

		Optional<JsonNode> optType = checkField(jsonObject, "_type", false, JsonType.STRING);
		String type = optType.isPresent() ? optType.get().asText() : "object";

		if (type.equals("text"))
			checkTextProperty(propertyName, jsonObject);
		else if (type.equals("string"))
			checkSimpleProperty(jsonObject, propertyName, type);
		else if (type.equals("date"))
			checkSimpleProperty(jsonObject, propertyName, type);
		else if (type.equals("time"))
			checkSimpleProperty(jsonObject, propertyName, type);
		else if (type.equals("timestamp"))
			checkSimpleProperty(jsonObject, propertyName, type);
		else if (type.equals("integer"))
			checkSimpleProperty(jsonObject, propertyName, type);
		else if (type.equals("long"))
			checkSimpleProperty(jsonObject, propertyName, type);
		else if (type.equals("float"))
			checkSimpleProperty(jsonObject, propertyName, type);
		else if (type.equals("double"))
			checkSimpleProperty(jsonObject, propertyName, type);
		else if (type.equals("boolean"))
			checkSimpleProperty(jsonObject, propertyName, type);
		else if (type.equals("object"))
			checkObjectProperty(propertyName, jsonObject);
		else if (type.equals("enum"))
			checkEnumProperty(propertyName, jsonObject);
		else if (type.equals("geopoint"))
			checkSimpleProperty(jsonObject, propertyName, type);
		else if (type.equals("stash"))
			checkStashProperty(propertyName, jsonObject);
		else
			throw new InvalidSchemaException("Invalid field type: " + type);
	}

	private static void checkSimpleProperty(JsonNode json, String propertyName, String propertyType)
			throws InvalidSchemaException {
		checkField(json, "_required", false, JsonType.BOOLEAN);
		checkField(json, "_array", false, JsonType.BOOLEAN);
		checkIfInvalidField(json, false, "_type", "_required", "_array");
	}

	private static void checkStashProperty(String type, JsonNode json) {
		checkField(json, "_required", false, JsonType.BOOLEAN);
		checkIfInvalidField(json, false, "_type", "_required");
	}

	private static void checkEnumProperty(String propertyName, JsonNode json) throws InvalidSchemaException {
		checkField(json, "_required", false, JsonType.BOOLEAN);
		checkField(json, "_array", false, JsonType.BOOLEAN);
		checkIfInvalidField(json, false, "_type", "_required", "_array");
	}

	private static void checkTextProperty(String propertyName, JsonNode json) throws InvalidSchemaException {
		checkIfInvalidField(json, false, "_type", "_required", "_language", "_array");
		checkField(json, "_required", false, JsonType.BOOLEAN);
		checkField(json, "_language", false, JsonType.STRING);
		checkField(json, "_array", false, JsonType.BOOLEAN);
	}

	private static void checkIfInvalidField(JsonNode json, boolean checkSettingsOnly, String... validFieldNames) {

		Iterable<String> fieldNames = () -> json.fieldNames();

		StreamSupport.stream(fieldNames.spliterator(), false)
				.filter(name -> checkSettingsOnly ? name.charAt(0) == ('_') : true).filter(name -> {
					for (String validName : validFieldNames) {
						if (name.equals(validName))
							return false;
					}
					return true;
				}).findFirst().ifPresent(name -> {
					throw InvalidSchemaException.invalidField(name, validFieldNames);
				});
	}

	private static void checkField(JsonNode jsonObject, String fieldName, boolean required, JsonType fieldType,
			JsonNode anticipatedFieldValue) throws InvalidSchemaException {

		checkField(jsonObject, fieldName, required, fieldType) //
				.ifPresent(fieldValue -> {
					if (!fieldValue.equals(anticipatedFieldValue))
						throw InvalidSchemaException.invalidFieldValue(fieldName, fieldValue, anticipatedFieldValue);
				});
	}

	private static Optional<JsonNode> checkField(JsonNode jsonObject, String fieldName, boolean required,
			JsonType fieldType) throws InvalidSchemaException {

		JsonNode fieldValue = jsonObject.get(fieldName);

		if (fieldValue == null)
			if (required)
				throw new InvalidSchemaException("This schema field is required: " + fieldName);
			else
				return Optional.empty();

		if ((fieldValue.isObject() && fieldType == JsonType.OBJECT)
				|| (fieldValue.isTextual() && fieldType == JsonType.STRING)
				|| (fieldValue.isArray() && fieldType == JsonType.ARRAY)
				|| (fieldValue.isBoolean() && fieldType == JsonType.BOOLEAN)
				|| (fieldValue.isNumber() && fieldType == JsonType.NUMBER))
			return Optional.of(fieldValue);

		throw new InvalidSchemaException(String.format("Invalid type [%s] for schema field [%s]. Must be [%s]",
				getJsonType(fieldValue), fieldName, fieldType));
	}

	private static String getJsonType(JsonNode value) {
		return value.isTextual() ? "string"
				: value.isObject() ? "object"
						: value.isNumber() ? "number"
								: value.isArray() ? "array" : value.isBoolean() ? "boolean" : "null";
	}

	public static class InvalidSchemaException extends SpaceException {

		private static final long serialVersionUID = 6335047694807220133L;

		public InvalidSchemaException(String message, Object... args) {
			super(HttpStatus.SC_BAD_REQUEST, message, args);
		}

		public static InvalidSchemaException invalidField(String fieldName, String... expectedFiedNames) {
			return new InvalidSchemaException("field [%s] invalid: expected fields %s", fieldName,
					Arrays.toString(expectedFiedNames));
		}

		public static InvalidSchemaException invalidSchemaType(String schemaName, String type) {
			return new InvalidSchemaException("schema type [%s] invalid", type);
		}

		public static InvalidSchemaException invalidFieldValue(String fieldName, JsonNode fieldValue,
				JsonNode anticipatedFieldValue) {
			return new InvalidSchemaException("schema field [%s] equal to [%s] should be equal to [%s]", fieldName,
					fieldValue, anticipatedFieldValue);
		}

		public static InvalidSchemaException noProperty(String propertyName) {
			return new InvalidSchemaException("property [%s] of type [object] has no properties", propertyName);
		}
	}

}
