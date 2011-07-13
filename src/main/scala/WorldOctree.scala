package xöpäx

import simplex3d.math._
import simplex3d.math.float._
import simplex3d.math.float.functions._

import Util._
import org.lwjgl.util.vector.Vector3f
import collection.Map
import util.parsing.input.OffsetPosition

case class WorldNodeInfo(pos:Vec3i,size:Int,value:Hexaeder)

object WorldTimers {
	val subtimer = new Timer
	val applytimer = new Timer
	val surfacetimer = new Timer
}
import WorldTimers._

class WorldOctree(var rootNodeSize:Int,var rootNodePos:Vec3i = Vec3i(0)) extends Data3D[Hexaeder] with Serializable with Iterable[WorldNodeInfo]{

	var worldWindowPos:Vec3i = Vec3i(0)
	val worldWindowSize:Int = rootNodeSize
	val minMeshNodeSize = 32

	val vsize = Vec3i(worldWindowSize)
	var root:Octant = new Leaf(EmptyHexaeder)

	var meshGenerated = false

	def apply(p:Vec3i) = {
		
		applytimer.measure{
			//assert(Util.indexInRange(p,worldWindowPos,worldWindowSize))
			if(Util.indexInRange(p,rootNodePos,rootNodeSize))
				root(p,rootNodePos,rootNodeSize)
			else
				UndefHexaeder
		}
	}

	def update(p:Vec3i,h:Hexaeder) {
		if(Util.indexInRange(p,worldWindowPos,worldWindowSize)) {
			if(meshGenerated)
				root = root.patchWorld(p,h,-1,-1, Vec3i(0), rootNodeSize)._1
			else 
				root = root.updated(p,h,Vec3i(0),rootNodeSize)
		}
		else {
			println("update out of world",p,h)
		}
	}
	
	override def toString = "Octree("+root.toString+")"

	def iterator = new Iterator[WorldNodeInfo] {
		case class InnerNodeInfo(pos:Vec3i,size:Int,node:Octant)

		var history = List( InnerNodeInfo(Vec3i(0),vsize.x, root) )
		var height = Util.log2(rootNodeSize)

		def hasNext = history != Nil
		def next = history.head match{
			case InnerNodeInfo(pos,size, n:Leaf) =>
				history = history.tail
				WorldNodeInfo(pos, size, n.h)

			case InnerNodeInfo(pos, size, n:InnerNode) =>
				val hsize = size >> 1
				// Vec3i(0) until Vec3i(2)
				val ndata =
				for(i <- 0 until 8) yield {
					val offset = Vec3i(i&1,(i&2)>>1,(i&4)>>2)
					InnerNodeInfo( pos + offset*hsize, hsize, n.data(i) )
				}

				history = ndata ++: history.tail
				next
		}
	}
	
	def draw{
		root.draw
	}
	
	def genMesh {
		root = root.genMesh(rootNodePos,rootNodeSize,minMeshNodeSize,(x => {if(indexInRange(x)) apply(x) else World(x)}) )
		meshGenerated = true
	}
	
	import akka.dispatch.Future
	var queue:List[Pair[() => Boolean,() => Unit]] = Nil

