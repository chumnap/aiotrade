package ru.circumflex.orm


import java.util.Date
import ORM._
import ru.circumflex.orm.Constraint.AssociativeForeignKey
import ru.circumflex.orm.Validator.{NotEmptyValidator, PatternValidator}

/**
 * Base functionality for columns.
 */
class Column[T, R](val relation: Relation[R],
                   val columnName: String,
                   val sqlType: String
) extends SchemaObject {

  protected var _nullable = true
  protected var _defaultExpression: Option[String] = None

  def cloneForView[V](view: View[V]): Column[T, V] =
    new Column[T, V](view, relation.relationName + "_" + columnName, sqlType)

  def qualifiedName = dialect.qualifyColumn(this)

  /**
   * DSL-like way to qualify a column with NOT NULL constraint.
   */
  def notNull: this.type = {
    _nullable = false
    return this
  }

  /**
   * Is this column nullable?
   */
  def nullable_?(): Boolean = _nullable

  /**
   * DSL-like way to qualify a column with UNIQUE constraint.
   */
  def unique: this.type = {
    relation.unique(this)
    return this
  }

  /**
   * DSL-like way to transform a column to foreign key association.
   */
  def references[P](parentRelation: Relation[P]): AssociativeForeignKey[T, R, P] =
    relation.foreignKey(parentRelation, this)

  /**
   * Returns the default expression for this column.
   */
  def default: Option[String] = _defaultExpression

  /**
   * Sets the default expression for this column.
   */
  def default(expr: String): this.type = {
    _defaultExpression = Some(dialect.defaultExpression(expr))
    return this
  }

  /**
   * DSL-like way to create a sequence for this column.
   */
  def autoIncrement(): this.type = {
    dialect.prepareAutoIncrementColumn(this)
    _defaultExpression = Some(dialect.autoIncrementExpression(this))
    this
  }

  /* DDL */

  /**
   * Produces SQL definition for a column (e.q. "mycolumn varchar not null unique")
   */
  def sqlDefinition: String = dialect.columnDefinition(this)

  def sqlCreate = dialect.alterTableAddColumn(this)

  def sqlDrop = dialect.alterTableDropColumn(this)

  def objectName = columnName

  /* MISCELLANEOUS */

  override def toString = columnName

  override def equals(obj: Any) = obj match {
    case col: Column[T, R] =>
      col.relation.equals(this.relation) &&
      col.columnName.equalsIgnoreCase(this.columnName)
    case _ => false
  }

  override def hashCode = this.relation.hashCode * 31 +
  this.columnName.toLowerCase.hashCode
}

object Column {
  /**
   * String (text) column.
   */
  class StringColumn[R](relation: Relation[R],
                        name: String,
                        sqlType: String
  ) extends Column[String, R](relation, name, sqlType) with XmlSerializableColumn[String] {

    def this(relation: Relation[R], name: String) =
      this(relation, name, dialect.stringType)

    def this(relation: Relation[R], name: String, size: Int) =
      this(relation, name, dialect.varcharType + "(" + size + ")")

    /**
     * DSL-like way to add NotEmptyValidator.
     */
    def validateNotEmpty: this.type = {
      relation.addFieldValidator(this, new NotEmptyValidator(qualifiedName))
      return this
    }

    /**
     * DSL-like way to add PatternValidator.
     */
    def validatePattern(regex: String): this.type = {
      relation.addFieldValidator(this, new PatternValidator(qualifiedName, regex))
      return this
    }

    /**
     * Sets the default string expression for this column
     * (quoting literals as necessary).
     */
    def defaultString(expr: String): this.type = {
      _defaultExpression = Some(dialect.quoteLiteral(expr))
      return this
    }

    def stringToValue(str: String) = str

  }

  /**
   * BINARY or VARBINARY column.
   */
  class BinaryColumn[R](relation: Relation[R],
                        name: String,
                        sqlType: String
  ) extends Column[Array[Byte], R](relation, name, sqlType) with XmlSerializableColumn[String] {

    def this(relation: Relation[R], name: String, size: Int) =
      this(relation, name, dialect.varbinaryType + "(" + size + ")")

    /**
     * DSL-like way to add NotEmptyValidator.
     */
    def validateNotEmpty: this.type = {
      relation.addFieldValidator(this, new NotEmptyValidator(qualifiedName))
      return this
    }

    /**
     * DSL-like way to add PatternValidator.
     */
    def validatePattern(regex: String): this.type = {
      relation.addFieldValidator(this, new PatternValidator(qualifiedName, regex))
      return this
    }

    /**
     * Sets the default string expression for this column
     * (quoting literals as necessary).
     */
    def defaultString(expr: String): this.type = {
      _defaultExpression = Some(dialect.quoteLiteral(expr))
      return this
    }

    def stringToValue(str: String) = str

  }


  /**
   * Integer column.
   */
  class IntegerColumn[R](relation: Relation[R], name: String
  ) extends Column[Int, R](relation, name, dialect.integerType) with XmlSerializableColumn[Int] {
    def stringToValue(str: String): Int = str.toInt
  }

  /**
   * Long (int8 or bigint) column.
   */
  class LongColumn[R](relation: Relation[R], name: String
  ) extends Column[Long, R](relation, name, dialect.longType) with XmlSerializableColumn[Long] {
    def stringToValue(str: String): Long = str.toLong
  }

  /**
   * Integer column.
   */
  class NumericColumn[R](relation: Relation[R], name: String, sqlType: String
  ) extends Column[Double, R](relation, name, sqlType) with XmlSerializableColumn[Double] {

    def this(relation: Relation[R], name: String, precision: Int, scale: Int) =
      this(relation, name, dialect.numericType + "(" + precision + "," + scale + ")")

    def this(relation: Relation[R], name: String) =
      this(relation, name, dialect.numericType)

    def stringToValue(str: String): Double = str.toDouble

  }

  /**
   * Boolean column.
   */
  class BooleanColumn[R](relation: Relation[R], name: String
  ) extends Column[Boolean, R](relation, name, dialect.booleanType) with XmlSerializableColumn[Boolean] {
    def stringToValue(str: String): Boolean = str.toBoolean
  }

  /**
   * Timestamp column.
   */
  class TimestampColumn[R](relation: Relation[R], name: String
  ) extends Column[Date, R](relation, name, dialect.timestampType) with XmlSerializableColumn[Date] {
    def stringToValue(str: String): Date = new Date(java.sql.Timestamp.valueOf(str).getTime)
    override def valueToString(d: Date) = new java.sql.Timestamp(d.getTime).toString
  }

  /**
   * Date column.
   */
  class DateColumn[R](relation: Relation[R], name: String
  ) extends Column[Date, R](relation, name, dialect.dateType) with XmlSerializableColumn[Date] {
    def stringToValue(str: String): Date = new Date(str)
  }

  /**
   * Time column.
   */
  class TimeColumn[R](relation: Relation[R], name: String
  ) extends Column[Date, R](relation, name, dialect.timeType) with XmlSerializableColumn[Date] {
    def stringToValue(str: String): Date = new Date(str)
  }

  /**
   * Specifies that certain column values can be serialized to and deserialized
   * from XML representation.
   */
  trait XmlSerializableColumn[T] {
    def stringToValue(str: String): T
    def valueToString(value: T): String = value.toString
  }

}


