package io.spacedog.client.data;

import java.util.Objects;

import org.joda.time.DateTime;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonAutoDetect(fieldVisibility = Visibility.ANY, //
		getterVisibility = Visibility.NONE, //
		isGetterVisibility = Visibility.NONE, //
		setterVisibility = Visibility.NONE)
public class DataObjectBase implements DataObject {

	private String owner;
	private String group;
	private DateTime createdAt;
	private DateTime updatedAt;

	public DataObjectBase() {
	}

	@Override
	public String owner() {
		return owner;
	}

	@Override
	public void owner(String owner) {
		this.owner = owner;
	}

	@Override
	public String group() {
		return group;
	}

	@Override
	public void group(String group) {
		this.group = group;
	}

	@Override
	public DateTime createdAt() {
		return createdAt;
	}

	@Override
	public void createdAt(DateTime createdAt) {
		this.createdAt = createdAt;
	}

	@Override
	public DateTime updatedAt() {
		return updatedAt;
	}

	@Override
	public void updatedAt(DateTime updatedAt) {
		this.updatedAt = updatedAt;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof DataObjectBase == false)
			return false;

		DataObjectBase meta = (DataObjectBase) obj;
		return Objects.equals(this.owner, meta.owner)//
				&& Objects.equals(this.group, meta.group)//
				&& Objects.equals(this.createdAt, meta.createdAt)//
				&& Objects.equals(this.updatedAt, meta.updatedAt);
	}
}