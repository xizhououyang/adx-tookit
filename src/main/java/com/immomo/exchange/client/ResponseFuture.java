package com.immomo.exchange.client;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Created by wudikua on 2016/4/9.
 */
public class ResponseFuture implements Future<Response> {

	private Connection connection;

	private volatile boolean cancelled = false;

	private volatile boolean done = false;

	public ResponseFuture(Connection connection) {
		this.connection = connection;
	}

	public boolean cancel(boolean mayInterruptIfRunning) {
		if (cancelled) {
			return true;
		}
		connection.close();
		return true;
	}

	public boolean isCancelled() {
		return cancelled;
	}

	public boolean isDone() {
		return done;
	}

	public Response get() throws InterruptedException, ExecutionException {
		synchronized (connection.notify) {
			connection.notify.wait();
		}
		Response response =  connection.response;
		connection.finish();
		connection.response = null;
		return response;
	}

	public Response get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
		return null;
	}
}