	def move(dir:Vec3i){
		// checkrange
		worldWindowPos += dir * minMeshNodeSize

		if(any(lessThan(worldWindowPos,rootNodePos))) {
			println("World gets bigger-")
			val newroot = new InnerNodeOverVertexArray(EmptyHexaeder)
			for(i <- 0 until 7)
				newroot.data(i) = DeadInnderNode
			newroot.data(7) = root
			root = newroot

			rootNodePos -= rootNodeSize
			rootNodeSize *= 2
		}
		else if(any(greaterThan(worldWindowPos+worldWindowSize,rootNodePos+rootNodeSize))) {
			println("World gets bigger+")
			val newroot = new InnerNodeOverVertexArray(EmptyHexaeder)
			for(i <- 1 until 8)
				newroot.data(i) = DeadInnderNode
			newroot.data(0) = root
			root = newroot
			println("old rootNodeSize"+rootNodeSize)
			rootNodeSize *= 2
			println(rootNodeSize)
		}
		//TODO shrink tree

		val slicepos = worldWindowPos + (dir+1)/2*worldWindowSize - abs(dir) * minMeshNodeSize
		val slicesize = (Vec3i(1) - abs(dir)) * (worldWindowSize / minMeshNodeSize) + abs(dir)
		println("slicesize",slicesize)
		println((Vec3i(1) - abs(dir)))
		println(worldWindowSize / minMeshNodeSize)
		
		val sliceFuture = WorldNodeGenerator.generateSliceAt(slicepos, minMeshNodeSize, slicesize)
		val ready:() => Boolean = sliceFuture.isCompleted _
		val run:() => Unit = ( () => {
			// fügt eine ebene zur welt hinzu
			val slice = sliceFuture.get
			println("add")
			for(vi <- Vec3i(0) until slicesize){
				val spos = slicepos + vi*minMeshNodeSize
				println(spos)
				root = root.insertNode(rootNodePos,rootNodeSize,slice(vi),spos,minMeshNodeSize)
			}
			
			val remslicepos = worldWindowPos + (-dir+1)/2*worldWindowSize - abs(dir) * minMeshNodeSize
			
			println("remove")
			println(worldWindowPos)
			println((-dir+1)/2 * worldWindowSize)
			println((-dir-1)/2 * minMeshNodeSize)
			println(remslicepos)
			// löscht nicht mehr benötigte ebene
			for(vi <- Vec3i(0) until slicesize) {
				val spos = remslicepos + vi * minMeshNodeSize
				println(spos)
				root = root.insertNode(rootNodePos,rootNodeSize,DeadInnderNode,spos,minMeshNodeSize)
			}
			
			println("done")
		} )
		
		queue ::= Pair(ready,run)
	}

	// fügt die im intergrund berechneten Nodes in den Baum ein
	def makeNodeUpdates{
		val (set,unset) = queue.partition( _._1() )
		
		for( (_,run) <- set )
			run()
		
		queue = unset
	}
}

trait Octant extends Serializable{
	def apply(p:Vec3i,nodepos:Vec3i,nodesize:Int) : Hexaeder
	def updated(p:Vec3i,nh:Hexaeder,nodepos:Vec3i,nodesize:Int):Octant

	/**generates the polygons for this Octant
	 * @return number of added vertices
	 */
	// creates polygons in subtree and adds them to meshBuilder
	// TODO: Hier weitermachen worldaccess implementieren !!!
	def genPolygons(nodepos:Vec3i,nodesize:Int,meshBuilder:TextureMeshBuilder,worldaccess:(Vec3i =>Hexaeder)):Int
	//similar to updated, but this function also generates patches to update the mesh
	def patchWorld(p:Vec3i, nh:Hexaeder, vertpos:Int, offset:Int, nodepos:Vec3i, nodesize:Int) : (Octant, Patch[TextureMeshData])
	//similar to patch, but it does not change anything in the Tree
	def repolyWorld(p:Vec3i, vertpos:Int, offset:Int, nodepos:Vec3i, nodesize:Int) : Patch[TextureMeshData]
	// adds InnerNodeWithVertexArray into the tree, and creates Meshes inside of them
	def genMesh(nodepos: Vec3i, nodesize: Int, dstnodesize: Int, worldaccess:(Vec3i => Hexaeder) ):Octant

	def draw{}

	def insertNode(nodepos:Vec3i, nodesize:Int, insertnode:Octant, insertnodepos:Vec3i, insertnodesize:Int) : Octant
	def index2vec(idx:Int) =
		Vec3i((idx & 1),(idx & 2) >> 1,(idx & 4) >> 2)
	def indexVec(p:Vec3i,nodepos:Vec3i,nodesize:Int) = ((p-nodepos)*2)/nodesize
	def flat(ivec:Vec3i) = ivec.x+(ivec.y<<1)+(ivec.z<<2)
}

