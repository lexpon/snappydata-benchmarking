package de.lexpon.snappy.benchmark

import com.typesafe.config.Config
import java.io.{BufferedWriter, FileWriter, PrintWriter}
import org.apache.spark.sql._
import scala.collection.mutable.ListBuffer
import scala.io.Source

class RunSqlQueriesAndMesaureRunTime extends SnappySQLJob
{
    val logFileName: String = "runtime_measurement.csv"
    val errFileName: String = "runtime_error.out"
    val benchmarkFileName: String = "/home/snappydata/benchmarks/tpcds/queries/benchmark_queries_cleared.sql"


    override def isValidJob(snappySession: SnappySession, config: Config): SnappyJobValidation = SnappyJobValid()


    override def runSnappyJob(sc: SnappySession, jobConfig: Config): Unit =
    {
        clearLogs()
        writeMsgToLog("query number; run time in ns; run time in ms; run time in s")
        val sqlQueryList: List[String] = findSqlQueriesInFile(sc, benchmarkFileName)
        sqlQueryList.foreach(query =>
        {
            runSqlQuery(sc, query)
        })
    }


    def clearLogs(): Unit =
    {
        val fileNames = List(logFileName, errFileName)
        fileNames.foreach(fileName =>
        {
            val fw = new FileWriter(fileName, false)
            val bw = new BufferedWriter(fw)
            val out = new PrintWriter(bw)
            out.close()
        })
    }


    def writeMsgToLog(msg: String): Unit =
    {
        println(msg)
        val fw = new FileWriter(logFileName, true)
        val bw = new BufferedWriter(fw)
        val out = new PrintWriter(bw)
        out.println(msg)
        out.close()
    }


    def writeErrToLog(err: String): Unit =
    {
        println(err)
        val fw = new FileWriter(errFileName, true)
        val bw = new BufferedWriter(fw)
        val out = new PrintWriter(bw)
        out.println(err)
        out.close()
    }


    def findSqlQueriesInFile(snappySession: SnappySession, benchmarkFileName: String): List[String] =
    {
        val lines = Source.fromFile(benchmarkFileName).getLines()
        val queryBuffer: ListBuffer[String] = new ListBuffer()
        var query: StringBuffer = new StringBuffer()

        lines.foreach(line =>
        {
            if (line.startsWith("--BEGIN: QUERY"))
            {
                query = new StringBuffer()
            }
            query.append(line)
            query.append("\n")
            if (line.startsWith("-- END: QUERY"))
            {
                queryBuffer.+=(query.toString)
            }
        })

        queryBuffer.toList
    }


    def parseQueryNumber(query: String): String =
    {
        query.lines.foreach(line =>
        {
            if (line.startsWith("--BEGIN: QUERY"))
            {
                return line.substring(line.length - 2, line.length)
            }
        })
        writeErrToLog("could not parse query number from query: " + query)
        ""
    }


    def runSqlQuery(snappySession: SnappySession, query: String): Unit =
    {
        val queriesWithoutExceptions: ListBuffer[String] = new ListBuffer()
        try
        {
            val queryNumber: String = parseQueryNumber(query)

            val t0 = System.nanoTime()
            val result: CachedDataFrame = snappySession.sql(query)
            val t1 = System.nanoTime()

            val ns = t1 - t0
            val ms = ns / 1000000
            val s = ms / 1000
            writeMsgToLog(queryNumber + "; " + ns + "; " + ms + "; " + s)
        }
        catch
        {
            case e: Exception =>
                writeErrToLog("this query lead to an exception: " + query)
        }
    }


}
