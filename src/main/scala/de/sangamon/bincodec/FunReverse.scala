package de.sangamon.bincodec

import java.nio.file._
import java.time._
import java.{io => jio}

import cats._
import cats.implicits._
import cats.data._
import cats.effect._
import javax.xml.stream._
import javax.xml.stream.events._
import scodec._
import scodec.codecs._
import scodec.stream._

import scala.{io => sio}

/**
 * - Print reverse of input lines, exit on empty line
 * - Write call log to file
 * - On exit, dump call log to XML file
 */
object FunReverse extends IOApp {

  case class Call(time: Instant, input: String, result: String)

  object Call {
    val instantCodec: Codec[Instant] = vlong.xmap[Instant](Instant.ofEpochMilli, _.toEpochMilli)
    implicit val callCodec: Codec[Call] = (instantCodec :: utf8_32 :: utf8_32).as[Call]
  }

  trait Log[F[_], A] { def log(item: A): F[Unit] }

  object Log {

    def apply[F[_], A](implicit log: Log[F, A]): Log[F, A] = implicitly[Log[F, A]]

    class FileLog[F[_] : Sync, A : Codec](out: jio.OutputStream) extends Log[F, A] {
      import Attempt._
      override def log(item: A): F[Unit] =
        implicitly[Codec[A]].encode(item) match {
          case Successful(bv) =>
            Sync[F].delay {
              out.write(bv.toByteArray)
              out.flush()
            }
          case Failure(err) =>
            new IllegalStateException(err.messageWithContext).raiseError[F, Unit]
        }
    }

    def fileLog[F[_] : Sync, A : Codec](target: Path): Resource[F, Log[F, A]] =
      Resource
        .fromAutoCloseable(Sync[F].delay { Files.newOutputStream(target) })
        .map(new FileLog(_))

    implicit def logSyncLog[F[_] : Sync, A : Codec]: Log[ReaderT[F, Log[F, A], *], A] = (item: A) => ReaderT(_.log(item))

    def logFileContent[F[_] : Sync : ContextShift, A : Codec](source: Path)(implicit blocker: Blocker): fs2.Stream[F, A] = {
      val callsDecoder = StreamDecoder.many(implicitly[Codec[A]]).toPipeByte[F]
      fs2.io.file.readAll[F](source, blocker, 4096).through(callsDecoder)
    }

  }

  object XMLCallLogUtil {

    class XMLBuilder[F[_]]() {
      val evFact: XMLEventFactory = XMLEventFactory.newInstance()

      def txt(value: String): fs2.Stream[F, XMLEvent] =
        fs2.Stream(evFact.createCharacters(value))

      def elt(name: String)(nested: fs2.Stream[F, XMLEvent]): fs2.Stream[F, XMLEvent] =
        fs2.Stream(evFact.createStartElement("", "", name)) ++
          nested ++
          fs2.Stream(evFact.createEndElement("", "", name))
    }

    def callToXml[F[_]](call: Call)(implicit bld: XMLBuilder[F]): fs2.Stream[F, XMLEvent] =
      bld.elt("call") {
        bld.elt("time")(bld.txt(call.time.toString)) ++
          bld.elt("input")(bld.txt(call.input)) ++
          bld.elt("output")(bld.txt(call.result))
      }

    def callsToXml[F[_]](calls: fs2.Stream[F, Call])(implicit bld: XMLBuilder[F]): fs2.Stream[F, XMLEvent] =
      bld.elt("calls")(calls.flatMap(callToXml[F]))

    // template: fs2.io#writeOutputStream
    def writeXml[F[_] : Sync](
        target: Path)(
        events: fs2.Stream[F, XMLEvent])(
        implicit blocker: Blocker, ctxShift: ContextShift[F]
    ): fs2.Stream[F, Unit] = {
      def delay[A, B](f: A => B)(a: A) = blocker.delay(f(a))
      val writer =
        fs2.Stream
          .bracket(blocker.delay(Files.newOutputStream(target)))(delay(_.close()))
          .evalMap(delay(XMLOutputFactory.newInstance().createXMLEventWriter))
      def write(writer: XMLEventWriter): fs2.Stream[F, Unit] = events.evalMap(delay(writer.add))
      writer >>= write
    }
  }

  object AppLogic {

    private def readLine[F[_] : Sync]: F[String] = Sync[F].delay { sio.StdIn.readLine("> ") }

    private def writeLine[F[_] : Sync](s: String): F[Unit] = Sync[F].delay { println(s) }

    // TODO Cannot use kind-projector in context bound?
    // Log[*, Call] complains about missing type param for F, Log[*[_], Call] crashes compiler
    private type CallLog[F[_]] = Log[F, Call]

    private def loggingReverse[F[_] : Sync : CallLog](inp: String): F[String] = {
      val rev = inp.reverse
      for {
        time <- Sync[F].delay { Instant.now() }
        _ <- Log[F, Call].log(Call(time, inp, rev))
      } yield rev
    }

    def runReverse[F[_] : Sync : CallLog]: F[Option[String]] =
      readLine[F] >>= {
        case "" => Option.empty[String].pure[F]
        case inp =>
          for {
            rev <- loggingReverse(inp)
            _ <- writeLine[F](rev)
          } yield Option(rev)
      }
  }

  override def run(args: List[String]): IO[ExitCode] = {
    import AppLogic._
    import Log._
    import XMLCallLogUtil._
    implicit val xmlBld: XMLBuilder[IO] = new XMLBuilder()
    type LogIO[A] = ReaderT[IO, Log[IO, Call], A]
    val logPath = Paths.get("calls.binlog")
    val xmlPath = Paths.get("calls.xml")
    val repl = Monad[LogIO].iterateWhile(runReverse[LogIO])(_.isDefined)
    val dumpXmlLog =
      fs2.Stream.resource(Blocker[IO]) >>= { implicit blocker =>
        logFileContent[IO, Call](logPath)
          .through(callsToXml[IO])
          .through(writeXml(xmlPath))
      }
    val msg =
      s"""|*** FunReverse ***
          |Entering an arbitrary line will print the reverse of the input.
          |Entering an empty line will exit processing.
          |Binary call log is written to ${logPath.toAbsolutePath}""".stripMargin
    IO { println(msg) } >>
      fileLog[IO, Call](logPath).use(repl.run) >>
      dumpXmlLog.compile.drain.as(ExitCode.Success) <*
      IO { println(s"XML call log has been written to ${xmlPath.toAbsolutePath}") }
  }

}
