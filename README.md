# COP4520 Assignment3

## Usage
To compile and run, first ensure you have a properly setup and installed JDK with access to the `javac` and `java` commands. Then:
* Clone this repository
* Open a terminal
* Navigate to wherever you cloned the repository, then to Assignment3/src
* Run `javac BirthdayPresents.java`
* Run `java BirthdayPresents`

Once content with part 1, to run part two:
* Navigate to wherever you cloned the repository, then to Assignment3/src
* Run `javac Rover.java`
* Run `java Rover`


## Problem One: What could have Gone Wrong? 



## Efficiency

### Problem One
This is actually fairly efficient, if only because the size of the list remains fairly small. While it's odd, since we never have to buffer the entire set of presents in the ordered list, it will often times only have a small number of gifts in it, resulting in very fast runtimes. For insert operations, it ends up being `O(n)` where `n` is the size of the list, since we simply iterate from the beginning to find our spot, and re-try whenever we have a collision due to multi-threading. This is similar for delete and contains, as they both need to search as well, though contains doesn't need any retries since it's a read-only operation.

### Problem Two
This is also fairly efficient, it ensures that the ony times when data is moved is in theory during 'off periods' when the threads aren't otherwise busy. This happens when we aren't near a reading time. This also only is done by one thread at a time due to the lock, which in particular uses "tryLock" which will not block if it can't get a lock, thus ensuring we don't have threads doing things at multiple times.


## Problem One Correctness

This is challenging, but correct as we ensure to use only an atomic compare and set whenever changing the structure of the list. We can do this by taking advantage of java's Atomic Reference class, which makes this fairly easy on an object level.

## Problem Two Correctness

This is as correct as we can manage, it runs eight threads which all use the previously built linked list class, that's currently operating more like a buffer than anything else. This buffer acclumulates and gets pulled out of in `O(1)`, and with the same correctness methods. We then just limit the number of threads pulling out of this and storing data into the final statistics, and this avoids race conditions.

The statistics could probably use more work, but that isn't really due to any parallelism. It seems fairly correct however.

Of note, this runs at non-real time, as running for hours to test wasn't really a good idea. All durations are tweakable at the top of the program, and by default one simulation hour is sixty seconds. The simulation runs for a little over three 'hours' in the current version.


