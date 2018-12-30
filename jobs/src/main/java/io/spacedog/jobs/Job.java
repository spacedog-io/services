package io.spacedog.jobs;

import org.joda.time.DateTime;

import com.google.common.base.Throwables;

import io.spacedog.utils.Utils;

public abstract class Job {

	public static final String OK = "OK";

	private String firstname;
	private String lastname;

	public abstract String run();

	public Job firstname(String firstname) {
		this.firstname = firstname;
		return this;
	}

	public Job lastname(String lastname) {
		this.lastname = lastname;
		return this;
	}

	public String description() {
		if (firstname == null)
			firstname = this.getClass().getSimpleName();

		return Utils.join("/", firstname, lastname);
	}

	public String okOncePerDay() {
		int hourOfDay = DateTime.now().getHourOfDay();
		if (3 <= hourOfDay && hourOfDay < 4)
			ok();
		return OK;
	}

	public String ok() {
		notify(" is OK OK OK", "Everything is working properly.");
		return OK;
	}

	public String ok(String message) {
		notify(" is OK OK OK", message);
		return OK;
	}

	public String error(Throwable t) {
		return error(Throwables.getStackTraceAsString(t));
	}

	public String error(String message, Throwable t) {
		return error(message + "\n" + Throwables.getStackTraceAsString(t));
	}

	public String error(String message) {
		System.err.println();
		System.err.println(message);
		notify(" is DOWN DOWN DOWN", message);
		return message;
	}

	public void notify(String titleSuffix, String message) {
		Internals.get().notify(description() + titleSuffix, message);
	}
}
