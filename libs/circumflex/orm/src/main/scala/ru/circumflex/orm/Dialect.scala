package ru.circumflex.orm

import ru.circumflex.orm.Constraint.CascadeAction
import ru.circumflex.orm.Constraint.CheckConstraint
import ru.circumflex.orm.Constraint.ForeignKey
import ru.circumflex.orm.Constraint.ForeignKeyAction
import ru.circumflex.orm.Constraint.NoAction
import ru.circumflex.orm.Constraint.PhysicalPrimaryKey
import ru.circumflex.orm.Constraint.RestrictAction
import ru.circumflex.orm.Constraint.SetDefaultAction
import ru.circumflex.orm.Constraint.SetNullAction
import ru.circumflex.orm.Constraint.UniqueKey
import ru.circumflex.orm.DMLQuery.Delete
import ru.circumflex.orm.DMLQuery.InsertSelect
import ru.circumflex.orm.DMLQuery.Update
import ru.circumflex.orm.Predicate.EmptyPredicate
import ru.circumflex.orm.Query.Select
import ru.circumflex.orm.Query.Subselect
import ru.circumflex.orm.RelationNode.JoinNode

/**
 * A default dialect singleton.
 * If you feel that some of the statements do not work
 * with your RDBMS vendor, trace the exact method and provide it's
 * implementation in your object. Of course, you should override the
 * default configuration with your dialect object in that case.
 */
object DefaultDialect extends Dialect

/**
 * This little thingy does all dirty SQL stuff.
 */
class Dialect {

  /* SQL TYPES */

  def longType = "bigint"
  def integerType = "integer"
  def numericType = "numeric"
  def stringType = "text"
  def varcharType = "varchar"
  def varbinaryType = "varbinary"
  def booleanType = "boolean"
  def dateType = "date"
  def timeType = "time"
  def timestampType = "timestamptz"

  /* FOREIGN KEY ACTIONS */

  def foreignKeyAction(action: ForeignKeyAction) = action match {
    case NoAction => "no action"
    case CascadeAction => "cascade"
    case RestrictAction => "restrict"
    case SetNullAction => "set null"
    case SetDefaultAction => "set default"
  }

  /* FEATURES COMPLIANCE */

  def supportsSchema_?(): Boolean = true
  def supportDropConstraints_?(): Boolean = true

  /* JOIN KEYWORDS */

  def innerJoin = "inner join"

  def leftJoin = "left join"

  def rightJoin = "right join"

  def fullJoin = "full join"

  /* SIMPLE PREDICATES AND KEYWORDS */

  def dummy = "1 = 1"

  def parameterizedIn(params: Seq[_]) =
    " in (" + params.map(p => "?").mkString(", ") + ")"

  def subquery(expr: String, subselect: Subselect) =
    expr + " ( " + subselect.toSubselectSql + " )"

  /* ORDER SPECIFICATOR KEYWORDS */

  def asc = "asc"

  def desc = "desc"

  /* COMMON STUFF */

  def quoteLiteral(expr: String) = "'" + expr.replace("'", "''") + "'"

  protected def columnSequenceName(col: Column[_, _]) =
    col.relation.qualifiedName + "_" + col.columnName + "_seq"

  /**
   * Produces qualified name of a table
   * (e.g. "myschema.mytable").
   */
  def qualifyRelation(rel: Relation[_]) =
    rel.schemaName + "." + rel.relationName

  def qualifyColumn(col: Column[_, _]) =
    col.relation.relationName + "." + col.columnName

  /**
   * Override this definition to provide logical "unwrapping" for relation-based operations.
   */
  protected def unwrap(relation: Relation[_]) = relation

  /* DEFINITIONS */

  /**
   * Produces SQL definition for a column
   * (e.g. "mycolumn varchar not null unique").
   */
  def columnDefinition(col: Column[_, _]): String = {
    var result = col.columnName + " " + col.sqlType
    if (!col.nullable_?) result += " not null"
    col.default match {
      case Some(expr) => result += " " + expr
      case _ =>
    }
    return result
  }

  def defaultExpression(expr: String): String =
    "default " + expr

  def autoIncrementExpression(col: Column[_, _]): String =
    "default nextval('" + columnSequenceName(col) + "')"

  /**
   * Produces PK definition (e.g. "primary key (id)").
   */
  def primaryKeyDefinition(pk: PhysicalPrimaryKey[_, _]) =
    "primary key (" + pk.column.columnName + ")"

  /**
   * Produces unique constraint definition (e.g. "unique (name, value)").
   */
  def uniqueKeyDefinition(uniq: UniqueKey[_]) =
    "unique (" + uniq.columns.map(_.columnName).mkString(",") + ")"

