package org.aiotrade.lib.trading

import org.aiotrade.lib.collection.ArrayList
import org.aiotrade.lib.securities.model.Sec
import java.util.UUID

trait Transaction {
  def id: Long
  def time: Long
  def description: String
  def amount: Double
  def subTransactions: Array[Transaction]
  
  /**
   * Gets the order that originated this transaction, if any.
   *
   * @return the order reference, or <code>null</code> if the transaction wasn't
   * originated from an order.
   */
  def order: Order
}

final case class ExpensesTransaction(time: Long, amount: Double) extends Transaction {
  def this(amount: Double) = this(System.currentTimeMillis, amount)

  val id = UUID.randomUUID.getMostSignificantBits
  val description = "Expenses"
  val order: Order = null
  val subTransactions: Array[Transaction] = Array[Transaction]()
}

/**
 * @Note quantity and amount should consider signum according to the side
 */
final case class SecurityTransaction(time: Long, sec: Sec, price: Double, quantity: Double, amount: Double, side: OrderSide) extends Transaction {
  val id = UUID.randomUUID.getMostSignificantBits
  val description = "%s %s at %s, amount %s".format(sec.uniSymbol, price, quantity, amount)
  val order: Order = null
  val subTransactions: Array[Transaction] = Array[Transaction]()
}

final case class TradeTransaction(time: Long, securityTransactions: Array[SecurityTransaction], expensesTransaction: ExpensesTransaction, order: Order) extends Transaction {
  val id = UUID.randomUUID.getMostSignificantBits
  val description = "%s".format(securityTransactions)

  val subTransactions = {
    val xs = new ArrayList[Transaction]() ++= securityTransactions
    xs += expensesTransaction
    
    xs.toArray
  }

  val amount = {
    var sum = 0.0
    var i = 0
    while (i < subTransactions.length) {
      sum += subTransactions(i).amount
      i += 1
    }
    sum
  }
}
