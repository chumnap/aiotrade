package org.aiotrade.lib.amqp

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.logging.{Level, Logger}
import com.rabbitmq.client.Channel
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.ConnectionParameters
import com.rabbitmq.client.Consumer

import scala.actors.Actor


object FileConsumer {

  // --- simple test
  def main(args: Array[String]) {
    val queue = "filequeue"
    val exchange = "market.file"
    val routingKey = "faster.server.dbffile"
    val port = 5672

    val host = "localhost"

    val params = new ConnectionParameters
    params.setUsername("guest")
    params.setPassword("guest")
    params.setVirtualHost("/")
    params.setRequestedHeartbeat(0)

    val outputDirPath = System.getProperty("user.home") + File.separator + "storage"

    val factory = new ConnectionFactory(params)

    for (i <- 0 until 5) {
      val queuei = queue + i
      val consumer = new FileConsumer(factory,
                                      host,
                                      port,
                                      exchange,
                                      queuei,
                                      routingKey,
                                      outputDirPath)
      
      new consumer.SafeProcessor
      consumer.start
    }
  }

}

class FileConsumer(cf: ConnectionFactory, host: String, port: Int, exchange: String, queue: String, routingKey: String, outputDirPath: String
) extends AMQPDispatcher(cf, host, port, exchange) {
  val outputDir = new File(outputDirPath)
  if (!outputDir.exists) {
    outputDir.mkdirs
  } else {
    assert(outputDir.isDirectory, "outputDir should be director: " + outputDir)
  }
  
  @throws(classOf[IOException])
  override def configure(channel: Channel): Option[Consumer] = {
    channel.exchangeDeclare(exchange, "direct", true)
    channel.queueDeclare(queue, true)
    channel.queueBind(queue, exchange, routingKey)
    
    val consumer = new AMQPConsumer(channel)
    channel.basicConsume(queue, consumer)
    Some(consumer)
  }

  abstract class Processor extends Actor {
    start
    FileConsumer.this ! AMQPAddListener(this)

    protected def process(msg: AMQPMessage)

    def act = loop {
      react {
        case msg: AMQPMessage => process(msg)
        case AMQPStop => exit
      }
    }
  }

  class DefaultProcessor extends Processor {
    
    override protected def process(msg: AMQPMessage) {
      val headers = msg.props.headers
      val content = msg.content.asInstanceOf[Array[Byte]]

      try {
        var fileName = headers.get("filename").toString
        var outputFile = new File(outputDir, fileName)
        var i = 1
        while (outputFile.exists) {
          fileName = fileName + "_" + i
          outputFile = new File(outputDir, fileName)
          i += 1
        }
        
        val out = new FileOutputStream(outputFile)
        out.write(content)
        out.close
      } catch {
        case e => e.printStackTrace
      }
    }
  }

  /**
   * Firstly save the file with a temporary file name.
   * When finish receiving all the data, then rename to the regular file in the same folder.
   */
  class SafeProcessor extends Processor {
    private val log = Logger.getLogger(this.getClass.getName)
    
    override protected def process(msg: AMQPMessage) {
      val headers = msg.props.headers
      val content = msg.content.asInstanceOf[Array[Byte]]

      try {
        var fileName = headers.get("filename").toString
        var outputFile = new File(outputDir, "." + fileName + ".tmp")
        var i = 1
        while (outputFile.exists) {
          fileName = fileName + "_" + i
          outputFile = new File(outputDir, "." + fileName + ".tmp")
          i += 1
        }
        
        val out = new FileOutputStream(outputFile)
        out.write(content)
        out.close

        outputFile.renameTo(new File(outputDir, fileName))
        log.info("Received " + fileName)
      } catch {
        case e => e.printStackTrace
      }
    }
  }

}
