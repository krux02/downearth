package downearth.generation

import simplex3d.math._
import simplex3d.math.double._
import simplex3d.math.double.functions._

import downearth._
import downearth.Config._
import downearth.worldoctree._
import downearth.util._
import downearth.worldoctree.PowerOfTwoCube
import downearth.rendering.{TextureMesh, ObjManager}
import akka.util.Timeout
import downearth.AkkaMessages.{FinishedGeneratingJob, Predicted}

//import downearth.server.LocalServer
import scala.concurrent.{ExecutionContext, Future, Await}
import akka.pattern.ask
import downearth.message.implicits._
import collection.mutable
import ExecutionContext.Implicits.global
import scala.concurrent.duration._
import downearth.worldoctree.PowerOfTwoCube
import downearth.worldoctree.PowerOfTwoCube

object WorldGenerator {
	import Config._
	
	def generateInitialWorld(gameState:GameState):WorldOctree = {
    // create an octree that contains the player
    val octreeSize = nextPowerOfTwo(((length(Config.startPos)+playerRadius) * 2).toInt)
    val octreeArea = PowerOfTwoCube( pos = Vec3i(-octreeSize/2), octreeSize )
		val octree = new WorldOctree(octreeArea, MeshNode.ungenerated, gameState)

    // octree( Vec3i(1,2,3) ) = new ObjLeaf(ObjManager.testMesh)

		octree
	}

  def generateNode(area:PowerOfTwoCube,
                   worldFunction:WorldFunction = WorldDefinition):MeshNode = {
    if( area.size >= Config.minPredictionSize ) {
      // split
      val meshNode = MeshNode.ungenerated
      for(subarea <- area.splitOct) {
        val interval = WorldDefinition.range(subarea.toInterval3)
        val surfaceNotInArea = !interval(0)
        if( surfaceNotInArea ) {
          //TODO: if( Config.predictionDebug )
          //  debugLog ! Predicted(area)
          val LeafMeshNode = new MeshNode(Leaf(
            if(interval.isPositive) FullHexaeder else EmptyHexaeder
          ))
          LeafMeshNode.mesh = TextureMesh.empty

          meshNode.insertNode(area, subarea, LeafMeshNode)
        } else // surface in area: recursion
          meshNode.insertNode(area, subarea, generateNode(subarea, worldFunction))
      }

      meshNode
    } else { // sample
      val hexaeders = hexaederMC(area, worldFunction)

      // Fill Octree with hexeaders
      val root = EmptyLeaf.fill( area, pos => Leaf(hexaeders(pos)) )

      // generate Mesh
      root.genMesh( area, x => {
        if (area.indexInRange(x)) root(area, x).h else hexaeders(x)
      } )
    }
  }


  def sampleArea(area: PowerOfTwoCube, worldFunction: WorldFunction) = {
    // Braucht eine zusätzliche größe von 2 damit die Nachbarn besser angrenzen können
    // Marching-Cubes für n Cubes: n+1 Datenpunkte
    // Für Umrandungen: n+2 Cubes mit n+3 Datenpunkten
    val sampleArea = Cuboid(area.pos - 1, area.vsize + 3)
    val originalNoiseData = new Array3D[Double](sampleArea.vsize)

    originalNoiseData.fill( v => worldFunction.density(Vec3(sampleArea.pos + v)) )

    originalNoiseData
  }


