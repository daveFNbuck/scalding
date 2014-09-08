package com.twitter.scalding.macros

import cascading.tuple.{ Tuple => CTuple, TupleEntry }

import org.scalatest.{ Matchers, WordSpec }

import com.twitter.scalding._
import com.twitter.scalding.macros._
import com.twitter.scalding.macros.impl._

class MacrosUnitTests extends WordSpec with Matchers {
  // Note: if these are nested in a method, it will not work, likely due to the way scala mangles the names.
  case class A(x: Int, y: String)
  case class B(a1: A, a2: A, y: String)
  case class C(a: A, b: B, c: A, d: B, e: B)

  import MacroImplicits._
  def isMg[T](t: T): T = {
    t shouldBe a[MacroGenerated]
    t
  }

  def mgConv[T](te: TupleEntry)(implicit conv: TupleConverter[T]): T = isMg(conv)(te)
  def mgSet[T](t: T)(implicit set: TupleSetter[T]): TupleEntry = new TupleEntry(isMg(set)(t))

  def shouldRoundTrip[T: IsCaseClass: TupleSetter: TupleConverter](t: T) {
    t shouldBe mgConv(mgSet(t))
  }

  def shouldRoundTripOther[T: IsCaseClass: TupleSetter: TupleConverter](te: TupleEntry, t: T) {
    val inter = mgConv(te)
    inter shouldBe t
    mgSet(inter) shouldBe te
  }

  "MacroGenerated TupleSetter and TupleConverter" should {
    "round trip class -> tupleentry -> class" in {
      shouldRoundTrip(A(100, "onehundred"))
      shouldRoundTrip(B(A(100, "onehundred"), A(-1, "zero"), "what"))
      val a = A(73, "hrm")
      val b = B(a, a, "hrm")
      shouldRoundTrip(b)
      shouldRoundTrip(C(a, b, A(123980, "hey"), B(a, A(-1, "zero"), "zoo"), b))
    }

    "round trip tupleentry -> class -> tupleEntry" in {
      val a_tup = CTuple.size(2)
      a_tup.setInteger(0, 100)
      a_tup.setString(1, "onehundred")
      val a_te = new TupleEntry(a_tup)
      val a = A(100, "onehundred")
      shouldRoundTripOther(a_te, a)

      val b_tup = CTuple.size(3)
      b_tup.set(0, a_tup)
      b_tup.set(1, a_tup)
      b_tup.setString(2, "what")
      val b_te = new TupleEntry(b_tup)
      val b = B(a, a, "what")
      shouldRoundTripOther(b_te, b)

      val c_tup = CTuple.size(5)
      c_tup.set(0, a_tup)
      c_tup.set(1, b_tup)
      c_tup.set(2, a_tup)
      c_tup.set(3, b_tup)
      c_tup.set(4, b_tup)
      val c_te = new TupleEntry(c_tup)
      val c = C(a, b, a, b, b)
      shouldRoundTripOther(c_te, c)
    }
  }
}