class Leaf(val h:Hexaeder) extends Octant{
	def insertNode(nodepos:Vec3i, nodesize:Int, insertnode:Octant, insertnodepos:Vec3i, insertnodesize:Int) = insertnode

	override def apply(p:Vec3i,nodepos:Vec3i,nodesize:Int) = h

	override def updated(p:Vec3i,nh:Hexaeder,nodepos:Vec3i,nodesize:Int) = {
		if(h == nh)
			this
		else{
			if(nodesize >= 2) {
				// go deeper into the tree?
				val replacement = new InnerNode(h)
				replacement.updated(p,nh,nodepos,nodesize)
			}
			else {
				new Leaf(nh)
			}
		}
	}

	override def toString = if(h eq null) "null" else h.toString

	override def equals(that:Any) = {
		that match {
			case l:Leaf =>
				h == l.h
			case _ =>
				false
		}
	}
	
	// Fügt die oberfläche zwischen zwei hexaedern zum meshBuilder hinzu
	def addSurface(from:Hexaeder,to:Hexaeder,pos:Vec3i,dir:Int,meshBuilder:TextureMeshBuilder) = {
		surfacetimer.start
		assert(meshBuilder != null)
	
		import meshBuilder._
		
		val axis = dir >> 1
		val direction = dir & 1

		var vertexCounter = 0

		if(  (to == EmptyHexaeder)
				 || !to.planemax(axis,1-direction)
				 || !from.planemax(axis,direction)
				 || !occludes2d(
				occludee=from.planecoords(axis,direction).toSet,
				occluder=to.planecoords(axis,1-direction).toSet)
			){

			val triangleCoords = from.planetriangles(axis, direction)
			val (t1,t2) = triangleCoords splitAt 3

			val axisa = 1-((axis+1) >> 1)
			val axisb = (2 - (axis >> 1))

			for( t @ Seq(v0,v1,v2) <- List( t1, t2 ) ) {
				if(v0 != v1 && v1 != v2 && v0 != v2){

					for(v <- t){
						vertexBuilder += (Vec3(pos) + v)
						texCoordBuilder += Vec2( v(axisa)/2f + (direction & (axis >> 1))/2f , v(axisb)/2f )
						vertexCounter += 1
					}

					normalBuilder += normalize(cross(v2-v1,v0-v1))
				}
			}
		}
		surfacetimer.stop
		vertexCounter
	}
	
	def genPolygons(nodepos:Vec3i,nodesize:Int,meshBuilder:TextureMeshBuilder,worldaccess:(Vec3i =>Hexaeder)):Int = {
		assert(meshBuilder != null)
		var vertexCounter = 0
		if(nodesize == 1) {
			for( i <- (0 to 5) ){
				val p2 = nodepos.clone
				p2(i >> 1) += ((i&1)<<1)-1
				
		subtimer.start
				val to = worldaccess(p2)

				vertexCounter += addSurface(h,to,nodepos,i,meshBuilder)
		subtimer.stop
			}
		}
		else {
			if(h == FullHexaeder){
				for( dir <- (0 to 5) ){
					val axis = dir >> 1

					val axisa = 1-((axis+1) >> 1)
					val axisb = (2 - (axis >> 1))

					//TODO: Oberfläche eines Octanten als Quadtree abfragen
					for( spos <- Vec2i(0) until Vec2i(nodesize) ){
						val p1 = nodepos.clone
						p1( axisa ) += spos(0)
						p1( axisb ) += spos(1)
						p1( axis )  += (nodesize-1) * (dir&1)
						val p2 = p1.clone
						p2( axis ) += ((dir&1)<<1)-1
					subtimer.start
						val other = worldaccess(p2)

						vertexCounter += addSurface(h,other,p1,dir,meshBuilder)
					subtimer.stop
					}
				}
			}
		}
		vertexCounter
	}
	
	override def patchWorld(p:Vec3i, nh:Hexaeder, vertpos:Int, offset:Int, nodepos:Vec3i, nodesize:Int) : (Octant, Patch[TextureMeshData]) = {
		val replacement = updated(p,nh,nodepos,nodesize)

		val builder = new TextureMeshBuilder
		replacement.genPolygons(nodepos,nodesize,builder,World.apply _)
		val patch = Patch(vertpos,offset,builder.result)
		(replacement,patch)
	}

