package downearth

import org.lwjgl.opengl.GL11._

import org.lwjgl.opengl.ARBBufferObject._
import org.lwjgl.opengl.ARBVertexBufferObject._

import simplex3d.math.double.{Vec2,Vec3,Vec4}
import simplex3d.math.Vec3i
import simplex3d.math.double.functions.normalize

import simplex3d.data._
import simplex3d.data.double._
import simplex3d.math.integration.RFloat
import org.lwjgl.BufferUtils

// Klassen zur verwaltung von VertexArrays. Sie kapseln zum einen die Daten, und
// erlauben einen vereinfachten Zugriff und Manipulation, zum anderen übernehmen
// sie die Kommunikation mit der Grafikkarte.

trait Mesh extends Serializable {
	var vertexBufferObject:Int = 0
	def genvbo
	def freevbo
	def free = glDeleteBuffersARB(vertexBufferObject)
	def size:Int
}
 
trait MutableMesh[T <: MeshData] extends Mesh {
	def applyUpdates(updates:Iterable[Update[T]])
}

trait MeshData {
	def size:Int
}

trait MeshBuilder[T <: MeshData] {
	def result : T
}

case class TextureMeshData(
			vertexArray:Array[Vec3],
			normalArray:Array[Vec3],
			texcoordsArray:Array[Vec2]
//			colorArray:Array[Vec4]
		) extends MeshData{
	def size = vertexArray.size
}

import scala.collection.mutable.ArrayBuilder

case class TextureMeshBuilder(
			vertexBuilder:ArrayBuilder[Vec3] = ArrayBuilder.make[Vec3],
			normalBuilder:ArrayBuilder[Vec3] = ArrayBuilder.make[Vec3],
			texCoordBuilder:ArrayBuilder[Vec2] = ArrayBuilder.make[Vec2]
//			colorBuilder:ArrayBuilder[Vec4] = ArrayBuilder.make[Vec4]
			) extends MeshBuilder[TextureMeshData] {
	def result = TextureMeshData(
					vertexBuilder.result,
					normalBuilder.result,
					texCoordBuilder.result
//					colorBuilder.result
					)
}

// A <: B <: MeshData => Patch[A] <: Patch[B]
case class Update[+T <: MeshData](pos:Int,size:Int,data:T) {
	//the difference of the size after the patch has been applied
	def sizedifference = data.size - size
}

object MutableTextureMesh {
	
	def apply(data:TextureMeshData) = {
	import data.{vertexArray,texcoordsArray}
//	import data.{vertexArray, colorArray}
	
	val normalArray = if(Config.smoothShading) new Array[Vec3](vertexArray.size) else (data.normalArray flatMap (x => Seq(x,x,x)))
	
	
	if(Config.smoothShading) {
		
		val indices = (0 until vertexArray.size).sortWith( (a,b) => {
			val v1 = vertexArray(a)
			val v2 = vertexArray(b)
			(v1.x < v2.x) || (v1.x == v2.x && v1.y < v2.y) || (v1.xy == v2.xy && v1.z < v2.z)
		})
		
		var equals:List[Int] = Nil
		
		for(index <- indices){
			if( equals == Nil || vertexArray(equals.head) == vertexArray(index) )
				equals ::= index
			else{
				val normal = normalize( (equals map ( i => data.normalArray(i/3) ) ).reduce(_+_) )
				for(j <- equals)
					normalArray(j) = normal
				equals = index :: Nil
			}
		}
		
		def makeSmooth{
			val normal = normalize( (equals map ( i => data.normalArray(i/3) ) ).reduce(_+_) )
			for(i <- equals)
				normalArray(i) = normal
		}
		
		if(equals != Nil)
			makeSmooth
	}


    val byteBuffer = BufferUtils.createByteBuffer(vertexArray.length * 8 * 4)
    val vertices = DataView[Vec3,RFloat](byteBuffer, 0, 8)
    val normals  = DataView[Vec3,RFloat](byteBuffer, 3, 8)
    val texcoords= DataView[Vec2,RFloat](byteBuffer, 6, 8)

		for(i <- 0 until vertexArray.length){
			vertices(i) = vertexArray(i)
			normals(i) = normalArray(i)
			texcoords(i) = texcoordsArray(i)
		}
		new MutableTextureMesh(vertices,normals,texcoords)
//		new MutableTextureMesh(vertices,normals,colors)
	}
	
