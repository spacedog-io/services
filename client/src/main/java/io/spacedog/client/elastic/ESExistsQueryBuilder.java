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
 * Constructs a query that only match on documents that the field has a value in
 * them.
 */
public class ESExistsQueryBuilder extends ESQueryBuilder {

	private String name;

	private String queryName;

	public ESExistsQueryBuilder(String name) {
		this.name = name;
	}

	/**
	 * Sets the query name for the query that can be used when searching for
	 * matched_queries per hit.
	 */
	public ESExistsQueryBuilder queryName(String queryName) {
		this.queryName = queryName;
		return this;
	}

	@Override
	protected void doXContent(ESJsonContentBuilder builder) throws IOException {
		builder.startObject("exists");
		builder.field("field", name);
		if (queryName != null) {
			builder.field("_name", queryName);
		}
		builder.endObject();
	}
}
