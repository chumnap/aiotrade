package org.aiotrade.lib.trading

import org.aiotrade.lib.collection.ArrayList
import org.aiotrade.lib.securities.model.Sec
import org.aiotrade.lib.util.actors.Publisher
import java.util.Currency
import java.util.Locale
import java.util.UUID
import scala.collection.mutable

class Account(private var _description: String, 
              private var _balance: Double, 
              private var _expenseScheme: ExpenseScheme = NoExpensesScheme, 
              val currency: Currency = Currency.getInstance(Locale.getDefault)) extends Publisher {
  
  val id = UUID.randomUUID.getMostSignificantBits
  
  private val _transactions = new ArrayList[Transaction]()
  private val _secToPosition = new mutable.HashMap[Sec, Position]()
  
  val initialEquity = _balance
  def equity = {
    _balance + _secToPosition.foldRight(0.0){(x, s) => s + x._2.equity}
  }

  def balance: Double = _balance
  def increaseFund(fund: Double) {
    _balance += fund
  }

  def description = _description
  def description(description: String) {
    _description = description
  }
  
  def expenseScheme = _expenseScheme
  def expenseScheme_=(expenseScheme: ExpenseScheme) {
    _expenseScheme = expenseScheme
  }

  def positions = _secToPosition
  def transactions = _transactions.toArray
  
  def processFilledOrder(time: Long, order: Order) {
    val expenses = order.side match {
      case OrderSide.Buy => _expenseScheme.getBuyExpenses(order.filledQuantity, order.averagePrice)
      case OrderSide.Sell => _expenseScheme.getSellExpenses(order.filledQuantity, order.averagePrice)
      case _ => 0.0
    }
    
    val transaction = TradeTransaction(time, order, order.transactions, if (expenses != 0.0) ExpenseTransaction(time, expenses) else null)
    _transactions += transaction

    _balance -= transaction.amount

    val quantity = order.side match {
      case OrderSide.Sell | OrderSide.SellShort => -order.filledQuantity 
      case OrderSide.Buy  | OrderSide.BuyCover  =>  order.filledQuantity
    }
    val averagePrice = transaction.amount / order.filledQuantity
    
    _secToPosition.get(order.sec) match {
      case None => 
        val position = Position(time, order.sec, quantity, averagePrice)
        _secToPosition(order.sec) = position
        publish(PositionOpened(this, position))
        
      case Some(position) =>
        position.add(time, quantity, averagePrice)
        if (position.quantity == 0) {
          _secToPosition -= order.sec
          publish(PositionClosed(this, position))
        } else {
          publish(PositionChanged(this, position))
        }
    }
  }
}

