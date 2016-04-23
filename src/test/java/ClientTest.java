import com.immomo.exchange.client.Client;
import com.immomo.exchange.client.ThirdHttpClient;
import com.immomo.exchange.client.nio.MultiThreadSelector;
import com.immomo.exchange.client.protocal.Response;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by mengjun on 16/4/1.
 */
public class ClientTest {

//	private static final String url = "http://127.0.0.1:8880";
	private static final String url = "http://m.baidu.com";

	private static Client client;

	static {
		MultiThreadSelector selector = new MultiThreadSelector(1);
		try {
			selector.init();
			selector.start();
			client = new Client(selector);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Test
	public void getTimeout() throws Exception {
		Future<Response> future = client.get(url);
		Response resp = future.get(10, TimeUnit.MILLISECONDS);
		if (resp == null) {
			future.cancel(true);
			System.out.println("null");
		} else {
			System.out.println(System.currentTimeMillis()/1000 + " " + new String(resp.getBody()).substring(0, 10) + "...");
		}
		Thread.sleep(1000);
	}

	@Test
	public void hex() {
		String s = "17ffe";
		int n=0;
		for (int i=0; i<s.length(); i++) {
			if (s.charAt(i) >= 'a') {
				n = n*16 + 10 + s.charAt(i) - 'a';
			} else {
				n = n*16 + s.charAt(i) - '0';
			}
		}
		System.out.println(n);
	}

	@Test
	public void getOne() throws Exception {
		request();
		synchronized (ClientTest.class) {
			ClientTest.class.wait();
		}
	}

	@Test
	public void abNing() throws InterruptedException {
		int N = 10;
		final CountDownLatch finish = new CountDownLatch(N);
		Long begin = System.currentTimeMillis();
		final AtomicLong success = new AtomicLong(0);
		for (int i=0; i<N; i++) {
			new Thread(new Runnable() {
				@Override
				public void run() {
					for (int j =0; j<10; j++) {
						try {
							Future f = ThirdHttpClient.client.prepareGet(url).execute();
							com.ning.http.client.Response r = ThirdHttpClient.waitResponse(f, 3000);
							if (r.getStatusCode() == 200) {
								System.out.println("thread " + Thread.currentThread() + " success");
								success.incrementAndGet();
							}
							if (success.get() % 5 == 0) {
								System.out.println("thread " + Thread.currentThread() + " " + success.get());
							}
						} catch (Exception e) {

						}
					}
					finish.countDown();
				}
			}).start();
		}
		finish.await();
		Long end = System.currentTimeMillis();
		System.out.println("time total use " + (end - begin) + " request " + N + " success " + success.get());
	}

	@Test
	public void ab() throws InterruptedException {
		int N = 2;
		final CountDownLatch finish = new CountDownLatch(N);
		Long begin = System.currentTimeMillis();
		final AtomicLong success = new AtomicLong(0);
		for (int i=0; i<N; i++) {
			new Thread(new Runnable() {
				@Override
				public void run() {
					for (int j =0; j<10; j++) {
						try {
							Future<Response> future = client.get(url);
							Response resp = future.get(3000, TimeUnit.MILLISECONDS);
							if (resp.getStatus() == 200) {
								success.incrementAndGet();
//								System.out.println(new String(resp.getBody()));
							}
						} catch (Exception e) {

						}
						if (success.get()%5 == 0) {
							System.out.println("thread " + Thread.currentThread() + " " + success.get());
						}
					}
					finish.countDown();
				}
			}).start();
		}
		finish.await();
		Long end = System.currentTimeMillis();
		System.out.println("time total use " + (end - begin) + " request " + N + " success " + success.get());
	}

	@Test
	public void testTimePerformance() {
		Long b = System.currentTimeMillis();
		for (int i=0; i<100000; i++) {
			System.currentTimeMillis();
		}
		System.out.println((System.currentTimeMillis() - b));
	}

	@Test
	public void get() throws Exception {
		for (int i=0; i<1; i++) {
			new Thread(new Runnable() {
				public void run() {
					for (int i = 0; i < 1; i++) {
						try {
							request();
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
					synchronized (ClientTest.class) {
						ClientTest.class.notifyAll();
					}
				}
			}).start();
		}
		synchronized (ClientTest.class) {
			ClientTest.class.wait();
		}
	}


	private void request() throws Exception {
		Future<Response> future = client.get(url);
		Response resp = future.get();
		if (resp == null) {
			future.cancel(true);
			System.out.println("null");
		} else {
			System.out.println(System.currentTimeMillis()/1000 + " " + new String(resp.getBody()).substring(0, 13) + "...");
		}
//		Thread.sleep(1000);
	}

	@Test
	public void server() {
		new TimeServer();
	}

}
