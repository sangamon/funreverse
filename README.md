# FunReverse

Simple Scala CLI app based on [Cats](https://typelevel.org/cats/), [Cats Effect](https://typelevel.org/cats-effect/) and [fs2](https://fs2.io/). The goal of this learning project was to exercise the combined use of these libraries with a simplistic scenario that still transcends single type class/construct/library examples.

## Mission Statement

- A command line REPL that prints the reverse of each input line. Empty line input exits processing.
- Input/output pairs are appended to a log file together with a time stamp.
- After REPL exit, read the log file and dump it to an XML file.

## Sample Session

```
$ sbt run
[...]
*** FunReverse ***
Entering an arbitrary line will print the reverse of the input.
Entering an empty line will exit processing.
Binary call log is written to /home/patrick/prog/scala/funreverse/calls.binlog
> test
tset
> blah
halb
>
XML call log has been written to /home/patrick/prog/scala/funreverse/calls.xml
[success] Total time: 19 s, completed Apr 13, 2020 9:49:32 PM
$ cat /home/patrick/prog/scala/funreverse/calls.xml
<calls><call><time>2020-04-13T19:49:28.887Z</time><input>test</input><output>tset</output></call><call><time>2020-04-13T19:49:31.284Z</time><input>blah</input><output>halb</output></call></calls>
```

## Implementation Notes

- REPL is implemented via `Monad#iterateWhile()`.
- Logger instance is passed via `ReaderT`.
- Log entries are written using [scodec](http://scodec.org/) and read using [scodec-stream](https://github.com/scodec/scodec-stream).
- XML is generated using the JDK [StAX](https://docs.oracle.com/javase/tutorial/jaxp/stax/using.html) API.
