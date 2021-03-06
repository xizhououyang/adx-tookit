package com.immomo.exchange.client.nio;

import com.immomo.exchange.client.connection.Connection;
import com.immomo.exchange.client.connection.NIOHandler;
import com.immomo.exchange.client.event.ChangeEvent;
import com.immomo.exchange.client.event.ConnectEvent;
import com.immomo.exchange.client.event.NIOEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by wudikua on 2016/4/4.
 */
public class SingleThreadSelector implements Runnable {

	private static final Logger logger = LoggerFactory.getLogger(SingleThreadSelector.class);

	private Selector selector;

	private Queue<NIOEvent> pending = new ConcurrentLinkedQueue<NIOEvent>();

	private static ExecutorService reactor = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors() * 2,
			Runtime.getRuntime().availableProcessors() * 16, 10 * 1000,
			TimeUnit.MILLISECONDS,
			new LinkedBlockingQueue<Runnable>(100)
	);

	private boolean started = false;

	private AtomicBoolean wakenUp = new AtomicBoolean();

	private int selectZeroCount = 0;
	private int maxZeroCount = 20;

	public SingleThreadSelector() {
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
		new Thread(this).start();
	}

	public void register(SocketChannel channel, int op, Connection connection, SelectionKey sk) {
		if (SelectionKey.OP_CONNECT == op) {
			pending.add(new ConnectEvent(channel, connection, op, sk));
		} else {
			pending.add(new ChangeEvent(channel, connection, op, sk));
		}
		if (wakenUp.compareAndSet(false, true)) {
			selector.wakeup();
		}
	}

	private void changeEvent() {
		if (!pending.isEmpty()) {
			Iterator<NIOEvent> it = pending.iterator();
			while(it.hasNext()) {
				NIOEvent e = it.next();
				try {
					if (e.getSelectionKey() == null) {
						if (!e.getChannel().isRegistered()) {
							e.getChannel().register(selector, e.getOp(), e.getConnection());
							it.remove();
						} else {
							logger.info("register next term");
						}
					} else if (e.getSelectionKey().isValid()) {
						e.getSelectionKey().interestOps(e.getOp());
						it.remove();
					} else {
						it.remove();
					}
				} catch (Exception  ex) {
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
				wakenUp.set(false);
				select = selector.select(1000);
			} catch (IOException e) {
				e.printStackTrace();
				continue;
			}
			if (select > 0) {
				selectZeroCount = 0;
				Iterator<SelectionKey> it = selector.selectedKeys().iterator();
				while (it.hasNext()) {
					NIOHandler handler = null;
					SelectionKey sk = null;
					try {
						sk = it.next();
						if (!sk.isValid()) {
							sk.cancel();
							continue;
						}
						handler = (NIOHandler) sk.attachment();
						if (handler.isClosed()) {
							sk.cancel();
							continue;
						}
						if (sk.isConnectable()) {
							sk.interestOps(sk.interestOps() & (~SelectionKey.OP_CONNECT));
							reactor.submit(new ConnectTask(handler, sk));
						} else if (sk.isWritable()) {
							sk.interestOps(sk.interestOps() & (~SelectionKey.OP_WRITE));
							reactor.submit(new WriteTask(handler, sk));
						} else if (sk.isReadable()) {
							sk.interestOps(sk.interestOps() & (~SelectionKey.OP_READ));
							reactor.submit(new ReadTask(handler, sk));
						} else {
							sk.cancel();
						}
					} catch (Exception e) {
						if (handler != null) {
							handler.close();
						}
						if (sk != null) {
							sk.cancel();
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