	def apply(meshes:Array[MutableTextureMesh]) = {
		val size = meshes.map(_.size).sum

    val byteBuffer = BufferUtils.createByteBuffer(size * 8 * 4)
    val vertices = DataView[Vec3,RFloat](byteBuffer, 0, 8)
    val normals  = DataView[Vec3,RFloat](byteBuffer, 3, 8)
    val texcoords= DataView[Vec2,RFloat](byteBuffer, 6, 8)

		var currentpos = 0
		var currentsize = 0
		
		for(mesh <- meshes){
			currentsize = mesh.size
			vertices.bindingBufferSubData(currentpos,currentsize) put 
				mesh.vertices.bindingBufferSubData(0,currentsize)
			currentpos += currentsize
		}
		new MutableTextureMesh(vertices,normals,texcoords)
//		new MutableTextureMesh(vertices,normals,colors)
	}
}

// Diese Klasse wird verwendet, um den Octree darzustellen. Er repräsentiert ein
// Mesh und stellt Methoden zur Verfügung, die es erlauben das Mesh über Updates
// zu verändern.
class MutableTextureMesh(vertices_ :DataView[Vec3,RFloat], 
                         normals_ :DataView[Vec3,RFloat], 
                         texcoords_ :DataView[Vec2,RFloat]
//                         colors_ :DataView[Vec4,RFloat]
                         ) 
    	extends TextureMesh(vertices_, normals_, texcoords_) // colors_), 
    	with MutableMesh[TextureMeshData] {

	private var msize = vertices_.size
	override def size = msize
	
	// fügt mehrere Updates in den Hexaeder ein. Hier ist es Sinnvoll alle 
	// Updates erst zusammenzuführen, um sie dann alle in einem Schritt in den 
	// Hexaeder einzufügen.
	def applyUpdates(updates:Iterable[Update[TextureMeshData]]) {
		val oldvertices = vertices
		/*val oldnormals = normals
		val oldcoords = texcoords*/
		
		var newsize = size
		for(update ← updates){
			newsize += update.sizedifference
		}
		
		assert(newsize >= 0,"newsize must be greater than or equal to 0")

    val byteBuffer = BufferUtils.createByteBuffer(newsize * 8 * 4)
    vertices = DataView[Vec3,RFloat](byteBuffer, 0, 8)
    normals  = DataView[Vec3,RFloat](byteBuffer, 3, 8)
    texcoords= DataView[Vec2,RFloat](byteBuffer, 6, 8)

//		colors = t._3

		case class View(offset:Int,size:Int,data:TextureMeshData){
			def split(splitpos:Int) = {
				assert(splitpos > 0 && splitpos < size )
				(View(offset,splitpos,data),View(offset+splitpos,size-splitpos,data))
			}
		}

		var dataview = List(View(0,oldvertices.size,null))

		implicit class RichViewSplit(viewlist:List[View]) {
			def viewsplit(pos:Int) = {
				var destoffset = 0
				val (pre,other) = viewlist.span {
					v =>
						if( destoffset + v.size <= pos ) {
							destoffset += v.size
							true
						}
						else
							false
				}

				if( destoffset < pos ) {
					val (left,right) = other.head.split(pos-destoffset)
					(pre :+ left ,right :: other.tail)
				}
				else
					(pre, other)
			}
		}

		for(update <- updates) {
			val (pre, other) = dataview.viewsplit(update.pos)
			val post = other.viewsplit(update.size)._2
			dataview = (pre ::: View(0,update.data.size,update.data) :: post)
		}

		var index = 0;
		for(View(offset,size,data) <- dataview) {
			if(data != null) {
				for(i <- offset until (offset+size) ) {
					vertices(index) = data.vertexArray(i)
					normals(index) = data.normalArray(i/3)
					texcoords(index) = data.texcoordsArray(i)
					//colors(index) = data.colorArray(i)
					index += 1
				}
			}
			else{
				vertices.bindingBufferSubData(index,size).put(oldvertices.bindingBufferSubData(offset,size))
				index += size
			}
		}

		msize = newsize
		genvbo
	}
	
	def split(chunksizes:Array[Int]) = {
		var index = 0
		for(chunksize ← chunksizes) yield {
      val byteBuffer = BufferUtils.createByteBuffer(chunksize * 8 * 4)
      val newvertices = DataView[Vec3,RFloat](byteBuffer, 0, 8)
      val newnormals  = DataView[Vec3,RFloat](byteBuffer, 3, 8)
      val newtexcoords= DataView[Vec2,RFloat](byteBuffer, 6, 8)


			newvertices.bindingBuffer.put(vertices.bindingBufferSubData(index,chunksize))
			index += chunksize
			new MutableTextureMesh(newvertices,newnormals,newtexcoords)
//			new MutableTextureMesh(newvertices,newnormals,newcolors)
		}
	}
}