  /**
   * Produces foreign key constraint definition
   * (e.g. "foreign key (ref_id) references public.ref(id) on delete cascade on update no action").
   */
  def foreignKeyDefinition(fk: ForeignKey[_, _]) =
    "foreign key (" + fk.childColumns.map(_.columnName).mkString(", ") +
  ") references " + unwrap(fk.parentRelation).qualifiedName + " (" +
  fk.parentColumns.map(_.columnName).mkString(", ") + ") " +
  "on delete " + foreignKeyAction(fk.onDelete) + " " +
  "on update " + foreignKeyAction(fk.onUpdate)

  /**
   * Produces check constraint definition (e.g. "check (age between 18 and 40)").
   */
  def checkConstraintDefinition(check: CheckConstraint[_]) =
    "check (" + check.expression + ")"

  /**
   * Produces constraint definition (e.g. "constraint mytable_pkey primary key(id)").
   */
  def constraintDefinition(constraint: Constraint[_]) =
    "constraint " + constraint.constraintName + " " + constraint.sqlDefinition

  /* CREATE/ALTER/DROP STATEMENTS */

  /**
   * Produces CREATE SCHEMA statement.
   */
  def createSchema(schema: Schema) =
    "create schema " + schema.schemaName

  /**
   * Produces CREATE TABLE statement without constraints.
   */
  def createTable(tab: Table[_]) =
    "create table " + qualifyRelation(tab) + " (" +
  tab.columns.map(_.sqlDefinition).mkString(", ") + ", " +
  tab.primaryKey.sqlFullDefinition + ")"

  /**
   * Produces CREATE VIEW statement.
   */
  def createView(view: View[_]) =
    "create view " + qualifyRelation(view) + " (" +
  view.columns.map(_.columnName).mkString(", ") + ") as " +
  view.query.toInlineSql

  /**
   * Produces CREATE INDEX statement.
   */
  def createIndex(index: Index[_]): String =
    "create " + (if (index.unique_?) "unique" else "") + " index " +
  index.indexName + " on " + unwrap(index.relation).qualifiedName + " using " +
  index.using + " (" + index.expressions.mkString(", ") + ")" +
  (if (index.where != EmptyPredicate)
    " where " + index.where.toInlineSql
   else "")

  /**
   * Produces ALTER TABLE statement with abstract action.
   */
  def alterTable(rel: Relation[_], action: String) =
    "alter table " + unwrap(rel).qualifiedName + " " + action

  /**
   * Produces ALTER TABLE statement with ADD CONSTRAINT action.
   */
  def alterTableAddConstraint(constraint: Constraint[_]) =
    alterTable(constraint.relation, "add " + constraintDefinition(constraint));

  /**
   * Produces ALTER TABLE statement with ADD COLUMN action.
   */
  def alterTableAddColumn(column: Column[_, _]) =
    alterTable(column.relation, "add column " + columnDefinition(column));

  /**
   * Produces ALTER TABLE statement with DROP CONSTRAINT action.
   */
  def alterTableDropConstraint(constraint: Constraint[_]) =
    alterTable(constraint.relation, "drop constraint " + constraint.constraintName);

  /**
   * Produces ALTER TABLE statement with DROP COLUMN action.
   */
  def alterTableDropColumn(column: Column[_, _]) =
    alterTable(column.relation, "drop column " + column.columnName);

  /**
   * Produces DROP TABLE statement
   */
  def dropTable(tab: Table[_]) =
    "drop table " + qualifyRelation(tab)

  /**
   * Produces DROP VIEW statement
   */
  def dropView(view: View[_]) =
    "drop view " + qualifyRelation(view)

  /**
   * Produces DROP SCHEMA statement.
   */
  def dropSchema(schema: Schema) =
    "drop schema " + schema.schemaName + " cascade"

  /**
   * Produces DROP INDEX statement.
   */
  def dropIndex(index: Index[_]) =
    "drop index " + index.relation.schemaName + "." + index.indexName

  /* SELECT STATEMENTS AND RELATED */

  def columnAlias(col: Column[_, _], columnAlias: String, tableAlias: String) =
    qualifyColumn(col, tableAlias) + " as " + columnAlias

  def scalarAlias(expression: String, alias: String) = expression + " as " + alias

  /**
   * Produces table with alias (e.g. "public.mytable my").
   */
  def tableAlias(tab: Table[_], alias: String) = tab.qualifiedName + " as " + alias

  /**
   * Produces table with alias (e.g. "public.mytable my").
   */
  def viewAlias(view: View[_], alias: String) = view.qualifiedName + " as " + alias

  /**
   * Qualifies a column with table alias (e.g. "p.id")
   */
  def qualifyColumn(col: Column[_, _], tableAlias: String) = tableAlias + "." + col.columnName

