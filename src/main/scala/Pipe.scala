
import annotation.tailrec
import concurrent.ExecutionContext
import concurrent.future
import collection.GenTraversableOnce
import collection.mutable.Builder
import java.util.concurrent.locks.ReentrantLock

class Pipe[Elem](implicit ec: ExecutionContext) extends TraversableOnce[Elem] { self =>

	import collection.mutable

	val queueLock = new ReentrantLock()

	val queueEvent = queueLock.newCondition()

	val queue = new mutable.Queue[Elem]()

	def withLock[Result](body: => Result): Result = {
		queueLock.lock()
		try body
		finally queueLock.unlock()
	}

	def withData[Result](body: => Result): Result = withLock {
		while (queue.isEmpty && !closed) queueEvent.await()
		body
	}

	@volatile
	var closed = false

	def isClosed: Boolean = closed

	def read(): Elem = withData {
		if (closed && queue.isEmpty) throw ClosedException()
		queue.dequeue()
	}

	def write(a: Elem): Unit = withLock {
		if (closed) throw ClosedException()
		queue.enqueue(a)
		queueEvent.signal()
	}

	def close() = withLock {
		closed = true
		queueEvent.signalAll()
	}

	def foreach[Result](f: Elem => Result) {

		@tailrec
		def impl {
			while (queue.isEmpty && !closed) queueEvent.await()
			if (closed && queue.isEmpty) ()
			else {
				f(queue.dequeue())
				impl
			}
		}

		future(withLock(impl))

	}

	def seq = this

	def isEmpty = withLock {
		queue.isEmpty
	}

	def isTraversableAgain = false

	def copyToArray[ArrayType >: Elem](xs: Array[ArrayType], start: Int, len: Int): Unit =
		withData {
			for (i <- start until start+len) xs(i) = queue.dequeue()
		}

	def exists(p: Elem => Boolean): Boolean = find(p).isDefined
	def forall(p: Elem => Boolean) = !exists(x => !p(x))
	
	def find(p: Elem => Boolean): Option[Elem] = withLock {
		while (queue.isEmpty && !closed) queueEvent.await()
		if (!queue.isEmpty) {
			val candidate = queue.dequeue()
			if (p(candidate)) Some(candidate)
			else find(p)
		} else None
	}

	def hasDefiniteSize = true

	override def size = withLock {
		queue.size
	}

	def toIterator = new Iterator[Elem] {
		def next() = self.read()
		def hasNext() = self.isEmpty
	}

	def toStream = withLock {
		while (queue.isEmpty && !closed) queueEvent.await()
		if (isEmpty) Stream.empty
		else queue.dequeue() #:: toStream
	}
	
	def toTraversable = toStream

	def map[NewElem](f: Elem => NewElem): Pipe[NewElem] = {

		val ret = new Pipe[NewElem]

		@tailrec
		def impl {
			while (queue.isEmpty && !closed) queueEvent.await()
			if (closed && queue.isEmpty) ()
			else {
				ret.write(f(queue.dequeue()))
				impl
			}
		}

		future{
			withLock(impl)
			ret.close()
		}

		ret
	}

	override def toList = toStream.toList

	def flatMap[NewElem](f: Elem => GenTraversableOnce[NewElem]): Pipe[NewElem] = {
		val ret = new Pipe[NewElem]

		@tailrec
		def impl {
			while (queue.isEmpty && !closed) queueEvent.await()
			if (closed && queue.isEmpty) ()
			else {
				f(queue.dequeue()).foreach(ret.write)
				impl
			}
		}

		future{
			withLock(impl)
			ret.close()
		}

		ret
	}

	def withFilter(p: Elem => Boolean): Pipe[Elem] = {
		val ret = new Pipe[Elem]
		for (i <- this if p(i)) ret.write(i)
		ret
	}

	def filter(p: Elem => Boolean): TraversableOnce[Elem] = withFilter(p)

}

