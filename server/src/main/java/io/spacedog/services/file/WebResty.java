/**
 * © David Attias 2015
 */
package io.spacedog.services.file;

import java.io.InputStream;

import org.elasticsearch.common.Strings;

import io.spacedog.client.credentials.Credentials;
import io.spacedog.client.credentials.Permission;
import io.spacedog.client.file.FileBucket;
import io.spacedog.client.file.SpaceFile;
import io.spacedog.client.http.SpaceHeaders;
import io.spacedog.client.http.WebPath;
import io.spacedog.server.Server;
import io.spacedog.server.Services;
import io.spacedog.server.SpaceFilter;
import io.spacedog.server.SpaceResty;
import io.spacedog.utils.Exceptions;
import net.codestory.http.Context;
import net.codestory.http.constants.Methods;
import net.codestory.http.filters.PayloadSupplier;
import net.codestory.http.payload.Payload;

@SuppressWarnings("serial")
public class WebResty extends SpaceResty implements SpaceFilter {

	@Override
	public boolean matches(String uri, Context context) {
		return uri.startsWith("/2/web") || Server.context().isWww();
	}

	@Override
	public Payload apply(String uri, Context context, PayloadSupplier nextFilter) throws Exception {

		String method = context.method();

		if (Methods.GET.equals(method))
			return doGet(toWebPath(uri), context);

		if (Methods.HEAD.equals(method))
			return doHead(toWebPath(uri), context);

		throw Exceptions.methodNotAllowed(method, uri);
	}

	//
	// Implementation
	//

	private Payload doGet(WebPath path, Context context) {
		return doGet(true, path, context);
	}

	private Payload doHead(WebPath path, Context context) {
		return doGet(false, path, context);
	}

	private Payload doGet(boolean withContent, WebPath webPath, Context context) {

		Payload payload = Payload.notFound();

		if (webPath.size() > 0) {

			WebPath path = webPath.removeFirst();
			FileBucket bucket = Services.files().getBucket(webPath.first());

			Credentials credentials = Server.context().credentials();
			bucket.permissions.checkPermission(credentials, Permission.read);

			SpaceFile file = Services.files().getMeta(bucket.name, path.toString(), false);

			if (file == null)
				file = Services.files().getMeta(bucket.name, //
						path.addLast("index.html").toString(), //
						false);

			if (file == null //
					&& !Strings.isNullOrEmpty(bucket.notFoundPage))
				file = Services.files().getMeta(bucket.name, //
						WebPath.parse(bucket.notFoundPage).toString(), //
						false);

			if (file != null) {
				payload = toPayload(file, //
						Services.files().getAsByteStream(bucket.name, file), //
						context);
			}
		}

		return payload;
	}

	private Payload toPayload(SpaceFile file, InputStream content, Context context) {

		Payload payload = new Payload(file.getContentType(), content)//
				.withHeader(SpaceHeaders.ETAG, file.getHash());

		// Since fluent-http only provides gzip encoding,
		// we only set Content-Length header if Accept-encoding
		// does not contain gzip. In case client accepts gzip,
		// fluent will gzip this file stream and use 'chunked'
		// Transfer-Encoding incompatible with Content-Length header

		if (!context.header(SpaceHeaders.ACCEPT_ENCODING).contains(SpaceHeaders.GZIP))
			payload.withHeader(SpaceHeaders.CONTENT_LENGTH, //
					Long.toString(file.getLength()));

		return payload;
	}

	private WebPath toWebPath(String uri) {

		return Server.context().isWww() //
				// add www bucket prefix
				? WebPath.parse(uri).addFirst("www")
				// remove '/1/web'
				: WebPath.parse(uri.substring(6));
	}

}
