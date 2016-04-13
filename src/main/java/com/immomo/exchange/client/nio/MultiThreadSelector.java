package com.immomo.exchange.client.nio;

import com.immomo.exchange.client.connection.Connection;
import com.immomo.exchange.client.connection.NIOHandler;
import com.immomo.exchange.client.event.ConnectEvent;
import com.immomo.exchange.client.event.NIOEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Created by wudikua on 2016/4/4.
 */
public class MultiThreadSelector implements Runnable {

	private static final Logger logger = LoggerFactory.getLogger(MultiThreadSelector.class);

	private static Selector selector;

	private static Queue<NIOEvent> pending = new LinkedBlockingDeque<NIOEvent>();

	private int count;

	private boolean started = false;

	public MultiThreadSelector(int count) {
		this.count = count;
	}

	public Selector getSelector() {
		return selector;
	}

	public void init() throws IOException {
		if (started) {
			return;
		}
		started = true;
		selector = Selector.open();
	}

	public void start() {
		for (int i=0; i<count; i++) {
			new Thread(new MultiThreadSelector(count)).start();
		}
	}

	public void register(SocketChannel channel, int op, Connection connection) {
		pending.add(new ConnectEvent(channel, connection, op));
		logger.debug("add pending queue {}", pending.size());
		selector.wakeup();
	}

	private void changeEvent() {
		logger.debug("check pending size is {}", pending.size());
		if (pending.size() > 0) {
			logger.debug("something is to be register");
			Iterator<NIOEvent> it = pending.iterator();
			while(it.hasNext()) {
				NIOEvent e = it.next();
				it.remove();
				logger.debug("register {}", e.getConnection());
				try {
					e.getChannel().register(selector, e.getOp(), e.getConnection());
				} catch (ClosedChannelException  ex) {
					ex.printStackTrace();
					e.getConnection().close();
				}
			}
		}
	}

	public void run() {
		while(true) {
			changeEvent();
			int select = 0;
			try {
				select = selector.select();
			} catch (IOException e) {
				e.printStackTrace();
				continue;
			}
			logger.debug("select is {}", select);
			if (select > 0) {
				Iterator<SelectionKey> it = selector.selectedKeys().iterator();
				while (it.hasNext()) {
					NIOHandler handler = null;
					SelectionKey sk = null;
					try {
						sk = it.next();
						logger.debug("key op {}", sk.readyOps());
						handler = (NIOHandler) sk.attachment();
						if (sk.isConnectable()) {
							handler.connect(sk);
						} else if (sk.isWritable()) {
							handler.write(sk);
						} else if (sk.isReadable()) {
							handler.read(sk);
						}
					} catch (Exception e) {
						if (handler != null) {
							handler.close();
						}
						e.printStackTrace();
					} finally {
						it.remove();
					}
				}
			}

		}
	}
}