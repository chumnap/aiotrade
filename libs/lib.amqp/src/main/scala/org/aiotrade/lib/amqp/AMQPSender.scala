package org.aiotrade.lib.amqp

import com.rabbitmq.client.AMQP
import com.rabbitmq.client.ConnectionFactory
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream

class AMQPSender[T](cf: ConnectionFactory, host: String, port: Int, exchange: String){

  val conn = cf.newConnection(host, port)
  val channel = conn.createChannel

  def send(msg: T, routingKey: String, props: AMQP.BasicProperties) {
    val bytes = new ByteArrayOutputStream
    val store = new ObjectOutputStream(bytes)
    store.writeObject(msg)
    store.close

    val body = bytes.toByteArray

    channel.basicPublish(exchange, routingKey, props, body)
    //println(msg + " sent: routingKey=" + routingKey + " size=" + body.length)
  }
}