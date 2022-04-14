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


## Efficiency

### Problem One
This is actually fairly efficient, if only because the size of the list remains fairly small. While it's odd, since we never have to buffer the entire set of presents in the ordered list, it will often times only have a small number of gifts in it, resulting in very fast runtimes. For insert operations, it ends up being `O(n)` where `n` is the size of the list, since we simply iterate from the beginning to find our spot, and re-try whenever we have a collision due to multi-threading. This is similar for delete and contains, as they both need to search as well, though contains doesn't need any retries since it's a read-only operation.

### Problem Two



## Problem One Correctness

This is challenging, but correct as we ensure to use only an atomic compare and set whenever changing the structure of the list. We can do this by taking advantage of java's Atomic Reference class, which makes this fairly easy on an object level.

## Problem Two Options




