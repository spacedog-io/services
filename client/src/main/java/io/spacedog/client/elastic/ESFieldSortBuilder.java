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

import java.io.IOException;

/**
 * A sort builder to sort based on a document field.
 */
public class ESFieldSortBuilder extends ESSortBuilder {

	private final String fieldName;

	private ESSortOrder order;

	private Object missing;

	private Boolean ignoreUnmapped;

	private String unmappedType;

	private String sortMode;

	private ESQueryBuilder nestedFilter;

	private String nestedPath;

	/**
	 * Constructs a new sort based on a document field.
	 *
	 * @param fieldName
	 *            The field name.
	 */
	public ESFieldSortBuilder(String fieldName) {
		if (fieldName == null) {
			throw new IllegalArgumentException("fieldName must not be null");
		}
		this.fieldName = fieldName;
	}

	/**
	 * The order of sorting. Defaults to {@link ESSortOrder#ASC}.
	 */
	@Override
	public ESFieldSortBuilder order(ESSortOrder order) {
		this.order = order;
		return this;
	}

	/**
	 * Sets the value when a field is missing in a doc. Can also be set to
	 * <tt>_last</tt> or <tt>_first</tt> to sort missing last or first
	 * respectively.
	 */
	@Override
	public ESFieldSortBuilder missing(Object missing) {
		this.missing = missing;
		return this;
	}

	/**
	 * Set the type to use in case the current field is not mapped in an index.
	 * Specifying a type tells Elasticsearch what type the sort values should
	 * have, which is important for cross-index search, if there are sort fields
	 * that exist on some indices only. If the unmapped type is <tt>null</tt>
	 * then query execution will fail if one or more indices don't have a
	 * mapping for the current field.
	 */
	public ESFieldSortBuilder unmappedType(String type) {
		this.unmappedType = type;
		return this;
	}

	/**
	 * Defines what values to pick in the case a document contains multiple
	 * values for the targeted sort field. Possible values: min, max, sum and
	 * avg
	 * <p/>
	 * The last two values are only applicable for number based fields.
	 */
	public ESFieldSortBuilder sortMode(String sortMode) {
		this.sortMode = sortMode;
		return this;
	}

	/**
	 * Sets the nested filter that the nested objects should match with in order
	 * to be taken into account for sorting.
	 */
	public ESFieldSortBuilder setNestedFilter(ESQueryBuilder nestedFilter) {
		this.nestedFilter = nestedFilter;
		return this;
	}

	/**
	 * Sets the nested path if sorting occurs on a field that is inside a nested
	 * object. By default when sorting on a field inside a nested object, the
	 * nearest upper nested object is selected as nested path.
	 */
	public ESFieldSortBuilder setNestedPath(String nestedPath) {
		this.nestedPath = nestedPath;
		return this;
	}

	@Override
	public ESJsonContentBuilder toJsonContent(ESJsonContentBuilder builder) throws IOException {
		builder.startObject(fieldName);
		if (order != null) {
			builder.field("order", order.toString());
		}
		if (missing != null) {
			builder.field("missing", missing);
		}
		if (ignoreUnmapped != null) {
			builder.field("ignore_unmapped", ignoreUnmapped);
		}
		if (unmappedType != null) {
			builder.field("unmapped_type", unmappedType);
		}
		if (sortMode != null) {
			builder.field("mode", sortMode);
		}
		if (nestedFilter != null) {
			builder.field("nested_filter", nestedFilter);
		}
		if (nestedPath != null) {
			builder.field("nested_path", nestedPath);
		}
		builder.endObject();
		return builder;
	}
}
