/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.spacedog.client.elastic;

import java.util.Collection;

/**
 * A static factory for simple "import static" usage.
 */
public abstract class ESQueryBuilders {

	/**
	 * A query that match on all documents.
	 */
	public static ESMatchAllQueryBuilder matchAllQuery() {
		return new ESMatchAllQueryBuilder();
	}

	/**
	 * Creates a match query for the provided field name and text.
	 *
	 * @param name
	 *            The field name.
	 * @param text
	 *            The query text (to be analyzed).
	 */
	public static ESMatchQueryBuilder matchQuery(String name, Object text) {
		return new ESMatchQueryBuilder(name, text);
	}

	/**
	 * Creates a match phrase text query for the provided field name and text.
	 *
	 * @param name
	 *            The field name.
	 * @param text
	 *            The query text (to be analyzed).
	 */
	public static ESMatchPhraseQueryBuilder matchPhraseQuery(String name, Object text) {
		return new ESMatchPhraseQueryBuilder(name, text);
	}

	/**
	 * Creates a match phrase prefix query for the provided field name and text.
	 *
	 * @param name
	 *            The field name.
	 * @param text
	 *            The query text (to be analyzed).
	 */
	public static ESMatchPhrasePrefixQueryBuilder matchPhrasePrefixQuery(String name, Object text) {
		return new ESMatchPhrasePrefixQueryBuilder(name, text);
	}

	/**
	 * A query that parses a query string and runs it. There are two modes that this
	 * operates. The first, when no field is added (using
	 * {@link ESQueryStringQueryBuilder#field(String)}, will run the query once and
	 * non prefixed fields will use the
	 * {@link ESQueryStringQueryBuilder#defaultField(String)} set. The second, when
	 * one or more fields are added (using
	 * {@link ESQueryStringQueryBuilder#field(String)}), will run the parsed query
	 * against the provided fields, and combine them either using DisMax or a plain
	 * boolean query (see {@link ESQueryStringQueryBuilder#useDisMax(boolean)}).
	 *
	 * @param queryString
	 *            The query string to run
	 */
	public static ESQueryStringQueryBuilder queryStringQuery(String queryString) {
		return new ESQueryStringQueryBuilder(queryString);
	}

	/**
	 * A query that acts similar to a query_string query, but won't throw exceptions
	 * for any weird string syntax. See
	 * {@link org.LuceneSimpleQueryParserConstants.lucene.queryparser.simple.SimpleQueryParser}
	 * for the full supported syntax.
	 */
	public static ESSimpleQueryStringBuilder simpleQueryStringQuery(String queryString) {
		return new ESSimpleQueryStringBuilder(queryString);
	}

	/**
	 * Constructs a query that will match only specific ids within types.
	 *
	 * @param types
	 *            The mapping/doc type
	 */
	public static ESIdsQueryBuilder idsQuery(String... types) {
		return new ESIdsQueryBuilder(types);
	}

	/**
	 * A Query that matches documents containing a term.
	 *
	 * @param name
	 *            The name of the field
	 * @param value
	 *            The value of the term
	 */
	public static ESTermQueryBuilder termQuery(String name, String value) {
		return new ESTermQueryBuilder(name, value);
	}

	/**
	 * A Query that matches documents containing a term.
	 *
	 * @param name
	 *            The name of the field
	 * @param value
	 *            The value of the term
	 */
	public static ESTermQueryBuilder termQuery(String name, int value) {
		return new ESTermQueryBuilder(name, value);
	}

	/**
	 * A Query that matches documents containing a term.
	 *
	 * @param name
	 *            The name of the field
	 * @param value
	 *            The value of the term
	 */
	public static ESTermQueryBuilder termQuery(String name, long value) {
		return new ESTermQueryBuilder(name, value);
	}

	/**
	 * A Query that matches documents containing a term.
	 *
	 * @param name
	 *            The name of the field
	 * @param value
	 *            The value of the term
	 */
	public static ESTermQueryBuilder termQuery(String name, float value) {
		return new ESTermQueryBuilder(name, value);
	}

	/**
	 * A Query that matches documents containing a term.
	 *
	 * @param name
	 *            The name of the field
	 * @param value
	 *            The value of the term
	 */
	public static ESTermQueryBuilder termQuery(String name, double value) {
		return new ESTermQueryBuilder(name, value);
	}

