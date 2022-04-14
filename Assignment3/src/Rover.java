import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Rover {

	private static final int TWELVE_SECONDS = 200;
	private static final int ONE_MINUTE = 1000;
	private static final int TEN_MINUTES = 10_000;
	private static final int ONE_HOUR = 60_000;

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

		@Override
		public String toString() {
			return "TempReading [reading=" + reading + ", time=" + time + "]";
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
			System.out.println("Reading: "+ value);
			List<Integer> l = readings.get(value.getTime());
			if (l == null) {
				l = new ArrayList<>();
				readings.put(value.getTime(), l);
			}
			l.add(value.getReading());
		}

		private static Integer min(List<Integer> list) {
			if (list.isEmpty())
				return Integer.MAX_VALUE;
			if (list.size() == 1)
				return list.get(0);
			int min = list.get(0);
			for (int i : list)
				if (i < min)
					min = i;
			return min;
		}

		private static Integer max(List<Integer> list) {
			if (list.isEmpty())
				return Integer.MIN_VALUE;
			if (list.size() == 1)
				return list.get(0);
			int max = list.get(0);
			for (int i : list)
				if (i > max)
					max = i;
			return max;
		}

		public void output() {
			System.out.println("Have a total of " + this.readings.size() + " readings from " + readings.firstKey()
					+ " to " + readings.lastKey());
			Instant start = readings.firstKey();

			while (start.isBefore(readings.lastKey())) {

				Instant end = readings.floorKey(start.plus(highLowPeriod));

				List<Integer> highest = new ArrayList<>();
				List<Integer> lowest = new ArrayList<>();
				Instant highStart = null, highEnd = null;
				int recordDiff = 0;

				for (Instant key = start; key.isBefore(end); key = readings.higherKey(key)) {
					if (key.minus(deltaPeriod).isAfter(start)) {
						int curDiff = Math.abs(min(readings.get(key))
								- max(readings.get(readings.ceilingKey(key.minus(deltaPeriod)))));
						if (curDiff > recordDiff) {
							highStart = key.minus(deltaPeriod);
							highEnd = key;
							recordDiff = curDiff;
						}
					}
					for (int reading : readings.get(key)) {
						if (highest.isEmpty() || reading > min(highest)) {
							if (highest.size() > 4)
								highest.remove(min(highest));
							highest.add(reading);
						}
						if (lowest.isEmpty() || reading < max(lowest)) {
							if (lowest.size() > 4)
								lowest.remove(max(lowest));
							lowest.add(reading);
						}
					}
				}
				Collections.sort(highest);
				Collections.sort(lowest);

				System.out.println("\n\nBetween " + start + " and " + end + ", we have:");
				if(highStart != null)
				System.out.println(
						"Record diff was between " + highStart + " and " + highEnd + " with a diff of " + recordDiff);
				else
					System.out.println("Period was shorter than the delta period.");
				System.out.println("The highest values were " + highest);
				System.out.println("The lowest values were " + lowest);

				start = end;
			}
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
						TempReading r = readings.pollFirst();
						if (r != null)
							stats.add(r);
						statsLock.unlock();
					}
			}));

		System.out.println("[" + Instant.now() + "]Simulation Starting");
		for (Thread t : threads)
			t.start();
		try {
			Thread.sleep(ONE_HOUR*3);
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
