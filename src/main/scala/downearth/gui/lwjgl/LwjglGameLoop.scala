package downearth.gui.lwjgl

import downearth._
import org.lwjgl.opengl.{DisplayMode, Display}
import org.lwjgl.input.{Keyboard, Mouse}
import simplex3d.math.Vec2i
import simplex3d.math.double._
import downearth.Config._
import downearth.util._
import downearth.gui._
import simplex3d.math.doublex.functions._
import downearth.world.World
import downearth.gui.MouseDown
import downearth.gui.MouseUp
import annotation.switch

/**
 * User: arne
 * Date: 29.04.13
 * Time: 00:39
 */
class LwjglGameLoop extends GameLoop with Publisher with Logger { gameLoop =>

  def swapBuffers() {
    Display.update()
  }

  def extraLoopOperation() {
    handleInput()
  }

  var windowMode:DisplayMode = null

  def handleInput() {
    import Mouse._
    import Keyboard._

    if( Display.isCloseRequested )
      finished = true

    val mouseDelta = Vec2i(getDX, getDY)
    // Move and rotate player
    val delta = Vec3(0)
    val delta_angle = Vec3(0)

    if( isKeyDown(keyForward) )
      delta.z -= 1
    if( isKeyDown(keyBackward) )
      delta.z += 1
    if( isKeyDown(keyLeft) )
      delta.x -= 1
    if( isKeyDown(keyRight) )
      delta.x += 1

    val mouseGrabIndependant: Int => Unit = {
      case `keyQuit` =>
        finished = true
      case `keyScreenshot` =>
        screenShot( "screenshot" )
      case `keyMouseGrab` =>
        Mouse setGrabbed !Mouse.isGrabbed
      case `keyPlayerReset` =>
        Player.resetPos
      case `keyStreaming` =>
        streamWorld = !streamWorld
      case `keyWireframe` =>
        wireframe = !wireframe
      case `keyFrustumCulling` =>
        frustumCulling = !frustumCulling
      case `keyTurbo` =>
        turbo = ! turbo
        log.println(s"Turbo is ${if(turbo) "on" else "off"}." )
      case `keyPausePhysics` =>
        BulletPhysics.pause = !BulletPhysics.pause
      case `keyDebugDraw` =>
        debugDraw = !debugDraw
      case `keyToggleGhostPlayer` =>
        Player.toggleGhost
      case `keyToggleInventory` =>
        MainWidget.inventory.visible = !MainWidget.inventory.visible
      case `keyJump` =>
        Player.jump
      case `keyIncOctreeDepth` =>
        World.octree.incDepth()
      case `keyToggleFullScreen` =>
        if( Display.isFullscreen ) {
          Display.setDisplayModeAndFullscreen(windowMode)
          MainWidget.resize( Vec2i(windowMode.getWidth, windowMode.getHeight) )
        }
        else {
          windowMode = Display.getDisplayMode
          val mode = Display.getDesktopDisplayMode
          assert(mode.isFullscreenCapable)
          Display.setDisplayModeAndFullscreen(mode)
          MainWidget.resize( Vec2i(mode.getWidth, mode.getHeight) )
        }
      case _ =>
    }

    if( Mouse.isGrabbed ) {

      // rotate with mouse
      delta_angle.y = -mouseDelta.x/300.0
      delta_angle.x = mouseDelta.y/300.0

      // Turbo mode
      if( turbo && Mouse.isButtonDown(0) )
        Player.primaryAction

      // Keyboard Events
      while ( Keyboard.next ) {
        if (getEventKeyState) {
          mouseGrabIndependant(getEventKey)
        }
        // implement some mouse grab dependant keys here
      }

      // Mouse events
      while( Mouse.next ) {
        ( getEventButton, getEventButtonState ) match {
          case (0 , true) => // left down
          case (0 , false) => // left up
            Player.primaryAction
          case (1 , true) => // right down
          case (1 , false) => // right up
            //Player.secondarybutton
            Mouse setGrabbed false
            Mouse setCursorPosition( Display.getWidth / 2, Display.getHeight / 2)
          case (-1, false) => // wheel
          // Player.updownbutton( Mouse.getDWheel / 120 )
          case _ =>
        }
      }
    }
    else { // if Mouse is not grabbed

      // Keyboard Events
      while ( Keyboard.next ) {
        if( getEventKey != KEY_NONE ){
          if( getEventKeyState )
            publish( KeyPress(getEventKey) )
          else
            publish( KeyRelease(getEventKey) )
        }

        if ( getEventKeyState ) {
          mouseGrabIndependant(getEventKey)
          // implement some mouse grab dependant keys here
        }

        val c = Keyboard.getEventCharacter
        if( c.intValue != 0 ) {
          if( c.isControl ) {
            c match {
              case '\t' =>
                print("<tab>")
              case '\b' =>
                print("<back>")
              case '\n' =>
                print("<enter>")
              case '\r' =>
                print("<return>")
              case _ =>
                print(s"<${c.intValue}>")
            }
          }
          else
            print( Keyboard.getEventCharacter )
        }
      }

      if( Display.wasResized ) {
        MainWidget.resize(Vec2i(Display.getWidth, Display.getHeight))
      }

      // Mouse events
      while( Mouse.next ) {
        ( getEventButton, getEventButtonState ) match {
          case (n , true) if n >= 0 =>
            val event =  MouseDown(Vec2i(getEventX,Display.getHeight - getEventY), n)
            publish( event )
          case (n , false) if n >= 0 =>
            val event =  MouseUp(Vec2i(getEventX,Display.getHeight - getEventY), n)
            publish( event )
            if(n == 1)
              Mouse setGrabbed true
          case (-1, _) =>
            val dx =  Mouse.getEventDX
            val dy = -Mouse.getEventDY
            val x  =  Mouse.getEventX
            val y  = Display.getHeight - Mouse.getEventY
            val dW = Mouse.getDWheel
            assert( (dx != 0 || dy != 0) ^ (dW != 0) )
            if( dx != 0 || dy != 0 ) {
              publish( MouseMove(Vec2i(x-dx,y-dy),Vec2i(x,y)) )
            }
          case _ =>
        }
      }
    }

    val factor = if(turbo) cameraTurboSpeed else cameraSpeed
    Player.move(factor*(delta/max(1,length(delta)))*timeStep)
    Player.rotate(2.0*delta_angle)
  }
}