	override def repolyWorld(p:Vec3i, vertpos:Int, offset:Int, nodepos:Vec3i, nodesize:Int) : Patch[TextureMeshData] = {
		val builder = new TextureMeshBuilder
		genPolygons(nodepos,nodesize,builder,World.apply _)
		Patch(vertpos,offset,builder.result)
	}

	def genMesh(nodepos: Vec3i, nodesize: Int, dstnodesize: Int, worldaccess:(Vec3i => Hexaeder) ):Octant = {
		throw new NoSuchMethodException("a Leaf does not contain a Mesh, this should be higher in the Octree")
	}
}

class InnerNodeOverVertexArray(h:Hexaeder) extends Octant {
	val data = new Array[Octant](8)
	//initiali the 8 child nodes
	for(fidx <- 0 until 8) {
		data(fidx) = new Leaf(h)
	}
	
	def apply(p:Vec3i,nodepos:Vec3i,nodesize:Int) = {
		val v = indexVec(p,nodepos,nodesize)
		val index = flat(v)
		val hsize = nodesize >> 1 // half size
		data(index)(p,nodepos+v*hsize,hsize)
	}

	def merge_? = {
		val first = data(0)
		var merge = true
			for(i <- data )
				merge = merge && (i == first)
		merge
	}

	def updated(p:Vec3i,h:Hexaeder,nodepos:Vec3i,nodesize:Int) = {
		assert( all(lessThanEqual(nodepos,p)) )
		assert( all(lessThan(p,nodepos+nodesize)) )

		val v = indexVec(p,nodepos,nodesize)
		val index = flat(v)
		val hsize = nodesize >> 1 // half size

		data(index) = data(index).updated(p,h,nodepos+v*hsize,hsize)

		if(merge_?)
			new Leaf(h)
		else
			this
	}

	def genMesh(nodepos: Vec3i, nodesize: Int, dstnodesize: Int, worldaccess:(Vec3i => Hexaeder) ):Octant = {
		throw new NoSuchMethodException("if InnerNodeOverVertexArray exists then a mesh should already be generated")
	}
	
	override def patchWorld(p:Vec3i, nh:Hexaeder, vertpos:Int, offset:Int, nodepos:Vec3i,nodesize:Int):(Octant, Patch[TextureMeshData]) = {
		//TODO nachbarn patchen
		assert(Util.indexInRange(p,nodepos,nodesize))

		val v = indexVec(p,nodepos,nodesize)
		val index = flat(v)
		val hsize = nodesize >> 1 // half size
		data(index).patchWorld(p, nh, -1, -1, nodepos+v*hsize, hsize)

		val neigbours = ((0 until  6) map (i => {val v = Vec3i(0); v(i/2) = 2*(i&1)-1; p+v} )
			filter ( n => indexInRange(n,nodepos,nodesize) )
			map ( n => Pair(n, indexVec(n,nodepos,nodesize) ) )
			distinctBy( _._2 )
		)

		for( (n,nv) <- neigbours if(nv != v) ){
			val index = flat(nv)
			data(index).repolyWorld(n,-1,-1, nodepos+nv*hsize, hsize)
		}
		
		(this,null)
	}

	override def repolyWorld(p:Vec3i, vertpos:Int, offset:Int, nodepos:Vec3i,nodesize:Int):Patch[TextureMeshData] = {
		val v = indexVec(p,nodepos,nodesize)
		val index = flat(v)
		val hsize = nodesize >> 1 // half size
		data(index).repolyWorld(p, -1, -1, nodepos+v*hsize, hsize)
		null
	}
	
	override def draw{
		for(child <- data)
			child.draw
	}
	
	override def toString = data.mkString("(",",",")")
	
	override def genPolygons(nodepos:Vec3i, nodesize:Int, meshBuilder:TextureMeshBuilder, worldaccess:(Vec3i =>Hexaeder)):Int = 
		throw new NoSuchMethodException("in root use genMesh instead of genPolygons")
	
