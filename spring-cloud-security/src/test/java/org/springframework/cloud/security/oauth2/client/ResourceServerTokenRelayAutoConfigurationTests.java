/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.security.oauth2.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.After;
import org.junit.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.EnableOAuth2Sso;
import org.springframework.boot.autoconfigure.security.oauth2.resource.UserInfoTokenServices;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.OAuth2ClientContext;
import org.springframework.security.oauth2.client.OAuth2RestTemplate;
import org.springframework.security.oauth2.client.resource.OAuth2ProtectedResourceDetails;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * @author Dave Syer
 *
 */
public class ResourceServerTokenRelayAutoConfigurationTests {

	private ConfigurableApplicationContext context;

	@After
	public void close() {
		if (this.context != null) {
			this.context.close();
		}
	}

	@Test
	public void clientNotConfigured() {
		this.context = new SpringApplicationBuilder(NoClientConfiguration.class)
				.properties("spring.config.name=test", "server.port=0",
						"security.oauth2.resource.userInfoUri:http://example.com")
				.run();
		assertFalse(this.context.containsBean("loadBalancedOauth2RestTemplate"));
	}

	@Test
	public void clientConfigured() throws Exception {
		this.context = new SpringApplicationBuilder(ClientConfiguration.class)
				.properties("spring.config.name=test", "server.port=0",
						"security.oauth2.resource.userInfoUri:http://example.com",
						"security.oauth2.client.clientId=foo")
				.run();
		RequestContextHolder.setRequestAttributes(
				new ServletRequestAttributes(new MockHttpServletRequest()));
		OAuth2ClientContext client = this.context.getBean(OAuth2ClientContext.class);
		assertNull(client.getAccessToken());
		UserInfoTokenServices services = context.getBean(UserInfoTokenServices.class);
		OAuth2RestTemplate template = (OAuth2RestTemplate) ReflectionTestUtils
				.getField(services, "restTemplate");
		MockRestServiceServer server = MockRestServiceServer.createServer(template);
		server.expect(requestTo("http://example.com"))
				.andRespond(withSuccess("{\"id\":\"user\"}", MediaType.APPLICATION_JSON));
		services.loadAuthentication("FOO");
		assertEquals("FOO", client.getAccessToken().getValue());
		server.verify();
	}

	@Test
	public void noUserInfo() throws Exception {
		this.context = new SpringApplicationBuilder(ClientConfiguration.class)
				.properties("spring.config.name=test", "server.port=0",
						"security.oauth2.resource.tokenInfoUri:http://example.com",
						"security.oauth2.client.clientId=foo")
				.run();
		HandlerInterceptor interceptor = this.context
				.getBean("tokenRelayRequestInterceptor", HandlerInterceptor.class);
		assertNotNull(interceptor);
	}

	@EnableAutoConfiguration
	@Configuration
	@EnableResourceServer
	protected static class NoClientConfiguration {
	}

	@EnableAutoConfiguration
	@Configuration
	@EnableResourceServer
	@EnableOAuth2Sso
	protected static class ClientConfiguration {

		@Bean
		public OAuth2RestTemplate oauth2RestTemplate(
				OAuth2ProtectedResourceDetails resource,
				OAuth2ClientContext oauth2Context) {
			return new OAuth2RestTemplate(resource, oauth2Context);
		}

	}
}