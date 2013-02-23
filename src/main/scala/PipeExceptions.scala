
class PipeException protected(val msg: String, val cause: Option[Throwable]) extends Exception(msg, cause.getOrElse(null)) {
	def this(msg: String) = this(msg, None)
	def this(msg: String, cause: Throwable) = this(msg, Some(cause))
}

class ClosedException protected() extends PipeException("Pipe is closed")
object ClosedException {
	def apply() = new ClosedException()
}

class BrokenPipeException protected(cause: Option[Throwable]) extends PipeException("Broken Pipe", cause)
object BrokenPipeException {
	def apply() = new BrokenPipeException(None)
	def apply(cause: Throwable) = new BrokenPipeException(Some(cause))
}

