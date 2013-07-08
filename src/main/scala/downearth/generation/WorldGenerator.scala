package downearth.generation

import simplex3d.math._
import simplex3d.math.double._

import downearth._
import downearth.Config._
import downearth.worldoctree._
import downearth.util._
import downearth.worldoctree.PowerOfTwoCube
import downearth.rendering.ObjManager
import akka.util.Timeout
import downearth.server.LocalServer
import scala.concurrent.{ExecutionContext, Future, Await}
import akka.pattern.ask
import downearth.message.implicits._
import collection.mutable
import ExecutionContext.Implicits.global
import scala.concurrent.duration._
import downearth.worldoctree.PowerOfTwoCube
import downearth.worldoctree.PowerOfTwoCube

object WorldGenerator {
	import Config.worldWindowSize
	
	def genWorld:WorldOctree = {
		// val rootNodeInfo = Cube(Vec3i(-cubesize/2), cubesize)
		val initArea = PowerOfTwoCube( pos = Vec3i(-worldWindowSize/4), size = worldWindowSize/2 )
		val octree = new WorldOctree(initArea,genWorldAt(initArea))
		octree.incDepth()

//    octree( Vec3i(1,2,3) ) = new ObjLeaf(ObjManager.testMesh)

		octree
	}

  def genWorldAt(area:PowerOfTwoCube,
                 worldFunction:WorldFunction = WorldDefinition,
                 deltaSetFuture:Future[message.DeltaSet] = Future{message.DeltaSet()},
                 prediction:Boolean = false):NodeOverMesh = {
    val PowerOfTwoCube(nodepos, nodesize) = area
    import HexaederMC._

    // Braucht eine zusätzliche größe um 2 damit die Nachbarn besser angrenzen können
    // Marching-Cubes für n Cubes: n+1 Datenpunkte
    // Für Umrandungen: n+2 Cubes mit n+3 Datenpunkten
    val sampleArea = Cuboid(area.pos-1, area.vsize+3)
    val originalNoiseData = new Array3D[Double](sampleArea.vsize)
    val (toSample:Seq[Cuboid], _, _) = if(prediction)
        findNodesToSample(sampleArea, worldFunction)
      else
        (Seq(sampleArea), null, null)

    originalNoiseData.fill(v => worldFunction.density(Vec3(area.pos+v-1)), toSample.map(_.withBorder(1)).map(_.intersection(sampleArea)), offset = -area.pos + 1)

    val marchingArea = Cuboid(area.pos, area.vsize+2)
    val toSampleClamped = toSample.map(_.intersection(marchingArea))
    val cubesToMarch = toSampleClamped.flatMap(_.coordinates)


    val exactCaseData = new Array3D[Short](Vec3i(nodesize+2))

    // Fall für jeden Cube ermitteln und abspeichern
    for( pos <- cubesToMarch ) {
      val coord = pos - marchingArea.pos
      val exactCase = dataToCase(originalNoiseData.extract(coord))
      exactCaseData(coord) = exactCase.toShort
    }

    val modifiedNoiseData = originalNoiseData.clone
    // für jeden Cube:
    for( pos <- cubesToMarch ) {
      val coord = pos - marchingArea.pos
      // Datenpunkte extrahieren
      val originalData = originalNoiseData.extract(coord)
      val modifiedData = modifiedNoiseData.extract(coord)

      // Fall für diesen Cube auslesen und benennen
      val exactCase = exactCaseData(coord)
      val caseType = caseTypeLookup(exactCase)

      // Wenn Fall nicht darstellbar
      if( !isStableCase(caseType) ) {
        // In einen darstellbaren Fall transformieren
        val (newData, newCase) = transformToStable(originalData, exactCase)

        // Stabilisierung auf die schon modifizierten Datan anwenden
        val merge = 
        for( i <- 0 until 8 ) yield {
          if( newData(i) == 0 )
            0
          else
            modifiedData(i)
        }

        // Transformierten Cube abspeichern
        modifiedNoiseData(coord) = merge
        exactCaseData(coord) = newCase.toShort
      }
    }

    // wait for world d from server to overwrite generated blocks
    val deltaSet = Await.result(deltaSetFuture, Timeout(5 seconds).duration).asInstanceOf[message.DeltaSet]
    val deltaMap = (deltaSet.set map {
      case message.Delta(pos, block) => messageToSimplexVec3i(pos) -> block
    }).toMap



    // Liest die abgespeicherten Fälle aus und erzeugt entsprechende Hexaeder
    // Berücksichtigt auch gespeicherte User-Änderungen vom Server
    def fillfun(v: Vec3i) = {
      val arraypos = v + 1 - nodepos
      val h = data2hexaeder(modifiedNoiseData.extract(arraypos), exactCaseData(arraypos))

      val delta = deltaMap.get(v)
      if (delta.isDefined) {
        Hexaeder.fromMessage(delta.get.shape)
      } else
      if (h.noVolume)
        EmptyHexaeder
      else
        h
    }

    // Octree mit Hexaedern füllen
    // TODO: use prediction here
    val root = EmptyLeaf.fill( area, pos => Leaf(fillfun(pos)) )

    root.genMesh( area, minMeshNodeSize, x => {
      if (area.indexInRange(x)) root(area, x).h else fillfun(x)
    } )
  }


  // Ask server for World delta asynchronly
  def askServerForDeltas(nodeInfo: PowerOfTwoCube): Future[message.DeltaSet] = {
    implicit val timeout = Timeout(5 seconds)
    (LocalServer.server ? message.NodeInfo(nodeInfo.pos, nodeInfo.size)).asInstanceOf[Future[message.DeltaSet]]
  }

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

  def predictionHierarchy(area: Cuboid,
                          worldFunction:WorldFunction = WorldDefinition,
                          minPredictionSize: Int = Config.minPredictionSize):(Cuboid,Any) = {
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

  def linearizeHierarchy(hierarchy:(Cuboid,Any)):List[Cuboid] = {
    hierarchy match {
      case (area,1) => Nil
      case (area,-1) => Nil
      case (area,0) => area :: Nil
      case (area,hierarchy:Any) => hierarchy.asInstanceOf[Array[(Cuboid,Any)]].toList.flatMap(linearizeHierarchy)
    }
  }
}

