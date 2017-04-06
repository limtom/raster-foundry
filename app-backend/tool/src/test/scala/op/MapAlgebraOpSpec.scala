package com.azavea.rf.tool.op

import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.ast.MapAlgebraAST._

import geotrellis.raster._
import geotrellis.raster.op._
import geotrellis.raster.testkit._
import geotrellis.raster.render._
import org.scalatest._
import cats.data._
import cats.data.Validated._

import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import java.util.UUID


class MapAlgebraOpSpec
    extends FunSpec
       with Matchers
       with TileBuilders
       with RasterMatchers {
  import MapAlgebraAST._

  val scene = SceneRaster(UUID.randomUUID, None)

  val red = RFMLRasterSource(
    id = UUID.randomUUID,
    label = Some("RED"),
    value = Some(scene.copy(band = Some(4)))
  )

  val nir = RFMLRasterSource(
    id = UUID.randomUUID,
    label = Some("NIR"),
    value = Some(scene.copy(band = Some(5)))
  )

  val blue = RFMLRasterSource(
    id = UUID.randomUUID,
    label = Some("BLUE"),
    value = Some(scene.copy(band = Some(1)))
  )

  val tile: MultibandTile = ArrayMultibandTile(
    createValueTile(d = 4, v = 0),
    createValueTile(d = 4, v = 1),
    createValueTile(d = 4, v = 2),
    createValueTile(d = 4, v = 3),
    createValueTile(d = 4, v = 4),
    createValueTile(d = 4, v = 5)
  )

  var requests = List.empty[RFMLRaster]
  val goodSource = (raster: RFMLRaster, z: Int, x: Int, y: Int) => {
    raster match {
      case r@SceneRaster(id, Some(band)) =>
        requests = raster :: requests
        Future { Some(tile.bands(band)) }
      case _ => Future.failed(new Exception("can't find that"))
    }
  }

  it("should evaluate classification") {
    requests = Nil
    // This breakmap should convert all cells (which are set to a value of 5) to 77
    val breakmap = ClassBreaks(Map(6.0 -> 77), ClassBreaks.Options(LessThanOrEqualTo, 123))
    val tms = Interpreter.tms(
      ast = red.classify(breakmap), source = goodSource
    )

    val ret = tms(0, 1, 1)
    val op = Await.result(ret, 10.seconds) match {
      case Valid(tileOp) =>
        val maybeTile = tileOp.toTile(IntCellType)
        requests.length should be (1)
        assertEqual(maybeTile.get, createValueTile(4, 77).toArray)
      case i@Invalid(_) =>
        fail(s"$i")
    }
  }

  it("should evaluate subtraction") {
    requests = Nil
    val tms = Interpreter.tms(
      ast = blue - nir, source = goodSource
    )

    val ret = tms(0, 1, 1)
    val op = Await.result(ret, 10.seconds) match {
      case Valid(tileOp) =>
        val maybeTile = tileOp.toTile(DoubleCellType)
        requests.length should be (2)
        maybeTile.get.get(0, 0) should be (1 - 5)
      case i@Invalid(_) =>
        fail(s"$i")
    }
  }

  it("should preserve order of operations while evaluating subtraction") {
    requests = Nil
    // 4, 5, 1
    val ast = Subtraction(List(nir, red, blue), UUID.randomUUID(), None)
    val tms = Interpreter.tms(
      ast = ast, source = goodSource
    )

    val ret = tms(0, 1, 1)
    val op = Await.result(ret, 10.seconds) match {
      case Valid(tileOp) =>
        val maybeTile = tileOp.toTile(DoubleCellType)
        requests.length should be (ast.args.length)
        maybeTile.get.get(0, 0) should be (5 - 4 - 1)
      case i@Invalid(_) =>
        fail(s"$i")
    }
  }

  it("should evaluate division") {
    requests = Nil
    val tms = Interpreter.tms(
      ast = red / nir, source = goodSource
    )

    val ret = tms(0, 1, 1)
    val op = Await.result(ret, 10.seconds) match {
      case Valid(tileOp) =>
        val maybeTile = tileOp.toTile(DoubleCellType)
        requests.length should be (2)
        maybeTile.get.getDouble(0, 0) should be (4.0 / 5.0)
      case i@Invalid(_) =>
        fail(s"$i")
    }
  }

  it("should preserve order of operations while evaluating division") {
    requests = Nil
    val ast = Division(List(blue, nir, red), UUID.randomUUID(), None)
    val tms = Interpreter.tms(
      ast = ast, source = goodSource
    )

    val ret = tms(0, 1, 1)
    val op = Await.result(ret, 10.seconds) match {
      case Valid(tileOp) =>
        val maybeTile = tileOp.toTile(DoubleCellType)
        requests.length should be (ast.args.length)
        maybeTile.get.getDouble(0, 0) should be (1.0 / 5.0 / 4.0)
      case i@Invalid(_) =>
        fail(s"$i")
    }
  }

  it("should evaluate multiplication") {
    requests = Nil
    val tms = Interpreter.tms(
      ast = red * nir, source = goodSource
    )

    val ret = tms(0, 1, 1)
    val op = Await.result(ret, 10.seconds) match {
      case Valid(tileOp) =>
        val maybeTile = tileOp.toTile(DoubleCellType)
        requests.length should be (2)
        maybeTile.get.getDouble(0, 0) should be (4.0 * 5.0)
      case i@Invalid(_) =>
        fail(s"$i")
    }
  }

  it("should evaluate addition") {
    requests = Nil
    val tms = Interpreter.tms(
      ast = red + nir, source = goodSource
    )

    val ret = tms(0, 1, 1)
    val op = Await.result(ret, 10.seconds) match {
      case Valid(tileOp) =>
        val maybeTile = tileOp.toTile(DoubleCellType)
        requests.length should be (2)
        maybeTile.get.getDouble(0, 0) should be (4.0 + 5.0)
      case i@Invalid(_) =>
        fail(s"$i")
    }
  }
}
