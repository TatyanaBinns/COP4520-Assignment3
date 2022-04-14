import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Rover {

	private static final int TWELVE_SECONDS = 20;
	private static final int ONE_MINUTE = 100;
	private static final int TEN_MINUTES = 10_00;
	private static final int ONE_HOUR = 60_00;

	/**
	 * A generic concurrent linked list class. This uses atomic compare and set
	 * whenever altering the structure of the list techniques to ensure correctness.
	 * 
	 * @author Tatyana Binns
	 *
	 * @param <T> the type of value to store in this list
	 */
	public static class ConcurrentLinkedList<T extends Comparable<T>> implements Iterable<T> {
		/**
		 * An inner class used to hold values and references to the next item in the
		 * list.
		 * 
		 * @author Tatyana Binns
		 *
		 * @param <T> the type of value being stored
		 */
		public static class ConcurrentNode<T> {
			private T value;
			private AtomicReference<ConcurrentNode<T>> next = new AtomicReference<>();

			public ConcurrentNode(T data) {
				this.value = data;
			}

			public T value() {
				return this.value;
			}

			public ConcurrentNode<T> next() {
				return this.next.get();
			}

			public boolean compareAndSet(ConcurrentNode<T> expected, ConcurrentNode<T> newVal) {
				return this.next.compareAndSet(expected, newVal);
			}

			public void setNext(ConcurrentNode<T> n) {
				this.next.set(n);
			}
		}

		private AtomicReference<ConcurrentNode<T>> head = new AtomicReference<>(null);

		public void addFirst(T item) {
			ConcurrentNode<T> newNode = new ConcurrentNode<>(item);
			while (true) {
				ConcurrentNode<T> h = this.head.get();
				newNode.setNext(h);
				if (this.head.compareAndSet(h, newNode))
					return;
			}
		}

		public T pollFirst() {
			while (true) {
				ConcurrentNode<T> res = this.head.get();
				if (res == null)
					return null;
				if (this.head.compareAndSet(res, res.next()))
					return res.value();
			}
		}

		public void clear() {
			this.head.set(null);
		}

		public boolean isEmpty() {
			return this.head.get() == null;
		}

		public void insert(T item) {
			ConcurrentNode<T> newVal = new ConcurrentNode<>(item);
			while (true) {
				ConcurrentNode<T> h = head.get();
				if (h == null)
					if (head.compareAndSet(h, newVal))
						return;
					else
						continue;
				while (h.next() != null && h.next().value().compareTo(item) < 1)
					h = h.next();
				ConcurrentNode<T> prevNext = h.next();
				if (h.compareAndSet(prevNext, newVal))
					return;
			}
		}

		public void delete(T item) {
			if (head.get() == null)
				return;
			while (true) {
				ConcurrentNode<T> h = head.get();
				if (h.value().compareTo(item) == 0)
					if (this.head.compareAndSet(h, h.next()))
						return;
					else
						continue;
				while (h.next() != null && h.next().value().compareTo(item) != 0)
					h = h.next();
				ConcurrentNode<T> prevNext = h.next();
				ConcurrentNode<T> prevNextNext = h.next().next();
				if (h.compareAndSet(prevNext, prevNextNext))
					return;
			}
		}

		public boolean contains(T item) {
			ConcurrentNode<T> v = head.get();
			if (v == null)
				return false;
			while (v != null)
				if (v.value().compareTo(item) == 0)
					return true;
				else
					v = v.next();
			return false;
		}

		@Override
		public Iterator<T> iterator() {
			return new Iterator<>() {
				private ConcurrentNode<T> cur = head.get();

				@Override
				public boolean hasNext() {
					return cur != null;
				}

				@Override
				public T next() {
					T v = cur.value();
					cur = cur.next();
					return v;
				}
			};
		}
	}

	public static class TempReading implements Comparable<TempReading> {
		private int reading;
		private Instant time;

		public TempReading(int reading, Instant time) {
			this.reading = reading;
			this.time = time;
		}

		public int getReading() {
			return reading;
		}

		public Instant getTime() {
			return time;
		}

		public static TempReading make() {
			return new TempReading(ThreadLocalRandom.current().nextInt(-100, 71), Instant.now());
		}

		public int compareTo(TempReading o) {
			return this.time.compareTo(o.time);
		}
	}

	public static class Stats {

		private NavigableMap<Instant, List<Integer>> readings = new TreeMap<>();
		private Duration deltaPeriod;
		private Duration highLowPeriod;

		public Stats(Duration deltaAnalysisPeriod, Duration highLowAnalysisPeriod) {
			this.deltaPeriod = deltaAnalysisPeriod;
			this.highLowPeriod = highLowAnalysisPeriod;
		}

		public void add(TempReading value) {
			List<Integer> l = readings.get(value.getTime());
			if (l == null) {
				l = new ArrayList<>();
				readings.put(value.getTime(), l);
			}
			l.add(value.getReading());
		}

		public void output() {

		}
	}

	private static boolean simRunning = true;

	public static void main(String[] args) {
		ConcurrentLinkedList<TempReading> readings = new ConcurrentLinkedList<>();

		Duration readingSoon = Duration.ofMillis(TWELVE_SECONDS);
		Duration betweenReadings = Duration.ofMillis(ONE_MINUTE);

		Duration deltaAnalysisPeriod = Duration.ofMillis(TEN_MINUTES);
		Duration highLowAnalysisPeriod = Duration.ofMillis(ONE_HOUR);

		Stats stats = new Stats(deltaAnalysisPeriod, highLowAnalysisPeriod);

		Lock statsLock = new ReentrantLock();
		List<Thread> threads = new ArrayList<>();
		for (int i = 0; i < 8; i++)
			threads.add(new Thread(() -> {
				Instant nextReadingTime = Instant.now();
				while (simRunning)
					if (nextReadingTime.isBefore(Instant.now())) {
						readings.insert(TempReading.make());
						nextReadingTime = nextReadingTime.plus(betweenReadings);
					} else if (nextReadingTime.minus(readingSoon).isBefore(Instant.now()))
						continue;
					else if (!readings.isEmpty() && statsLock.tryLock()) {
						stats.add(readings.pollFirst());
						statsLock.unlock();
					}
			}));

		System.out.println("[" + Instant.now() + "]Simulation Starting");
		for (Thread t : threads)
			t.start();
		try {
			Thread.sleep(ONE_HOUR * 3);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		simRunning = false;
		for (Thread t : threads)
			try {
				t.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		System.out.println("[" + Instant.now() + "]Simulation over, outputting results...");

		stats.output();
	}

}
