package openworld

import simplex3d.math._
import simplex3d.math.double._
import simplex3d.math.double.functions._

import simplex3d.data._
import simplex3d.data.double._

import org.lwjgl.opengl.GL11._
import org.lwjgl.input.Mouse

import Config._
import Util._
import org.lwjgl.BufferUtils
import java.nio.FloatBuffer

abstract class Camera {
	def renderScene
}

// Eine Kamera, die frei im Raum bewegt werden kann, und selbst dafür sorgt, dass alles was sie sieht gerendert wird
class Camera3D(var position:Vec3,var directionQuat:Quat4) extends Camera {
	def this (positionVec:Vec3,directionVec:Vec3) = this(positionVec,quaternion(lookAt(-directionVec,worldUpVector)))
	
	def camera = this
	
	def direction = {
		if( Mouse.isGrabbed ){
			directionQuat.rotateVector(-worldUpVector)
		}
		else {
			val rx = (Mouse.getX * 2.0 - screenWidth ) / screenHeight
			val ry = (Mouse.getY * 2.0 - screenHeight) / screenHeight
			directionQuat.rotateVector( normalize(Vec3(rx,ry,-1)) )
		}
	}
	def rotateVector(v:Vec3) = directionQuat.rotateVector(v)
	
	def move(delta:Vec3) {
		position += rotateVector(delta)
	}
	
	def rotate(rot:Vec3) {
		directionQuat *= Quat4 rotateX(rot.x) rotateY(rot.y) rotateZ(rot.z)
	}
	

	// rotiert die Kamera, damit der worldUpVector auch für die Kamera oben ist
	def lerpUp(factor:Double) {
		val up = inverse(directionQuat) rotateVector worldUpVector
		val α = atan(up.y, up.x) - Pi/2
		
		directionQuat *= Quat4 rotateZ(α*pow(factor, 1.5))
	}
	

	def frustum = {
		val v = screenWidth.toDouble / screenHeight.toDouble
		
		val n = 0.05 //near
		val f = 1000.0   //far
		val r = v*n   //right
		val t = n     //top
		//l = -r; b = -t
		
		Mat4(n/r,0,0,0, 0,n/t,0,0, 0,0,(f+n)/(n-f),-1, 0,0,2*f*n/(n-f),0)
	}
	def modelview = Mat4(inverse(Mat4x3 rotate(directionQuat) translate(position)))
	val m_frustumBuffer = DataBuffer[Mat4,RFloat](1)
	val m_modelviewBuffer = DataBuffer[Mat4,RFloat](1)

	def frustumBuffer = {
		m_frustumBuffer(0) = frustum
		m_frustumBuffer.buffer
	}
	def modelviewBuffer = {
		m_modelviewBuffer(0) = modelview
		m_modelviewBuffer.buffer
	}
	

	def lighting{
		if( wireframe ) {
			glPolygonMode( GL_FRONT_AND_BACK, GL_LINE )
			glDisable(GL_LIGHTING)
		}
		else {
			glPolygonMode( GL_FRONT_AND_BACK, GL_FILL )
			glEnable(GL_LIGHTING)

			//Add positioned light
			glLight(GL_LIGHT0, GL_POSITION, Vec4(position, 1.0))
			glEnable(GL_LIGHT0)

			//Add ambient light
			glLightModel(GL_LIGHT_MODEL_AMBIENT, Vec4(0.2, 0.2, 0.2, 1))
		}
	}
	
	def renderScene {
		glViewport(0, 0, screenWidth, screenHeight)
		glEnable(GL_CULL_FACE)
		glEnable(GL_COLOR_MATERIAL)
		glEnable(GL_TEXTURE_2D)
		
		glMatrixMode( GL_PROJECTION )
		glLoadMatrix( frustumBuffer )
		
		Skybox.render

		glMatrixMode( GL_MODELVIEW )
		glLoadMatrix( modelviewBuffer )

		lighting
		
		glEnable(GL_DEPTH_TEST)
		
		val frustumtest:FrustumTest =
		if( Config.frustumCulling )
			new FrustumTestImpl(frustum, modelview)
		else {
			new FrustumTest {
				def testNode( info:NodeInfo ) = true 
			}
		}
		
		World.draw(frustumtest)
		Player.activeTool.draw
		
		if(Config.debugDraw) {
			BulletPhysics.debugDrawWorld
			Draw.drawSampledNodes
		}
	}
}


trait FrustumTest {
	def testNode( info:NodeInfo ):Boolean
}

// Frustum Culling
// Idea by Mark Morley, http://web.archive.org/web/20030601123911/http://www.markmorley.com/opengl/frustumculling.html
class FrustumTestImpl(projection:Mat4, modelview:Mat4) extends FrustumTest {

	val planes = Array(Vec4(0),Vec4(0),Vec4(0),Vec4(0),Vec4(0),Vec4(0))
	val rows = transpose(projection * modelview)
	planes(0) = normalize(rows(3) - rows(0)) //right plane
	planes(1) = normalize(rows(3) + rows(0)) //left plane
	planes(2) = normalize(rows(3) + rows(1)) //bottom plane
	planes(3) = normalize(rows(3) - rows(1)) //top plane
	planes(4) = normalize(rows(3) - rows(2)) //far plane
	planes(5) = normalize(rows(3) + rows(2)) //near plane

	def testNode( info:NodeInfo ):Boolean = {
		val inside = testCube(Vec3(info.pos + info.size / 2), info.size / 2)
		return inside
	}
	
	private def testCube( pos:Vec3, radius:Double ):Boolean = {
		// TODO: Give the information, if a cube is completely in the frustum
		var p = 0
		import pos.{x,y,z}
		while( p < 6 )
		{
			if( planes(p).x * (x - radius) + planes(p).y * (y - radius) + planes(p).z * (z - radius) + planes(p).w <= 0 )
			if( planes(p).x * (x + radius) + planes(p).y * (y - radius) + planes(p).z * (z - radius) + planes(p).w <= 0 )
			if( planes(p).x * (x - radius) + planes(p).y * (y + radius) + planes(p).z * (z - radius) + planes(p).w <= 0 )
			if( planes(p).x * (x + radius) + planes(p).y * (y + radius) + planes(p).z * (z - radius) + planes(p).w <= 0 )
			if( planes(p).x * (x - radius) + planes(p).y * (y - radius) + planes(p).z * (z + radius) + planes(p).w <= 0 )
			if( planes(p).x * (x + radius) + planes(p).y * (y - radius) + planes(p).z * (z + radius) + planes(p).w <= 0 )
			if( planes(p).x * (x - radius) + planes(p).y * (y + radius) + planes(p).z * (z + radius) + planes(p).w <= 0 )
			if( planes(p).x * (x + radius) + planes(p).y * (y + radius) + planes(p).z * (z + radius) + planes(p).w <= 0 )
				return false
			p += 1
		}
		return true
	}
}
