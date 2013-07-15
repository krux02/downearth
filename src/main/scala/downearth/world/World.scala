package downearth.world

import simplex3d.math._
import simplex3d.math.double._
import simplex3d.math.double.functions._

import downearth.util._
import downearth.entity._
import downearth.generation.WorldGenerator
import downearth.worldoctree._
import downearth.rendering.ObjLoader
import downearth.{BulletPhysics, util}


import java.io._
//import downearth.server.LocalServer

object World {
	val octree = WorldGenerator.genWorld

	def update(pos:Vec3i, l:Leaf) {
		octree(pos) = l
		BulletPhysics.worldChange(pos)

    //import downearth.message._
    //import downearth.message.implicits._
    /*LocalSver.server ! Delta(
      pos,
      Block(
        l.h.toMessage,
        l.material
      )
    )*/
  }

def apply(pos:Vec3i) = octree(pos)

val dynamicWorld = DynamicWorld.testScene
}