object TextureMesh{
	def apply(data:TextureMeshData) = {
	import data._
    val byteBuffer = BufferUtils.createByteBuffer(vertexArray.length * 8 * 4)
    val vertices = DataView[Vec3,RFloat](byteBuffer, 0, 8)
    val normals  = DataView[Vec3,RFloat](byteBuffer, 3, 8)
    val texcoords= DataView[Vec2,RFloat](byteBuffer, 6, 8)

		for(i <- 0 until vertexArray.size){
			vertices(i) = vertexArray(i)
			normals(i) = normalArray(i/3)
			texcoords(i) = texcoordsArray(i)
//			colors(i) = colorArray(i)
		}
		new TextureMesh(vertices,normals,texcoords)
//		new TextureMesh(vertices,normals,colors)
	}
}

// die Basisklasse TextureMesh kann serialisiert werden, und somit auch auf der
// Festplatte gespeichert werden.
class TextureMesh(@transient var vertices:DataView[Vec3,RFloat], 
                  @transient var normals:DataView[Vec3,RFloat], 
                  @transient var texcoords:DataView[Vec2,RFloat]
                  //@transient var colors:DataView[Vec4,RFloat]
                  ) 
                       extends Mesh with Serializable {
  
	import java.io.{ObjectInputStream, ObjectOutputStream, IOException}
	@throws(classOf[IOException])
	private[this] def writeObject(out:ObjectOutputStream) {
		out.writeInt(msize)
		out.writeObject(new InterleavedData(vertices,normals,texcoords))
//		out.writeObject(new InterleavedData(vertices,normals,colors))
	}
	
	@throws(classOf[IOException]) @throws(classOf[ClassNotFoundException])
	private[this] def readObject(in:ObjectInputStream) {
		msize = in.readInt
		val data = in.readObject.asInstanceOf[InterleavedData]
		vertices = data(0).asInstanceOf[DataView[Vec3,RFloat]]
		normals  = data(1).asInstanceOf[DataView[Vec3,RFloat]]
		texcoords= data(2).asInstanceOf[DataView[Vec2,RFloat]]
//		colors   = data(2).asInstanceOf[DataView[Vec4,RFloat]]
	}
	
	@transient private var msize = vertices.size
	def size = msize
	
	def genvbo {
		freevbo
		// vbo with size of 0 can't be initialized
		if( size > 0 ) {
			vertexBufferObject = glGenBuffersARB()
			glBindBufferARB(GL_ARRAY_BUFFER_ARB, vertexBufferObject)
			glBufferDataARB(GL_ARRAY_BUFFER_ARB, vertices.bindingBuffer, GL_STATIC_COPY_ARB)
			glBindBufferARB(GL_ARRAY_BUFFER_ARB, 0)
		}
		else
			vertexBufferObject = -1
	}
	
	def freevbo {
		if( vertexBufferObject > 0 ) {
			glDeleteBuffersARB(vertexBufferObject)
			vertexBufferObject = 0
		}
	}
}