  def hexaederMC(area: PowerOfTwoCube, worldFunction:WorldFunction) = {
    import HexaederMC._

    val originalNoiseData = sampleArea(area, worldFunction)

    val marchingArea = Cuboid(area.pos, area.vsize + 2)
    val exactCaseData = new Array3D[Short](marchingArea.vsize)
    val cubesToMarch = Vec3i(0) until marchingArea.vsize

    // Fall für jeden Cube ermitteln und abspeichern
    for (coord <- cubesToMarch) {
      val exactCase = dataToCase(originalNoiseData.extractCubeCorners(coord))
      exactCaseData(coord) = exactCase.toShort
    }

    val modifiedNoiseData = originalNoiseData.clone

    // für jeden Cube:
    for (coord <- cubesToMarch) {
      // Datenpunkte extrahieren
      val originalData = originalNoiseData.extractCubeCorners(coord)
      val modifiedData = modifiedNoiseData.extractCubeCorners(coord)

      // Fall für diesen Cube auslesen und benennen
      val exactCase = exactCaseData(coord)
      val caseType = caseTypeLookup(exactCase)

      // Wenn Fall nicht darstellbar
      if (!isStableCase(caseType)) {
        // In einen darstellbaren Fall transformieren
        val (newData, newCase) = transformToStable(originalData, exactCase)

        // Stabilisierung auf die schon modifizierten Daten anwenden
        val merge = Array.tabulate(8) { i =>
            if (newData(i) == 0) 0
            else modifiedData(i)
          }

        // Transformierten Cube abspeichern
        modifiedNoiseData(coord) = merge
        exactCaseData(coord) = newCase.toShort
      }
    }

    // Liest die abgespeicherten Fälle aus und erzeugt entsprechende Hexaeder
    val hexaeders = { (pos:ReadVec3i) =>
      val arraypos = pos + 1 - area.pos
      val h = data2hexaeder(modifiedNoiseData.extractCubeCorners(arraypos), exactCaseData(arraypos))
      if (h.noVolume) EmptyHexaeder else h
    }

    hexaeders
  }

  // Ask server for World delta asynchronly
  /*def askServerForDeltas(nodeInfo: PowerOfTwoCube): Future[message.DeltaSet] = {
    implicit val timeout = Timeout(5 seconds)
    (LocalServer.server ? message.NodeInfo(nodeInfo.pos, nodeInfo.size)).asInstanceOf[Future[message.DeltaSet]]

    // wait for world delta from server to overwrite generated blocks
    /*
    val deltaSetMessage = Await.result(deltaSetFuture, Timeout(5 seconds).duration)
    val deltaSet = deltaSetMessage.set map {
      case message.Delta(pos, block) => messageToSimplexVec3i(pos) -> block
    }
    for( (pos, block) <- deltaSet )
      root = root.updated(area, pos, Leaf(Hexaeder.fromMessage(block.shape)))
    */
  }*/

  // Find areas inside Area to be sampled (using range prediction)
  def findNodesToSample(area: Cuboid,
                        worldFunction:WorldFunction = WorldDefinition,
                        minPredictionSize: Int = Config.minPredictionSize
                         ): (Seq[Cuboid],Seq[Cuboid],Seq[Cuboid]) = {
    val toSample = mutable.ArrayBuffer.empty[Cuboid]
    val positives = mutable.ArrayBuffer.empty[Cuboid]
    val negatives = mutable.ArrayBuffer.empty[Cuboid]
    val toCheck = mutable.Queue.empty[Cuboid]

    toCheck += area //TODO: don't double check in higher level prediction phase
    while (toCheck.nonEmpty) {
      val current = toCheck.dequeue()
      val range = worldFunction.range(current.toInterval3)
      val surfaceInArea = range(0) // interval contains zero
      if (surfaceInArea) {
        if (current.shortestEdgeLength > minPredictionSize)
          toCheck ++= current.splitOct
        else
          toSample += current
      } else if( range.isPositive )
        positives += current
      else // range.isNegative
        negatives += current
    }
    (toSample, positives, negatives)
  }

  def predictionHierarchy(area: PowerOfTwoCube,
                          worldFunction:WorldFunction = WorldDefinition,
                          minPredictionSize: Int = Config.minPredictionSize):(PowerOfTwoCube,Any) = {
    val range = worldFunction.range(area.toInterval3)
    val surfaceInArea = range(0) // interval contains zero
    if (surfaceInArea) {
      if (area.shortestEdgeLength > minPredictionSize)
        (area,area.splitOct map (predictionHierarchy(_, worldFunction, minPredictionSize)))
      else
        (area,0)
    } else if( range.isPositive )
      (area,1)
    else // range.isNegative
      (area,-1)
  }

  def linearizeHierarchy(hierarchy:(PowerOfTwoCube,Any)):List[PowerOfTwoCube] = {
    hierarchy match {
      case (area,1) => Nil
      case (area,-1) => Nil
      case (area,0) => area :: Nil
      case (area,hierarchy:Any) => hierarchy.asInstanceOf[Array[(PowerOfTwoCube,Any)]].toList.flatMap(linearizeHierarchy)
    }
  }
}

