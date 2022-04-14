import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class BirthdayPresents {

	private static final int GUEST_COUNT = 5_000_000;

	public static class ConcurrentLinkedList<T extends Comparable<T>> implements Iterable<T> {
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
		}

		private AtomicReference<ConcurrentNode<T>> head = new AtomicReference<>(null);

		public T pollFirst() {
			while (true) {
				ConcurrentNode<T> res = this.head.get();
				if (res == null)
					return null;
				if (this.head.compareAndSet(res, res.next()))
					return res.value();
			}
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

	public static void main(String[] args) {

		List<String> gifts = new ArrayList<>(GUEST_COUNT);

		System.out.println("["+Instant.now()+"]Initializing gifts...");
		for (int i = 0; i < GUEST_COUNT; i++)
			gifts.add(String.format("Gift %6d", i));

		Collections.shuffle(gifts);

		System.out.println("["+Instant.now()+"]Gifts created.");

		ConcurrentLinkedList<String> orderedChain = new ConcurrentLinkedList<>();

		AtomicInteger gIndex = new AtomicInteger(0);

		
		List<Thread> threads = new ArrayList<>();
		for(int i = 0; i < 1; i++)
			threads.add(genServant(gIndex, gifts, orderedChain));
		
		System.out.println("["+Instant.now()+"]Starting Servants");
		for(Thread t : threads)
			t.start();

		for(Thread t : threads)
			try {
				t.join();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		System.out.println("["+Instant.now()+"]Complete");
	}

	private static Thread genServant(AtomicInteger gIndex, List<String> gifts,
			BirthdayPresents.ConcurrentLinkedList<String> orderedChain) {
		return new Thread(() -> {
			while (gIndex.get() < gifts.size() || !orderedChain.isEmpty()) {

				int action = ThreadLocalRandom.current().nextInt(3);
//				System.out.println("On Index: " + gIndex.get()+", taking action "+action);

				switch (action) {
				// Take a present, and add it to the ordered chain
				case 0:
					if (gIndex.get() >= gifts.size())
						continue;
					String giftFromPile = gifts.get(gIndex.getAndIncrement());
					orderedChain.insert(giftFromPile);
//					System.out.println("\nAdded " + giftFromPile + " to chain:");
//					for (String s : orderedChain)
//						System.out.println(s);
					break;
				// Write a thankyou card for the gift
				case 1:
					String giftFromChain = orderedChain.pollFirst();
					// Thank you whoever this is!
					if (giftFromChain != null)
						;// System.out.println("Thanks for " + giftFromChain);
//					System.out.println("\nWrote card for " + giftFromChain + ", remaining gifts on chain:");
//					for (String s : orderedChain)
//						System.out.println(s);
					break;
				case 2:
					if (orderedChain
							.contains(String.format("Gift %6d", ThreadLocalRandom.current().nextInt(GUEST_COUNT))))
						;
					break;
				}
			}
		});
	}

}
