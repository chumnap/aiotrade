package org.aiotrade.lib.math.algebra


/** Implements vector as an array of doubles */
import org.aiotrade.lib.math.Functions

class DenseVector private (private var values: Array[Double]) extends AbstractVector(values.length) {

  /** For serialization purposes only */
  def this() = {
    this(new Array[Double](0))
  }

  override
  protected[algebra] def matrixLike(rows: Int, columns: Int): Matrix = {
    DenseMatrix(rows, columns)
  }

  override
  def clone: DenseVector = {
    DenseVector(values.clone)
  }

  /**
   * @return true
   */
  override
  def isDense = true

  /**
   * @return true
   */
  override
  def isSequentialAccess = true

  override
  def dotSelf: Double = {
    var result = 0.0
    val max = size
    var i = 0
    while (i < max) {
      val value = this.getQuick(i)
      result += value * value
      i += 1
    }
    result
  }

  override
  def getQuick(index: Int): Double = {
    values(index)
  }

  override
  def like: DenseVector = {
    DenseVector(size)
  }

  override
  def setQuick(index: Int, value: Double) {
    lengthSquared = -1.0
    values(index) = value
  }
  
  override
  def assign(value: Double): Vector = {
    this.lengthSquared = -1;
    java.util.Arrays.fill(values, value)
    this
  }
  
  override
  def assign(other: Vector, function: (Double, Double) => Double): Vector = {
    if (size != other.size) {
      throw new CardinalityException(size, other.size)
    }
    // is there some other way to know if function.apply(0, x) = x for all x?
    function match {
      case f: Functions.PlusMult =>
        val it = other.iterateNonZero
        var e: Element = null
        while (it.hasNext && {e = it.next; e != null}) {
          values(e.index) = function(values(e.index), e.get)
        }
      case _ =>
        var i = 0
        while (i < size) {
          values(i) = function(values(i), other.getQuick(i))
          i += 1
        }
    }
    lengthSquared = -1
    this
  }

  def assign(vector: DenseVector): Vector = {
    // make sure the data field has the correct length
    if (vector.values.length != this.values.length) {
      this.values = new Array[Double](vector.values.length)
    }
    // now copy the values
    System.arraycopy(vector.values, 0, this.values, 0, this.values.length)
    this;
  }

  override
  def getNumNondefaultElements: Int = {
    return values.length;
  }

  override
  def viewPart(offset: Int, length: Int): Vector = {
    if (offset < 0) {
      throw new IndexException(offset, size)
    }
    if (offset + length > size) {
      throw new IndexException(offset + length, size)
    }
    VectorView(this, offset, length)
  }

  /**
   * Returns an iterator that traverses this Vector from 0 to cardinality-1, in that order.
   */
  override
  def iterateNonZero: Iterator[Element] = {
    new NonDefaultIterator()
  }

  override
  def iterator: Iterator[Element] = {
    new AllIterator()
  }

  override
  def equals(o: Any): Boolean = o match {
    case that: DenseVector =>
      java.util.Arrays.equals(values, that.values) // Speedup for DenseVectors
    case _ =>
      super.equals(o)
  }

  override
  def getLengthSquared: Double = {
    if (lengthSquared >= 0.0) {
      return lengthSquared
    }

    var result = 0.0
    var i = 0
    while (i < values.length) {
      val value = values(i)
      result += value * value
      i += 1
    }
    lengthSquared = result
    result
  }

  def addAll(v: Vector) {
    if (size != v.size) {
      throw new CardinalityException(size, v.size)
    }
    
    val iter = v.iterateNonZero
    while (iter.hasNext) {
      val element = iter.next
      values(element.index) += element.get
    }
  }

  private final class NonDefaultIterator extends Iterator[Element] {
    private val element = new DenseElement()
    private var index = 0

    def hasNext = {
      while (index < size && values(index) == 0.0) {
        index += 1
      }
      index < size
    }
    def next = {
      if (hasNext) {
        element.index = index
        index += 1
        element
      } else {
        null
      }
    }

  }

  private final class AllIterator extends Iterator[Element] {
    private val element = new DenseElement()
    element.index = -1

    def hasNext = element.index + 1 < size
    def next = {
      if (hasNext) {
        element.index += 1
        element
      } else {
        null
      }
    }
  }

  private final class DenseElement extends Element {
    var index: Int = _

    def get = values(index)
    def set(value: Double) {
      lengthSquared = -1
      values(index) = value
    }
  }

}

object DenseVector {
  /** Construct a new instance using provided values */
  def apply(values: Array[Double]) = new DenseVector(values)

  def apply(values: Array[Double], shallowCopy: Boolean) = new DenseVector(if (shallowCopy) values else values.clone)

  def apply(values: DenseVector, shallowCopy: Boolean): DenseVector = apply(values.values, shallowCopy)

  /** Construct a new instance of the given cardinality */
  def apply(cardinality: Int) = new DenseVector(new Array[Double](cardinality))

  /**
   * Copy-constructor (for use in turning a sparse vector into a dense one, for example)
   * @param vector
   */
  def apply(vector: Vector) = {
    val values = new Array[Double](vector.size)
    val it = vector.iterateNonZero
    while (it.hasNext) {
      val e = it.next
      values(e.index) = e.get
    }
    new DenseVector(values)
  }
}