package io.spacedog.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Sets;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;

import io.spacedog.utils.Check;
import io.spacedog.utils.Exceptions;
import io.spacedog.utils.WebPath;

@Parameters(commandNames = { "sync" }, //
commandDescription = "synchronize source folder to backend")
public class FileSynchronizer {

	@Parameter(names = { "-s", "--source" }, //
	required = true, //
	description = "the source directory to synchronize")
	private String source;

	@Parameter(names = { "-b", "--backend" }, //
	required = true, //
	description = "the backend identifier to synchronize to")
	private String backendId;

	@Parameter(names = { "-p", "--prefix" }, //
	required = true, //
	description = "the file bucket prefix to use")
	private String prefix;

	@Parameter(names = { "-l", "--login" }, //
	required = true, //
	description = "the administrator login to use")
	private String login;

	private String password = "hi test";

	/**
	 * Set of server file path checked with matching local files. A checked
	 * local file is uploaded if different.
	 */
	private Set<String> checked = Sets.newHashSet();

	/**
	 * Set of local file path uploaded to the server.
	 */
	private Set<String> uploaded = Sets.newHashSet();

	/**
	 * Set of server file path deleted from the server.
	 */
	private Set<String> deleted = Sets.newHashSet();

	public static FileSynchronizer newInstance() {
		return new FileSynchronizer();
	}

	public FileSynchronizer source(String source) {
		this.source = source;
		return this;
	}

	public FileSynchronizer source(Path source) {
		this.source = source.toString();
		return this;
	}

	public FileSynchronizer backendId(String backendId) {
		this.backendId = backendId;
		return this;
	}

	public FileSynchronizer prefix(String prefix) {
		this.prefix = prefix;
		return this;
	}

	public FileSynchronizer login(String login) {
		this.login = login;
		return this;
	}

	public FileSynchronizer password(String password) {
		this.password = password;
		return this;
	}

	private FileSynchronizer() {
	}

	public void synch() throws IOException {
		Check.notNull(source, "source");
		Check.notNullOrEmpty(backendId, "backend id");
		Check.notNullOrEmpty(prefix, "prefix");
		Check.notNullOrEmpty(login, "login");
		Check.notNullOrEmpty(password, "password");

		synchFromServer();
		synchFromLocal();
	}

	public Set<String> uploaded() {
		return Collections.unmodifiableSet(uploaded);
	}

	public Set<String> deleted() {
		return Collections.unmodifiableSet(deleted);
	}

	public Set<String> checked() {
		return Collections.unmodifiableSet(checked);
	}

	public void reset() {
		uploaded.clear();
		deleted.clear();
		checked.clear();
	}

	//
	// Implementation
	//

	private void synchFromServer() throws IOException {
		SpaceResponse response = SpaceRequest.get("/1/file/" + prefix)//
				.basicAuth(backendId, login, password)//
				.go(200, 404);

		if (response.httpResponse().getStatus() == 200) {
			Iterator<JsonNode> elements = response.get("results").elements();
			while (elements.hasNext()) {
				check(elements.next());
			}
		}
	}

	private void check(JsonNode file) throws IOException {
		String webPath = file.get("path").asText();
		// removes slash, prefix and slash
		String relativePath = webPath.substring(prefix.length() + 2);
		Path filePath = Paths.get(source).resolve(relativePath);

		if (Files.isRegularFile(filePath)) {
			checked.add(webPath);

			if (!check(filePath, file.get("etag").asText()))
				upload(filePath);

		} else
			delete(webPath);
	}

	private void synchFromLocal() throws IOException {

		Files.walk(Paths.get(source))//
				.filter(path -> !path.getFileName().toString().startsWith("."))//
				.filter(Files::isRegularFile)//
				.filter(this::notAlreadyCheckedAndSynched)//
				.forEach(path -> upload(path));
	}

	private boolean notAlreadyCheckedAndSynched(Path filePath) {
		String webPath = toWebPath(filePath).toString();
		return !checked.contains(webPath);
	}

	private WebPath toWebPath(Path filePath) {
		return WebPath//
				.parse(Paths.get(source).relativize(filePath).toString())//
				.addFirst(prefix);
	}

	private void delete(String webPath) {
		SpaceRequest.delete("/1/file" + webPath)//
				.basicAuth(backendId, login, password)//
				.go(200);

		deleted.add(webPath);
	}

	private void upload(Path filePath) {

		try {
			WebPath webPath = toWebPath(filePath);
			SpaceRequest.put("/1/file" + webPath.toEscapedString())//
					.basicAuth(backendId, login, password)//
					.bodyFile(filePath)//
					.go(200);

			uploaded.add(webPath.toString());
		} catch (Exception e) {
			Exceptions.runtime(e);
		}
	}

	private boolean check(Path path, String etag) throws IOException {
		HashCode local = com.google.common.io.Files.hash(path.toFile(), Hashing.md5());
		return local.equals(HashCode.fromString(etag));
	}
}
