package io.spacedog.services;

import java.io.IOException;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.search.SearchHit;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;

import io.spacedog.model.CsvRequest;
import io.spacedog.model.CsvRequest.Column;
import io.spacedog.utils.Json7;
import net.codestory.http.payload.StreamingOutput;

public class CsvStreamingOutput implements StreamingOutput {

	private static Function<JsonNode, Object> defaultFormatter = value -> toValue(value);

	private CsvRequest request;
	private SearchResponse response;
	private List<Object> row;
	private ElasticClient elastic;
	private CsvWriterSettings settings;
	private List<Function<JsonNode, Object>> formatters;
	private Function<JsonNode, Object> defaultTimestampFormatter;
	private Function<JsonNode, Object> defaultFloatingFormatter;

	public CsvStreamingOutput(CsvRequest request, SearchResponse response, Locale locale) {
		this.request = request;
		this.response = response;
		this.elastic = Start.get().getElasticClient();
		this.row = Lists.newArrayListWithCapacity(request.columns.size());

		this.settings = new CsvWriterSettings();
		this.settings.getFormat().setDelimiter(request.settings.delimiter);

		this.settings.setHeaders(//
				request.columns.stream()//
						.map(column -> column.header == null ? column.field : column.header)//
						.toArray(String[]::new));

		defaultTimestampFormatter = timestampFormatter("dd/MM/yyyy HH:mm", locale);
		NumberFormat numberFormat = NumberFormat.getNumberInstance(locale);
		defaultFloatingFormatter = value -> numberFormat.format(value.asDouble());

		formatters = Lists.newArrayListWithCapacity(request.columns.size());
		for (Column column : request.columns)
			formatters.add(toFormatter(column, locale));
	}

	private Function<JsonNode, Object> toFormatter(Column column, Locale locale) {

		if (column.type == Column.Type.timestamp)
			return timestampFormatter(column.pattern, locale);

		if (column.type == Column.Type.floating)
			return floatingFormatter(column.pattern, locale);

		return defaultFormatter;
	}

	private Function<JsonNode, Object> floatingFormatter(String pattern, Locale locale) {

		if (Strings.isNullOrEmpty(pattern))
			return defaultFloatingFormatter;

		NumberFormat formatter = new DecimalFormat(pattern);

		return value -> {
			try {
				return formatter.format(value.asDouble());

			} catch (Exception e) {
				return value.asText();
			}
		};
	}

	private Function<JsonNode, Object> timestampFormatter(String pattern, Locale locale) {

		if (Strings.isNullOrEmpty(pattern))
			return defaultTimestampFormatter;

		DateTimeFormatter formatter = DateTimeFormat.forPattern(pattern).withLocale(locale);

		return value -> {
			try {
				return formatter.print(DateTime.parse(value.asText()));

			} catch (Exception e) {
				return value.asText();
			}
		};
	}

	private static Object toValue(JsonNode value) {

		if (Json7.isNull(value))
			return null;

		if (value.isTextual())
			return value.textValue();

		if (value.isNumber())
			return value.numberValue();

		else if (value.isBoolean())
			return value.booleanValue();

		else
			return value.toString();
	}

	@Override
	public void write(OutputStream output) throws IOException {
		CsvWriter writer = new CsvWriter(output, settings);

		if (request.settings.firstRowOfHeaders)
			writer.writeHeaders();

		do {
			for (SearchHit hit : response.getHits())
				writer.writeRow(toRow(hit));

			response = elastic.prepareSearchScroll(response.getScrollId())//
					.setScroll(TimeValue.timeValueSeconds(60)).get();

		} while (response.getHits().getHits().length != 0);

		writer.close();
	}

	private Collection<?> toRow(SearchHit hit) {
		ObjectNode node = Json7.readObject(hit.sourceAsString());

		row.clear();

		for (int i = 0; i < formatters.size(); i++) {
			String field = request.columns.get(i).field;
			JsonNode fieldValue = Json7.get(node, field);
			row.add(formatters.get(i).apply(fieldValue));
		}

		return row;
	}
}