	def insertNode(nodepos:Vec3i, nodesize:Int, insertnode:Octant, insertnodepos:Vec3i, insertnodesize:Int) = {
		assert(indexInRange(insertnodepos,nodepos,nodesize))
		if(nodesize == insertnodesize)
			insertnode
		else{
			val v = indexVec(insertnodepos,nodepos,nodesize)
			val index = flat(v)
			val hsize = nodesize >> 1
			data(index) = data(index).insertNode(nodepos+v*hsize, hsize, insertnode, insertnodepos, insertnodesize)
			this
		}
		// TODO merge?
	}
}

class InnerNode(h:Hexaeder) extends InnerNodeOverVertexArray(h) {
	val voffset = new Array[Int](8)
	
	override def genPolygons(nodepos:Vec3i,nodesize:Int,meshBuilder:TextureMeshBuilder,worldaccess:(Vec3i =>Hexaeder)) = {
		val hsize = nodesize >> 1 // half size
		for(i <- 0 until 8){
			val v = Vec3i(i&1, (i&2)>>1, i>>2)
			voffset(i) = data(i).genPolygons(nodepos+v*hsize,hsize,meshBuilder,worldaccess)
		}
		voffset.sum
	}
	
	override def patchWorld(p:Vec3i, nh:Hexaeder, vertpos:Int, offset:Int, nodepos:Vec3i,nodesize:Int) = {
		val hsize = nodesize >> 1 // half size
		val v = indexVec(p,nodepos,nodesize)
		val index = flat(v)
		
		val newvertpos = vertpos + voffset.view(0,index).sum
		val newoffset = voffset(index)

		val (newNode,patch) = data(index).patchWorld(p,nh,newvertpos,newoffset,nodepos+v*hsize,hsize)

		data(index) = newNode
		
		voffset(index) += patch.data.size - patch.size

		if(merge_?){
			val mb = new TextureMeshBuilder
			val replacement = new Leaf(nh)
			replacement.genPolygons(nodepos,nodesize,mb, World.apply _)
			( replacement, Patch(vertpos,offset,mb.result) )
		}
		else
			(this,patch)
	}

	override def repolyWorld(p:Vec3i, vertpos:Int, offset:Int, nodepos:Vec3i,nodesize:Int) = {
		val hsize = nodesize >> 1 // half size
		val v = indexVec(p,nodepos,nodesize)
		val index = flat(v)

		val newvertpos = vertpos + voffset.view(0,index).sum
		val newoffset = voffset(index)

		val patch = data(index).repolyWorld(p, newvertpos, newoffset, nodepos+v*hsize, hsize)
		//vertexzahl hat sich geändert, und braucht ein update
		voffset(index) += patch.data.size - patch.size

		patch
	}

	override def genMesh(nodepos:Vec3i, nodesize:Int, destnodesize:Int, worldaccess:(Vec3i => Hexaeder)) = {
		if(nodesize <= destnodesize){
			val replacement = new InnerNodeWithVertexArray(EmptyHexaeder)
			for(i <- 0 until 8)
				replacement.data(i) = data(i)
			replacement.genMesh(nodepos,nodesize,destnodesize,worldaccess)
		}
		else{
			val replacement = new InnerNodeOverVertexArray(EmptyHexaeder)
			for(i <- 0 until  8){
				val v = index2vec(i)
				val hsize = nodesize >> 1
				replacement.data(i) = data(i).genMesh(v*hsize+nodepos, hsize, destnodesize, worldaccess)
			}
			replacement
		}
	}
}

class InnerNodeWithVertexArray(h:Hexaeder) extends InnerNode(h) {
	var mesh:MutableTextureMesh = null
	
	override def genMesh(nodepos:Vec3i, nodesize:Int, destnodesize:Int, worldaccess:(Vec3i => Hexaeder)) = {
		assert(mesh == null)
		val meshBuilder = new TextureMeshBuilder
		val result = super.genPolygons(nodepos, nodesize, meshBuilder, worldaccess)
		mesh = new MutableTextureMesh(meshBuilder.result)
		//TODO genvbo sollte hier nicht aufgerufen werden, damit genPolygons auch in anderen Threads als dem render Thread aufgerufen werden kann
		//mesh.genvbo
		
		this
	}
	