	/**
	 * A Query that matches documents containing a term.
	 *
	 * @param name
	 *            The name of the field
	 * @param value
	 *            The value of the term
	 */
	public static ESTermQueryBuilder termQuery(String name, boolean value) {
		return new ESTermQueryBuilder(name, value);
	}

	/**
	 * A Query that matches documents containing a term.
	 *
	 * @param name
	 *            The name of the field
	 * @param value
	 *            The value of the term
	 */
	public static ESTermQueryBuilder termQuery(String name, Object value) {
		return new ESTermQueryBuilder(name, value);
	}

	/**
	 * A Query that matches documents containing terms with a specified prefix.
	 *
	 * @param name
	 *            The name of the field
	 * @param prefix
	 *            The prefix query
	 */
	public static ESPrefixQueryBuilder prefixQuery(String name, String prefix) {
		return new ESPrefixQueryBuilder(name, prefix);
	}

	/**
	 * A Query that matches documents within an range of terms.
	 *
	 * @param name
	 *            The field name
	 */
	public static ESRangeQueryBuilder rangeQuery(String name) {
		return new ESRangeQueryBuilder(name);
	}

	/**
	 * A Query that matches documents matching boolean combinations of other
	 * queries.
	 */
	public static ESBoolQueryBuilder boolQuery() {
		return new ESBoolQueryBuilder();
	}

	/**
	 * A filer for a field based on several terms matching on any of them.
	 *
	 * @param name
	 *            The field name
	 * @param values
	 *            The terms
	 */
	public static ESTermsQueryBuilder termsQuery(String name, String... values) {
		return new ESTermsQueryBuilder(name, values);
	}

	/**
	 * A filer for a field based on several terms matching on any of them.
	 *
	 * @param name
	 *            The field name
	 * @param values
	 *            The terms
	 */
	public static ESTermsQueryBuilder termsQuery(String name, int... values) {
		return new ESTermsQueryBuilder(name, values);
	}

	/**
	 * A filer for a field based on several terms matching on any of them.
	 *
	 * @param name
	 *            The field name
	 * @param values
	 *            The terms
	 */
	public static ESTermsQueryBuilder termsQuery(String name, long... values) {
		return new ESTermsQueryBuilder(name, values);
	}

	/**
	 * A filer for a field based on several terms matching on any of them.
	 *
	 * @param name
	 *            The field name
	 * @param values
	 *            The terms
	 */
	public static ESTermsQueryBuilder termsQuery(String name, float... values) {
		return new ESTermsQueryBuilder(name, values);
	}

	/**
	 * A filer for a field based on several terms matching on any of them.
	 *
	 * @param name
	 *            The field name
	 * @param values
	 *            The terms
	 */
	public static ESTermsQueryBuilder termsQuery(String name, double... values) {
		return new ESTermsQueryBuilder(name, values);
	}

	/**
	 * A filer for a field based on several terms matching on any of them.
	 *
	 * @param name
	 *            The field name
	 * @param values
	 *            The terms
	 */
	public static ESTermsQueryBuilder termsQuery(String name, Object... values) {
		return new ESTermsQueryBuilder(name, values);
	}

	/**
	 * A filer for a field based on several terms matching on any of them.
	 *
	 * @param name
	 *            The field name
	 * @param values
	 *            The terms
	 */
	public static ESTermsQueryBuilder termsQuery(String name, Collection<?> values) {
		return new ESTermsQueryBuilder(name, values);
	}

	/**
	 * A filter to filter based on a specific distance from a specific geo location
	 * / point.
	 *
	 * @param name
	 *            The location field name.
	 */
	public static ESGeoDistanceQueryBuilder geoDistanceQuery(String name) {
		return new ESGeoDistanceQueryBuilder(name);
	}

	/**
	 * A filter to filter only documents where a field exists in them.
	 *
	 * @param name
	 *            The name of the field
	 */
	public static ESExistsQueryBuilder existsQuery(String name) {
		return new ESExistsQueryBuilder(name);
	}

	//
	// New helpers
	//

	public static ESBoolQueryBuilder tagsQuery(String name, String... tags) {
		ESBoolQueryBuilder query = ESQueryBuilders.boolQuery();
		for (String tag : tags)
			query.must(ESQueryBuilders.termQuery(name, tag));
		return query;
	}

	private ESQueryBuilders() {

	}
}
