/**
 * User: arne
 * Date: 26.04.13
 * Time: 01:14
 */

package downearth.rendering

import org.lwjgl.opengl.GL11._
import org.lwjgl.opengl.GL15._
import simplex3d.math.double._
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.{ARBFragmentShader, ARBVertexShader, ARBShaderObjects}
import org.lwjgl.opengl.ARBBufferObject._
import org.lwjgl.opengl.ARBVertexBufferObject._
import downearth._
import downearth.gui.{Gui}
import downearth.worldoctree._
import downearth.world.World
import downearth.worldoctree.NodeInfo
import scala.collection.mutable.ArrayBuffer

object Renderer {

  val lightPos = BufferUtils.createFloatBuffer(4)
  val ambientLight = BufferUtils.createFloatBuffer(4)
  ambientLight.put( Array(0.2f, 0.2f, 0.2f, 1f) )
  ambientLight.rewind()

  var shader = 0
  var vertShader = 0
  var fragShader = 0

  def draw() {
    glClear( GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT )
    renderScene( Player.camera )
    Gui.renderScene
  }

  def initshaders {
    shader = ARBShaderObjects.glCreateProgramObjectARB
    if( shader != 0 ) {
      vertShader = ARBShaderObjects.glCreateShaderObjectARB(ARBVertexShader.GL_VERTEX_SHADER_ARB)
      fragShader=ARBShaderObjects.glCreateShaderObjectARB(ARBFragmentShader.GL_FRAGMENT_SHADER_ARB)
      if( vertShader != 0 ) {
        val vertexPath = getClass.getClassLoader.getResource("shaders/screen.vert").getPath
        val vertexCode = io.Source.fromFile(vertexPath).mkString
        ARBShaderObjects.glShaderSourceARB(vertShader, vertexCode)
        ARBShaderObjects.glCompileShaderARB(vertShader)
      }

      if( fragShader != 0 ) {
        val fragPath = getClass.getClassLoader.getResource("shaders/screen.frag").getPath
        val fragCode = io.Source.fromFile(fragPath).mkString
        ARBShaderObjects.glShaderSourceARB(fragShader, fragCode)
        ARBShaderObjects.glCompileShaderARB(fragShader)
      }

      if(vertShader !=0 && fragShader !=0) {
        ARBShaderObjects.glAttachObjectARB(shader, vertShader)
        ARBShaderObjects.glAttachObjectARB(shader, fragShader)
        ARBShaderObjects.glLinkProgramARB(shader)
        ARBShaderObjects.glValidateProgramARB(shader)
      }
    }
//    printLogInfo(shader)
//    printLogInfo(vertShader)
//    printLogInfo(fragShader)
  }

  def lighting( position:Vec3 ) {
    if( Config.wireframe ) {
      glPolygonMode( GL_FRONT_AND_BACK, GL_LINE )
      glDisable(GL_LIGHTING)
    }
    else {
      glPolygonMode( GL_FRONT_AND_BACK, GL_FILL )
      glEnable(GL_LIGHTING)

      //Add positioned light
      lightPos.put(0, position(0).toFloat)
      lightPos.put(1, position(1).toFloat)
      lightPos.put(2, position(2).toFloat)
      lightPos.put(3, 1)

      glLight(GL_LIGHT0, GL_POSITION, lightPos )
      glEnable(GL_LIGHT0)

      //Add ambient light
      glLightModel(GL_LIGHT_MODEL_AMBIENT, ambientLight)
    }
  }

  def renderScene(camera:Camera) {
    glViewport(0, 0, Main.width.toInt, Main.height.toInt)
    glEnable(GL_CULL_FACE)
    glEnable(GL_COLOR_MATERIAL)
    glEnable(GL_TEXTURE_2D)

    glMatrixMode( GL_PROJECTION )
    glLoadMatrix( camera.projectionBuffer )

    Skybox.render

    glMatrixMode( GL_MODELVIEW )
    glLoadMatrix( camera.viewBuffer )

    lighting( camera.position )

    glEnable(GL_DEPTH_TEST)

    val frustumTest:FrustumTest =
      if( Config.frustumCulling )
        new FrustumTestImpl(camera.projection, camera.view)
      else {
        new FrustumTest {
          def testNode( info:NodeInfo ) = true
        }
      }

    drawcalls = 0
    emptydrawcalls = 0

    val order = WorldOctree.frontToBackOrder(camera.direction)

    drawOctree(World.octree, frustumTest)

    if(Config.debugDraw) {
      drawDebugOctree(World.octree, order, frustumTest)
    }

    Player.activeTool.draw

    if(Config.debugDraw) {
      BulletPhysics.debugDrawWorld
      Draw.drawSampledNodes
    }
  }

  var drawcalls = 0
  var emptydrawcalls = 0