  /**
   * Produces join node sql representation (e.g. person p left join address a on p.id = a.person_id).
   */
  def join(j: JoinNode[_, _]): String = joinInternal(j, null)

  /**
   * Some magic to convert join tree to SQL.
   */
  protected def joinInternal(node: RelationNode[_], on: String): String = {
    var result = ""
    node match {
      case j: JoinNode[_, _] =>
        result += joinInternal(j.left, on) +
        " " + j.joinType.sql + " " +
        joinInternal(j.right, j.on)
      case _ =>
        result += node.toSql
        if (on != null) result += " " + on
    }
    return result
  }

  /**
   * Produces SELECT statement with ? parameters.
   */
  def select(q: Select): String = {
    var result = subselect(q)
    if (q.orders.size > 0)
      result += " order by " + q.orders.map(_.expression).mkString(", ")
    if (q.limit > -1)
      result += " limit " + q.limit
    if (q.offset > 0)
      result += " offset " + q.offset
    return result
  }

  /**
   * Produces SELECT statement (without LIMIT, OFFSET and ORDER BY clauses).
   */
  def subselect(q: Subselect): String = {
    var result = "select " + q.projections.map(_.toSql).mkString(", ")
    if (q.relations.size > 0)
      result += " from " + q.relations.map(_.toSql).mkString(", ")
    if (q.where != EmptyPredicate)
      result += " where " + q.where.toSql
    if (q.groupBy.size > 0)
      result += " group by " + q.groupBy.flatMap(_.sqlAliases).mkString(", ")
    if (q.having != EmptyPredicate)
      result += " having " + q.having.toSql
    q.setOps.foreach {
      case (op: SetOperation, subq: Subselect) =>
        result += " " + op.expression + " ( " + subq.toSubselectSql + " )"
      case _ =>
    }
    return result
  }

  /**
   * Produces SQL for ascending order.
   */
  def orderAsc(expr: String) = expr + " " + asc

  /**
   * Produces SQL for descending order.
   */
  def orderDesc(expr: String) = expr + " " + desc

  /* INSERT STATEMENTS */

  /**
   * Produces INSERT INTO .. VALUES statement.
   */
  def insertRecord(record: Record[_]): String =
    "insert into " + record.relation.qualifiedName +
  " (" + record.relation.columns.map(_.columnName).mkString(", ") +
  ") values (" + record.relation.columns.map(c =>
    if (c.default == None) "?" else "default").mkString(", ") + ")"

  /**
   * Produces SQL expression that is used to fetch last inserted record.
   */
  def lastIdExpression(rel: Relation[_]): String = {
    return "lastval()"
  }

  /**
   * Produces INSERT INTO .. SELECT statement.
   */
  def insertSelect(dml: InsertSelect[_]): String =
    "insert into " + dml.relation.qualifiedName +
  " ( " + dml.relation.columns.map(_.columnName).mkString(", ") +
  ") " + select(dml.query)

  /* UPDATE STATEMENTS */

  /**
   * Produces UPDATE statement with primary key criteria.
   */
  def updateRecord(record: Record[_]): String =
    "update " + record.relation.qualifiedName +
  " set " + record.relation.nonPKColumns.map(_.columnName + " = ?").mkString(", ") +
  " where " + record.relation.primaryKey.column.columnName + " = ?"

  /**
   * Produces UPDATE statement.
   */
  def update(dml: Update[_]): String = {
    var result = "update " + dml.relation.qualifiedName +
    " set " + dml.setClause.map(_._1.columnName + " = ?").mkString(", ")
    if (dml.where != EmptyPredicate) result += " where " + dml.where.toSql
    return result
  }

  /* DELETE STATEMENTS */

  /**
   * Produces DELETE statement with primary key criteria.
   */
  def deleteRecord(record: Record[_]): String =
    "delete from " + record.relation.qualifiedName +
  " where " + record.relation.primaryKey.column.columnName + " = ?"

  /**
   * Produces DELETE statement.
   */
  def delete(dml: Delete[_]): String = {
    var result = "delete from " + dml.relation.toSql
    if (dml.where != EmptyPredicate) result += " where " + dml.where.toSql
    return result
  }

  /* DIALECT-SPECIFIC BEHAVIOR */

  /**
   * Prepare a table for auto-increment column.
   * This implementation adds auxiliary sequence (PostgreSQL, Oracle and DB2 support sequences).
   */
  def prepareAutoIncrementColumn(col: Column[_, _]): Unit = {
    val seq = new SchemaObject {
      def objectName = columnSequenceName(col)
      def sqlDrop = "drop sequence " + objectName
      def sqlCreate = "create sequence " + objectName
    }
    if (!col.relation.preAuxiliaryObjects.contains(seq))
      col.relation.addPreAuxiliaryObjects(seq)
  }

}
