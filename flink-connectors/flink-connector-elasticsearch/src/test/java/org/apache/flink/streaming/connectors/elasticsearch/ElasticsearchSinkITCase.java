/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.streaming.connectors.elasticsearch;

import org.apache.flink.api.common.functions.RuntimeContext;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.elasticsearch.testutils.SourceSinkDataTestKit;
import org.apache.flink.streaming.connectors.elasticsearch.util.ElasticsearchUtils;

import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.Requests;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.transport.LocalTransportAddress;
import org.elasticsearch.common.transport.TransportAddress;
import org.junit.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * IT Cases for the {@link ElasticsearchSink}.
 */
public class ElasticsearchSinkITCase extends ElasticsearchSinkTestBase<Client, InetSocketAddress> {

	@Test
	public void testElasticsearchSink() throws Exception {
		runElasticsearchSinkTest();
	}

	@Test
	public void testNullAddresses() throws Exception {
		runNullAddressesTest();
	}

	@Test
	public void testEmptyAddresses() throws Exception {
		runEmptyAddressesTest();
	}

	@Test
	public void testInvalidElasticsearchCluster() throws Exception{
		runInvalidElasticsearchClusterTest();
	}

	// -- Tests specific to Elasticsearch 1.x --

	/**
	 * Tests that behaviour of the deprecated {@link IndexRequestBuilder} constructor works properly.
	 */
	@Test
	public void testDeprecatedIndexRequestBuilderVariant() throws Exception {
		final String index = "index-req-builder-test-index";

		final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

		DataStreamSource<Tuple2<Integer, String>> source = env.addSource(new SourceSinkDataTestKit.TestDataSourceFunction());

		Map<String, String> userConfig = new HashMap<>();
		// This instructs the sink to emit after every element, otherwise they would be buffered
		userConfig.put(ElasticsearchSinkBase.CONFIG_KEY_BULK_FLUSH_MAX_ACTIONS, "1");
		userConfig.put("cluster.name", CLUSTER_NAME);
		userConfig.put("node.local", "true");

		List<TransportAddress> transports = new ArrayList<>();
		transports.add(new LocalTransportAddress("1"));

		source.addSink(new ElasticsearchSink<>(
			userConfig,
			transports,
			new TestIndexRequestBuilder(index))
		);

		env.execute("Elasticsearch Deprecated IndexRequestBuilder Bridge Test");

		// verify the results
		Client client = embeddedNodeEnv.getClient();
		SourceSinkDataTestKit.verifyProducedSinkData(client, index);

		client.close();
	}

	@Override
	protected ElasticsearchSinkBase<Tuple2<Integer, String>, Client> createElasticsearchSink(
			int bulkFlushMaxActions,
			String clusterName,
			List<InetSocketAddress> transportAddresses,
			ElasticsearchSinkFunction<Tuple2<Integer, String>> elasticsearchSinkFunction) {

		return new ElasticsearchSink<>(
				Collections.unmodifiableMap(createUserConfig(bulkFlushMaxActions, clusterName)),
				ElasticsearchUtils.convertInetSocketAddresses(transportAddresses),
				elasticsearchSinkFunction);
	}

	@Override
	protected ElasticsearchSinkBase<Tuple2<Integer, String>, Client> createElasticsearchSinkForEmbeddedNode(
			int bulkFlushMaxActions,
			String clusterName,
			ElasticsearchSinkFunction<Tuple2<Integer, String>> elasticsearchSinkFunction) throws Exception {

		Map<String, String> userConfig = createUserConfig(bulkFlushMaxActions, clusterName);

		// Elasticsearch 1.x requires this setting when using
		// LocalTransportAddress to connect to a local embedded node
		userConfig.put("node.local", "true");

		List<TransportAddress> transports = new ArrayList<>();
		transports.add(new LocalTransportAddress("1"));

		return new ElasticsearchSink<>(
			Collections.unmodifiableMap(userConfig),
			transports,
			elasticsearchSinkFunction);
	}

	@Override
	protected ElasticsearchSinkBase<Tuple2<Integer, String>, Client> createElasticsearchSinkForNode(
			int bulkFlushMaxActions,
			String clusterName,
			ElasticsearchSinkFunction<Tuple2<Integer, String>> elasticsearchSinkFunction,
			String ipAddress) throws Exception {

		List<TransportAddress> transports = new ArrayList<>();
		transports.add(new InetSocketTransportAddress(InetAddress.getByName(ipAddress), 9300));

		return new ElasticsearchSink<>(
			Collections.unmodifiableMap(createUserConfig(bulkFlushMaxActions, clusterName)),
			transports,
			elasticsearchSinkFunction);
	}

	/**
	 * A {@link IndexRequestBuilder} with equivalent functionality to {@link SourceSinkDataTestKit.TestElasticsearchSinkFunction}.
	 */
	private static class TestIndexRequestBuilder implements IndexRequestBuilder<Tuple2<Integer, String>> {
		private static final long serialVersionUID = 1L;

		private final String index;

		public TestIndexRequestBuilder(String index) {
			this.index = index;
		}

		@Override
		public IndexRequest createIndexRequest(Tuple2<Integer, String> element, RuntimeContext ctx) {
			Map<String, Object> json = new HashMap<>();
			json.put("data", element.f1);

			return Requests.indexRequest()
				.index(index)
				.type("flink-es-test-type")
				.id(element.f0.toString())
				.source(json);
		}
	}
}
