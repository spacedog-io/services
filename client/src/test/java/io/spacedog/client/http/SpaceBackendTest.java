package io.spacedog.client.http;

import org.junit.Assert;
import org.junit.Test;

public class SpaceBackendTest extends Assert {

	@Test
	public void testIsBackendIdValid() {

		// valid backend ids
		assertTrue(SpaceBackend.isValid("a1234"));
		assertTrue(SpaceBackend.isValid("abcd"));
		assertTrue(SpaceBackend.isValid("1a2b34d"));

		// invalid backend ids
		assertFalse(SpaceBackend.isValid("a"));
		assertFalse(SpaceBackend.isValid("bb"));
		assertFalse(SpaceBackend.isValid("ccc"));
		assertFalse(SpaceBackend.isValid("dd-dd"));
		assertFalse(SpaceBackend.isValid("/eeee"));
		assertFalse(SpaceBackend.isValid("eeee:"));
		assertFalse(SpaceBackend.isValid("abcdE"));
		assertFalse(SpaceBackend.isValid("spacedog"));
		assertFalse(SpaceBackend.isValid("spacedog123"));
		assertFalse(SpaceBackend.isValid("123spacedog"));
		assertFalse(SpaceBackend.isValid("123spacedog123"));
	}

	@Test
	public void testFromDefaults() {
		SpaceBackend backend = SpaceBackend.fromDefaults("production");
		assertEquals("api.spacedog.io", backend.host());
		assertEquals(443, backend.port());
		assertEquals("https", backend.scheme());
		assertEquals("spacedog", backend.id());
		assertTrue(backend.ssl());
		assertTrue(backend.isMulti());
		assertEquals("https://*.spacedog.io", backend.toString());
		assertEquals("https://api.spacedog.io/index.html", backend.url("/index.html"));
	}

	@Test
	public void testInstanciate() {
		SpaceBackend backend = SpaceBackend.production.fromBackendId("test");
		assertEquals("test.spacedog.io", backend.host());
		assertEquals(443, backend.port());
		assertEquals("https", backend.scheme());
		assertEquals("test", backend.id());
		assertTrue(backend.ssl());
		assertFalse(backend.isMulti());
		assertEquals("https://test.spacedog.io", backend.toString());
		assertEquals("https://test.spacedog.io/index.html", backend.url("/index.html"));
	}

	@Test
	public void testFromUrlMonoApiBackend() {
		SpaceBackend backend = SpaceBackend.fromUrl("http://connect.acme.net");
		assertEquals("connect.acme.net", backend.host());
		assertEquals(80, backend.port());
		assertEquals("http", backend.scheme());
		assertEquals("spacedog", backend.id());
		assertFalse(backend.ssl());
		assertFalse(backend.isMulti());
		assertEquals("http://connect.acme.net", backend.toString());
		assertEquals("http://connect.acme.net/index.html", backend.url("/index.html"));
	}

	@Test
	public void testFromUrlMultiWebAppBackend() {
		SpaceBackend backend = SpaceBackend.fromUrl("http://www.*.acme.net:8080");
		assertEquals("www.api.acme.net", backend.host());
		assertEquals(8080, backend.port());
		assertEquals("http", backend.scheme());
		assertEquals("spacedog", backend.id());
		assertFalse(backend.ssl());
		assertTrue(backend.isMulti());
		assertEquals("http://www.*.acme.net:8080", backend.toString());
		assertEquals("http://www.api.acme.net:8080/index.html", backend.url("/index.html"));
	}

	@Test
	public void apiMultiBackendCanHandleRequests() {
		SpaceBackend backend = SpaceBackend.fromDefaults("production")//
				.fromRequest("test.spacedog.io").get();

		assertEquals("test.spacedog.io", backend.host());
		assertEquals("https://test.spacedog.io", backend.toString());
		assertEquals(443, backend.port());
		assertEquals("https", backend.scheme());
		assertEquals("test", backend.id());
		assertTrue(backend.ssl());
		assertFalse(backend.isMulti());

		try {
			backend.fromBackendId("test2");
			fail();

		} catch (SpaceException e) {
			assertEquals(400, e.httpStatus());
		}
	}

	@Test
	public void apiMultiBackendCanNotHandleWebAppRequest() {
		assertFalse(SpaceBackend.fromDefaults("production")//
				.fromRequest("test.www.spacedog.io")//
				.isPresent());
	}

	@Test
	public void webAppMultiBackendCanHandleWebAppRequest() {
		SpaceBackend backend = SpaceBackend.fromUrl("https://*.www.spacedog.io")//
				.fromRequest("test.www.spacedog.io").get();

		assertEquals("test.www.spacedog.io", backend.host());
		assertEquals(443, backend.port());
		assertEquals("https", backend.scheme());
		assertEquals("test", backend.id());
		assertTrue(backend.ssl());
		assertFalse(backend.isMulti());
		assertEquals("https://test.www.spacedog.io", backend.toString());
		assertEquals("https://test.www.spacedog.io/index.html", backend.url("/index.html"));
	}

	@Test
	public void api_backend_id_is_special() {

		SpaceBackend prod = SpaceBackend.fromDefaults("production");
		SpaceBackend backend = prod.fromRequest("api.spacedog.io").get();

		assertEquals(prod, backend);

		assertEquals("api.spacedog.io", backend.host());
		assertEquals("https://*.spacedog.io", backend.toString());
		assertEquals(443, backend.port());
		assertEquals("https", backend.scheme());
		assertEquals("spacedog", backend.id());
		assertTrue(backend.ssl());
		assertTrue(backend.isMulti());
		assertEquals("https://api.spacedog.io/1/data", backend.url("/1/data"));
	}

	@Test
	public void spacedog_backend_id_is_special() {

		SpaceBackend prod = SpaceBackend.fromDefaults("production");
		SpaceBackend backend = prod.fromRequest("spacedog.spacedog.io").get();

		assertEquals(prod, backend);

		assertEquals("api.spacedog.io", backend.host());
		assertEquals("https://*.spacedog.io", backend.toString());
		assertEquals(443, backend.port());
		assertEquals("https", backend.scheme());
		assertEquals("spacedog", backend.id());
		assertTrue(backend.ssl());
		assertTrue(backend.isMulti());
		assertEquals("https://api.spacedog.io/1/data", backend.url("/1/data"));
	}

	@Test
	public void testInstanciateWithApiOrSpacedogBackendId() {
		assertEquals(SpaceBackend.production, //
				SpaceBackend.production.fromBackendId("api"));
		assertEquals(SpaceBackend.production, //
				SpaceBackend.production.fromBackendId("spacedog"));
	}
}
