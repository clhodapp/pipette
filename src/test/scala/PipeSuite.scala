
import org.scalatest.FunSuite

class PipeSuite extends FunSuite {

	test("Sum with Pipe") {

		import concurrent.future
		import concurrent.ExecutionContext.Implicits.global

		def sum(a: Array[Int], p: Pipe[Int]) {
			var sum = 0
			for (x <- a) sum += x
			p.write(sum)
		}

		val a = Array(7, 2, 8, -9, 4, 0)

		val p = new Pipe[Int]

		future { sum(a.take(a.length/2), p) }
		future { sum(a.drop(a.length/2), p) }

		val x, y = p.read()

		assert(x + y === a.sum)
		assert(x == a.drop(a.length/2).sum || y == a.drop(a.length/2).sum)

	}

	test("Fibonacci with Pipe") {

    import concurrent.future
    import concurrent.ExecutionContext.Implicits.global

      def fibonacci(n: Int, p: Pipe[Int]) {
        var (x, y) = (0, 1)
          for (i <- 0 until n) {
            p.write(x)
              y += x
              x = y - x
          }
        p.close()
      }

    val p = new Pipe[Int]
    future { fibonacci(10, p) }
    for (i <- p) println(i)
  }

}