	override def draw{
		mesh.draw
	}
	
	override def patchWorld(p:Vec3i, nh:Hexaeder, vertpos:Int, offset:Int, nodepos:Vec3i,nodesize:Int) : (Octant, Patch[TextureMeshData]) = {
		
		def inRange(pos:Vec3i) = (all(lessThanEqual(nodepos,pos)) && all(lessThan(pos,nodepos + nodesize)))
		
		assert( inRange(p) )
		
		
		val (replacement,patch) = super.patchWorld(p, nh, 0, mesh.size, nodepos,nodesize)
		var patches = patch :: Nil
		
		// Patches die die Neuen Polygone generieren
		for(i <- 0 until 6) {
			val npos = p.clone
			npos(i >> 1) += ((i&1) << 1)-1
			if( inRange(npos) ){
				patches ::= super.repolyWorld(npos, 0, mesh.size, nodepos, nodesize)
			}
			// TODO nachbarn
		}

		// mehrer patches die hintereinander abgearbeitet werden können,
		// können hier auch in einem schritt ausgeführt werden
		mesh patch patches.reverse
		
		// es wurde schon gepatched, deshalb muss dieser patch nicht mehr mitgeschleppt werden
		(replacement,null)
	}

	override def repolyWorld(p:Vec3i, vertpos:Int, offset:Int, nodepos:Vec3i, nodesize:Int) = {

		val index = flat(indexVec(p,nodepos,nodesize))
		// vertpos und offset wird von super.repolyWorld gesetzt
		mesh patch List(super.repolyWorld(p,0,0,nodepos,nodesize))
		null 
	}
}

object DeadInnderNode extends Octant{
	def apply(p:Vec3i,nodepos:Vec3i,nodesize:Int) = EmptyHexaeder
	def updated(p:Vec3i,nh:Hexaeder,nodepos:Vec3i,nodesize:Int) = {
		throw new NoSuchMethodException("dead nodes cant be updated")
	}
	/**generates the polygons for this Octant
	 * @return number of added vertices
	 */
	// diese methode wird nicht gebraucht, da wir uns oberhalb der VBOs befinden, und auch keine Hexaeder definiert sind
	def genPolygons(nodepos:Vec3i,nodesize:Int,meshBuilder:TextureMeshBuilder,worldaccess:(Vec3i =>Hexaeder)) = {
		throw new NoSuchMethodException("dead nodes can't generate Polygons")
	}

	//similar to updated, but this function also generates patches to update the mesh
	def patchWorld(p:Vec3i, nh:Hexaeder, vertpos:Int, offset:Int, nodepos:Vec3i, nodesize:Int) : (Octant, Patch[TextureMeshData]) = {
		throw new NoSuchMethodException("dead nodes can't be patched")
	}

	// TODO EmptyPatch
	def repolyWorld(p:Vec3i, vertpos:Int, offset:Int, nodepos:Vec3i, nodesize:Int) : Patch[TextureMeshData] = null

	override def genMesh(nodepos: Vec3i, nodesize: Int, dstnodesize: Int, worldaccess:(Vec3i => Hexaeder)) = this

	def insertNode(nodepos:Vec3i, nodesize:Int, insertnode:Octant, insertnodepos:Vec3i, insertnodesize:Int) = {
		if(nodesize == insertnodesize)
			insertnode
		else{
			val replacement = new InnerNodeOverVertexArray(EmptyHexaeder)
			for(i <- 0 until 8)
				replacement.data(i) = DeadInnderNode

			val v = indexVec(insertnodepos,nodepos,nodesize)
			val index = flat(v)
			val hsize = nodesize >> 1
			replacement.data(index) = insertNode(nodepos+v*hsize,hsize,insertnode, insertnodepos, insertnodesize)
			replacement
		}
		// TODO merge?
	}
}