  def drawDebugOctree(octree:WorldOctree, order:Array[Int], test:FrustumTest) {

    glDisable(GL_LIGHTING)
    glDisable(GL_TEXTURE_2D)

    glPushMatrix()
    val pos2 = octree.worldWindowPos + 0.05
    glTranslated(pos2.x, pos2.y, pos2.z)
    glColor3f(0,1,0)
    Draw.renderCube(octree.worldWindowSize - 0.1)
    glPopMatrix()

    var maximumDrawcalls = Config.maxDebugDrawQubes

    octree.queryRegion(test)(order) {
    case (info,octant) =>

      if(! octant.hasChildren) {
        glPushMatrix
        val p = info.pos
        glTranslatef(p.x, p.y, p.z)

        if(octant.isInstanceOf[MeshNode])
          glColor3f(1,0,0)
        else
          glColor3f(0,0,1)

        Draw.renderCube(info.size)
        glPopMatrix

        maximumDrawcalls -= 1
      }
      maximumDrawcalls > 0
    }
  }

  def drawOctree(octree:WorldOctree, test:FrustumTest) {

    import org.lwjgl.opengl.GL11._
    glColor3f(1,1,1)

    val order = Array(0,1,2,3,4,5,6,7)

    octree.queryRegion( test ) (order) {
      case (info, node:MeshNode) =>
        drawTextureMesh(node.mesh)
        false
      case _ => true
    }

    TextureManager.box.bind

    val nodeInfoBuffer = ArrayBuffer[NodeInfo]()

    octree.queryRegion( test ) (order) {
      case (info, UngeneratedInnerNode) =>
        nodeInfoBuffer += info
        false
      case (info, node:MeshNode) =>
        false
      case _ =>
        true
    }

    val buffer = BufferUtils.createIntBuffer( nodeInfoBuffer.size )
    glGenQueries( buffer )

    var queries:Seq[Int] = new IndexedSeq[Int] {
      val length = buffer.limit()
      def apply(i:Int) = buffer.get(i)
    }

    for( (info,queryId) <- nodeInfoBuffer zip queries ) {
      glBeginQuery(GL_SAMPLES_PASSED, queryId )

      glPushMatrix()
      glTranslatef(info.pos.x, info.pos.y, info.pos.z)
      glScaled(info.size,info.size,info.size)
      Draw.texturedCube()
      glPopMatrix()

      glEndQuery(GL_SAMPLES_PASSED)
    }


    var occluded = 0
    var visible  = 0
    var undecided = 0

    while( queries.size > 0 ) {
      queries = queries.filter { id =>
        val state = glGetQueryObjectui(id, GL_QUERY_RESULT_AVAILABLE) == GL_TRUE

        if( state ) {
          val pixelCount = glGetQueryObjectui(id, GL_QUERY_RESULT)

          if( pixelCount > 0 )
            visible += 1
          else
            occluded += 1
        }
        else
          undecided += 1

        ! state
      }
    }

    println( s"occlusion query result (${buffer.limit}):\noccluded: $occluded, visible: $visible, undecided: $undecided")

    glDeleteQueries(buffer)
  }

  def drawTextureMesh(mesh:TextureMesh) {
    //TextureManager.box.bind
    TextureManager.materials.bind

    if(mesh.vertexBufferObject == 0)
      mesh.genvbo

    if( mesh.size > 0 ) {
      drawcalls += 1
      glBindBufferARB(GL_ARRAY_BUFFER_ARB, mesh.vertexBufferObject)

      glEnableClientState(GL_VERTEX_ARRAY)
      glEnableClientState(GL_NORMAL_ARRAY)
      glEnableClientState(GL_TEXTURE_COORD_ARRAY)
      //glEnableClientState(GL_COLOR_ARRAY)

      glVertexPointer(mesh.vertices.components, mesh.vertices.rawEnum, mesh.vertices.byteStride, mesh.vertices.byteOffset)
      glNormalPointer(mesh.normals.rawEnum, mesh.normals.byteStride, mesh.normals.byteOffset)
      glTexCoordPointer(mesh.texcoords.components, mesh.texcoords.rawEnum, mesh.texcoords.byteStride, mesh.texcoords.byteOffset)
      //glColorPointer(colors.components, colors.rawType, colors.byteStride, colors.byteOffset)

      glDrawArrays(GL_TRIANGLES, 0, mesh.vertices.size)

      glDisableClientState(GL_VERTEX_ARRAY)
      glDisableClientState(GL_NORMAL_ARRAY)
      glDisableClientState(GL_TEXTURE_COORD_ARRAY)
      //glDisableClientState(GL_COLOR_ARRAY)

      glBindBufferARB(GL_ARRAY_BUFFER_ARB, 0)
    }
    else {
      emptydrawcalls += 1
    }
  }

  def activateShader(func: => Unit) {
    import ARBShaderObjects._

    val useshaders = shader != 0 && vertShader != 0 && fragShader != 0

    if(useshaders) {
      glUseProgramObjectARB(shader)
      // glUniform1fARB( glGetUniformLocationARB( shader, "time" ), ( time / 1000.0 ).toFloat )
    }

    func

    if(useshaders)
      glUseProgramObjectARB(0)
  }
}

