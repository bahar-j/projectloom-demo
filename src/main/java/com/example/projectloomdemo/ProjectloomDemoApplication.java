package com.example.projectloomdemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.embedded.tomcat.TomcatProtocolHandlerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@SpringBootApplication
public class ProjectloomDemoApplication {

	private static final Pattern WORKER_PATTERN = Pattern.compile("worker-[\\d?]");
	private static final Pattern POOL_PATTERN = Pattern.compile("ForkJoinPool-[\\\\d?]");

	public static void main(String[] args) {
		SpringApplication.run(ProjectloomDemoApplication.class, args);
	}

	@Bean
	public AsyncTaskExecutor asyncTaskExecutor() {
		return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
	}

	@Bean
	public TomcatProtocolHandlerCustomizer<?> protocolHandlerVirtualThreadExecutorCustomizer() {
		return protocolHandler -> {
			protocolHandler.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
		};
	}

	@RestController
	static class MyController {

		final JdbcTemplate jdbcTemplate;

		MyController(JdbcTemplate jdbcTemplate) {
			this.jdbcTemplate = jdbcTemplate;
		}

		@GetMapping("/sleep")
		String sleep() throws InterruptedException {
			System.out.println("current thread: " + Thread.currentThread());
			Thread.sleep(5000);
			System.out.println("current thread: " + Thread.currentThread()); // platform thread can be changed
			return "ok";
		}

		@GetMapping("/example")
		String example() {
			System.out.println("current thread: " + Thread.currentThread());
			// runs synchronously
			String result = jdbcTemplate.queryForList("select pg_sleep(10);").toString(); // umount -> mount
			System.out.println(result + ": task with result of blocking I/O");
			System.out.println("next task");
			System.out.println("current thread: " + Thread.currentThread()); // platform thread can be changed
			return result;
		}

//		@GetMapping("/async")
//		String async() {
//			try (var scope = new StructuredTaskScope.ShutdownOnSuccess<String>()) {
//				Future<String> resultA = scope.fork(this::readFromApi);
//				Future<String> resultB = scope.fork(this::readFromApi);
//				Future<String> resultC = scope.fork(this::readFromApi);
//
//				scope.join();
//				String result = scope.result();
//				return result;
//			} catch (ExecutionException e) {
//				throw new RuntimeException(e);
//			} catch (InterruptedException e) {
//				throw new RuntimeException(e);
//			}
//		}
//
//		private String readFromApi() throws InterruptedException {
//			Thread.sleep(1000);
//			return "result";
//		}


		@GetMapping("/test")
		String test() throws InterruptedException {
			Set<String> poolNames = ConcurrentHashMap.newKeySet();
			Set<String> pThreadNames = ConcurrentHashMap.newKeySet();

			var threads = IntStream.range(0, 1000_0000)
					.mapToObj(i -> Thread.ofVirtual()
							.unstarted(() -> {
								String poolName = readPoolName();
								poolNames.add(poolName);
								String workerName = readWorkerName();
								pThreadNames.add(workerName);
							}))
					.collect(Collectors.toList());

			Instant begin = Instant.now();
			threads.forEach(Thread::start);
			for (var thread : threads) {
				thread.join();
			}
			Instant end = Instant.now();
			System.out.println("# time = " + Duration.between(begin, end).toMillis() + " ms");
			System.out.println("# cores = " + Runtime.getRuntime().availableProcessors());
			System.out.println("# pools: " + poolNames.size());
			System.out.println("# platform threads: " + pThreadNames.size());
			return "OK";
		}

		private static String readWorkerName() {
			String name = Thread.currentThread().toString();
			Matcher workerMatcher = WORKER_PATTERN.matcher(name);
			if (workerMatcher.find()) {
				return workerMatcher.group();
			}
			return "worker not found";
		}

		private static String readPoolName() {
			String name = Thread.currentThread().toString();
			Matcher poolMatcher = POOL_PATTERN.matcher(name);
			if (poolMatcher.find()) {
				return poolMatcher.group();
			}
			return "pool not found";
		}
	}


}
